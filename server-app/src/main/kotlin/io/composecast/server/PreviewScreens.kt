package io.composecast.server

import io.composecast.server.sdk.ScreenRegistry
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * Draft previews (T-045 B2, ADR 0027): each "Preview" in the admin editor gets an unguessable
 * screen id `__preview-<token>` served by `GET /screens/{id}` like any screen, for [ttl], in memory
 * only (gone on restart, never in the database or `/screens`). At most [max] at a time; the oldest go first.
 */
class PreviewScreens(
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofMinutes(30),
    private val max: Int = 100,
) {
    private class Entry(val registry: ScreenRegistry, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    /** A fresh preview id; build the screen with it, then [put] it. */
    fun newId(): String = PREFIX + HexFormat.of().formatHex(ByteArray(16).also(random::nextBytes))

    fun put(id: String, registry: ScreenRegistry) {
        require(isPreview(id)) { "not a preview id: $id" }
        val now = clock.instant()
        entries.entries.removeIf { it.value.expiresAt <= now }
        while (entries.size >= max) {
            entries.entries.minByOrNull { it.value.expiresAt }?.let { entries.remove(it.key) } ?: break
        }
        entries[id] = Entry(registry, now.plus(ttl))
    }

    /** The registry serving preview [id], or null (unknown or expired). */
    operator fun get(id: String): ScreenRegistry? {
        val entry = entries[id] ?: return null
        if (entry.expiresAt <= clock.instant()) {
            entries.remove(id)
            return null
        }
        return entry.registry
    }

    companion object {
        const val PREFIX = "__preview-"

        fun isPreview(id: String) = id.startsWith(PREFIX)
    }
}
