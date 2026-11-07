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

/** Default [ImageLoader]: plain HTTP(S) GET with timeouts and redirects. */
object HttpImageLoader : ImageLoader {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun load(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() in 200..299) { "GET $url returned HTTP ${response.statusCode()}" }
        return response.body()
    }
}
