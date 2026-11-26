package io.composecast.server.store

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant

/**
 * documents of the admin mode (T-007). Stores screen JSON as text and never parses it: callers
 * validate (`ScreenDefinition.problems()`) before [saveDraft] / [publish], so the store stays
 * independent of the screen model and nothing invalid is ever stored as live.
 */
open class ScreenStore internal constructor(
    private val database: StoreDatabase,
    private val clock: Clock,
    tables: DocumentTables,
) {
    constructor(database: StoreDatabase, clock: Clock = Clock.systemUTC()) : this(database, clock, ScreenTables)

    private val documents = tables.documents
    private val history = tables.history

    data class StoredScreen(
        val id: String,
        val draftJson: String?,
        val liveJson: String?,
        val updatedAt: Instant,
        val publishedAt: Instant?,
        val publishedBy: String?,
    )

    data class Revision(
        val id: Int,
        val screenId: String,
        val json: String,
        val publishedAt: Instant,
        val publishedBy: String,
    )

    fun isEmpty(): Boolean = database.tx { documents.selectAll().empty() }

    /** All screens, ordered by id. */
    fun list(): List<StoredScreen> = database.tx { documents.selectAll().orderBy(documents.id).map(::screen) }

    fun get(id: String): StoredScreen? =
        database.tx { documents.selectAll().where { documents.id eq id }.singleOrNull()?.let(::screen) }

    /** Removes [id] and its history; returns true when a row was deleted. */
    fun delete(id: String): Boolean = database.tx {
        history.deleteWhere { history.screenId eq id }
        documents.deleteWhere { documents.id eq id } > 0
    }

    /** Removes a single past revision [revisionId] of [screenId]; returns true when a row was deleted. */
    fun deleteRevision(screenId: String, revisionId: Int): Boolean = database.tx {
        history.deleteWhere {
            (history.screenId eq screenId) and (history.id eq revisionId)
        } > 0
    }

    /** Live JSON of every published screen, ordered by id: what apps are served. */
    fun live(): Map<String, String> = database.tx {
        documents.selectAll().where { documents.liveJson.isNotNull() }.orderBy(documents.id)
            .associate { it[documents.id] to it[documents.liveJson]!! }
    }

    /** Stores [json] as the pending edit of [id] (creating the screen if new); apps are unaffected. */
    fun saveDraft(id: String, json: String) {
        val now = now()
        database.tx {
            val updated = documents.update({ documents.id eq id }) {
                it[draftJson] = json
                it[updatedAt] = now
            }
            if (updated == 0) {
                documents.insert {
                    it[documents.id] = id
                    it[draftJson] = json
                    it[updatedAt] = now
                }
            }
        }
    }

    /** Clears the pending draft of a published screen, reverting the editor to its live version. */
    fun discardDraft(id: String): Boolean = database.tx {
        documents.update(
            { (documents.id eq id) and documents.liveJson.isNotNull() and documents.draftJson.isNotNull() },
        ) {
            it[draftJson] = null
        } > 0
    }

    /**
     * Publishes all [screens] (id → JSON) in **one** transaction: an interrupted first-start import
     * leaves the database empty, so the next start imports again.
     */
    fun publishAll(screens: Map<String, String>, by: String) {
        database.tx { screens.forEach { (id, json) -> publishIn(id, json, by, now()) } }
    }

    /** Makes [json] the live version of [id], clears its draft and appends a history row. */
    fun publish(id: String, json: String, by: String) {
        database.tx { publishIn(id, json, by, now()) }
    }

    /** Runs inside the caller's transaction ([StoreDatabase.tx]). */
    private fun JdbcTransaction.publishIn(
        id: String,
        json: String,
        by: String,
        now: String,
        keepDraft: Boolean = false,
    ) {
        val updated = documents.update({ documents.id eq id }) {
            it[liveJson] = json
            if (!keepDraft) it[draftJson] = null
            it[updatedAt] = now
            it[publishedAt] = now
            it[publishedBy] = by
        }
        if (updated == 0) {
            documents.insert {
                it[documents.id] = id
                it[liveJson] = json
                it[updatedAt] = now
                it[publishedAt] = now
                it[publishedBy] = by
            }
        }
        history.insert {
            it[screenId] = id
            it[history.json] = json
            it[publishedAt] = now
            it[publishedBy] = by
        }
    }

    /** Published versions of [id], newest first. */
    fun history(id: String): List<Revision> = database.tx {
        history.selectAll().where { history.screenId eq id }
            .orderBy(history.id, SortOrder.DESC)
            .map(::revision)
    }

    /** Revision [revisionId] of [id], or null. */
    fun revision(id: String, revisionId: Int): Revision? = database.tx {
        history.selectAll()
            .where { (history.screenId eq id) and (history.id eq revisionId) }
            .singleOrNull()?.let(::revision)
    }

    /**
     * Publishes revision [revisionId] of [id] again (a new history row; the old one stays). A pending
     * draft is kept: rolling back the live version never throws away unpublished work.
     */
    fun rollback(id: String, revisionId: Int, by: String) {
        val revision =
            revision(id, revisionId) ?: throw IllegalArgumentException("'$id' has no revision $revisionId")
        database.tx { publishIn(id, revision.json, by, now(), keepDraft = true) }
    }

    private fun now() = Instant.now(clock).toString()

    private fun screen(row: ResultRow) = StoredScreen(
        id = row[documents.id],
        draftJson = row[documents.draftJson],
        liveJson = row[documents.liveJson],
        updatedAt = Instant.parse(row[documents.updatedAt]),
        publishedAt = row[documents.publishedAt]?.let(Instant::parse),
        publishedBy = row[documents.publishedBy],
    )

    private fun revision(row: ResultRow) = Revision(
        id = row[history.id],
        screenId = row[history.screenId],
        json = row[history.json],
        publishedAt = Instant.parse(row[history.publishedAt]),
        publishedBy = row[history.publishedBy],
    )
}

/**
 * Custom components of the admin mode (T-049, ADR 0033): drafts, publish, history and rollback like
 * screens, in their own tables (`components`, `component_history`). Callers validate
 * (`CustomComponents.check`) before storing.
 */
class ComponentStore(database: StoreDatabase, clock: Clock = Clock.systemUTC()) :
    ScreenStore(database, clock, ComponentTables)
