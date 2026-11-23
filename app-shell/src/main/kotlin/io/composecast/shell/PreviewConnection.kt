package io.composecast.shell

import android.content.Context
import androidx.core.content.edit
import java.net.URI
import java.net.URLDecoder

/**
 * The compose-cast Preview app (T-045, ADR 0024; `app.preview=true`): the server is chosen at
 * runtime instead of `app.serverUrl`. It arrives as a link `composecast://connect?url=<encoded>`
 * (opened from the server's `/connect` page, which the admin's QR code points to) or is typed in.
 * B2: `&screen=<id>` (a draft preview `__preview-…`) is opened on top of `main`.
 */
internal object PreviewLink {
    const val SCHEME = "composecast"
    const val HOST = "connect"
    private val SCREEN = Regex("[A-Za-z0-9_-]{1,128}")

    data class Link(val serverUrl: String, val screen: String?)

    /** A `composecast://connect?url=…[&screen=…]` link, or null for anything else or a bad URL. */
    fun parse(link: String?): Link? {
        val uri = link?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true) || !uri.host.equals(HOST, ignoreCase = true)) return null
        val query = uri.rawQuery.orEmpty().split('&')
            .associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8.name())
            }
        val url = query["url"]?.let(::normalize) ?: return null
        return Link(url, query["screen"]?.takeIf { SCREEN.matches(it) })
    }

    /** The server URL of a connect link (see [parse]). */
    fun serverUrl(link: String?): String? = parse(link)?.serverUrl

    /**
     * A typed or linked server address as the client needs it: `http(s)://host[:port][/path]`
     * without a trailing slash; no scheme = `https://`. Null when it is not such an address.
     */
    fun normalize(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Scheme check before trimming slashes: "https://" alone must not become "https://https:".
        val withScheme = (if ("://" in trimmed) trimmed else "https://$trimmed").trimEnd('/')
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return withScheme
    }
}

/** The Preview app's chosen server, kept across restarts. */
internal class PreviewServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("composecast_preview", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY, null)
        set(value) {
            prefs.edit { if (value == null) remove(KEY) else putString(KEY, value) }
        }

    private companion object {
        const val KEY = "server_url"
    }
}
