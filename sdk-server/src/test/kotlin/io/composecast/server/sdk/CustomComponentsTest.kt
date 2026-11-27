package io.composecast.server.sdk

import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentJson
import io.composecast.protocol.ComponentParam
import io.composecast.protocol.ComponentParamType
import io.composecast.protocol.ParamSpec
import io.composecast.protocol.ScreenDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Custom components in raw RC JSON (ADR 0033, T-049). */
class CustomComponentsTest {

    private fun body(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val tileBody = body(
        """
        { "type": "box",
          "modifiers": [ { "fillMaxWidth": 1 }, { "height": "{{params.height}}" }, { "clip": 16 },
                         { "onClick": { "type": "hostAction", "name": "{{params.action}}" } } ],
          "children": [
            { "type": "canvas", "modifiers": [ { "fillMaxSize": 1 } ], "commands": [
              { "type": "paint", "ops": [ { "linearGradient": { "x2": "width", "y2": "height",
                                                              "colors": ["{{params.accent}}", "#1E1E1E"] } } ] },
              { "type": "drawRoundRect", "left": 0, "top": 0, "right": "width", "bottom": "height",
                "rx": 16, "ry": 16 },
              { "type": "paint", "ops": [ { "shader": 0 }, { "color": "{{brand.primaryColor}}" } ] },
              { "type": "drawCircle", "cx": "width - 30", "cy": 30, "radius": 14 } ] },
            { "type": "text", "text": "{{params.title}}", "color": "#FFFFFF", "fontSize": 22 } ] }
        """,
    )

    private val tile = ComponentDefinition(
        id = "tile",
        rcVersion = CustomComponents.RC_VERSION,
        params = mapOf(
            "title" to ComponentParam(),
            "accent" to ComponentParam(ComponentParamType.Color, default = "#6A1B9A"),
            "height" to ComponentParam(ComponentParamType.Number, default = "160"),
            "action" to ComponentParam(ComponentParamType.Action, default = "reload:"),
        ),
        body = tileBody,
    )

    @Test
    fun `a custom component renders inside the screen with args, typed numbers, brand colors and its action`() {
        val registry = composeCast {
            brand("B", primaryColor = "#123456", secondaryColor = "#000000")
            component(tile)
            screen("s") {
                text("Before")
                custom("tile", mapOf("title" to "Paczka w drodze", "action" to "navigate:parcels"))
                text("After")
            }
        }
        val dump = registry.get("s")!!.describe()
        assertTrue("Paczka " in dump, dump)
        assertTrue("HostNamedActionOperation" in dump && "navigat" in dump, dump)
        assertTrue("0xff6a1b9a" in dump, "default accent in the gradient:\n$dump")
        assertTrue(
            "lightFallback=${0xFF123456.toInt()}" in dump,
            "brand primary color as themed ColorTheme op:\n$dump",
        )
        assertTrue("Height(EXACT, 160.0)" in dump, "number param substituted as a number:\n$dump")
        val canvas = dump.indexOf("CANVAS")
        assertTrue(dump.indexOf("Before") < canvas && canvas < dump.lastIndexOf("CORE_TEXT"))
    }

    @Test
    fun `check reports whitelist problems with paths`() {
        fun problems(json: String) = CustomComponents.check(tile.copy(params = emptyMap(), body = body(json)))
        fun only(json: String) = problems(json).single()
        assertTrue(only("""{ "type": "custom", "config": {} }""").startsWith("body.type: component 'custom'"))
        assertTrue(
            problems("""{ "type": "canvas", "commands": [], "children": [ { "type": "text", "text": "x" } ] }""")
                .single().startsWith("body.children: a canvas cannot have children"),
        )
        assertTrue(
            only("""{ "type": "canvas", "commands": [ { "type": "paint", "ops": [ { "shader": "x" } ] } ] }""")
                .contains("runtime shaders are not allowed"),
        )
        assertTrue(only("""{ "type": "canvas", "commands": [ { "type": "playSound" } ] }""").contains("playsound"))
        assertTrue(
            problems(
                """{ "type": "box",
                     "modifiers": [ { "onClick": { "type": "hostAction", "name": "nope" } } ] }""",
            ).single().contains("'nope' is not an action"),
        )
        assertTrue(only("""{ "type": "box", "modifiers": [ { "include": "x" } ] }""").contains("'include'"))
        // Grammar errors come from the trial parse, with the parser's context path.
        assertTrue(problems("""{ "type": "text" }""").isEmpty(), "text without value is allowed by RC")
        assertTrue(only("""{ "type": "canvas", "commands": [ { "type": "drawRect" } ] }""").startsWith("body: "))
        assertEquals(emptyList(), CustomComponents.check(tile))
    }

    @Test
    fun `screens must name known components with fitting args`() {
        assertFailsWith<IllegalArgumentException> {
            composeCast { screen("s") { custom("missing") } }
        }.also { assertTrue("unknown custom component 'missing'" in it.message!!, it.message) }
        assertFailsWith<IllegalArgumentException> {
            composeCast {
                component(tile)
                screen("s") { custom("tile") }
            }
        }.also { assertTrue("args.title: required" in it.message!!, it.message) }
        assertFailsWith<IllegalArgumentException> {
            composeCast {
                component(tile)
                screen("s") { custom("tile", mapOf("title" to "x", "accent" to "red", "extra" to "1")) }
            }
        }.also {
            assertTrue("args.accent: expected #RRGGBB" in it.message!!, it.message)
            assertTrue("args.extra: component 'tile' has no such param" in it.message!!, it.message)
        }
    }

    @Test
    fun `bound args are rendered per request and keep the document structure`() {
        val registry = composeCast {
            component(tile)
            screen(
                ScreenDefinition(
                    id = "b",
                    params = mapOf("name" to ParamSpec(default = "one")),
                    components = listOf(
                        io.composecast.protocol.Component.Custom("tile", mapOf("title" to "Hello {{params.name}}")),
                    ),
                ),
            )
        }
        val a = registry.render("b", RenderContext(mapOf("name" to "Ala")))!!
        val b = registry.render("b", RenderContext(mapOf("name" to "Ola")))!!
        assertTrue("Hello A" in a.describe(), a.describe())
        assertNotEquals(a.etag, b.etag)
        assertEquals(a.structure, b.structure, "a different text arg is a data update (ADR 0029)")
    }

    @Test
    fun `a component from another RC release that fails now is broken and skipped, not fatal`() {
        val brokenBody = body("""{ "type": "canvas", "commands": [ { "type": "drawRect" } ] }""")
        val old = tile.copy(rcVersion = "1.0.0-alpha01", body = brokenBody, params = emptyMap())
        val registry = composeCast {
            component(old)
            screen("s") {
                text("Still here")
                custom("tile")
            }
        }
        assertTrue(registry.brokenComponents().getValue("tile").startsWith("validated with RC 1.0.0-alpha01"))
        val dump = registry.get("s")!!.describe()
        assertTrue("Still h" in dump && "CANVAS" !in dump, dump)
        // The same broken body validated with this release fails the build like a screen would.
        val current = old.copy(rcVersion = CustomComponents.RC_VERSION)
        assertFailsWith<IllegalArgumentException> { composeCast { component(current) } }
    }

    @Test
    fun `clients below minApiLevel get nothing in the component's place`() {
        val registry = composeCast {
            component(tile.copy(minApiLevel = 99))
            screen("s") { custom("tile", mapOf("title" to "x")) }
        }
        assertTrue("CANVAS" !in registry.get("s")!!.describe())
    }

    @Test
    fun `definitions round-trip through ComponentJson and report undeclared placeholders`() {
        val text = ComponentJson.encode(tile)
        assertEquals(tile, ComponentJson.decode(text, "t"))
        val bad = tile.copy(params = emptyMap())
        assertTrue(bad.problems().any { "{{params.title}} is not declared" in it }, bad.problems().toString())
        assertTrue(
            tile.copy(body = body("""{ "type": "text", "text": "{{brand.nope}}" }"""), params = emptyMap())
                .problems().single().contains("{{brand.nope}} is unknown"),
        )
        assertTrue(CustomComponents.RC_VERSION.startsWith("1.0.0-"), CustomComponents.RC_VERSION)
    }

    @Test
    fun `designed dark color overrides and brand tokens emit ColorTheme ops and validate cleanly`() {
        val definition = ComponentJson.decode(
            ComponentJson.encode(
                ComponentDefinition(
                    id = "themed-card",
                    rcVersion = CustomComponents.RC_VERSION,
                    design = io.composecast.protocol.ComponentDesign(
                        width = 360f,
                        height = 120f,
                        elements = listOf(
                            io.composecast.protocol.DesignElement(
                                id = "bg",
                                type = io.composecast.protocol.DesignElementType.Rect,
                                w = 360f,
                                h = 120f,
                                anchor = io.composecast.protocol.DesignAnchor.Stretch,
                                fill = "#FFFFFFFF",
                                darkFill = "#FF1E1E24",
                                fill2 = "{{brand.surfaceColor}}",
                                stroke = "#FFDCDCE0",
                                darkStroke = "#FF49454F",
                                strokeWidth = 1f,
                            ),
                            io.composecast.protocol.DesignElement(
                                id = "label",
                                type = io.composecast.protocol.DesignElementType.Text,
                                x = 16f,
                                y = 16f,
                                w = 200f,
                                text = "Themed",
                                fill = "{{brand.textColor}}",
                            ),
                        ),
                    ),
                ),
            ),
            "test",
        )
        assertEquals(emptyList(), CustomComponents.check(definition))
        val registry = composeCast {
            component(definition)
            screen("s") { custom("themed-card") }
        }
        val dump = registry.get("s")!!.describe()
        assertTrue(
            "lightFallback=${0xFFFFFFFF.toInt()};darkFallback=${0xFF1E1E24.toInt()}" in dump,
            "explicit fill/darkFill ColorTheme:\n$dump",
        )
        assertTrue(
            "lightFallback=${0xFFDCDCE0.toInt()};darkFallback=${0xFF49454F.toInt()}" in dump,
            "explicit stroke/darkStroke ColorTheme:\n$dump",
        )
        assertTrue("ColorId([" in dump, "paint uses themed ColorId:\n$dump")
    }
}
