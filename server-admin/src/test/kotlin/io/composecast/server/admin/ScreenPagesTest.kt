package io.composecast.server.admin

import io.composecast.protocol.Component
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-007 A3: screen list, JSON editor with validation, drafts. */
class ScreenPagesTest {
    private val host = AdminTestHost()
    private val screens = host.screens

    private val home = ScreenDefinition(id = "main", title = "Home", components = listOf(Component.Text("Hello")))
    private val homeJson = ScreenJson.encode(home)

    private suspend fun HttpClient.form(cookie: String, path: String, vararg fields: Pair<String, String>) = submitForm(
        path,
        parameters {
            fields.forEach { (k, v) -> append(k, v) }
        },
    ) { header(HttpHeaders.Cookie, cookie) }

    private suspend fun HttpClient.page(cookie: String, path: String): HttpResponse =
        get(path) { header(HttpHeaders.Cookie, cookie) }

    @Test
    fun `the list shows every screen with its title and status`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        screens.publish("about", ScreenJson.encode(ScreenDefinition("about", title = "About us")), by = "import")
        screens.saveDraft("about", ScreenJson.encode(ScreenDefinition("about", title = "About (edited)")))
        screens.saveDraft("new", ScreenJson.encode(ScreenDefinition("new")))
        val cookie = with(host) { client.loginAs(Role.VIEWER) }

        val body = client.page(cookie, "/admin/screens").bodyAsText()
        listOf(
            "Home",
            "About (edited)",
            "status-live",
            "status-draft",
            "status-unpublished",
            "import",
            "never",
        ).forEach {
            assertContains(body, it)
        }
        assertFalse("New screen" in body, "viewers cannot create screens")
    }

    @Test
    fun `an editor creates a screen as an unpublished draft`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val created = client.form(cookie, "/admin/screens", "id" to "settings", "title" to "Settings")
        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertEquals("/admin/screens/settings", created.headers[HttpHeaders.Location])
        val stored = screens.get("settings")!!
        assertEquals(ScreenDefinition("settings", title = "Settings"), ScreenJson.decode(stored.draftJson!!, "draft"))
        assertNull(stored.liveJson, "apps do not see it")

        for (id in listOf("settings", "Bad Id", "", "-x")) {
            val rejected = client.form(cookie, "/admin/screens", "id" to id)
            assertEquals(HttpStatusCode.UnprocessableEntity, rejected.status, "id '$id'")
        }
        assertContains(client.form(cookie, "/admin/screens", "id" to "settings").bodyAsText(), "already exists")
    }

    @Test
    fun `an invalid edit shows the problems with JSON paths and is not saved`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val invalid = """{"id": "main", "components": [{"type": "text", "text": "Hi", "color": "red"}]}"""

        val response = client.form(cookie, "/admin/screens/main", "json" to invalid)
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertContains(body, "components[0]")
        assertContains(body, "not saved until fixed")
        assertContains(body, "&quot;color&quot;: &quot;red&quot;", message = "the edit stays in the textarea (escaped)")
        assertNull(screens.get("main")!!.draftJson, "nothing saved")

        val syntax = client.form(cookie, "/admin/screens/main", "json" to """{"id": "main",""")
        assertEquals(HttpStatusCode.UnprocessableEntity, syntax.status)
        val renamed = client.form(cookie, "/admin/screens/main", "json" to """{"id": "other"}""")
        assertContains(renamed.bodyAsText(), "id: must be &#39;main&#39;")
        assertNull(screens.get("main")!!.draftJson)
    }

    @Test
    fun `a saved draft is canonical and does not change what apps get`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val edited = """{ "id": "main", "title": "Home v2",
            "components": [ { "type": "text", "text": "Hello again" } ] }"""

        val saved = client.form(cookie, "/admin/screens/main", "json" to edited)
        assertEquals(HttpStatusCode.SeeOther, saved.status)
        assertEquals("/admin/screens/main?saved", saved.headers[HttpHeaders.Location])
        val expected = home.copy(title = "Home v2", components = listOf(Component.Text("Hello again")))
        assertEquals(ScreenJson.encode(expected), screens.get("main")!!.draftJson, "stored as canonical JSON")
        assertEquals(mapOf("main" to homeJson), screens.live(), "apps still get the live version")

        val editor = client.page(cookie, "/admin/screens/main?saved").bodyAsText()
        assertContains(editor, "Draft saved")
        assertContains(editor, "Home v2")
        assertContains(editor, "status-draft")
    }

    @Test
    fun `validation while typing returns the problems fragment`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        val valid = client.form(cookie, "/admin/screens/main/validate", "json" to homeJson)
        assertEquals(HttpStatusCode.OK, valid.status)
        assertContains(valid.bodyAsText(), "Valid.")
        assertFalse("<html" in valid.bodyAsText(), "a fragment, not a page")

        val invalid = client.form(cookie, "/admin/screens/main/validate", "json" to """{"id": " "}""").bodyAsText()
        assertContains(invalid, "id: must not be blank")
    }

    @Test
    fun `the editor loads HTMX for editors only and viewers get a read-only page`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        val editor = client.page(with(host) { client.loginAs(Role.EDITOR) }, "/admin/screens/main").bodyAsText()
        assertContains(editor, "htmx.org@2.0.11")
        assertContains(editor, "integrity=\"sha384-")
        assertContains(editor, "hx-post=\"/admin/screens/main/validate\"")
        assertContains(editor, "Save draft")

        val viewer = client.page(with(host) { client.loginAs(Role.VIEWER) }, "/admin/screens/main").bodyAsText()
        assertContains(viewer, "readonly")
        assertFalse("Save draft" in viewer || "htmx" in viewer)
        assertEquals(
            HttpStatusCode.NotFound,
            client.page(
                with(host) {
                    client.loginAs(Role.ADMIN)
                },
                "/admin/screens/nope",
            ).status,
        )
    }

    @Test
    fun `screen content cannot break out of the textarea`() = host.run { client ->
        val hostile =
            ScreenDefinition("main", components = listOf(Component.Text("</textarea><script>alert(1)</script>")))
        screens.publish("main", ScreenJson.encode(hostile), by = "import")
        val body = client.page(with(host) { client.loginAs(Role.EDITOR) }, "/admin/screens/main").bodyAsText()
        assertFalse("<script>alert" in body)
        assertTrue("&lt;/textarea&gt;" in body)
    }
}
