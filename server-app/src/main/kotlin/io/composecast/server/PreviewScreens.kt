package io.composecast.server

import io.composecast.protocol.PreviewIds
import io.composecast.server.sdk.ScreenRegistry
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * Draft previews (T-045 B2, ADR 0027; live since T-032, ADR 0030): an unguessable screen id
 * `__preview-<token>` served by `GET /screens/{id}` and `/screens/{id}/live` like any screen, in
 * memory only (gone on restart, never in the database or `/screens`). One id per owner (admin
 * user) and screen: it stays the same while the owner edits, every [update] replaces the screen in
 * place and extends it by [ttl]; [revoke] ends all of an owner's previews (logout). At most [max]
 * at a time; the oldest go first.
 */
class PreviewScreens(
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofMinutes(30),
    private val max: Int = 100,
) {
    private class Entry(val registry: ScreenRegistry, val expiresAt: Instant, val owner: String?)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    /** `owner + NUL + screen id` → preview id. */
    private val byOwner = ConcurrentHashMap<String, String>()

    /** A fresh preview id; build the screen with it, then [put] it. */
    fun newId(): String = PREFIX + HexFormat.of().formatHex(ByteArray(16).also(random::nextBytes))

    /**
     * The preview of [screenId] by [owner]: the current id while it is valid, else a new one;
     * [build] makes its registry for that id. Synchronized: two clicks give one id.
     */
    @Synchronized
    fun open(owner: String, screenId: String, build: (String) -> ScreenRegistry): String {
        val key = "$owner\u0000$screenId"
        val id = byOwner[key]?.takeIf { get(it) != null } ?: newId()
        put(id, build(id), owner)
        byOwner[key] = id
        return id
    }

    /** Replaces the open preview of [screenId] by [owner] (a saved edit); false when there is none. */
    @Synchronized
    fun update(owner: String, screenId: String, build: (String) -> ScreenRegistry): Boolean {
        val id = byOwner["$owner\u0000$screenId"]?.takeIf { get(it) != null } ?: return false
        put(id, build(id), owner)
        return true
    }

    /** Ends every preview of [owner] at once (logout): open phones get "not found" on their next tick. */
    @Synchronized
    fun revoke(owner: String) {
        entries.entries.removeIf { it.value.owner == owner }
        byOwner.keys.removeIf { it.startsWith("$owner\u0000") }
    }

    fun put(id: String, registry: ScreenRegistry, owner: String? = null) {
        require(isPreview(id)) { "not a preview id: $id" }
        val now = clock.instant()
        entries.entries.removeIf { it.value.expiresAt <= now }
        while (entries.size >= max) {
            entries.entries.minByOrNull { it.value.expiresAt }?.let { entries.remove(it.key) } ?: break
        }
        entries[id] = Entry(registry, now.plus(ttl), owner)
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
        const val PREFIX = PreviewIds.PREFIX

        fun isPreview(id: String) = PreviewIds.isPreview(id)
    }
}
