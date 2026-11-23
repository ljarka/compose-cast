package io.composecast.server.admin

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** T-031 W3: the field picker and data sources as tree nodes. */
class DataFieldsTest {
    private val host = AdminTestHost()
    private val shows = javaClass.getResource("/fixtures/apis/tvmaze-shows.json")!!.readText()

    /** A list screen like the wizard's: a repeat of shows. */
    private val screen = """{"id": "list", "params": {"page": {"default": "0"}},
        "data": {"shows": {"url": "https://api.tvmaze.com/shows?page={{params.page}}"}},
        "components": [{"type": "repeat", "items": "shows", "as": "show",
            "template": [{"type": "card", "children": [{"type": "text", "text": "{{show.name}}"}]}]}]}"""

    @Test
    fun `inside a repeat the alias' fields come first, with real samples`() {
        host.apiResponses["https://api.tvmaze.com/shows?page=0"] = shows
        val groups = DataFields(
            host.probe,
        ).groups(Json.parseToJsonElement(screen) as JsonObject, "components.0.template.0.children.0")
        assertEquals(
            listOf("Each item of the repeat ({{show}})", "Data source shows", "Screen parameters"),
            groups.map {
                it.title
            },
        )
        val item = groups.first().fields.associate { it.binding to it.sample }
        assertEquals("Under the Dome", item["{{show.name}}"])
        assertEquals(
            true,
            item.getValue(
                "{{show.image.medium}}",
            ).startsWith("https://static.tvmaze.com/uploads/images/medium_portrait/"),
        )
        assertEquals("a list of 4", groups[1].fields.single { it.binding == "{{shows}}" }.sample)
        assertEquals("0", groups[2].fields.single().sample)
    }

    @Test
    fun `outside a repeat no alias, a failing source says why`() {
        val groups = DataFields(host.probe).groups(Json.parseToJsonElement(screen) as JsonObject, "components.0")
        assertEquals(listOf("Data source shows", "Screen parameters"), groups.map { it.title })
        assertEquals("the API answered HTTP 404", groups.first().error)
    }

    @Test
    fun `sources are added, edited and deleted as tree nodes, and the picker answers over HTTP`() = host.run { client ->
        host.apiResponses["https://api.tvmaze.com/shows?page=0"] = shows
        host.screens.publish(
            "list",
            io.composecast.protocol.ScreenJson.encode(io.composecast.protocol.ScreenJson.decode(screen, "s")),
            by = "import",
        )
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        suspend fun post(url: String, vararg fields: Pair<String, String>) = client.submitForm(
            url,
            parameters {
                fields.forEach { (k, v) -> append(k, v) }
            },
        ) { header(HttpHeaders.Cookie, cookie) }

        val tree = client.get("/admin/screens/list/visual") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertContains(tree, "Data sources")
        assertContains(tree, "shows · https://api.tvmaze.com/shows")

        val added = post(
            "/admin/screens/list/visual/edit?node=data",
            "op" to "add-source",
            "name" to "people",
            "url" to "https://api.tvmaze.com/people",
        )
        assertEquals("/admin/screens/list/visual?node=data.people&saved", added.headers[HttpHeaders.Location])
        val form = client.get("/admin/screens/list/visual?node=data.people") {
            header(HttpHeaders.Cookie, cookie)
        }.bodyAsText()
        assertContains(form, "name=\"cacheSeconds\" type=\"number\" step=\"1\" value=\"60\"")
        post(
            "/admin/screens/list/visual?node=data.people",
            "url" to "https://api.tvmaze.com/people",
            "cacheSeconds" to "600",
            "timeoutMs" to "5000",
        )
        val draft = io.composecast.protocol.ScreenJson.decode(host.screens.get("list")!!.draftJson!!, "d")
        assertEquals(600, draft.data.getValue("people").cacheSeconds)
        assertEquals(setOf("shows", "people"), draft.data.keys)

        val duplicate = post(
            "/admin/screens/list/visual/edit?node=data",
            "op" to "add-source",
            "name" to "people",
            "url" to "https://x.org",
        )
        assertContains(duplicate.bodyAsText(), "there is already a data source")
        val inUse = post("/admin/screens/list/visual/edit?node=data.shows", "op" to "delete-source")
        assertEquals(HttpStatusCode.UnprocessableEntity, inUse.status, "the repeat still lists shows")
        assertEquals(
            HttpStatusCode.SeeOther,
            post(
                "/admin/screens/list/visual/edit?node=data.people",
                "op" to "delete-source",
            ).status,
        )

        val picker = client.get("/admin/screens/list/visual/fields?node=components.0.template.0.children.0") {
            header(HttpHeaders.Cookie, cookie)
        }.bodyAsText()
        assertContains(picker, "data-binding=\"{{show.name}}\"")
        assertFalse("<html" in picker, "a fragment")
        assertContains(
            client.get("/admin/screens/list/visual?node=components.0") {
                header(HttpHeaders.Cookie, cookie)
            }.bodyAsText(),
            "Show fields from the data",
        )
    }
}
