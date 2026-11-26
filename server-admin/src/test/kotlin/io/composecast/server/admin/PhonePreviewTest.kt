package io.composecast.server.admin

import com.sun.net.httpserver.HttpServer
import io.composecast.protocol.Component
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-048: the editor's phone frame, `phone.png`, and the renderer client. */
class PhonePreviewTest {
    private val host = AdminTestHost()
    private val live = ScreenJson.encode(
        ScreenDefinition(id = "main", title = "Home", components = listOf(Component.Text("Live"))),
    )
    private val draft = ScreenJson.encode(
        ScreenDefinition(id = "main", title = "Home", components = listOf(Component.Text("Draft"))),
    )

    /** Renders to "png:<document>", so tests see what was sent. */
    private val rendered = mutableListOf<String>()
    private val fake = PhoneRenderer { document ->
        "png:${document.decodeToString()}".toByteArray().also {
            rendered +=
                it.decodeToString()
        }
    }

    private suspend fun HttpClient.page(cookie: String, path: String) = get(path) { header(HttpHeaders.Cookie, cookie) }

    @Test
    fun `without a renderer the editor has no phone frame and no image route`() = host.run { client ->
        host.screens.publish("main", live, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        assertFalse("phone.png" in client.page(cookie, "/admin/screens/main").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.page(cookie, "/admin/screens/main/phone.png").status)
    }

    @Test
    fun `the editor shows the saved draft in a phone frame`() = host.run(phoneRenderer = fake) { client ->
        host.screens.publish("main", live, by = "import")
        host.screens.saveDraft("main", draft)
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        assertContains(client.page(cookie, "/admin/screens/main").bodyAsText(), "src=\"/admin/screens/main/phone.png\"")
        assertContains(
            client.page(cookie, "/admin/screens/main/visual").bodyAsText(),
            "src=\"/admin/screens/main/phone.png\"",
        )
        val image = client.page(cookie, "/admin/screens/main/phone.png")
        assertEquals(ContentType.Image.PNG, image.contentType())
        assertEquals("png:rc:$draft", image.bodyAsText()) // the draft, rendered by publishing.document
    }

    @Test
    fun `a viewer sees the phone frame too`() = host.run(phoneRenderer = fake) { client ->
        host.screens.publish("main", live, by = "import")
        val cookie = with(host) { client.loginAs(Role.VIEWER) }

        assertEquals("png:rc:$live", client.page(cookie, "/admin/screens/main/phone.png").bodyAsText())
    }

    @Test
    fun `failures are an escaped message in the frame, not a broken image`() = host.run(
        phoneRenderer = { error("renderer <down>") },
    ) { client ->
        host.screens.publish("main", live, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        val down = client.page(cookie, "/admin/screens/main/phone.png")
        assertEquals(HttpStatusCode.OK, down.status)
        assertEquals(ContentType.Image.SVG, down.contentType())
        assertContains(down.headers["Content-Security-Policy"].orEmpty(), "default-src 'none'")
        val svg = down.bodyAsText()
        assertContains(svg, "No phone preview")
        assertContains(svg, "renderer &lt;down&gt;")
        assertFalse("<down>" in svg)

        host.failWith = "secret NAME is not set"
        assertContains(client.page(cookie, "/admin/screens/main/phone.png").bodyAsText(), "Cannot build this screen")
    }

    @Test
    fun `the cache asks the renderer once per document`() {
        val cached = CachingPhoneRenderer(fake, size = 2)
        repeat(3) { cached.png("a".toByteArray()) }
        cached.png("b".toByteArray())
        cached.png("c".toByteArray()) // evicts "a" (least recently used)
        cached.png("a".toByteArray())
        assertEquals(listOf("png:a", "png:b", "png:c", "png:a"), rendered)
    }

    @Test
    fun `the HTTP client posts the document and reports the service's errors`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/render") { exchange ->
                val body = exchange.requestBody.readAllBytes()
                val bad = body.decodeToString() == "bad"
                val status = if (bad) 400 else 200
                val answer = if (bad) "not a document".toByteArray() else "png:".toByteArray() + body
                exchange.sendResponseHeaders(status, answer.size.toLong())
                exchange.responseBody.use { it.write(answer) }
            }
            start()
        }
        try {
            val renderer = HttpPhoneRenderer("http://127.0.0.1:${server.address.port}/")
            assertContentEquals("png:doc".toByteArray(), renderer.png("doc".toByteArray()))
            val error = assertFailsWith<IllegalStateException> { renderer.png("bad".toByteArray()) }
            assertContains(error.message.orEmpty(), "400: not a document")
        } finally {
            server.stop(0)
        }
        val down = assertFailsWith<IllegalStateException> { HttpPhoneRenderer("http://127.0.0.1:1").png(ByteArray(1)) }
        assertTrue("not reachable" in down.message.orEmpty())
    }

    @Test
    fun `placeholder text wraps and stays inside the frame`() {
        val svg = placeholderSvg("Title", "word ".repeat(200))
        assertEquals(1 + 8, Regex("<text ").findAll(svg).count()) // title + at most 8 lines
    }

    /** A phone-sized PNG (1078×2399, like the preview renderer's) for the thumbnail tests. */
    private val phonePng: ByteArray = run {
        val image = java.awt.image.BufferedImage(1078, 2399, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun `the Screens list shows a small phone image per screen, only with a renderer`() =
        host.run(phoneRenderer = { phonePng }) { client ->
            host.screens.publish("main", live, by = "import")
            val cookie = with(host) { client.loginAs(Role.VIEWER) }
            assertContains(client.page(cookie, "/admin/screens").bodyAsText(), "src=\"/admin/screens/main/thumb.png\"")

            val thumb = client.page(cookie, "/admin/screens/main/thumb.png")
            assertEquals(ContentType.Image.PNG, thumb.contentType())
            val image = javax.imageio.ImageIO.read(thumb.bodyAsBytes().inputStream())
            assertEquals(THUMB_WIDTH, image.width)
            assertEquals(2399 * THUMB_WIDTH / 1078, image.height) // aspect kept
            assertEquals(HttpStatusCode.NotFound, client.page(cookie, "/admin/screens/nope/thumb.png").status)
        }

    @Test
    fun `without a renderer the Screens list has no images`() = host.run { client ->
        host.screens.publish("main", live, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        assertFalse("thumb.png" in client.page(cookie, "/admin/screens").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.page(cookie, "/admin/screens/main/thumb.png").status)
    }

    @Test
    fun `a thumbnail that cannot be drawn is the SVG message, not a broken image`() =
        host.run(phoneRenderer = { error("renderer down") }) { client ->
            host.screens.publish("main", live, by = "import")
            val cookie = with(host) { client.loginAs(Role.EDITOR) }
            val thumb = client.page(cookie, "/admin/screens/main/thumb.png")
            assertEquals(ContentType.Image.SVG, thumb.contentType())
            assertContains(thumb.bodyAsText(), "renderer down")
        }
}
