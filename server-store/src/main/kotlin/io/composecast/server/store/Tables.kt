package io.composecast.server.store

import org.jetbrains.exposed.v1.core.Table

// Schema of the screens and settings (ADR 0016, T-007). Timestamps are ISO-8601 UTC strings.

/** Drafts and live versions of JSON documents: screens, and custom components (T-049, ADR 0033). */
internal class Documents(name: String) : Table(name) {
    val id = varchar("id", 128)

    /** Canonical JSON (`ScreenJson.encode` / `ComponentJson.encode`) of the unpublished edit; null = none. */
    val draftJson = text("draft_json").nullable()

    /** Canonical JSON served to apps; null = never published. */
    val liveJson = text("live_json").nullable()
    val updatedAt = varchar("updated_at", 40)
    val publishedAt = varchar("published_at", 40).nullable()
    val publishedBy = varchar("published_by", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Append-only: every publish (including imports and rollbacks) adds a row; rows are never changed. */
internal class DocumentHistory(name: String, idColumn: String) : Table(name) {
    val id = integer("id").autoIncrement()
    val screenId = varchar(idColumn, 128).index()
    val json = text("json_text")
    val publishedAt = varchar("published_at", 40)
    val publishedBy = varchar("published_by", 255)
    override val primaryKey = PrimaryKey(id)
}

/** A document table with its history. */
internal class DocumentTables(val documents: Documents, val history: DocumentHistory)

internal val Screens = Documents("screens")
internal val ScreenHistory = DocumentHistory("screen_history", "screen_id")
internal val ScreenTables = DocumentTables(Screens, ScreenHistory)

internal val Components = Documents("components")
internal val ComponentHistory = DocumentHistory("component_history", "component_id")
internal val ComponentTables = DocumentTables(Components, ComponentHistory)

internal object AppSettings : Table("app_settings") {
    val key = varchar("key", 128)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}

/** Admin accounts (T-007 A2). `hash` is a bcrypt hash; the store never sees passwords. */
internal object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val hash = varchar("hash", 100)

    /** `admin` | `editor` | `viewer`; interpreted by `server-admin`. */
    val role = varchar("role", 16)
    override val primaryKey = PrimaryKey(id)
}

/** Login sessions. `id` is a hash of the cookie token, so a copied database grants no logins. */
internal object Sessions : Table("sessions") {
    val id = varchar("id", 64)
    val userId = integer("user_id").references(Users.id).index()
    val expiresAt = varchar("expires_at", 40)
    override val primaryKey = PrimaryKey(id)
}
