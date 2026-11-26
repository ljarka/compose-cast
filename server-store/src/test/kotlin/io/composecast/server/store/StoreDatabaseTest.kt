package io.composecast.server.store

import java.nio.file.Files
import java.sql.DriverManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoreDatabaseTest {

    @Test
    fun `fresh database sets schema version, enforces foreign keys, and responds to ping`() {
        val dir = Files.createTempDirectory("composecast-db-test")
        try {
            val db = StoreDatabase.open(dir.resolve("test.db"))
            assertEquals(StoreDatabase.SCHEMA_VERSION, db.schemaVersion())
            assertTrue(db.ping())

            val users = UserStore(db)
            // Foreign key Sessions.user_id -> Users.id must be enforced.
            assertFailsWith<Exception> {
                users.createSession("orphan-token", userId = 9999, expiresAt = Instant.parse("2030-01-01T00:00:00Z"))
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `migrates pre-versioned database without losing data and rejects newer schema`() {
        val dir = Files.createTempDirectory("composecast-db-migrate")
        val file = dir.resolve("test.db")
        try {
            val first = StoreDatabase.open(file)
            SettingsStore(first)[SettingsStore.HOME_SCREEN] = "welcome"

            // Simulate a pre-T-053 database with user_version = 0.
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { conn ->
                conn.createStatement().use { it.execute("PRAGMA user_version = 0") }
            }

            val reopened = StoreDatabase.open(file)
            assertEquals(StoreDatabase.SCHEMA_VERSION, reopened.schemaVersion())
            assertEquals("welcome", SettingsStore(reopened)[SettingsStore.HOME_SCREEN])

            // Simulate a database created by a newer server version.
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { conn ->
                conn.createStatement().use { it.execute("PRAGMA user_version = 99") }
            }
            assertFailsWith<IllegalStateException> { StoreDatabase.open(file) }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
