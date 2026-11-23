package io.composecast.server.admin

import io.composecast.protocol.ScreenJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** T-029 C3: the palette's starters and structural edits. */
class PaletteTest {
    private fun screen(json: String) = Json.parseToJsonElement(json) as JsonObject

    private val empty = screen("""{"id": "s"}""")

    /** A screen with a data source (a repeat needs one). */
    private val withData = screen("""{"id": "s", "data": {"shows": {"url": "https://api.tvmaze.com/shows"}}}""")

    /** Types of the screen's top-level components, nested ones in brackets. */
    private fun shape(screen: JsonObject): String = ScreenTree.rows(screen).filter {
        it.type != null &&
            it.type != "source"
    }
        .joinToString(" ") { "${"·".repeat(it.depth - 1)}${it.type}" }

    @Test
    fun `every component type has a starter that is valid on its own`() {
        assertEquals(
            FormSchema.componentTypes.keys,
            Palette.starters.keys,
            "a new component needs a starter in Palette",
        )
        assertEquals(
            FormSchema.componentTypes.keys,
            Palette.groups.flatMap {
                it.second
            }.toSet(),
            "and a palette group",
        )
        for (type in Palette.starters.keys) {
            val screen = Palette.add(withData, "components", null, type).screen
            assertEquals(emptyList(), ScreenJson.check(screen.toString()).problems, type)
        }
    }

    @Test
    fun `add, duplicate, move and delete keep the selection on the edited component`() {
        var s = Palette.add(empty, "components", null, "text")
        assertEquals("components.0", s.select)
        s = Palette.add(s.screen, "components", 1, "image")
        s = Palette.add(s.screen, "components", 0, "button")
        assertEquals("button text image", shape(s.screen))

        s = Palette.move(s.screen, "components.2", -1)
        assertEquals("button image text" to "components.1", shape(s.screen) to s.select)
        assertEquals(s.screen, Palette.move(s.screen, "components.0", -1).screen, "nothing above the first")

        s = Palette.duplicate(s.screen, "components.1")
        assertEquals("button image image text" to "components.2", shape(s.screen) to s.select)
        s = Palette.delete(s.screen, "components.3")
        assertEquals("button image image" to "components.2", shape(s.screen) to s.select)
        s =
            Palette.delete(
                Palette.delete(Palette.delete(s.screen, "components.0").screen, "components.0").screen,
                "components.0",
            )
        assertEquals("" to "", shape(s.screen) to s.select, "the last one deleted: the screen is selected")
    }

    @Test
    fun `nesting moves components into and out of containers`() {
        var s = Palette.add(empty, "components", null, "column")
        s = Palette.add(s.screen, "components", null, "text")
        s = Palette.intoPrevious(s.screen, "components.1")
        assertEquals("column ·text" to "components.0.children.0", shape(s.screen) to s.select)

        s = Palette.add(s.screen, "components.0.children", null, "button")
        s = Palette.outOfParent(s.screen, "components.0.children.0")
        assertEquals("column ·button text" to "components.1", shape(s.screen) to s.select)

        s = Palette.wrap(s.screen, "components.1", "card")
        assertEquals("column ·button card ·text" to "components.1", shape(s.screen) to s.select)

        assertFailsWith<IllegalArgumentException> { Palette.intoPrevious(s.screen, "components.0") }
        assertFailsWith<IllegalArgumentException> { Palette.outOfParent(s.screen, "components.0") }
        assertFailsWith<IllegalArgumentException> { Palette.wrap(s.screen, "components.0", "text") }
        assertFailsWith<IllegalArgumentException>("into a text") {
            Palette.intoPrevious(Palette.add(s.screen, "components", 1, "text").screen, "components.2")
        }
        assertEquals(emptyList(), ScreenJson.check(s.screen.toString()).problems)
    }

    @Test
    fun `a repeat needs a data source and its empty list can be filled although it is absent`() {
        val message = assertFailsWith<IllegalArgumentException> {
            Palette.add(empty, "components", null, "repeat")
        }.message!!
        assertEquals(true, "add a data source first" in message, message)
        val s = Palette.add(withData, "components", null, "repeat").screen
        assertEquals(JsonPrimitive("shows"), (ScreenTree.get(s, "components.0") as JsonObject)["items"])
        assertEquals(
            listOf("template", "empty"),
            ScreenTree.rows(s).filter {
                it.type == null && it.path !in setOf("data", "components", "error")
            }.map { it.label },
        )
        val filled = Palette.add(s, "components.0.empty", null, "text")
        assertEquals(
            "components.0.empty.0" to JsonPrimitive("New text"),
            filled.select to (ScreenTree.get(filled.screen, filled.select) as JsonObject)["text"],
        )
    }
}
