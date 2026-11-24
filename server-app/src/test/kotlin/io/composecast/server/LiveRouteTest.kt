package io.composecast.server

import io.composecast.protocol.Headers
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.DataRequest
import io.composecast.server.sdk.Documents
import io.composecast.server.sdk.composeCast
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `/screens/{id}/live` (T-047 step 2, ADR 0029). */
class LiveRouteTest {

    /** Each fetch returns the next CPU value, so every tick has new data. */
    private class Ticker : DataLoader {
        private var cpu = 10
        override fun load(request: DataRequest): String = """{"cpu": ${cpu.also { cpu += 10 }}}"""
    }

    private val screens = LiveScreens(
        composeCast(dataLoader = Ticker()) {
            screen("dash") {
                live(everyMs = 100)
                data("m", url = "https://example.com/metrics", cacheSeconds = 0)
                gauge("{{m.cpu}}", label = "{{m.cpu}}%")
            }
            screen("still") {
                live(everyMs = 100)
                text("Nothing changes")
            }
            screen("plain") { text("Not live") }
        },
    )

    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(screens) }
        block()
    }

    private fun ApplicationTestBuilder.wsClient() = createClient { install(WebSockets) }

    @Test
    fun `GET marks live screens with their interval`() = test {
        assertEquals("100", client.get("/screens/dash").headers[Headers.LIVE])
        assertNull(client.get("/screens/plain").headers[Headers.LIVE])
    }

    @Test
    fun `first frame is the full document, the next ones are data updates`() = test {
        wsClient().webSocket("/screens/dash/live") {
            val frames = withTimeout(5_000) { List(3) { (incoming.receive() as Frame.Binary).readBytes() } }
            val dumps = frames.map(Documents::describe)
            assertFalse("DOC_DATA_UPDATE" in dumps[0], dumps[0])
            assertTrue(dumps.drop(1).all { "DOC_DATA_UPDATE 1" in it }, dumps.joinToString("\n---\n"))
            // Values move (10 → 20 → 30 %), the structure stays the same.
            assertTrue("= 0.1" in dumps[0] && "= 0.2" in dumps[1] && "= 0.3" in dumps[2], dumps.joinToString())
            assertEquals(1, frames.map(Documents::structure).toSet().size)
        }
    }

    @Test
    fun `an unchanged screen sends only the first frame`() = test {
        wsClient().webSocket("/screens/still/live") {
            withTimeout(5_000) { incoming.receive() }
            // Several ticks (100 ms) pass without a frame.
            val next = withTimeout(5_000) { kotlinx.coroutines.withTimeoutOrNull(600) { incoming.receive() } }
            assertNull(next)
        }
    }

    @Test
    fun `a screen that is not live, or unknown, closes with the reason`() = test {
        for ((path, reason) in listOf("plain" to "is not live", "nope" to "not found")) {
            wsClient().webSocket("/screens/$path/live") {
                val close = withTimeout(5_000) { closeReason.await() }
                assertEquals(CloseReason.Codes.CANNOT_ACCEPT.code, close?.code)
                assertTrue(reason in close?.message.orEmpty(), "$path: $close")
            }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/screens/nope").status)
    }
}
