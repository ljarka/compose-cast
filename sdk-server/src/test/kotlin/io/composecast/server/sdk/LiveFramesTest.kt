package io.composecast.server.sdk

import io.composecast.server.sdk.render.RemoteComposeRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Live screens (T-047 step 2, ADR 0029): which frame goes to the app on each tick. */
class LiveFramesTest {

    /** Serves whatever [body] currently holds; cacheSeconds = 0 so every tick fetches. */
    private class Metrics(var body: String) : DataLoader {
        override fun load(request: DataRequest) = body
    }

    private fun registry(metrics: Metrics) = composeCast(dataLoader = metrics) {
        screen("dash") {
            live(everyMs = 500)
            data("m", url = "https://example.com/metrics", cacheSeconds = 0)
            gauge("{{m.cpu}}", label = "{{m.cpu}}%")
            kpi("{{m.requests}}", label = "Requests")
            sparkline("{{m.history}}")
        }
    }

    private fun frames(registry: () -> ScreenRegistry?) = LiveFrames("dash", RenderContext(), registry)

    @Test
    fun `first frame is full, unchanged data sends nothing, changed data sends a data update`() {
        val metrics = Metrics("""{"cpu": 20, "requests": "1,000", "history": [1, 2, 3]}""")
        val registry = registry(metrics)
        val frames = frames { registry }

        val first = assertNotNull(frames.next())
        assertTrue(first.full)
        assertFalse("DOC_DATA_UPDATE" in RemoteComposeRenderer.describe(first.document))

        assertNull(frames.next(), "same data → no frame")

        metrics.body = """{"cpu": 70, "requests": "1,500", "history": [3, 1, 2]}"""
        val update = assertNotNull(frames.next())
        assertFalse(update.full)
        val dump = RemoteComposeRenderer.describe(update.document)
        assertTrue("DOC_DATA_UPDATE 1" in dump, dump)
        assertTrue("= 0.7" in dump && "1,500" in dump && "[1.0, 0.0, 0.5]" in dump, dump)
        // Same ids and operations as the full document, so the player can apply it by id.
        assertEquals(
            RemoteComposeRenderer.structure(first.document),
            RemoteComposeRenderer.structure(update.document),
        )
    }

    @Test
    fun `a change of structure sends the full document`() {
        val metrics = Metrics("""{"cpu": 20, "requests": "1,000", "history": [1, 2, 3]}""")
        val registry = registry(metrics)
        val frames = frames { registry }
        frames.next()

        metrics.body = """{"cpu": 20, "requests": "1,000", "history": [1, 2, 3, 4]}""" // one more line
        assertEquals(true, frames.next()?.full)

        metrics.body = """{"cpu": 0, "requests": "1,000", "history": [1, 2, 3, 4]}""" // no value arc at 0
        assertEquals(true, frames.next()?.full)
    }

    @Test
    fun `texts that become equal share one id, so the structure changes and the frame is full`() {
        // RemoteComposeWriter.addText reuses the id of an identical string (remote-compose.md behavior 21).
        val metrics = Metrics("""{"cpu": 20, "requests": "1,000", "history": [1, 2]}""")
        val registry = registry(metrics)
        val frames = frames { registry }
        frames.next()
        metrics.body = """{"cpu": 20, "requests": "Requests", "history": [1, 2]}"""
        assertEquals(true, frames.next()?.full)
    }

    @Test
    fun `a new registry (publish, reload) reaches an open stream`() {
        val metrics = Metrics("""{"cpu": 20, "requests": "1,000", "history": [1, 2]}""")
        var current = registry(metrics)
        val frames = frames { current }
        frames.next()
        current = composeCast(dataLoader = metrics) {
            screen("dash") {
                live()
                text("Redesigned")
            }
        }
        val frame = assertNotNull(frames.next())
        assertTrue(frame.full)
        assertNotEquals(-1, RemoteComposeRenderer.describe(frame.document).indexOf("Redesig"))
    }

    @Test
    fun `live is part of the definition, bound screens keep it`() {
        val registry = registry(Metrics("""{"cpu": 1, "requests": "1", "history": [1, 2]}"""))
        assertEquals(500, registry.definitions().getValue("dash").live?.everyMs)
        assertEquals(500, registry.render("dash")!!.definition.live?.everyMs)
    }
}
