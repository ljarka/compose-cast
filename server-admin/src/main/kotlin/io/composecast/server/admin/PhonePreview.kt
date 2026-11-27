package io.composecast.server.admin

import io.composecast.protocol.ScreenDefinition
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.composeCast
import io.composecast.server.store.ScreenStore
import io.composecast.templates.AppTemplate
import io.composecast.templates.AppTemplates
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
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import javax.imageio.ImageIO

/**
 * T-048: turns a Remote Compose document into a PNG of the phone screen. The real implementation is
 * [HttpPhoneRenderer] (the optional `preview-renderer` service: sdk-android's player under
 * Robolectric). Throws when it cannot render; the message is shown in the phone frame.
 */
fun interface PhoneRenderer {
    fun png(document: ByteArray): ByteArray

    /** Renders [document] in dark or light theme; default ignores [dark] for simple test fakes. */
    fun png(document: ByteArray, dark: Boolean): ByteArray = png(document)
}

/** Calls the `preview-renderer` service at [baseUrl] (`POST /render[?theme=dark]`, RC document → `image/png`). */
class HttpPhoneRenderer(
    baseUrl: String,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
) : PhoneRenderer {
    private val renderLight = URI.create(baseUrl.trimEnd('/') + "/render")
    private val renderDark = URI.create(baseUrl.trimEnd('/') + "/render?theme=dark")

    override fun png(document: ByteArray): ByteArray = png(document, dark = false)

    override fun png(document: ByteArray, dark: Boolean): ByteArray {
        val request = HttpRequest.newBuilder(if (dark) renderDark else renderLight)
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
 * Remembers the last [size] PNGs by document hash + theme: reloading the editor without a change
 * (or a change that renders the same) does not ask the renderer again.
 */
class CachingPhoneRenderer(private val renderer: PhoneRenderer, private val size: Int = 64) : PhoneRenderer {
    private val cache = object : LinkedHashMap<String, ByteArray>(size, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) =
            this.size > this@CachingPhoneRenderer.size
    }

    override fun png(document: ByteArray): ByteArray = png(document, dark = false)

    override fun png(document: ByteArray, dark: Boolean): ByteArray {
        val prefix = if (dark) "dark:" else "light:"
        val key = prefix + MessageDigest.getInstance("SHA-256").digest(document).joinToString("") { "%02x".format(it) }
        synchronized(cache) { cache[key] }?.let { return it }
        return renderer.png(document, dark).also { synchronized(cache) { cache[key] = it } }
    }
}

/** Set by [adminRoutes] when a renderer is configured: pages show the phone frame. */
internal val PhoneRendererKey = AttributeKey<PhoneRenderer>("composecast.phoneRenderer")

/** True when the editor should show the phone frame (a renderer is configured). */
internal val ApplicationCall.hasPhonePreview: Boolean get() = application.attributes.contains(PhoneRendererKey)

/**
 * `GET /admin/screens/{id}/phone.png`: the screen as the editor shows it (draft, else live) on a
 * phone. `GET /admin/screens/{id}/thumb.png`: the same, [THUMB_WIDTH] px wide, for the Screens
 * list. Errors come back as an SVG in the same frame (still an image), never as a broken image.
 */
internal fun Route.phonePreview(screens: ScreenStore, publishing: ScreenPublishing, renderer: PhoneRenderer) {
    get("/screens/{id}/phone.png") {
        val png = call.screenPng(screens, publishing, renderer) ?: return@get
        call.respondBytes(png, ContentType.Image.PNG)
    }
    get("/screens/{id}/thumb.png") {
        val png = call.screenPng(screens, publishing, renderer) ?: return@get
        val thumb = try {
            withContext(Dispatchers.Default) { thumbnail(png, THUMB_WIDTH) }
        } catch (e: Exception) {
            return@get call.placeholder("No phone preview", e.message.orEmpty())
        }
        call.respondBytes(thumb, ContentType.Image.PNG)
    }
    get("/templates/{template}/screens/{screen}/phone.png") {
        val png = call.templateScreenPng(renderer) ?: return@get
        call.respondBytes(png, ContentType.Image.PNG)
    }
    get("/templates/{template}/screens/{screen}/thumb.png") {
        val png = call.templateScreenPng(renderer) ?: return@get
        val thumb = try {
            withContext(Dispatchers.Default) { thumbnail(png, TEMPLATE_THUMB_WIDTH) }
        } catch (e: Exception) {
            return@get call.placeholder("No phone preview", e.message.orEmpty())
        }
        call.respondBytes(thumb, ContentType.Image.PNG)
    }
}

/** Width of the Templates page phone thumbnails in pixels (shown at 110 CSS px: sharp on 2x displays). */
internal const val TEMPLATE_THUMB_WIDTH = 220

/** Renders a bundled template screen offline with its fixtures and brand into a phone PNG. */
private suspend fun ApplicationCall.templateScreenPng(renderer: PhoneRenderer): ByteArray? {
    val template = AppTemplates[parameters["template"].orEmpty()] ?: run {
        respond404()
        return null
    }
    val screen = template.screens.firstOrNull { it.id == parameters["screen"].orEmpty() } ?: run {
        respond404()
        return null
    }
    val dark = request.queryParameters["theme"] == "dark"
    return try {
        withContext(Dispatchers.IO) { renderer.png(templateDocument(template, screen), dark) }
    } catch (e: Exception) {
        log.warn("Template preview of '${template.id}/${screen.id}' failed: ${e.message}")
        placeholder("No phone preview", e.message.orEmpty(), dark)
        null
    }
}

/** Compiles [screen] of [template] with its bundled fixtures and brand colors (no network calls). */
internal fun templateDocument(template: AppTemplate, screen: ScreenDefinition): ByteArray {
    val data = DataLoader { request ->
        template.fixture(request.url) ?: throw PublishException("no fixture for ${request.url}")
    }
    val placeholderPng = templateImagePlaceholder(template.brand?.surfaceColor, template.brand?.primaryColor)
    val images = ImageLoader { _ -> placeholderPng }
    val registry = composeCast(images, data, env = { null }) {
        template.brand?.let { brand(it) }
        screen(screen)
    }
    return requireNotNull(registry.render(screen.id, RenderContext())).document
}

private fun templateImagePlaceholder(surfaceHex: String?, accentHex: String?): ByteArray {
    val img = BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    try {
        g.color = parseColorOr(surfaceHex, Color(0xE8, 0xEC, 0xF2))
        g.fillRect(0, 0, 160, 100)
        g.color = parseColorOr(accentHex, Color(0x94, 0xA3, 0xB8))
        g.fillOval(56, 26, 48, 48)
    } finally {
        g.dispose()
    }
    return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
}

private fun parseColorOr(hex: String?, fallback: Color): Color {
    val clean = hex?.trim()?.removePrefix("#") ?: return fallback
    val rgb = when (clean.length) {
        6 -> clean
        8 -> clean.substring(2)
        else -> return fallback
    }
    return rgb.toIntOrNull(16)?.let { Color(it) } ?: fallback
}

/** The screen's phone PNG, or null after answering 404 / a placeholder SVG. */
private suspend fun ApplicationCall.screenPng(
    screens: ScreenStore,
    publishing: ScreenPublishing,
    renderer: PhoneRenderer,
): ByteArray? {
    val stored = screens.get(screenId) ?: run {
        respond404()
        return null
    }
    val json = stored.draftJson ?: stored.liveJson.orEmpty()
    val dark = request.queryParameters["theme"] == "dark"
    return try {
        withContext(Dispatchers.IO) { renderer.png(publishing.document(json), dark) }
    } catch (e: PublishException) {
        placeholder("Cannot build this screen", e.message.orEmpty(), dark)
        null
    } catch (e: Exception) {
        log.warn("Phone preview of '${stored.id}' failed: ${e.message}")
        placeholder("No phone preview", e.message.orEmpty(), dark)
        null
    }
}

/** Width of the Screens list thumbnails in pixels (shown at 90 CSS px: sharp on 2x displays). */
internal const val THUMB_WIDTH = 180

/**
 * [png] scaled to [width] px (aspect kept), halving first so the small image stays smooth
 * (one bilinear step from 1078 px would skip pixels).
 */
internal fun thumbnail(png: ByteArray, width: Int): ByteArray {
    var image: BufferedImage = requireNotNull(ImageIO.read(ByteArrayInputStream(png))) { "not an image" }
    val height = (image.height.toLong() * width / image.width).toInt().coerceAtLeast(1)
    while (image.width / 2 >= width) image = scaled(image, image.width / 2, image.height / 2)
    image = scaled(image, width, height)
    return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}

/**
 * A component's phone PNG cut to the component (the screen background below it removed, judged by
 * the bottom left pixel, plus a small margin), then scaled to [width] px: for the Components list.
 */
internal fun componentThumbnail(png: ByteArray, width: Int = COMPONENT_THUMB_WIDTH): ByteArray {
    val image = requireNotNull(ImageIO.read(ByteArrayInputStream(png))) { "not an image" }
    val background = image.getRGB(0, image.height - 1)
    var last = image.height - 1
    while (last > 0 && (0 until image.width).all { x -> image.getRGB(x, last) == background }) last--
    val height = (last + 1 + image.height / 100).coerceIn(1, image.height)
    val cut = image.getSubimage(0, 0, image.width, height)
    return thumbnail(ByteArrayOutputStream().also { ImageIO.write(cut, "png", it) }.toByteArray(), width)
}

/** Width of the Components list thumbnails in pixels (shown at 180 CSS px). */
internal const val COMPONENT_THUMB_WIDTH = 360

private fun scaled(source: BufferedImage, width: Int, height: Int): BufferedImage {
    val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = target.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(source, 0, 0, width, height, null)
    } finally {
        g.dispose()
    }
    return target
}

private suspend fun ApplicationCall.respond404() =
    respondBytes(ByteArray(0), ContentType.Text.Plain, HttpStatusCode.NotFound)

/** A phone-sized SVG with [title] and [detail]; text is XML-escaped and scripts are blocked (CSP). */
private suspend fun ApplicationCall.placeholder(title: String, detail: String, dark: Boolean = false) {
    response.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
    respondBytes(placeholderSvg(title, detail, dark).toByteArray(), ContentType.Image.SVG)
}

internal fun placeholderSvg(title: String, detail: String, dark: Boolean = false): String {
    val bg = if (dark) "#141218" else "#f4f4f6"
    val fg = if (dark) "#e6e0e9" else "#1d1d1f"
    val muted = if (dark) "#9a9aa2" else "#6b6b76"
    val font = """text-anchor="middle" font-family="system-ui, sans-serif""""
    val lines = detail.chunkedWords(34).take(8).mapIndexed { i, line ->
        """<text x="205" y="${470 + i * 26}" $font font-size="18" fill="$muted">${line.xml()}</text>"""
    }
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 411 914" width="411" height="914">""" +
        """<rect width="411" height="914" fill="$bg"/>""" +
        """<text x="205" y="430" $font font-size="22" font-weight="600" fill="$fg">${title.xml()}</text>""" +
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
