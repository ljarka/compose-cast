package io.composecast.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T-057: designs compile to the RC JSON the T-049 pipeline renders; how pictures look is in compat-tests. */
class ComponentDesignTest {
    private fun rect(id: String, vararg more: Pair<String, Any>) = element(id, DesignElementType.Rect, *more)

    private fun element(id: String, type: DesignElementType, vararg more: Pair<String, Any>): DesignElement {
        val m = more.toMap()
        return DesignElement(
            id = id,
            type = type,
            x = (m["x"] ?: 0f) as Float,
            y = (m["y"] ?: 0f) as Float,
            w = (m["w"] ?: 10f) as Float,
            h = (m["h"] ?: 10f) as Float,
            anchor = (m["anchor"] ?: DesignAnchor.Left) as DesignAnchor,
            fill = m["fill"] as String? ?: "#FF000000",
            text = m["text"] as String?,
            action = m["action"] as String?,
            path = m["path"] as String?,
            pathWidth = m["pathWidth"] as Float?,
            pathHeight = m["pathHeight"] as Float?,
        )
    }

    private val JsonObject.children get() = this["children"]!!.jsonArray.map { it.jsonObject }
    private val JsonObject.type get() = this["type"]!!.jsonPrimitive.content

    /** The commands inside the `save` of the first shape of the design's only canvas. */
    private fun ComponentDesign.shapeCommands(): List<JsonObject> =
        toBody().children.single()["commands"]!!.jsonArray.first().jsonObject["commands"]!!.jsonArray.map {
            it.jsonObject
        }

    private fun JsonObject.clickName(): String = modifier("onClick")!!.jsonObject["name"]!!.jsonPrimitive.content

    private fun JsonObject.modifier(name: String) =
        this["modifiers"]!!.jsonArray.map { it.jsonObject }.firstOrNull { name in it }?.get(name)

    @Test
    fun `the body is a full-width box of the design's height, clipped by its corners`() {
        val body = ComponentDesign(height = 90f, cornerRadius = 12f, elements = listOf(rect("a"))).toBody()
        assertEquals("box", body.type)
        assertEquals("90.0", body.modifier("height").toString())
        assertEquals("12.0", body.modifier("clip").toString())
        assertNotNull(body.modifier("fillMaxWidth"))
    }

    @Test
    fun `consecutive shapes share a canvas, texts split them, tap areas go on top`() {
        val design = ComponentDesign(
            elements = listOf(
                rect("a"),
                rect("b", "action" to "navigate:main"),
                element("t", DesignElementType.Text, "text" to "Hi"),
                rect("c"),
                element("tap", DesignElementType.Tap, "action" to "back"),
            ),
        )
        val layers = design.toBody().children
        assertEquals(listOf("canvas", "box", "canvas", "box", "box"), layers.map { it.type })
        assertEquals(2, layers[0]["commands"]!!.jsonArray.size) // a, b
        assertEquals("text", layers[1].children.single().type)
        // Tap areas: an outer box with padding holds a box of the element's size with the click
        // (RC clicks cover a component's whole bounds, padding included).
        assertEquals("navigate:main", layers[3].children.single().children.single().clickName())
        assertEquals("back", layers[4].children.single().children.single().clickName())
    }

    @Test
    fun `shapes move by anchor in pixels and draw in their own dp box`() {
        fun translate(anchor: DesignAnchor): String {
            val shape = rect("a", "x" to 300f, "w" to 40f, "anchor" to anchor)
            val commands = ComponentDesign(width = 360f, elements = listOf(shape)).shapeCommands()
            assertEquals(listOf("translate", "scale"), commands.take(2).map { it.type })
            return commands[0]["dx"]!!.jsonPrimitive.content
        }
        assertEquals("300 * density", translate(DesignAnchor.Left))
        assertEquals("width - 60 * density", translate(DesignAnchor.Right))
        assertEquals("width / 2 + 120 * density", translate(DesignAnchor.Center))
    }

    @Test
    fun `a stretched shape grows with the component, a scaled path keeps its viewBox`() {
        val background = rect("bg", "w" to 360f, "anchor" to DesignAnchor.Stretch)
        val draw = ComponentDesign(width = 360f, elements = listOf(background)).shapeCommands().first {
            it.type ==
                "drawRect"
        }
        assertEquals("width / density - 0", draw["right"]!!.jsonPrimitive.content)

        val icon = element(
            "i",
            DesignElementType.Path,
            "w" to 48f,
            "h" to 48f,
            "path" to "M0 0 L24 24",
            "pathWidth" to 24f,
            "pathHeight" to 24f,
        )
        val commands = ComponentDesign(elements = listOf(icon)).shapeCommands()
        val scales = commands.filter { it.type == "scale" }
        assertEquals(2, scales.size) // density, then viewBox → element box
        assertEquals("2.0", scales[1]["sx"].toString())
    }

    @Test
    fun `texts are placed with padding, never offset (RC moves drawing but not touch by offset)`() {
        val text = element("t", DesignElementType.Text, "x" to 20f, "y" to 30f, "w" to 100f, "text" to "Hi")
        val body = ComponentDesign(elements = listOf(text)).toBody().toString()
        assertFalse("offset" in body, body)
        val padding = ComponentDesign(elements = listOf(text)).toBody().children.single().children.single()
            .modifier("padding")!!.jsonObject
        assertEquals("20.0", padding["start"].toString())
        assertEquals("30.0", padding["top"].toString())
    }

    @Test
    fun `problems name the element and field`() {
        val design = ComponentDesign(
            height = 4f,
            elements = listOf(
                rect("a", "fill" to "red"),
                rect("a", "action" to "dance"),
                element("p", DesignElementType.Path),
                element("l", DesignElementType.Line, "w" to -20f, "h" to -5f),
            ),
        )
        val problems = design.problems()
        assertTrue(problems.any { it.startsWith("design.height:") }, "$problems")
        assertTrue(problems.any { it.startsWith("design.elements[0].fill:") }, "$problems")
        assertTrue(problems.any { it.startsWith("design.elements[1].id:") }, "$problems")
        assertTrue(problems.any { it.startsWith("design.elements[1].action:") }, "$problems")
        assertTrue(problems.any { it.startsWith("design.elements[2].path:") }, "$problems")
        assertFalse(problems.any { it.startsWith("design.elements[3]") }, "a line may point up and left: $problems")
        assertTrue(ComponentDesign(elements = listOf(rect("a", "fill" to "{{params.color}}"))).problems().isEmpty())
    }

    @Test
    fun `a designed definition is stored without its body and compiled when read`() {
        val definition = ComponentDefinition(
            id = "card",
            rcVersion = "1.0.0-alpha23",
            design = ComponentDesign(elements = listOf(rect("a"))),
        )
        val json = ComponentJson.encode(definition)
        assertFalse("\"body\"" in json, json)
        val read = ComponentJson.decode(json, "t")
        assertEquals("box", read.body.type)
        assertTrue(read.body.children.isNotEmpty())
        // A design's params are checked like a body's.
        val tinted = ComponentDesign(elements = listOf(rect("a", "fill" to "{{params.tint}}")))
        val undeclared = definition.copy(design = tinted)
        assertTrue(ComponentJson.check(undeclared).problems.any { "params.tint" in it })
        assertEquals(JsonArray::class, read.body["children"]!!::class)
    }

    @Test
    fun `dark color overrides compile to themed placeholders and are validated`() {
        val design = ComponentDesign(
            elements = listOf(
                DesignElement(
                    id = "card",
                    type = DesignElementType.Rect,
                    w = 360f,
                    h = 120f,
                    fill = "#FFFFFFFF",
                    darkFill = "#FF1E1E24",
                    fill2 = "#FFF0F0F5",
                    darkFill2 = "#FF2A2A32",
                    stroke = "#FFDCDCE0",
                    darkStroke = "#FF49454F",
                    strokeWidth = 1f,
                ),
                DesignElement(
                    id = "title",
                    type = DesignElementType.Text,
                    x = 16f,
                    y = 16f,
                    w = 200f,
                    text = "Hello",
                    fill = "#FF1D1D1F",
                    darkFill = "#FFE6E0E9",
                ),
            ),
        )
        assertEquals(emptyList(), design.problems())
        val bodyText = design.toBody().toString()
        assertTrue("{{themed:#FFFFFFFF|#FF1E1E24}}" in bodyText, bodyText)
        assertTrue("{{themed:#FFF0F0F5|#FF2A2A32}}" in bodyText, bodyText)
        assertTrue("{{themed:#FFDCDCE0|#FF49454F}}" in bodyText, bodyText)
        assertTrue("{{themed:#FF1D1D1F|#FFE6E0E9}}" in bodyText, bodyText)

        val bad = ComponentDesign(
            elements = listOf(
                DesignElement(
                    id = "bad",
                    type = DesignElementType.Rect,
                    w = 100f,
                    h = 40f,
                    fill = "#FFFFFFFF",
                    darkFill = "not-a-color",
                ),
            ),
        )
        assertTrue(bad.problems().any { it.startsWith("design.elements[0].darkFill:") }, "${bad.problems()}")
    }
}
