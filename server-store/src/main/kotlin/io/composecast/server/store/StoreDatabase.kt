package io.composecast.server.store

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * The admin database: one SQLite file (ADR 0016), usually `$COMPOSECAST_DATA_DIR/compose-cast.db`.
 * Feature modules own their tables and create them with [createTables]; access goes through [tx].
 * Schema evolution is tracked via SQLite's `PRAGMA user_version` (T-053).
 */
class StoreDatabase private constructor(private val database: Database) {
    /** Runs [block] in one transaction (SQLite: serializable, Exposed's default for the dialect). */
    fun <T> tx(block: JdbcTransaction.() -> T): T = transaction(database, statement = block)

    /** Creates missing [tables] (`CREATE TABLE IF NOT EXISTS`); existing data is never touched. */
    fun createTables(vararg tables: Table) = tx { SchemaUtils.create(*tables) }

    /** Lightweight readiness probe for `/health` (T-054). */
    fun ping(): Boolean = tx {
        exec("SELECT 1", explicitStatementType = StatementType.SELECT) { rs -> rs.next() } ?: false
    }

    /** Current SQLite `PRAGMA user_version`. */
    fun schemaVersion(): Int = tx {
        exec("PRAGMA user_version", explicitStatementType = StatementType.SELECT) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        } ?: 0
    }

    private fun migrate() = tx {
        val current = exec("PRAGMA user_version", explicitStatementType = StatementType.SELECT) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        } ?: 0
        check(current <= SCHEMA_VERSION) {
            "Database schema version $current is newer than supported version $SCHEMA_VERSION; upgrade the server"
        }
        if (current < 1) {
            SchemaUtils.create(Screens, ScreenHistory, Components, ComponentHistory, AppSettings, Users, Sessions)
            exec("PRAGMA user_version = 1", explicitStatementType = StatementType.UPDATE)
        }
        if (current < 2) {
            val hasLastLogin = exec("PRAGMA table_info(users)", explicitStatementType = StatementType.SELECT) { rs ->
                var found = false
                while (rs.next()) {
                    if (rs.getString("name") == "last_login_at") found = true
                }
                found
            } ?: false
            if (!hasLastLogin) {
                exec(
                    "ALTER TABLE users ADD COLUMN last_login_at VARCHAR(40)",
                    explicitStatementType = StatementType.UPDATE,
                )
            }
            exec("PRAGMA user_version = 2", explicitStatementType = StatementType.UPDATE)
        }
    }

    companion object {
        const val SCHEMA_VERSION: Int = 2

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
                        it.execute("PRAGMA foreign_keys = ON")
                    }
                },
            )
            return StoreDatabase(database).also { it.migrate() }
        }
    }
}
