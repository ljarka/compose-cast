package io.composecast.server.store

import org.jetbrains.exposed.v1.core.Table

// Schema of the screens and settings (ADR 0016, T-007). Timestamps are ISO-8601 UTC strings.

internal object Screens : Table("screens") {
    val id = varchar("id", 128)

    /** Canonical screen JSON (`ScreenJson.encode`) of the unpublished edit; null = no pending draft. */
    val draftJson = text("draft_json").nullable()

    /** Canonical screen JSON served to apps; null = never published. */
    val liveJson = text("live_json").nullable()
    val updatedAt = varchar("updated_at", 40)
    val publishedAt = varchar("published_at", 40).nullable()
    val publishedBy = varchar("published_by", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Append-only: every publish (including imports and rollbacks) adds a row; rows are never changed. */
internal object ScreenHistory : Table("screen_history") {
    val id = integer("id").autoIncrement()
    val screenId = varchar("screen_id", 128).index()
    val json = text("json_text")
    val publishedAt = varchar("published_at", 40)
    val publishedBy = varchar("published_by", 255)
    override val primaryKey = PrimaryKey(id)
}

internal object AppSettings : Table("app_settings") {
    val key = varchar("key", 128)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}
