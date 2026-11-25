package io.composecast.server.admin

import io.composecast.protocol.Component
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-029 C2 (ADR 0028): every field of every component is editable in a generated form, losslessly. */
class FormSchemaTest {
    private val demo = Path.of(System.getProperty("composecast.examples"), "demo", "screens")

    /** Fields a form shows as a JSON value on purpose (maps and the repeat source). */
    private val jsonFields = setOf("action.params", "modifier.action.params", "items")

    private fun FormSchema.Field.all(): List<FormSchema.Field> = listOf(this) + when (val kind = kind) {
        is FormSchema.Kind.Group -> kind.fields.flatMap { it.all() }
        is FormSchema.Kind.Variant -> kind.variants.values.flatten().flatMap { it.all() }
        else -> emptyList()
    }

    @Test
    fun `every component type has a form and no unexpected raw JSON field`() {
        val types = FormSchema.componentTypes.keys
        val known = setOf("text", "image", "icon", "divider", "card", "button", "spacer", "column", "row") +
            setOf("box", "fitBox", "flow", "grid", "repeat", "expandable") +
            setOf("gauge", "sparkline", "kpi", "slider")
        assertEquals(
            known,
            types,
            "a new component: check its form, then add it here",
        )
        for (type in types) {
            val raw = FormSchema.componentFields(type)!!.flatMap { it.all() }
                .filter { it.kind == FormSchema.Kind.JsonValue }.map { it.path }.toSet()
            assertTrue(jsonFields.containsAll(raw), "$type: unexpected JSON-value fields ${raw - jsonFields}")
        }
        assertEquals(
            listOf("title", "params", "live", "lookups", "values"),
            FormSchema.screenFields.map {
                it.key
            },
            "data sources are tree nodes (T-031 W3)",
        )
        assertEquals(
            listOf("url", "headers", "timeoutMs", "cacheSeconds", "optional"),
            FormSchema.sourceFields.map {
                it.key
            },
        )
    }

    @Test
    fun `every component of the demo screens survives form values and back`() {
        var checked = 0
        for (file in demo.listDirectoryEntries("*.json")) {
            val screen = ScreenJson.decode(file.readText(), file.toString())
            fun visit(component: Component) {
                val type = FormSchema.withDefaults(component)["type"].toString().trim('"')
                val fields = FormSchema.componentFields(type)!!
                val values = FormSchema.values(fields, FormSchema.withDefaults(component))
                val problems = mutableListOf<String>()
                val parsed = FormSchema.parse(fields, values::get, problems)
                assertEquals(emptyList(), problems, "${file.fileName}: $type")
                // Component lists are the tree, not form fields: take them from the original.
                val original = FormSchema.withDefaults(component)
                val lists = FormSchema.componentLists(FormSchema.componentTypes.getValue(type)).associateWith {
                    original.getValue(it)
                }
                val rebuilt = JsonObject(mapOf("type" to original.getValue("type")) + parsed + lists)
                assertEquals(
                    component,
                    Json.decodeFromJsonElement(Component.serializer(), rebuilt),
                    "${file.fileName}: $type",
                )
                checked++
                component.childLists().forEach { (_, children) -> children.forEach(::visit) }
            }
            (screen.components + screen.error).forEach(::visit)

            val screenValues = FormSchema.values(
                FormSchema.screenFields,
                Json.encodeToJsonElement(ScreenDefinition.serializer(), screen) as JsonObject,
            )
            val parsedScreen = FormSchema.parse(FormSchema.screenFields, screenValues::get, mutableListOf())
            assertEquals(screen.title, parsedScreen["title"].toString().trim('"'))
        }
        assertTrue(checked > 50, "only $checked components checked")
    }

    @Test
    fun `numbers are checked, empty optional fields are left out`() {
        val problems = mutableListOf<String>()
        val form = mapOf(
            "text" to "Hi",
            "fontSize" to "big",
            "maxLines" to "",
            "modifier.width" to "fill",
            "modifier.height" to "48",
        )
        val parsed = FormSchema.parse(FormSchema.componentFields("text")!!, form::get, problems)
        assertEquals(listOf("fontSize: expected a whole number, was 'big'"), problems)
        assertEquals("""{"text":"Hi","modifier":{"width":"fill","height":48}}""", parsed.toString())
    }

    @Test
    fun `the screen form makes a screen live with Every ms and normal again when it is blank (T-047)`() {
        val live = FormSchema.parse(
            FormSchema.screenFields,
            mapOf("title" to "D", "live.everyMs" to "500")::get,
            mutableListOf(),
        )
        assertEquals("""{"everyMs":500}""", live["live"].toString())
        val normal = FormSchema.parse(
            FormSchema.screenFields,
            mapOf("title" to "D", "live.everyMs" to "")::get,
            mutableListOf(),
        )
        assertEquals(null, normal["live"])
    }
}
