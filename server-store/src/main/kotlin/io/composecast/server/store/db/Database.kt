package io.composecast.server.store.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val email = text("email").uniqueIndex()
    val hash = text("hash")
    val role = text("role").default("editor")
    override val primaryKey = PrimaryKey(id)
}

object ScreensTable : Table("screens") {
    val id = text("id")
    val title = text("title")
    val draftJson = text("draft_json").nullable()
    val liveJson = text("live_json").nullable()
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")
    val publishedAt = text("published_at").nullable()
    val publishedBy = text("published_by").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ScreenHistoryTable : Table("screen_history") {
    val id = integer("id").autoIncrement()
    val screenId = text("screen_id")
    val ts = text("ts")
    val jsonText = text("json_text")
    val publishedBy = text("published_by")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(screenId, ts)
    }
}

object AppSettingsTable : Table("app_settings") {
    val key = text("key")
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}

object DatabaseFactory {
    fun init(dataDir: File) {
        dataDir.mkdirs()
        val dbFile = dataDir.resolve("compose-cast.db")
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction { Migrations.run() }
    }
}

object Migrations {
    fun run() {
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            ScreensTable,
            ScreenHistoryTable,
            AppSettingsTable,
        )
    }
}
