package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `live` on a screen (T-047, ADR 0029). */
class LiveTest {
    @Test
    fun `live roundtrips, defaults are omitted, and a missing live means a normal screen`() {
        val json = """{"id":"d","live":{"everyMs":250},"components":[{"type":"text","text":"x"}]}"""
        val screen = ScreenJson.decode(json, "d.json")
        assertEquals(Live(250), screen.live)
        assertEquals(screen, ScreenJson.decode(ScreenJson.encode(screen), "again"))
        assertTrue(
            "\"live\": {}" in ScreenJson.encode(screen.copy(live = Live())),
            ScreenJson.encode(screen.copy(live = Live())),
        )
        assertEquals(null, ScreenJson.decode("""{"id":"p"}""", "p.json").live)
    }

    @Test
    fun `everyMs must be between 100 ms and one hour`() {
        assertTrue(ScreenDefinition("d", live = Live(100)).problems().isEmpty())
        listOf(99, 3_600_001).forEach {
            assertEquals(
                listOf("live.everyMs: must be 100..3600000, was $it"),
                ScreenDefinition("d", live = Live(it)).problems(),
            )
        }
    }
}
