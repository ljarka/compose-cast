package io.composecast.server

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import java.net.URLEncoder

/**
 * `GET /connect` (T-045, ADR 0024): where the admin panel's QR code points. Any phone camera opens
 * an https link in the browser; this page then hands the server's address to the compose-cast
 * Preview app (`intent://` link with a fallback to the APK download, plus the plain
 * `composecast://connect` link and the address to type in).
 */
internal object ConnectPage {
    /** Application id of the Preview app (`app-preview/compose-cast.properties`). */
    const val PREVIEW_PACKAGE = "io.composecast.preview"
    const val PREVIEW_APK = "https://github.com/ljarka/compose-cast/releases/latest/download/compose-cast-preview.apk"

    /** Host header characters of a hostname, IPv4/IPv6 address and port; anything else is refused. */
    private val HOST = Regex("[A-Za-z0-9.\\-:\\[\\]]{1,255}")

    /**
     * This server's address as the phone reaches it: `X-Forwarded-Proto` (Caddy, https profile) or
     * the connection's scheme, and the `Host` header. Null for a malformed host.
     */
    fun serverUrl(call: ApplicationCall): String? {
        val host = call.request.headers["Host"]?.takeIf { HOST.matches(it) } ?: return null
        val forwarded = call.request.headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim()?.lowercase()
        val scheme = forwarded?.takeIf { it == "https" || it == "http" } ?: call.request.origin.scheme
        return "$scheme://$host"
    }

    fun html(serverUrl: String): String {
        val encoded = encode(serverUrl)
        val intent = "intent://connect?url=$encoded#Intent;scheme=composecast;package=$PREVIEW_PACKAGE;" +
            "S.browser_fallback_url=${encode(PREVIEW_APK)};end"
        val direct = "composecast://connect?url=$encoded"
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Connect · compose-cast Preview</title>
              <style>
                :root { color-scheme: light dark; }
                body { margin: 0; padding: 24px 16px; font: 17px/1.5 system-ui, sans-serif; max-width: 520px; margin: auto; }
                a.button { display: block; margin: 24px 0 12px; padding: 14px; text-align: center; border-radius: 10px;
                           background: #3b5bdb; color: #fff; text-decoration: none; font-weight: 600; }
                code { font-size: 15px; word-break: break-all; }
                .muted { opacity: .7; font-size: 15px; }
              </style>
            </head>
            <body>
              <h1>Preview this app</h1>
              <p>Opens <code>${escape(serverUrl)}</code> in the compose-cast Preview app.</p>
              <a class="button" id="open" href="${escape(intent)}">Open in compose-cast Preview</a>
              <p class="muted">Not installed? The button downloads it:
                <a href="${escape(
            PREVIEW_APK,
        )}">compose-cast-preview.apk</a> (Android; allow installing from your browser).</p>
              <p class="muted">Not opening? <a href="${escape(direct)}">Try this link</a>, or open the Preview app
                and type <code>${escape(serverUrl)}</code>.</p>
            </body>
            </html>
        """.trimIndent() + "\n"
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
