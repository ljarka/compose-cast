package io.composecast.server.sdk

import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** One resolved data-source request (templates already substituted). */
public data class DataRequest(
    public val url: String,
    public val headers: Map<String, String> = emptyMap(),
    public val timeoutMs: Int = 5000,
)

/**
 * Fetches the JSON body of a data source (T-006). Throws on any failure; the message must not
 * contain the URL or headers (they may carry secrets) — the caller adds a masked URL to logs.
 * Inject a fake in tests (no network).
 */
public fun interface DataLoader {
    public fun load(request: DataRequest): String
}

/** A data source failed and the screen has no `error` components: HTTP 502 (server-app). */
public class DataSourceException(message: String) : RuntimeException(message)

/**
 * Default [DataLoader]: HTTP(S) GET, `Accept: application/json`, 2xx only, at most [MAX_BYTES].
 *
 * Like [HttpImageLoader] (T-026, T-051), blocks loopback, private, link-local (cloud metadata),
 * multicast, and IPv6 ULA hosts unless [allowPrivateHosts] is true, and follows redirects manually
 * (up to [MAX_REDIRECTS] hops) so every hop is checked. Error messages omit URLs and headers.
 */
public class HttpDataLoader internal constructor(
    private val allowPrivateHosts: Boolean,
    private val resolve: (String) -> List<InetAddress>,
    private val isAllowed: (InetAddress) -> Boolean,
) : DataLoader {
    public constructor(allowPrivateHosts: Boolean = false) :
        this(allowPrivateHosts, { host ->
            InetAddress.getAllByName(host).toList()
        }, { !HttpImageLoader.isPrivateAddress(it) })

    override fun load(request: DataRequest): String {
        var uri = URI.create(request.url)
        repeat(MAX_REDIRECTS + 1) {
            checkTarget(uri)
            val builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(request.timeoutMs.toLong()))
                .header("Accept", "application/json")
                .GET()
            request.headers.forEach { (name, value) -> builder.header(name, value) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                val status = response.statusCode()
                if (status in REDIRECT_CODES) {
                    val location = response.headers().firstValue("Location").orElse(null)
                        ?: error("HTTP $status without Location")
                    uri = uri.resolve(location)
                    return@repeat
                }
                check(status in 200..299) { "HTTP $status" }
                val bytes = body.readNBytes(MAX_BYTES + 1)
                check(bytes.size <= MAX_BYTES) { "response larger than ${MAX_BYTES / 1024 / 1024} MB" }
                return String(bytes, Charsets.UTF_8)
            }
        }
        error("more than $MAX_REDIRECTS redirects")
    }

    private fun checkTarget(uri: URI) {
        require(uri.scheme == "http" || uri.scheme == "https") { "only http/https data sources are allowed" }
        val host = requireNotNull(uri.host) { "data source URL has no host" }
        if (allowPrivateHosts) return
        val addresses = resolve(host.removeSurrounding("[", "]"))
        val blocked = addresses.firstOrNull { !isAllowed(it) }
        require(blocked == null) { "private or local data source host is not allowed" }
    }

    public companion object : DataLoader by HttpDataLoader() {
        public const val MAX_BYTES: Int = 2 * 1024 * 1024
        public const val MAX_REDIRECTS: Int = 3
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        private val client: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }
}
