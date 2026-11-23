package io.composecast.server.admin

import io.composecast.protocol.Action
import io.composecast.protocol.Component
import io.composecast.protocol.FontWeight
import io.composecast.protocol.Modifier
import io.composecast.protocol.Padding
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.composecast.protocol.TextStyle
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** T-029 C2: the visual editor's tree and generated forms edit the stored draft. */
class VisualPagesTest {
    private val host = AdminTestHost()
    private val screens = host.screens

    private val screen = ScreenDefinition(
        id = "main",
        title = "Home",
        components = listOf(
            Component.Text("Hello", style = TextStyle.H1),
            Component.Column(
                children = listOf(Component.Button("Go", Action.Navigate("details"))),
                spacing = 8,
            ),
        ),
    )
    private val json = ScreenJson.encode(screen)

    private suspend fun HttpClient.page(cookie: String, path: String) = get(path) { header(HttpHeaders.Cookie, cookie) }

    private suspend fun HttpClient.save(cookie: String, path: String, fields: Map<String, String>) = submitForm(
        path,
        parameters {
            fields.forEach { (k, v) -> append(k, v) }
        },
    ) { header(HttpHeaders.Cookie, cookie) }

    @Test
    fun `the tree shows the components and the form shows the selected one`() = host.run { client ->
        screens.publish("main", json, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        val root = client.page(cookie, "/admin/screens/main/visual").bodyAsText()
        listOf(
            "<code>text</code>",
            "Hello",
            "<code>column</code>",
            "<code>button</code>",
            "Go",
            "name=\"title\" value=\"Home\"",
        )
            .forEach { assertContains(root, it) }

        val button = client.page(cookie, "/admin/screens/main/visual?node=components.1.children.0").bodyAsText()
        assertContains(button, "name=\"label\" value=\"Go\"")
        assertContains(button, "<option value=\"navigate\" selected>navigate</option>")
        assertContains(button, "name=\"action.screen\" value=\"details\"")
        assertContains(button, "<option value=\"filled\" selected>filled</option>", message = "defaults are shown")
        assertEquals(
            HttpStatusCode.NotFound,
            client.page(cookie, "/admin/screens/main/visual?node=components.9").status,
        )
    }

    @Test
    fun `saving a form changes only that component in the draft`() = host.run { client ->
        screens.publish("main", json, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        val saved = client.save(
            cookie,
            "/admin/screens/main/visual?node=components.0",
            mapOf(
                "text" to "Hello there",
                "style" to "h2",
                "fontWeight" to "bold",
                "modifier.padding.start" to "16",
                "modifier.padding.top" to "16",
                "modifier.padding.end" to "16",
                "modifier.padding.bottom" to "16",
            ),
        )
        assertEquals(HttpStatusCode.SeeOther, saved.status)
        assertEquals("/admin/screens/main/visual?node=components.0&saved", saved.headers[HttpHeaders.Location])

        val expected = screen.copy(
            components = listOf(
                Component.Text(
                    "Hello there",
                    style = TextStyle.H2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier(padding = Padding(16, 16, 16, 16)),
                ),
                screen.components[1],
            ),
        )
        assertEquals(ScreenJson.encode(expected), screens.get("main")!!.draftJson, "canonical, the column untouched")
        assertEquals(mapOf("main" to json), screens.live(), "apps unaffected")
        assertContains(
            client.page(cookie, "/admin/screens/main").bodyAsText(),
            "Hello there",
            message = "the JSON editor shows the same draft",
        )
    }

    @Test
    fun `invalid values are refused with the path and nothing is saved`() = host.run { client ->
        screens.publish("main", json, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        val response = client.save(
            cookie,
            "/admin/screens/main/visual?node=components.0",
            mapOf(
                "text" to "Hi",
                "color" to "red",
                "maxLines" to "two",
            ),
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertContains(body, "maxLines: expected a whole number, was &#39;two&#39;")
        assertContains(body, "components[0].color: expected #RRGGBB")
        assertContains(body, "name=\"color\" value=\"red\"", message = "the typed values stay")
        assertNull(screens.get("main")!!.draftJson)
    }

    @Test
    fun `the screen's own fields are editable and the id stays`() = host.run { client ->
        screens.publish("main", json, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        client.save(
            cookie,
            "/admin/screens/main/visual?node=",
            mapOf(
                "title" to "Start",
                "lookups" to """{"icons": {"*": "x"}}""",
            ),
        )
        val draft = ScreenJson.decode(screens.get("main")!!.draftJson!!, "draft")
        assertEquals("Start", draft.title)
        assertEquals(mapOf("icons" to mapOf("*" to "x")), draft.lookups)
        assertEquals(screen.components, draft.components)
    }

    @Test
    fun `picking another action type re-renders the form without saving`() = host.run { client ->
        screens.publish("main", json, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val panel = client.save(
            cookie,
            "/admin/screens/main/visual/panel?node=components.1.children.0",
            mapOf("label" to "Go", "action.type" to "open_url"),
        ).bodyAsText()
        assertContains(panel, "name=\"action.url\"")
        assertFalse("action.screen" in panel)
        assertNull(screens.get("main")!!.draftJson)

        client.save(
            cookie,
            "/admin/screens/main/visual?node=components.1.children.0",
            mapOf(
                "label" to "Site",
                "action.type" to "open_url",
                "action.url" to "https://example.com",
                "variant" to "outlined",
            ),
        )
        val button = ScreenJson.decode(
            screens.get("main")!!.draftJson!!,
            "d",
        ).components[1].childLists().single().second.single()
        assertEquals(
            Component.Button(
                "Site",
                Action.OpenUrl("https://example.com"),
                variant = io.composecast.protocol.ButtonVariant.Outlined,
            ),
            button,
        )
    }

    @Test
    fun `viewers see a disabled form`() = host.run { client ->
        screens.publish("main", json, by = "import")
        val page = client.page(
            with(host) {
                client.loginAs(Role.VIEWER)
            },
            "/admin/screens/main/visual?node=components.0",
        ).bodyAsText()
        assertContains(page, "<fieldset class=\"plain\" disabled>")
        assertFalse(">Save</button>" in page)
    }

    @Test
    fun `tree paths read and replace nodes`() {
        val root = Json.parseToJsonElement(json) as JsonObject
        assertEquals(JsonPrimitive("Go"), (ScreenTree.get(root, "components.1.children.0") as JsonObject)["label"])
        val replaced = ScreenTree.set(
            root,
            "components.1.children.0",
            JsonObject(mapOf("type" to JsonPrimitive("spacer"))),
        )
        assertEquals(
            "spacer",
            (ScreenTree.get(replaced, "components.1.children.0") as JsonObject)["type"].toString().trim('"'),
        )
        assertNull(ScreenTree.get(root, "components.5"))
    }

    @Test
    fun `every node of every demo screen opens, and saving it unchanged changes nothing`() = host.run { client ->
        val demo = java.nio.file.Path.of(System.getProperty("composecast.examples"), "demo", "screens")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        var nodes = 0
        for (file in kotlin.io.path.Path(demo.toString()).toFile().listFiles { f -> f.name.endsWith(".json") }!!) {
            val id = file.nameWithoutExtension
            val canonical = ScreenJson.encode(ScreenJson.decode(file.readText(), file.name))
            screens.publish(id, canonical, by = "import")
            val root = Json.parseToJsonElement(canonical) as JsonObject
            for (path in listOf("") + ScreenTree.rows(root).filter { it.type != null }.map { it.path }) {
                val url = "/admin/screens/$id/visual?node=$path"
                assertEquals(HttpStatusCode.OK, client.page(cookie, url).status, url)
                val node = ScreenTree.get(root, path) as JsonObject
                val values = when {
                    path.isEmpty() -> FormSchema.values(FormSchema.screenFields, node)
                    path.startsWith(
                        "data.",
                    ) -> FormSchema.values(FormSchema.sourceFields, FormSchema.sourceWithDefaults(node))
                    else -> FormSchema.values(
                        FormSchema.componentFields((node["type"] as JsonPrimitive).content)!!,
                        FormSchema.withDefaults(Json.decodeFromJsonElement(Component.serializer(), node)),
                    )
                }
                assertEquals(HttpStatusCode.SeeOther, client.save(cookie, url, values).status, url)
                assertEquals(canonical, screens.get(id)!!.draftJson, "$url changed the screen")
                nodes++
            }
        }
        kotlin.test.assertTrue(nodes > 60, "only $nodes nodes")
    }

    @Test
    fun `structural edits move, wrap and explain what is not possible`() = host.run { client ->
        screens.publish("main", json, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        suspend fun edit(node: String, vararg fields: Pair<String, String>) =
            client.save(cookie, "/admin/screens/main/visual/edit?node=$node", fields.toMap())

        val moved = edit("components.1", "op" to "up")
        assertEquals("/admin/screens/main/visual?node=components.0&saved", moved.headers[HttpHeaders.Location])
        val wrapped = edit("components.1", "op" to "wrap", "type" to "card")
        assertEquals(HttpStatusCode.SeeOther, wrapped.status)
        val draft = ScreenJson.decode(screens.get("main")!!.draftJson!!, "d")
        assertEquals(listOf("column", "card"), draft.components.map { it::class.simpleName!!.lowercase() })

        val refused = edit("components.0", "op" to "into-previous")
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertContains(refused.bodyAsText(), "there is no component above to move into")
        val noRepeat = edit("components.0", "op" to "add-after", "type" to "repeat")
        assertContains(noRepeat.bodyAsText(), "add a data source first")

        val page = client.page(cookie, "/admin/screens/main/visual?node=components.0").bodyAsText()
        assertContains(page, "draggable=\"true\"")
        assertContains(page, "id=\"move-form\"")
        assertContains(page, "value=\"add-inside\"", message = "a column is a container")

        val dragMoved = edit(
            "components.1",
            "op" to "move-to",
            "from" to "components.1",
            "target" to "components.0",
            "position" to "before",
        )
        assertEquals("/admin/screens/main/visual?node=components.0&saved", dragMoved.headers[HttpHeaders.Location])
        val list = client.page(cookie, "/admin/screens/main/visual?node=components.0.children")
        assertEquals(HttpStatusCode.OK, list.status)
        assertContains(list.bodyAsText(), "to this list")
        assertEquals(
            HttpStatusCode.NotFound,
            client.page(cookie, "/admin/screens/main/visual?node=components.0.nope").status,
        )
    }
}
