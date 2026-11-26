package io.composecast.server.store

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * The admin database: one SQLite file (ADR 0016), usually `$COMPOSECAST_DATA_DIR/compose-cast.db`.
 * Feature modules own their tables and create them with [createTables]; access goes through [tx].
 */
class StoreDatabase private constructor(private val database: Database) {
    /** Runs [block] in one transaction (SQLite: serializable, Exposed's default for the dialect). */
    fun <T> tx(block: JdbcTransaction.() -> T): T = transaction(database, statement = block)

    /** Creates missing [tables] (`CREATE TABLE IF NOT EXISTS`); existing data is never touched. */
    fun createTables(vararg tables: Table) = tx { SchemaUtils.create(*tables) }

    companion object {
        fun open(file: Path): StoreDatabase {
            file.toAbsolutePath().parent?.createDirectories()
            val database = Database.connect(
                url = "jdbc:sqlite:${file.toAbsolutePath()}",
                driver = "org.sqlite.JDBC",
                setupConnection = { connection ->
                    connection.createStatement().use {
                        // A reader never blocks the writer; wait instead of failing on a busy file.
                        it.execute("PRAGMA journal_mode = WAL")
                        it.execute("PRAGMA busy_timeout = 5000")
                    }
                },
            )
            return StoreDatabase(database).also {
                it.createTables(Screens, ScreenHistory, Components, ComponentHistory, AppSettings, Users, Sessions)
            }
        }
    }
}
