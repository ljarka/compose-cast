package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * What a component does when tapped. Authored on the server (DSL, later JSON screen definitions),
 * carried inside the Remote Compose document as a host-action name ([ActionCodec]), and handled by
 * the app.
 *
 * There is deliberately no "unknown" subtype: every [Action] is authorable and serializable.
 * Forward compatibility (an old app receiving a newer action type) is handled by
 * [ActionCodec.decode] returning `null`.
 */
@Serializable
sealed interface Action {
    /** Open another server-driven screen. [params] are passed to the server (used from T-003). */
    @Serializable
    @SerialName("navigate")
    data class Navigate(val screen: String, val params: Map<String, String> = emptyMap()) : Action

    /** Open [url] outside the app (browser or the app registered for it). */
    @Serializable
    @SerialName("open_url")
    data class OpenUrl(val url: String) : Action

    /** Fetch the current screen again. */
    @Serializable
    @SerialName("reload")
    data object Reload : Action

    /** Analytics event; the app decides where to send it. */
    @Serializable
    @SerialName("track")
    data class Track(val event: String, val params: Map<String, String> = emptyMap()) : Action

    /** Host-defined behavior; [payload] is passed through unchanged. */
    @Serializable
    @SerialName("custom")
    data class Custom(val payload: String) : Action
}

/**
 * Encodes [Action]s into Remote Compose host-action names and back.
 *
 * Grammar (`enc` = URL encoding, UTF-8; the type is separated at the first `:`):
 * - `navigate:` enc(screen) [`?` enc(k)`=`enc(v) (`&` …)*]
 * - `open_url:` url — raw, the query of the URL is not parsed
 * - `reload:` — empty payload
 * - `track:` enc(event) [params as for navigate]
 * - `custom:` payload — raw
 *
 * Parameters keep insertion order, so encoding is deterministic. Remote Compose stores the name as
 * a string of at most [MAX_ENCODED_LENGTH] characters.
 */
object ActionCodec {
    /** Remote Compose `Limits.MAX_STRING_SIZE` for the host-action name (verified alpha23). */
    const val MAX_ENCODED_LENGTH: Int = 4000

    fun encode(action: Action): String = when (action) {
        is Action.Navigate -> "${Actions.NAVIGATE}:${enc(action.screen)}${query(action.params)}"
        is Action.OpenUrl -> "${Actions.OPEN_URL}:${action.url}"
        Action.Reload -> "${Actions.RELOAD}:"
        is Action.Track -> "${Actions.TRACK}:${enc(action.event)}${query(action.params)}"
        is Action.Custom -> "${Actions.CUSTOM}:${action.payload}"
    }

    /** Returns `null` for an unknown type or a malformed payload; never throws. */
    fun decode(raw: String): Action? = try {
        val colon = raw.indexOf(':')
        if (colon < 0) {
            null
        } else {
            val payload = raw.substring(colon + 1)
            when (raw.substring(0, colon)) {
                Actions.NAVIGATE -> nameAndParams(payload)?.let { (screen, params) -> Action.Navigate(screen, params) }
                Actions.OPEN_URL -> payload.takeIf { it.isNotEmpty() }?.let { Action.OpenUrl(it) }
                Actions.RELOAD -> if (payload.isEmpty()) Action.Reload else null
                Actions.TRACK -> nameAndParams(payload)?.let { (event, params) -> Action.Track(event, params) }
                Actions.CUSTOM -> Action.Custom(payload)
                else -> null
            }
        }
    } catch (e: IllegalArgumentException) {
        null // malformed percent-encoding
    }

    private fun query(params: Map<String, String>): String =
        if (params.isEmpty()) "" else params.entries.joinToString("&", prefix = "?") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun nameAndParams(payload: String): Pair<String, Map<String, String>>? {
        val name = dec(payload.substringBefore('?'))
        if (name.isEmpty()) return null
        val query = payload.substringAfter('?', missingDelimiterValue = "")
        val params = LinkedHashMap<String, String>()
        if (query.isNotEmpty()) {
            for (pair in query.split('&')) {
                val eq = pair.indexOf('=')
                if (eq <= 0) return null
                params[dec(pair.substring(0, eq))] = dec(pair.substring(eq + 1))
            }
        }
        return name to params
    }

    private fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)
    private fun dec(s: String): String = URLDecoder.decode(s, Charsets.UTF_8)
}
