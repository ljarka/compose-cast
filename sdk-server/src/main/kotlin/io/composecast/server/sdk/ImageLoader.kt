package io.composecast.server.sdk

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches image bytes (PNG, JPEG, GIF, BMP) when screens are rendered.
 *
 * Images are embedded in the Remote Compose document instead of being referenced by URL:
 * in remote-compose 1.0.0-alpha20 the player rejects URL images by default, checks them against
 * a 1x1 size declared by the creation DSL, and would fetch them synchronously on the main thread.
 */
fun interface ImageLoader {
    fun load(url: String): ByteArray
}

/**
 * Default [ImageLoader]: HTTP(S) GET with timeouts and redirects. Only `http`/`https` URLs and at
 * most [MAX_BYTES] — image URLs may come from JSON files or the admin panel, so the server must not
 * read `file:` URLs or unbounded downloads.
 */
object HttpImageLoader : ImageLoader {
    const val MAX_BYTES: Int = 5 * 1024 * 1024

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun load(url: String): ByteArray {
        val uri = URI.create(url)
        require(uri.scheme == "http" || uri.scheme == "https") { "only http/https image URLs are allowed: $url" }
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            check(response.statusCode() in 200..299) { "GET $url returned HTTP ${response.statusCode()}" }
            val bytes = body.readNBytes(MAX_BYTES + 1)
            check(bytes.size <= MAX_BYTES) { "image $url is larger than ${MAX_BYTES / 1024 / 1024} MB" }
            return bytes
        }
    }
}
