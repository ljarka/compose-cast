package io.composecast.server.sdk

import io.composecast.protocol.Component
import io.composecast.protocol.ScreenDefinition
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScreensFromDirectoryTest {
    private val dir = createTempDirectory("screens")

    @Test
    fun `loads every json file in file-name order, next to DSL screens`() {
        dir.resolve("b.json").writeText("""{"id":"b","components":[{"type":"text","text":"B"}]}""")
        dir.resolve("a.json").writeText("""{"id":"a","title":"A"}""")
        dir.resolve("notes.txt").writeText("ignored")
        val registry = composeCast(imageLoader = { error("no images") }) {
            screen("dsl") { text("D") }
            screensFrom(dir)
        }
        assertEquals(listOf("dsl", "a", "b"), registry.ids())
        assertEquals(ScreenDefinition("b", "b", listOf(Component.Text("B"))), registry.get("b")!!.definition)
        assertTrue("B" in String(registry.get("b")!!.document, Charsets.ISO_8859_1))
    }

    @Test
    fun `file name must match the id`() {
        dir.resolve("main.json").writeText("""{"id":"home"}""")
        val e = assertFailsWith<IllegalArgumentException> { composeCast { screensFrom(dir) } }
        assertTrue("main.json" in e.message!! && "home.json" in e.message!!, e.message)
    }

    @Test
    fun `duplicate of a DSL screen and missing directory fail`() {
        dir.resolve("a.json").writeText("""{"id":"a"}""")
        assertFailsWith<IllegalArgumentException> {
            composeCast {
                screen("a") {}
                screensFrom(dir)
            }
        }
        assertFailsWith<IllegalArgumentException> { composeCast { screensFrom(dir.resolve("missing")) } }
    }

    @Test
    fun `invalid file names the file`() {
        dir.resolve("a.json").writeText("""{"id":"a","components":[{"type":"spacer","height":-5}]}""")
        val e = assertFailsWith<IllegalArgumentException> { composeCast { screensFrom(dir) } }
        assertTrue("a.json: components[0].height" in e.message!!, e.message)
    }
}
