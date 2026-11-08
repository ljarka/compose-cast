package io.composecast.server.sdk

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** One resolved data-source request (templates already substituted). */
data class DataRequest(val url: String, val headers: Map<String, String> = emptyMap(), val timeoutMs: Int = 5000)

/**
 * Fetches the JSON body of a data source (T-006). Throws on any failure; the message must not
 * contain the URL or headers (they may carry secrets) — the caller adds a masked URL to logs.
 * Inject a fake in tests (no network).
 */
fun interface DataLoader {
    fun load(request: DataRequest): String
}

/** A data source failed and the screen has no `error` components: HTTP 502 (server-app). */
class DataSourceException(message: String) : RuntimeException(message)

/**
 * Default [DataLoader]: HTTP(S) GET, `Accept: application/json`, redirects followed, 2xx only, at
 * most [MAX_BYTES].
 */
object HttpDataLoader : DataLoader {
    const val MAX_BYTES: Int = 2 * 1024 * 1024

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun load(request: DataRequest): String {
        val uri = URI.create(request.url)
        require(uri.scheme == "http" || uri.scheme == "https") { "only http/https data sources are allowed" }
        val builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(request.timeoutMs.toLong()))
            .header("Accept", "application/json")
            .GET()
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }
            val bytes = body.readNBytes(MAX_BYTES + 1)
            check(bytes.size <= MAX_BYTES) { "response larger than ${MAX_BYTES / 1024 / 1024} MB" }
            return String(bytes, Charsets.UTF_8)
        }
    }
}
