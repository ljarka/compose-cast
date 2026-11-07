package io.composecast.server.sdk

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteComposeRendererTest {

    private val registry = composeCast {
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
        val again = composeCast {
            brand(name = "Test", primaryColor = "#6200EE", secondaryColor = "#03DAC6")
            screen("second") { text("Only text") }
        }
        assertContentEquals(registry.get("second")!!.document, again.get("second")!!.document)
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
        assertFailsWith<IllegalArgumentException> { composeCast { screen("a") {}; screen("a") {} } }
        assertFailsWith<IllegalArgumentException> {
            composeCast { brand(name = "B", primaryColor = "purple", secondaryColor = "#000000"); screen("a") {} }
        }
    }

    @Test
    fun `parses colors`() {
        assertEquals(0xFF6200EE.toInt(), RemoteComposeRenderer.parseColor("#6200EE"))
        assertEquals(0x806200EE.toInt(), RemoteComposeRenderer.parseColor("#806200EE"))
    }
}
