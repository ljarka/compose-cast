package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteComposeRendererTest {

    /** Serves a generated 40x20 PNG for every URL, so tests never touch the network. */
    private val fakeImages = ImageLoader { _ ->
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }

    private val registry = composeCast(fakeImages) {
        brand(name = "Test", primaryColor = "#6200EE", secondaryColor = "#03DAC6")
        screen("main") {
            title = "Home"
            text("Welcome to ComposeCast", style = "h1")
            spacer(16)
            image("https://picsum.photos/400/200", alt = "Hero image")
            button("Get started", action = "navigate:onboarding")
        }
        screen("second") {
            text("Only text")
        }
    }

    @Test
    fun `renders every screen to a non-empty Remote Compose document`() {
        assertEquals(listOf("main", "second"), registry.ids())
        registry.all().values.forEach { assertTrue(it.size > 0, "screen ${it.id} is empty") }
    }

    @Test
    fun `document decodes with the Remote Compose core reader`() {
        val dump = assertNotNull(registry.get("main")).describe()
        println(dump)
        // The tree must contain real layout components, not just a header.
        assertTrue("COLUMN" in dump.uppercase(), "no column in:\n$dump")
        assertTrue("TEXT" in dump.uppercase(), "no text in:\n$dump")
        assertTrue("IMAGE" in dump.uppercase() || "BITMAP" in dump.uppercase(), "no image in:\n$dump")
    }

    @Test
    fun `document carries texts, image url and host action`() {
        val bytes = String(assertNotNull(registry.get("main")).document, Charsets.ISO_8859_1)
        listOf("Welcome to ComposeCast", "Get started", "https://picsum.photos/400/200", "navigate:onboarding")
            .forEach { assertTrue(it in bytes, "'$it' missing from document") }
    }

    @Test
    fun `rendering is deterministic`() {
        val again = composeCast(fakeImages) {
            brand(name = "Test", primaryColor = "#6200EE", secondaryColor = "#03DAC6")
            screen("second") { text("Only text") }
        }
        assertContentEquals(registry.get("second")!!.document, again.get("second")!!.document)
    }

    @Test
    fun `image is embedded with its real size, decodable without URL support`() {
        // describe() runs the RC reader with default Limits, i.e. URL images disabled.
        val dump = registry.get("main")!!.describe()
        assertTrue("BITMAP DATA" in dump, "no bitmap in:\n$dump")
        assertTrue("IMAGE_LAYOUT" in dump, "no image layout in:\n$dump")
    }

    @Test
    fun `sizes are device-side expressions, never collapsed to zero`() {
        val dump = registry.get("main")!!.describe()
        assertTrue("DENSITY_BEHAVIOR 2" in dump, "document not in dp mode:\n$dump")
        assertTrue("Height(EXACT, 0.0)" !in dump, "a height collapsed to 0:\n$dump")
    }

    @Test
    fun `failed image falls back to alt text instead of failing the build`() {
        val broken = composeCast(imageLoader = { error("offline") }) {
            screen("a") { image("https://example.com/x.png", alt = "Hero image") }
        }
        val dump = broken.get("a")!!.describe()
        assertTrue("IMAGE_LAYOUT" !in dump, "unexpected image in:\n$dump")
        assertTrue("Hero image" in String(broken.get("a")!!.document, Charsets.ISO_8859_1))
    }

    @Test
    fun `typed actions with params are encoded deterministically into the document`() {
        fun build() = composeCast(fakeImages) {
            screen("a") {
                button("Open", Action.Navigate("movie", linkedMapOf("id" to "42", "lang" to "pl")))
                button("Legacy", action = "navigate:onboarding")
            }
        }
        val bytes = build().get("a")!!.document
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue("navigate:movie?id=42&lang=pl" in text, "encoded action missing")
        assertTrue("navigate:onboarding" in text, "legacy action missing")
        assertContentEquals(bytes, build().get("a")!!.document)
    }

    @Test
    fun `title defaults to id`() {
        assertEquals("Home", registry.get("main")!!.title)
        assertEquals("second", registry.get("second")!!.title)
    }

    @Test
    fun `invalid definitions fail at build time`() {
        assertFailsWith<IllegalArgumentException> { composeCast { screen("a") { text("x", style = "huge") } } }
        assertFailsWith<IllegalArgumentException> { composeCast { screen("a") { button("x", action = "goto:b") } } }
        assertFailsWith<IllegalArgumentException> { composeCast { screen("a") { button("x", action = "navigate:") } } }
        assertFailsWith<IllegalArgumentException> {
            composeCast { screen("a") { button("x", Action.Custom("x".repeat(ActionCodec.MAX_ENCODED_LENGTH))) } }
        }
        assertFailsWith<IllegalArgumentException> {
            composeCast {
                screen("a") {}
                screen("a") {}
            }
        }
        assertFailsWith<IllegalArgumentException> {
            composeCast {
                brand(name = "B", primaryColor = "purple", secondaryColor = "#000000")
                screen("a") {}
            }
        }
    }

    @Test
    fun `parses colors`() {
        assertEquals(0xFF6200EE.toInt(), RemoteComposeRenderer.parseColor("#6200EE"))
        assertEquals(0x806200EE.toInt(), RemoteComposeRenderer.parseColor("#806200EE"))
    }
}
