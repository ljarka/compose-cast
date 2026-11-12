package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Containers and modifiers (T-012, ADR 0020): JSON shape and validation. */
class LayoutTest {
    private fun decode(components: String) = ScreenJson.decode("""{"id":"s","components":[$components]}""", "s.json")

    private fun problems(vararg components: Component) =
        ScreenDefinition("s", components = components.toList()).problems()

    @Test
    fun `nested containers with modifiers round trip`() {
        val screen = ScreenDefinition(
            "s",
            components = listOf(
                Component.Row(
                    children = listOf(
                        Component.Column(
                            listOf(
                                Component.Text("a"),
                                Component.Image("https://x.dev/i.png", modifier = Modifier(height = Dimension.Dp(80))),
                            ),
                            modifier = Modifier(
                                weight = 1f,
                                padding = Padding(4, 8, 4, 8),
                                background = "#FFFFFF",
                                cornerRadius = 12,
                                border = Border(1, "#80000000"),
                                action = Action.Navigate("a", mapOf("id" to "1")),
                            ),
                        ),
                        Component.Box(
                            listOf(Component.Text("b")),
                            Alignment.Center,
                            Alignment.End,
                            Modifier(width = Dimension.Fill, height = Dimension.Wrap, shape = Shape.Circle),
                        ),
                    ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Center,
                    spacing = 8,
                ),
            ),
        )
        val json = ScreenJson.encode(screen)
        assertEquals(screen, ScreenJson.decode(json, "s.json"))
        assertTrue("\"padding\": {" in json, json)
        assertTrue("\"width\": \"fill\"" in json, json)
    }

    @Test
    fun `padding is a number for all sides or an object`() {
        val all = decode("""{"type":"text","text":"x","modifier":{"padding":8}}""").components.single()
        assertEquals(Padding(8), all.modifier.padding)
        val sides = decode(
            """{"type":"text","text":"x","modifier":{"padding":{"top":4,"end":2}}}""",
        ).components.single()
        assertEquals(Padding(start = 0, top = 4, end = 2, bottom = 0), sides.modifier.padding)
        val json = ScreenJson.encode(ScreenDefinition("s", components = listOf(all)))
        assertTrue(Regex("\"padding\": 8\\b").containsMatchIn(json), json)
    }

    @Test
    fun `dimension is dp, fill or wrap`() {
        val c = decode("""{"type":"box","children":[],"modifier":{"width":"fill","height":40}}""").components.single()
        assertEquals(Dimension.Fill, c.modifier.width)
        assertEquals(Dimension.Dp(40), c.modifier.height)
        assertFailsWith<IllegalArgumentException> {
            decode("""{"type":"box","children":[],"modifier":{"width":"half"}}""")
        }
        assertFailsWith<IllegalArgumentException> {
            decode("""{"type":"box","children":[],"modifier":{"width":1.5}}""")
        }
    }

    @Test
    fun `existing JSON without modifiers stays valid and unchanged`() {
        val screen = decode("""{"type":"text","text":"x"},{"type":"button","label":"b","action":{"type":"reload"}}""")
        assertEquals(Modifier.NONE, screen.components[0].modifier)
        assertTrue("modifier" !in ScreenJson.encode(screen))
    }

    @Test
    fun `weight only for direct children of a row or column`() {
        val weighted = Component.Text("x", modifier = Modifier(weight = 1f))
        assertEquals(emptyList(), problems(Component.Row(listOf(weighted)), Component.Column(listOf(weighted))))
        assertEquals(
            listOf("components[0].modifier.weight: only allowed for direct children of a row or column"),
            problems(weighted),
        )
        assertEquals(
            listOf("components[0].children[0].modifier.weight: only allowed for direct children of a row or column"),
            problems(Component.Box(listOf(weighted))),
        )
        // A repeat inside a row expands into the row: its template components are row children.
        val repeat = Component.Repeat(RepeatItems.Path("d.items"), template = listOf(weighted))
        assertEquals(
            emptyList(),
            ScreenDefinition("s", components = listOf(Component.Row(listOf(repeat)))).contentProblems(),
        )
    }

    @Test
    fun `invalid modifiers fail with their path`() {
        val bad = Modifier(
            padding = Padding(-1),
            width = Dimension.Dp(-2),
            background = "red",
            cornerRadius = 4,
            shape = Shape.Circle,
            border = Border(0, "#12345"),
            action = Action.Navigate(""),
        )
        val found = problems(Component.Column(listOf(Component.Text("x", modifier = bad)), spacing = -1))
        listOf(
            "components[0].spacing: must be >= 0",
            "components[0].children[0].modifier.padding: must be >= 0",
            "components[0].children[0].modifier.width: must be >= 0",
            "components[0].children[0].modifier.background: expected #RRGGBB or #AARRGGBB, was 'red'",
            "components[0].children[0].modifier: set either cornerRadius or shape, not both",
            "components[0].children[0].modifier.border.width: must be > 0",
            "components[0].children[0].modifier.border.color: expected #RRGGBB or #AARRGGBB",
            "components[0].children[0].modifier.action:",
        ).forEach { expected -> assertTrue(found.any { it.startsWith(expected) }, "missing '$expected' in $found") }
    }

    @Test
    fun `nesting deeper than 32 levels is rejected`() {
        var node: Component = Component.Text("leaf")
        repeat(32) { node = Component.Column(listOf(node)) }
        val found = problems(node)
        assertEquals(1, found.size, "$found")
        assertTrue(found.single().endsWith(": nested deeper than 32 levels"), found.single())
        var ok: Component = Component.Text("leaf")
        repeat(31) { ok = Component.Column(listOf(ok)) }
        assertEquals(emptyList(), problems(ok))
    }

    @Test
    fun `bindings inside containers and modifier actions are found and checked`() {
        val screen = ScreenDefinition(
            "s",
            components = listOf(
                Component.Row(
                    listOf(
                        Component.Text(
                            "{{params.q}}",
                            modifier = Modifier(action = Action.Navigate("{{params.screen}}")),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(screen.isBound)
        assertEquals(
            setOf(
                "components[0].children[0].text: {{params.q}}: unknown param 'q' (declare it in \"params\")",
                "components[0].children[0].modifier.action.screen: {{params.screen}}: unknown param 'screen' (declare it in \"params\")",
            ),
            screen.problems().toSet(),
        )
    }
}
