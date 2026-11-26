package io.composecast.server.admin

import io.composecast.server.store.ScreenStore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/**
 * T-048: turns a Remote Compose document into a PNG of the phone screen. The real implementation is
 * [HttpPhoneRenderer] (the optional `preview-renderer` service: sdk-android's player under
 * Robolectric). Throws when it cannot render; the message is shown in the phone frame.
 */
fun interface PhoneRenderer {
    fun png(document: ByteArray): ByteArray
}

/** Calls the `preview-renderer` service at [baseUrl] (`POST /render`, RC document → `image/png`). */
class HttpPhoneRenderer(
    baseUrl: String,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
) : PhoneRenderer {
    private val render = URI.create(baseUrl.trimEnd('/') + "/render")

    override fun png(document: ByteArray): ByteArray {
        val request = HttpRequest.newBuilder(render)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(document))
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: java.io.IOException) {
            throw IllegalStateException("the preview renderer is not reachable (${e.javaClass.simpleName})", e)
        }
        check(response.statusCode() == 200) {
            "the preview renderer answered ${response.statusCode()}: ${response.body().decodeToString().take(200)}"
        }
        return response.body()
    }
}

/**
 * Remembers the last [size] PNGs by document hash: reloading the editor without a change (or a
 * change that renders the same) does not ask the renderer again.
 */
class CachingPhoneRenderer(private val renderer: PhoneRenderer, private val size: Int = 32) : PhoneRenderer {
    private val cache = object : LinkedHashMap<String, ByteArray>(size, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) =
            this.size > this@CachingPhoneRenderer.size
    }

    override fun png(document: ByteArray): ByteArray {
        val key = MessageDigest.getInstance("SHA-256").digest(document).joinToString("") { "%02x".format(it) }
        synchronized(cache) { cache[key] }?.let { return it }
        return renderer.png(document).also { synchronized(cache) { cache[key] = it } }
    }
}

/** Set by [adminRoutes] when a renderer is configured: pages show the phone frame. */
internal val PhoneRendererKey = AttributeKey<PhoneRenderer>("composecast.phoneRenderer")

/** True when the editor should show the phone frame (a renderer is configured). */
internal val ApplicationCall.hasPhonePreview: Boolean get() = application.attributes.contains(PhoneRendererKey)

/**
 * `GET /admin/screens/{id}/phone.png`: the screen as the editor shows it (draft, else live) on a
 * phone. Errors come back as an SVG in the same frame (still an image), never as a broken image.
 */
internal fun Route.phonePreview(screens: ScreenStore, publishing: ScreenPublishing, renderer: PhoneRenderer) {
    get("/screens/{id}/phone.png") {
        val stored = screens.get(call.screenId) ?: return@get call.respond404()
        val json = stored.draftJson ?: stored.liveJson.orEmpty()
        val png = try {
            withContext(Dispatchers.IO) { renderer.png(publishing.document(json)) }
        } catch (e: PublishException) {
            return@get call.placeholder("Cannot build this screen", e.message.orEmpty())
        } catch (e: Exception) {
            log.warn("Phone preview of '${stored.id}' failed: ${e.message}")
            return@get call.placeholder("No phone preview", e.message.orEmpty())
        }
        call.respondBytes(png, ContentType.Image.PNG)
    }
}

private suspend fun ApplicationCall.respond404() =
    respondBytes(ByteArray(0), ContentType.Text.Plain, HttpStatusCode.NotFound)

/** A phone-sized SVG with [title] and [detail]; text is XML-escaped and scripts are blocked (CSP). */
private suspend fun ApplicationCall.placeholder(title: String, detail: String) {
    response.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
    respondBytes(placeholderSvg(title, detail).toByteArray(), ContentType.Image.SVG)
}

internal fun placeholderSvg(title: String, detail: String): String {
    val font = """text-anchor="middle" font-family="system-ui, sans-serif""""
    val lines = detail.chunkedWords(34).take(8).mapIndexed { i, line ->
        """<text x="205" y="${470 + i * 26}" $font font-size="18" fill="#6b6b76">${line.xml()}</text>"""
    }
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 411 914" width="411" height="914">""" +
        """<rect width="411" height="914" fill="#f4f4f6"/>""" +
        """<text x="205" y="430" $font font-size="22" font-weight="600" fill="#1d1d1f">${title.xml()}</text>""" +
        lines.joinToString("") + "</svg>"
}

private fun String.xml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Greedy word wrap to lines of at most [width] characters (long words are cut). */
private fun String.chunkedWords(width: Int): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in split(Regex("\\s+")).filter { it.isNotEmpty() }.flatMap { it.chunked(width) }) {
        line = when {
            line.isEmpty() -> word
            line.length + 1 + word.length <= width -> "$line $word"
            else -> {
                lines += line
                word
            }
        }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

private val log = LoggerFactory.getLogger("io.composecast.server.admin.PhonePreview")
