package io.composecast.server.store.db

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.format.DateTimeFormatter

data class ScreenRecord(
    val id: String,
    val title: String,
    val draftJson: String?,
    val liveJson: String?,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String?,
    val publishedBy: String?,
)

data class HistoryRecord(
    val ts: String,
    val publishedBy: String,
)

private fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

private fun nowTs(): String =
    Instant.now().toString().replace(":", "").replace("-", "").replace(".", "").take(16) + "Z"

object Screens {
    fun listScreens(): List<ScreenRecord> = transaction {
        ScreensTable.selectAll().map { row ->
            ScreenRecord(
                id = row[ScreensTable.id],
                title = row[ScreensTable.title],
                draftJson = row[ScreensTable.draftJson],
                liveJson = row[ScreensTable.liveJson],
                createdAt = row[ScreensTable.createdAt],
                updatedAt = row[ScreensTable.updatedAt],
                publishedAt = row[ScreensTable.publishedAt],
                publishedBy = row[ScreensTable.publishedBy],
            )
        }
    }

    fun getScreen(id: String): ScreenRecord? = transaction {
        ScreensTable.selectAll().where { ScreensTable.id eq id }.singleOrNull()?.let { row ->
            ScreenRecord(
                id = row[ScreensTable.id],
                title = row[ScreensTable.title],
                draftJson = row[ScreensTable.draftJson],
                liveJson = row[ScreensTable.liveJson],
                createdAt = row[ScreensTable.createdAt],
                updatedAt = row[ScreensTable.updatedAt],
                publishedAt = row[ScreensTable.publishedAt],
                publishedBy = row[ScreensTable.publishedBy],
            )
        }
    }

    fun getDraft(id: String): String? = transaction {
        ScreensTable.selectAll().where { ScreensTable.id eq id }
            .singleOrNull()?.get(ScreensTable.draftJson)
    }

    fun getLive(id: String): String? = transaction {
        ScreensTable.selectAll().where { ScreensTable.id eq id }
            .singleOrNull()?.get(ScreensTable.liveJson)
    }

    fun createScreen(id: String, title: String, draftJson: String) {
        val now = nowIso()
        transaction {
            ScreensTable.insert {
                it[ScreensTable.id] = id
                it[ScreensTable.title] = title
                it[ScreensTable.draftJson] = draftJson
                it[ScreensTable.liveJson] = null
                it[ScreensTable.createdAt] = now
                it[ScreensTable.updatedAt] = now
            }
        }
    }

    fun saveDraft(id: String, json: String) {
        transaction {
            ScreensTable.update({ ScreensTable.id eq id }) {
                it[draftJson] = json
                it[updatedAt] = nowIso()
            }
        }
    }

    fun publish(id: String, publishedBy: String): Boolean = transaction {
        val row = ScreensTable.selectAll().where { ScreensTable.id eq id }.singleOrNull()
            ?: return@transaction false
        val json = row[ScreensTable.draftJson] ?: return@transaction false
        val now = nowIso()
        val ts = nowTs()
        ScreensTable.update({ ScreensTable.id eq id }) {
            it[liveJson] = json
            it[publishedAt] = now
            it[ScreensTable.publishedBy] = publishedBy
            it[updatedAt] = now
        }
        ScreenHistoryTable.insert {
            it[screenId] = id
            it[ScreenHistoryTable.ts] = ts
            it[jsonText] = json
            it[ScreenHistoryTable.publishedBy] = publishedBy
        }
        true
    }

    fun rollback(id: String, ts: String, publishedBy: String): Boolean = transaction {
        val histRow = ScreenHistoryTable.selectAll()
            .where { (ScreenHistoryTable.screenId eq id) and (ScreenHistoryTable.ts eq ts) }
            .singleOrNull() ?: return@transaction false
        val json = histRow[ScreenHistoryTable.jsonText]
        val now = nowIso()
        val newTs = nowTs()
        ScreensTable.update({ ScreensTable.id eq id }) {
            it[liveJson] = json
            it[publishedAt] = now
            it[ScreensTable.publishedBy] = publishedBy
            it[updatedAt] = now
        }
        ScreenHistoryTable.insert {
            it[screenId] = id
            it[ScreenHistoryTable.ts] = newTs
            it[jsonText] = json
            it[ScreenHistoryTable.publishedBy] = publishedBy
        }
        true
    }

    fun listHistory(id: String): List<HistoryRecord> = transaction {
        ScreenHistoryTable.selectAll()
            .where { ScreenHistoryTable.screenId eq id }
            .orderBy(ScreenHistoryTable.ts)
            .map { row ->
                HistoryRecord(
                    ts = row[ScreenHistoryTable.ts],
                    publishedBy = row[ScreenHistoryTable.publishedBy],
                )
            }
    }

    fun deleteScreen(id: String): Boolean = transaction {
        val deleted = ScreensTable.selectAll().where { ScreensTable.id eq id }.singleOrNull() != null
        if (deleted) {
            ScreensTable.update({ ScreensTable.id eq id }) {
                it[draftJson] = null
                it[liveJson] = null
                it[updatedAt] = nowIso()
            }
        }
        deleted
    }
}
