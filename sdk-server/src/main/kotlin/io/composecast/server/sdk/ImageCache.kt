package io.composecast.server.sdk

/**
 * Thread-safe LRU cache of loaded images, shared by all renders of one registry.
 *
 * - At most [maxEntries] images (least recently used evicted).
 * - A failed load (`null`) is remembered only for [failureTtlMillis], so a transient network error
 *   does not leave a placeholder until the server restarts.
 * - Loading runs outside the lock: a slow fetch never blocks renders of other screens (two
 *   concurrent first requests for the same URL may both load it; harmless).
 */
internal class ImageCache<T : Any>(
    private val maxEntries: Int = 256,
    private val failureTtlMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<T>(val value: T?, val expiresAt: Long)

    private val entries = object : LinkedHashMap<String, Entry<T>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<T>>) = size > maxEntries
    }

    fun get(url: String, load: (String) -> T?): T? {
        synchronized(entries) {
            val cached = entries[url]
            if (cached != null && cached.expiresAt > clock()) return cached.value
        }
        val value = load(url)
        val expiresAt = if (value == null) clock() + failureTtlMillis else Long.MAX_VALUE
        synchronized(entries) { entries[url] = Entry(value, expiresAt) }
        return value
    }
}
