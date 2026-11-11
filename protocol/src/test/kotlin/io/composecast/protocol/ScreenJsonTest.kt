package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScreenJsonTest {
    private val screen = ScreenDefinition(
        id = "movie",
        title = "Movie",
        components = listOf(
            Component.Text("Dune", TextStyle.H1),
            Component.Text("Body text"),
            Component.Image("https://x.dev/p.png", alt = "Poster"),
            Component.Spacer(16),
            Component.Button("Similar", Action.Navigate("movie", linkedMapOf("id" to "42", "lang" to "pl"))),
            Component.Button("Reload", Action.Reload),
            Component.Button("Site", Action.OpenUrl("https://x.dev/p?a=b&c=d")),
            Component.Expandable("Cast", listOf(Component.Text("Actor")), expanded = true),
        ),
    )

    @Test
    fun `round trip is stable`() {
        val json = ScreenJson.encode(screen)
        val decoded = ScreenJson.decode(json, "movie.json")
        assertEquals(screen, decoded)
        assertEquals(json, ScreenJson.encode(decoded))
    }

    @Test
    fun `defaults are optional and omitted`() {
        val minimal = ScreenJson.decode(
            """{"id":"a","components":[{"type":"text","text":"x"},{"type":"spacer"},{"type":"expandable","title":"More","content":[{"type":"text","text":"y"}]}]}""",
            "a.json",
        )
        assertEquals(
            ScreenDefinition(
                "a",
                "a",
                components = listOf(
                    Component.Text("x", TextStyle.Body),
                    Component.Spacer(8),
                    Component.Expandable("More", listOf(Component.Text("y", TextStyle.Body)), expanded = false),
                ),
            ),
            minimal,
        )
        val json = ScreenJson.encode(minimal)
        listOf("title\"", "style", "height", "expanded").forEach {
            // Note: expandable has a required "title" field, so check root title is omitted on first line
            if (it == "title\"") {
                assertTrue("\"id\": \"a\",\n  \"components\"" in json, "default screen 'title' encoded:\n$json")
            } else {
                assertTrue("\"$it\"" !in json, "default '$it' encoded:\n$json")
            }
        }
    }

    private fun error(json: String): String =
        assertFailsWith<IllegalArgumentException> { ScreenJson.decode(json, "screens/a.json") }.message!!
            .also { println(it) }

    private fun assertError(json: String, vararg expected: String) {
        val message = error(json)
        assertTrue(message.startsWith("screens/a.json: "), "no file in: $message")
        expected.forEach { assertTrue(it in message, "'$it' missing in: $message") }
    }

    @Test
    fun `unknown component type names file and path`() = assertError(
        """{"id":"a","components":[{"type":"text","text":"x"},{"type":"carousel"}]}""",
        "carousel",
        "components[1]",
    )

    @Test
    fun `missing field names file and path`() =
        assertError("""{"id":"a","components":[{"type":"button","label":"x"}]}""", "action", "components[0]")

    @Test
    fun `unknown action type names file and path`() = assertError(
        """{"id":"a","components":[{"type":"button","label":"x","action":{"type":"teleport"}}]}""",
        "teleport",
        "components[0].action",
    )

    @Test
    fun `unknown key and bad style are errors`() {
        assertError("""{"id":"a","components":[{"type":"text","txt":"x"}]}""", "txt", "components[0]")
        assertError(
            """{"id":"a","components":[{"type":"text","text":"x","style":"huge"}]}""",
            "huge",
            "components[0].style",
        )
    }

    @Test
    fun `invalid content names file and path`() {
        assertError("""{"id":"a","components":[{"type":"spacer","height":-1}]}""", "components[0].height", ">= 0")
        assertError(
            """{"id":"a","components":[{"type":"button","label":"x","action":{"type":"navigate","screen":""}}]}""",
            "components[0].action",
        )
        assertError("""{"id":" "}""", "id")
        val long = "x".repeat(4000)
        assertError(
            """{"id":"a","components":[{"type":"button","label":"x","action":{"type":"custom","payload":"$long"}}]}""",
            "4000",
        )
        assertError(
            """{"id":"a","components":[{"type":"expandable","title":" ","content":[]}]}""",
            "components[0].title",
            "components[0].content",
        )
        assertError(
            """{"id":"a","components":[{"type":"expandable","title":"More","content":[{"type":"spacer","height":-1}]}]}""",
            "components[0].content[0].height",
        )
        assertError("""{"id":"a",""", "screens/a.json")
    }
}
