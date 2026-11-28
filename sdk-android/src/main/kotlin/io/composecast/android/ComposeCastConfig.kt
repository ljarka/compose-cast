package io.composecast.android

/**
 * How a [ComposeCastClient] talks to a compose-cast server.
 *
 * Keep one instance per server (create it once, e.g. in your DI graph or with `remember`):
 * [rememberComposeCastClient] creates a new client whenever the config is not equal to the previous
 * one, and a fresh [headers] lambda is never equal to the previous lambda.
 */
public data class ComposeCastConfig(
    /** Base URL of the server, e.g. `https://cast.example.com` (no trailing `/screens`). */
    val serverUrl: String,
    /** Sent as `X-Composecast-Player-Version` on every request; the server may use it for compatibility. */
    val playerVersion: String = "1.0",
    /** Logs HTTP requests to logcat, tag `ComposeCast`. Leave off in release builds. */
    val logEnabled: Boolean = false,
    /** Time to establish a connection, in ms. */
    val connectTimeoutMs: Long = 10_000L,
    /** Time for a whole REST request, in ms (WebSockets of live screens have no limit). */
    val requestTimeoutMs: Long = 15_000L,
    /** Maximum inactivity between two data packets of a REST response, in ms. */
    val socketTimeoutMs: Long = 15_000L,
    /**
     * Extra HTTP headers for every request (screens, live screens, screen list, brand), e.g. an
     * `Authorization` token. Called once per request, so it may return a refreshed token. The
     * protocol headers ([io.composecast.protocol.Headers] version headers and `If-None-Match`)
     * are set by the SDK and cannot be overridden here: such names are ignored.
     */
    val headers: suspend () -> Map<String, String> = NO_HEADERS,
    /** How many screen documents (per screen and parameters) are kept for ETag revalidation; `0` = none. */
    val cacheSize: Int = DEFAULT_CACHE_SIZE,
) {
    init {
        require(cacheSize >= 0) { "cacheSize must be >= 0, was $cacheSize" }
    }

    private companion object {
        const val DEFAULT_CACHE_SIZE = 32
        val NO_HEADERS: suspend () -> Map<String, String> = { emptyMap() }
    }
}
