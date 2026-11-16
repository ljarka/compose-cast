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

    @Test
    fun `text, image, icon, divider, card and button variants roundtrip`() {
        val raw = """
            {
              "id": "components",
              "components": [
                {
                  "type": "text",
                  "text": "Styled",
                  "color": "#6200EE",
                  "fontWeight": "semiBold",
                  "fontSize": 18,
                  "textAlign": "center",
                  "maxLines": 2,
                  "overflow": "ellipsis",
                  "marquee": true
                },
                {
                  "type": "image",
                  "url": "https://example.com/a.png",
                  "contentScale": "crop",
                  "alpha": 0.8,
                  "aspectRatio": 1.5
                },
                { "type": "icon", "url": "https://example.com/ic.png", "alt": "Star", "size": 20 },
                { "type": "divider", "thickness": 2, "color": "#CCCCCC" },
                {
                  "type": "card",
                  "spacing": 8,
                  "children": [
                    { "type": "button", "label": "Outlined", "variant": "outlined", "icon": "https://example.com/ic.png", "action": { "type": "reload" } },
                    { "type": "button", "label": "Text", "variant": "text", "action": { "type": "reload" } }
                  ]
                }
              ]
            }
        """.trimIndent()
        val decoded = ScreenJson.decode(raw, "components.json")
        assertEquals(decoded, ScreenJson.decode(ScreenJson.encode(decoded), "components.json"))
    }

    @Test
    fun `invalid styling and new component fields report path`() {
        assertError("""{"id":"a","components":[{"type":"text","text":"x","color":"red"}]}""", "components[0].color")
        assertError("""{"id":"a","components":[{"type":"text","text":"x","fontSize":0}]}""", "components[0].fontSize")
        assertError("""{"id":"a","components":[{"type":"text","text":"x","maxLines":0}]}""", "components[0].maxLines")
        assertError(
            """{"id":"a","components":[{"type":"image","url":"https://a.com/x.png","alpha":1.5}]}""",
            "components[0].alpha",
        )
        assertError(
            """{"id":"a","components":[{"type":"image","url":"https://a.com/x.png","aspectRatio":0}]}""",
            "components[0].aspectRatio",
        )
        assertError("""{"id":"a","components":[{"type":"icon","url":" "}]}""", "components[0].url")
        assertError(
            """{"id":"a","components":[{"type":"icon","url":"https://a.com/i.png","size":0}]}""",
            "components[0].size",
        )
        assertError("""{"id":"a","components":[{"type":"divider","thickness":0}]}""", "components[0].thickness")
        assertError("""{"id":"a","components":[{"type":"divider","color":"bad"}]}""", "components[0].color")
        assertError("""{"id":"a","components":[{"type":"card","spacing":-1,"children":[]}]}""", "components[0].spacing")
        assertError(
            """{"id":"a","components":[{"type":"button","label":"x","icon":" ","action":{"type":"reload"}}]}""",
            "components[0].icon",
        )
    }

    @Test
    fun `flow, grid and modifier scroll roundtrip and validate`() {
        val raw = """
            {
              "id": "collections",
              "components": [
                {
                  "type": "row",
                  "spacing": 8,
                  "modifier": { "scroll": "horizontal" },
                  "children": [
                    { "type": "text", "text": "Item 1" },
                    { "type": "text", "text": "Item 2" }
                  ]
                },
                {
                  "type": "flow",
                  "maxItemsInEachRow": 3,
                  "maxLines": 2,
                  "children": [
                    { "type": "text", "text": "Tag A" },
                    { "type": "text", "text": "Tag B" }
                  ]
                },
                {
                  "type": "grid",
                  "columns": 2,
                  "spacing": 8,
                  "children": [
                    { "type": "text", "text": "Cell 1" },
                    { "type": "text", "text": "Cell 2" }
                  ]
                }
              ]
            }
        """.trimIndent()
        val decoded = ScreenJson.decode(raw, "collections.json")
        assertEquals(decoded, ScreenJson.decode(ScreenJson.encode(decoded), "collections.json"))

        assertError(
            """{"id":"a","components":[{"type":"flow","maxItemsInEachRow":0,"children":[]}]}""",
            "components[0].maxItemsInEachRow",
        )
        assertError(
            """{"id":"a","components":[{"type":"flow","maxLines":0,"children":[]}]}""",
            "components[0].maxLines",
        )
        assertError("""{"id":"a","components":[{"type":"grid","columns":0,"children":[]}]}""", "components[0].columns")
        assertError("""{"id":"a","components":[{"type":"grid","columns":13,"children":[]}]}""", "components[0].columns")
        assertError(
            """{"id":"a","components":[{"type":"grid","columns":2,"spacing":-1,"children":[]}]}""",
            "components[0].spacing",
        )
    }

    @Test
    fun `fitBox, collapsible and size constraints roundtrip and validate`() {
        val raw = """
            {
              "id": "adaptive",
              "components": [
                {
                  "type": "fitBox",
                  "children": [
                    { "type": "text", "text": "Wide", "modifier": { "minWidth": 500 } },
                    { "type": "text", "text": "Compact" }
                  ]
                },
                {
                  "type": "row",
                  "collapsible": true,
                  "spacing": 8,
                  "children": [
                    { "type": "text", "text": "Keep", "modifier": { "collapsePriority": 10.0 } },
                    { "type": "text", "text": "Drop", "modifier": { "collapsePriority": 1.0, "fillFraction": 0.5 } }
                  ]
                }
              ]
            }
        """.trimIndent()
        val decoded = ScreenJson.decode(raw, "adaptive.json")
        assertEquals(decoded, ScreenJson.decode(ScreenJson.encode(decoded), "adaptive.json"))

        assertError("""{"id":"a","components":[{"type":"text","text":"x","modifier":{"minWidth":-1}}]}""", "minWidth")
        assertError(
            """{"id":"a","components":[{"type":"text","text":"x","modifier":{"minWidth":200,"maxWidth":100}}]}""",
            "minWidth",
        )
        assertError(
            """{"id":"a","components":[{"type":"text","text":"x","modifier":{"minHeight":200,"maxHeight":100}}]}""",
            "minHeight",
        )
        assertError(
            """{"id":"a","components":[{"type":"text","text":"x","modifier":{"fillFraction":0}}]}""",
            "fillFraction",
        )
        assertError(
            """{"id":"a","components":[{"type":"text","text":"x","modifier":{"fillFraction":1.5}}]}""",
            "fillFraction",
        )
    }

    @Test
    fun `semantics roundtrip and clickable image without alt produces warning`() {
        val raw = """
            {
              "id": "a11y",
              "components": [
                {
                  "type": "image",
                  "url": "https://example.com/a.png",
                  "modifier": { "action": { "type": "reload" } }
                },
                {
                  "type": "image",
                  "url": "https://example.com/b.png",
                  "modifier": { "semantics": { "hidden": true } }
                },
                {
                  "type": "text",
                  "text": "Status",
                  "modifier": {
                    "semantics": {
                      "description": "Connection status",
                      "role": "tab",
                      "stateDescription": "Selected",
                      "enabled": true
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val decoded = ScreenJson.decode(raw, "a11y.json")
        assertEquals(decoded, ScreenJson.decode(ScreenJson.encode(decoded), "a11y.json"))
        val warnings = decoded.warnings()
        assertEquals(1, warnings.size)
        assertTrue("components[0]" in warnings.single())
    }
}
