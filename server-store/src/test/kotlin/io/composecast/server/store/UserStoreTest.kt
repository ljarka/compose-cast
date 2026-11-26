package io.composecast.server.store

import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserStoreTest {
    private val file = Files.createTempDirectory("composecast-users").resolve("compose-cast.db")
    private var now = Instant.parse("2026-11-23T10:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }
    private val store = UserStore(StoreDatabase.open(file), clock)

    @Test
    fun `the first account is created only when there is none`() {
        assertTrue(store.isEmpty())
        val admin = store.createIfNone("admin@example.com", "hash1", "admin")!!
        assertNull(store.createIfNone("other@example.com", "hash2", "admin"), "accounts exist: no bootstrap")
        assertEquals(admin, store.findByEmail("admin@example.com"))
        assertNull(store.findByEmail("other@example.com"))
        assertFailsWith<Exception>("emails are unique") { store.create("admin@example.com", "hash3", "viewer") }
    }

    @Test
    fun `sessions resolve to their user until they expire or are deleted`() {
        val user = store.create("editor@example.com", "hash", "editor")
        store.createSession("s1", user.id, now.plus(Duration.ofHours(1)))
        store.createSession("s2", user.id, now.plus(Duration.ofHours(2)))
        assertEquals(user.copy(lastLoginAt = now), store.sessionUser("s1"))
        assertNull(store.sessionUser("unknown"))

        store.deleteSession("s1")
        assertNull(store.sessionUser("s1"), "logged out")

        now = now.plus(Duration.ofHours(2))
        assertNull(store.sessionUser("s2"), "expired")
        assertEquals(0, store.deleteExpiredSessions(), "the expired lookup already deleted it")
    }

    @Test
    fun `expired sessions are cleaned up and data survives a reopen`() {
        val user = store.create("viewer@example.com", "hash", "viewer")
        store.createSession("old", user.id, now.plusMillis(500))
        store.createSession("new", user.id, now.plus(Duration.ofDays(1)))
        val loggedIn = user.copy(lastLoginAt = now)
        now = now.plusSeconds(1)
        assertEquals(1, store.deleteExpiredSessions())

        val reopened = UserStore(StoreDatabase.open(file), clock)
        assertEquals(loggedIn, reopened.sessionUser("new"))
    }

    @Test
    fun `list, role update, password reset and delete protect the last admin`() {
        val admin1 = store.create("a1@example.com", "h1", "admin")
        val editor = store.create("e@example.com", "h2", "editor")
        assertEquals(listOf(admin1, editor), store.list())

        assertFailsWith<UserStore.LastAdminException> { store.updateRole(admin1.id, "editor") }
        assertFailsWith<UserStore.LastAdminException> { store.delete(admin1.id) }

        assertTrue(store.updateRole(editor.id, "admin"))
        assertTrue(store.updateRole(admin1.id, "viewer"))
        assertEquals("viewer", store.findById(admin1.id)!!.role)

        store.createSession("s-keep", admin1.id, now.plus(Duration.ofHours(1)))
        store.createSession("s-drop", admin1.id, now.plus(Duration.ofHours(1)))
        assertTrue(store.updatePassword(admin1.id, "h-new", keepSessionId = "s-keep"))
        assertEquals("h-new", store.findById(admin1.id)!!.hash)
        assertNull(store.sessionUser("s-drop"))
        assertEquals(admin1.id, store.sessionUser("s-keep")?.id)

        assertTrue(store.delete(admin1.id))
        assertNull(store.findById(admin1.id))
        assertNull(store.sessionUser("s-keep"))
    }
}
