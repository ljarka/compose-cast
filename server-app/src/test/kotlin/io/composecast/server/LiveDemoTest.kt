package io.composecast.server

import io.composecast.server.demo.liveScreen
import io.composecast.server.sdk.LiveFrames
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.composeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The demo `live` screen keeps its structure while its values move: data updates only (T-047). */
class LiveDemoTest {
    @Test
    fun `30 seconds of the live demo are one full document and then data updates`() {
        var now = 1_700_000_000_000L
        val registry = composeCast { liveScreen { now } }
        val frames = LiveFrames("live", RenderContext()) { registry }
        val sent = (0 until 30).mapNotNull { frames.next().also { now += 1000 } }
        assertTrue(sent.first().full)
        assertEquals(30, sent.size, "every second has new values")
        assertEquals(
            emptyList(),
            sent.drop(1).withIndex().filter {
                it.value.full
            }.map { it.index + 1 },
            "full frames after the first",
        )
        assertEquals(1000, frames.everyMs)
    }
}
