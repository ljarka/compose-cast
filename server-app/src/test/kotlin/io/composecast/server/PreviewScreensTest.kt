package io.composecast.server

import io.composecast.server.sdk.composeCast
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewScreensTest {
    private var now = Instant.parse("2026-11-23T10:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }
    private val previews = PreviewScreens(clock, ttl = Duration.ofMinutes(30), max = 2)
    private val registry = composeCast { }

    @Test
    fun `ids are unguessable previews that expire`() {
        val id = previews.newId()
        assertTrue(PreviewScreens.isPreview(id) && id.length == "__preview-".length + 32, id)
        assertNotEquals(id, previews.newId())
        assertFailsWith<IllegalArgumentException> { previews.put("main", registry) }

        previews.put(id, registry)
        assertNotNull(previews[id])
        assertNull(previews["__preview-unknown"])
        now = now.plus(Duration.ofMinutes(30))
        assertNull(previews[id], "expired after the TTL")
    }

    @Test
    fun `at most max previews, the oldest goes first`() {
        val (a, b, c) = List(3) { previews.newId() }
        previews.put(a, registry)
        now = now.plusSeconds(1)
        previews.put(b, registry)
        now = now.plusSeconds(1)
        previews.put(c, registry)
        assertEquals(listOf(null, registry, registry), listOf(previews[a], previews[b], previews[c]))
    }

    @Test
    fun `one id per owner and screen while editing, updates in place, logout revokes (T-032)`() {
        val previews = PreviewScreens(clock, ttl = Duration.ofMinutes(30), max = 10)
        val first = composeCast { }
        val id = previews.open("user:1", "main") { first }
        assertEquals(id, previews.open("user:1", "main") { composeCast { } }, "a second click keeps the id")
        assertNotEquals(id, previews.open("user:2", "main") { first }, "another editor has its own")
        assertNotEquals(id, previews.open("user:1", "about") { first }, "another screen has its own")

        val edited = composeCast { }
        now = now.plus(Duration.ofMinutes(20))
        assertTrue(previews.update("user:1", "main") { edited })
        assertTrue(previews[id] === edited)
        now = now.plus(Duration.ofMinutes(20))
        assertNotNull(previews[id], "each update extends the preview")
        assertEquals(false, previews.update("user:3", "main") { edited }, "no open preview: nothing")

        previews.revoke("user:1")
        assertNull(previews[id])
        assertEquals(false, previews.update("user:1", "main") { edited })
        assertNotEquals(id, previews.open("user:1", "main") { first }, "after logout a new id")
    }

    @Test
    fun `an expired preview gets a new id`() {
        val id = previews.open("user:1", "main") { registry }
        now = now.plus(Duration.ofMinutes(31))
        assertNotEquals(id, previews.open("user:1", "main") { registry })
    }
}
