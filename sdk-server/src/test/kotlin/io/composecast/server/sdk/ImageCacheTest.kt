package io.composecast.server.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ImageCacheTest {
    private var now = 0L
    private val loads = mutableListOf<String>()

    private fun cache(max: Int = 2) = ImageCache<String>(maxEntries = max, failureTtlMillis = 1_000, clock = { now })

    @Test
    fun `successes are cached, failures only for the TTL`() {
        val cache = cache()
        var online = false
        val load = { url: String ->
            loads += url
            if (online) "img" else null
        }
        assertNull(cache.get("a", load))
        assertNull(cache.get("a", load))
        assertEquals(1, loads.size) // failure cached within TTL
        online = true
        now += 1_001
        assertEquals("img", cache.get("a", load))
        now += 1_000_000
        assertEquals("img", cache.get("a", load))
        assertEquals(2, loads.size) // success cached forever
    }

    @Test
    fun `least recently used entry is evicted`() {
        val cache = cache(max = 2)
        val load = { url: String ->
            loads += url
            url
        }
        cache.get("a", load)
        cache.get("b", load)
        cache.get("a", load)
        cache.get("c", load) // evicts b
        cache.get("a", load)
        cache.get("b", load)
        assertEquals(listOf("a", "b", "c", "b"), loads)
    }

    @Test
    fun `http image loader rejects non-http urls without touching the network`() {
        assertFailsWith<IllegalArgumentException> { HttpImageLoader.load("file:///etc/passwd") }
    }
}
