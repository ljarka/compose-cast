package io.composecast.server.admin

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Human-readable dates in the panel (owner 2026-11-26). */
class DatesTest {
    private val now = Instant.parse("2026-11-26T18:30:00Z")

    private fun ago(iso: String) = humanTime(Instant.parse(iso), now)

    @Test
    fun `recent times are relative, older ones are dates`() {
        assertEquals("just now", ago("2026-11-26T18:29:40Z"))
        assertEquals("just now", ago("2026-11-26T18:31:00Z")) // a clock a little ahead
        assertEquals("1 minute ago", ago("2026-11-26T18:29:00Z"))
        assertEquals("25 minutes ago", ago("2026-11-26T18:05:00Z"))
        assertEquals("1 hour ago", ago("2026-11-26T17:10:00Z"))
        assertEquals("6 hours ago", ago("2026-11-26T12:00:00Z"))
        assertEquals("yesterday at 21:15", ago("2026-11-25T21:15:00Z"))
        assertEquals("3 days ago", ago("2026-11-23T09:00:00Z"))
        assertEquals("2 Nov", ago("2026-11-02T09:00:00Z"))
        assertEquals("14 Dec 2025", ago("2025-12-14T09:00:00Z"))
    }

    @Test
    fun `the exact time is for tooltips`() {
        assertEquals("26 Nov 2026, 18:57 UTC", exactTime(Instant.parse("2026-11-26T18:57:35.205773Z")))
    }
}
