package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindingTest {
    @Test
    fun `parses literals, paths, indexes and filters with quoted args`() {
        val t = Template.parse("Now {{ w.current[0].temp_2m | round:1 | default:'n/a' }}° at {{w.time | date:'HH:mm'}}")
        assertEquals(4, t.parts.size) // "Now ", expr, "° at ", expr
        val first = t.expressions[0]
        assertEquals("w", first.root)
        assertEquals(listOf(PathStep.Key("current"), PathStep.Index(0), PathStep.Key("temp_2m")), first.path)
        assertEquals(listOf(FilterCall("round", listOf("1")), FilterCall("default", listOf("n/a"))), first.filters)
        assertEquals(FilterCall("date", listOf("HH:mm")), t.expressions[1].filters.single())
        assertEquals(Template.Literal("Now "), t.parts.first())
    }

    @Test
    fun `bare names, index root and plain text`() {
        assertEquals(
            FilterCall("lookup", listOf("wmo")),
            Template.parse("{{x | lookup:wmo}}").expressions.single().filters.single(),
        )
        assertEquals("\$index", Template.parse("{{\$index}}").expressions.single().root)
        assertEquals(listOf(Template.Literal("no bindings }}")), Template.parse("no bindings }}").parts)
        assertFalse(Template.hasBindings("plain"))
    }

    @Test
    fun `syntax errors`() {
        listOf(
            "{{a",
            "{{}}",
            "{{a..b}}",
            "{{a | 9x}}",
            "{{a | default:'x}}",
            "{{a | default:x y}}",
            "{{a b}}",
        ).forEach {
            assertFailsWith<IllegalArgumentException>(it) { Template.parse(it) }
        }
        assertFailsWith<IllegalArgumentException> { Template.parsePath("a.b | round") }
    }

    private fun screen(
        components: List<Component> = emptyList(),
        data: Map<String, DataSource> = mapOf("w" to DataSource("https://api.dev/v1?q={{params.q}}")),
        params: Map<String, ParamSpec> = mapOf("q" to ParamSpec("x")),
        lookups: Map<String, Map<String, String>> = mapOf("codes" to mapOf("*" to "?")),
        error: List<Component> = emptyList(),
    ) = ScreenDefinition("s", params = params, data = data, lookups = lookups, components = components, error = error)

    private fun problems(vararg components: Component) = screen(components.toList()).problems()

    @Test
    fun `valid bound screen has no problems and is bound`() {
        val s = screen(
            listOf(
                Component.Text("{{w.temp | round}} {{w.code | lookup:codes}} {{params.q | upper}}"),
                Component.Repeat(
                    RepeatItems.Zip(mapOf("day" to "w.daily.time", "max" to "w.daily.max")),
                    alias = "d",
                    template = listOf(
                        Component.Button("{{d.day}} #{{\$index}}", Action.Navigate("day", mapOf("d" to "{{d.day}}"))),
                    ),
                    empty = listOf(Component.Text("none {{w.x}}")),
                ),
            ),
            error = listOf(Component.Text("failed for {{params.q}}")),
        )
        assertEquals(emptyList(), s.problems())
        assertTrue(s.isBound)
        assertFalse(ScreenDefinition("plain", components = listOf(Component.Text("x"))).isBound)
    }

    @Test
    fun `scopes, names and filters are checked with paths`() {
        fun assertProblem(fragment: String, problems: List<String>) =
            assertTrue(problems.any { fragment in it }, "expected '$fragment' in $problems")

        assertProblem("components[0].text: {{nope.x}}: unknown 'nope'", problems(Component.Text("{{nope.x}}")))
        assertProblem("unknown param 'z'", problems(Component.Text("{{params.z}}")))
        assertProblem("secrets are only allowed", problems(Component.Text("{{secret.KEY}}")))
        assertProblem("unknown filter 'trim'", problems(Component.Text("{{w.a | trim}}")))
        assertProblem("unknown lookup 'other'", problems(Component.Text("{{w.a | lookup:other}}")))
        assertProblem("round digits", problems(Component.Text("{{w.a | round:42}}")))
        assertProblem("takes 1..1 arguments", problems(Component.Text("{{w.a | date}}")))
        assertProblem(
            "components[0].action.params.id",
            problems(Component.Button("b", Action.Navigate("m", mapOf("id" to "{{x.id}}")))),
        )
        // $index and the alias exist only inside the template
        assertProblem("unknown '\$index'", problems(Component.Text("{{\$index}}")))
        val repeat = Component.Repeat(RepeatItems.Path("w.list"), alias = "w", template = emptyList())
        assertProblem("components[0].as: 'w' hides", problems(repeat))
        assertProblem(
            "components[0].items: unknown 'other'",
            problems(Component.Repeat(RepeatItems.Path("other.list"), template = emptyList())),
        )
        // error components see only params
        assertProblem(
            "error[0].text: {{w.a}}: unknown 'w'",
            screen(error = listOf(Component.Text("{{w.a}}"))).problems(),
        )
    }

    @Test
    fun `data sources - literal host, secrets only there, limits`() {
        fun sourceProblems(source: DataSource) = screen(data = mapOf("w" to source)).problems()
        assertTrue(sourceProblems(DataSource("https://{{params.q}}/x")).any { "host must be literal" in it })
        assertTrue(sourceProblems(DataSource("https://api.dev{{params.q}}")).any { "host must be literal" in it })
        assertTrue(sourceProblems(DataSource("ftp://api.dev/x")).any { "http:// or https://" in it })
        assertEquals(
            emptyList(),
            sourceProblems(
                DataSource("https://api.dev/x?k={{secret.KEY}}", mapOf("Authorization" to "Bearer {{secret.TOKEN}}")),
            ),
        )
        assertTrue(sourceProblems(DataSource("https://api.dev/x", timeoutMs = 0)).any { "timeoutMs" in it })
        assertTrue(screen(data = mapOf("params" to DataSource("https://a.dev/"))).problems().any { "reserved" in it })
    }

    @Test
    fun `json round trip of a bound screen, repeat items as string or object`() {
        val json = """
            {"id":"f","params":{"lat":{"default":"52"}},
             "data":{"w":{"url":"https://a.dev/?lat={{params.lat}}","cacheSeconds":600}},
             "lookups":{"c":{"0":"Clear"}},
             "components":[
               {"type":"repeat","items":"w.list","template":[{"type":"text","text":"{{item.name}}"}]},
               {"type":"repeat","items":{"t":"w.time"},"as":"d","limit":3,"template":[{"type":"text","text":"{{d.t}}"}]}
             ],
             "error":[{"type":"text","text":"down"}]}
        """.trimIndent()
        val screen = ScreenJson.decode(json, "f.json")
        assertEquals(RepeatItems.Path("w.list"), (screen.components[0] as Component.Repeat).items)
        assertEquals(RepeatItems.Zip(mapOf("t" to "w.time")), (screen.components[1] as Component.Repeat).items)
        assertEquals(screen, ScreenJson.decode(ScreenJson.encode(screen), "f.json"))
        val bad = assertFailsWith<IllegalArgumentException> {
            ScreenJson.decode("""{"id":"f","components":[{"type":"repeat","items":5,"template":[]}]}""", "f.json")
        }
        assertTrue("f.json" in bad.message!!, bad.message)
    }
}
