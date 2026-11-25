package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisibilityTest {
    @Test
    fun `visibility modifier and toggle show hide actions round-trip in JSON`() {
        val screen = ScreenDefinition(
            id = "vis",
            components = listOf(
                Component.Button("Toggle", Action.Toggle("details")),
                Component.Button("Show", Action.Show("details")),
                Component.Button("Hide", Action.Hide("details")),
                Component.Card(
                    children = listOf(Component.Text("Hidden details")),
                    modifier = Modifier(
                        visibility = Visibility(
                            id = "details",
                            hidden = true,
                            transition = VisibilityTransition.SlideTop,
                            durationMs = 400,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(emptyList(), screen.problems())
        val json = ScreenJson.encode(screen)
        assertEquals(screen, ScreenJson.decode(json, "vis.json"))
    }

    @Test
    fun `ActionCodec round-trips toggle show and hide actions`() {
        listOf(
            Action.Toggle("details"),
            Action.Show("banner"),
            Action.Hide("tip-1"),
        ).forEach { action ->
            assertEquals(action, ActionCodec.decode(ActionCodec.encode(action)))
        }
    }

    @Test
    fun `validation rejects blank target, blank id, and out-of-range durationMs and warns on unknown target`() {
        val bad = ScreenDefinition(
            id = "bad",
            components = listOf(
                Component.Button("Blank", Action.Toggle(" ")),
                Component.Button("Missing", Action.Toggle("missing")),
                Component.Text(
                    "Hi",
                    modifier = Modifier(visibility = Visibility(id = " ", durationMs = 10)),
                ),
            ),
        )
        val problems = bad.problems()
        assertTrue(problems.any { "target must not be blank" in it }, "got $problems")
        assertTrue(problems.any { "visibility.id: must not be blank" in it }, "got $problems")
        assertTrue(problems.any { "visibility.durationMs:" in it }, "got $problems")
        val warnings = bad.warnings()
        assertTrue(warnings.any { "unknown visibility target 'missing'" in it }, "got $warnings")
    }
}
