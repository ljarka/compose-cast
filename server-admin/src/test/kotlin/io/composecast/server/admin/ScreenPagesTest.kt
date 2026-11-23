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

    @Test
    fun `publish makes the textarea live, records who, and clears the draft`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        screens.saveDraft("main", ScreenJson.encode(home.copy(title = "Old draft")))
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val shown = """{"id": "main", "title": "Home v2"}"""

        val published = client.form(cookie, "/admin/screens/main/publish", "json" to shown)
        assertEquals(HttpStatusCode.SeeOther, published.status)
        assertEquals("/admin/screens/main?published", published.headers[HttpHeaders.Location])
        val stored = screens.get("main")!!
        assertEquals(ScreenJson.encode(ScreenDefinition("main", title = "Home v2")), stored.liveJson)
        assertNull(stored.draftJson)
        assertEquals("editor@example.com", stored.publishedBy)

        val editor = client.page(cookie, "/admin/screens/main?published").bodyAsText()
        assertContains(editor, "Published: apps get this version")
        assertContains(editor, "#2")
        assertContains(editor, "Roll back to this")
    }

    @Test
    fun `an invalid or unbuildable publish changes nothing and says why`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        val invalid = client.form(cookie, "/admin/screens/main/publish", "json" to """{"id": " "}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        assertContains(invalid.bodyAsText(), "id: must not be blank")

        host.failWith = "screen 'main': COMPOSECAST_SECRET_KEY is not set"
        val failed = client.form(cookie, "/admin/screens/main/publish", "json" to homeJson)
        assertEquals(HttpStatusCode.UnprocessableEntity, failed.status)
        assertContains(
            failed.bodyAsText(),
            "Not published, nothing changed: screen &#39;main&#39;: COMPOSECAST_SECRET_KEY",
        )
        assertEquals(mapOf("main" to homeJson), screens.live())
        assertEquals(1, screens.history("main").size)
    }

    @Test
    fun `rollback republishes an old version and old versions can be viewed`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        screens.publish("main", ScreenJson.encode(home.copy(title = "v2")), by = "import")
        val first = screens.history("main").last()
        val cookie = with(host) { client.loginAs(Role.EDITOR) }

        val revision = client.page(cookie, "/admin/screens/main/revisions/${first.id}")
        assertEquals(HttpStatusCode.OK, revision.status)
        assertContains(revision.bodyAsText(), "Hello")
        assertEquals(HttpStatusCode.NotFound, client.page(cookie, "/admin/screens/main/revisions/999").status)

        val rolledBack = client.form(cookie, "/admin/screens/main/rollback", "revision" to first.id.toString())
        assertEquals("/admin/screens/main?rolledback", rolledBack.headers[HttpHeaders.Location])
        assertEquals(homeJson, screens.live()["main"])
        assertEquals(3, screens.history("main").size, "append-only")
        assertEquals(
            HttpStatusCode.NotFound,
            client.form(cookie, "/admin/screens/main/rollback", "revision" to "999").status,
        )
    }

    @Test
    fun `preview shows a QR code for the textarea and saves nothing`() = host.run { client ->
        screens.publish("main", homeJson, by = "import")
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val shown = """{"id": "main", "title": "Draft only"}"""

        val response = client.submitForm(
            "/admin/screens/main/preview",
            parameters { append("json", shown) },
        ) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.Host, "app.example.com")
            header("X-Forwarded-Proto", "https")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "<svg xmlns=\"http://www.w3.org/2000/svg\"")
        assertContains(body, "href=\"https://app.example.com/connect?screen=__preview-test1\"")
        assertContains(body, "Draft only", message = "the textarea keeps the text")
        assertEquals(listOf(ScreenJson.encode(ScreenDefinition("main", title = "Draft only"))), host.previews)
        assertNull(screens.get("main")!!.draftJson, "nothing saved")
        assertEquals(mapOf("main" to homeJson), screens.live())

        val invalid = client.form(cookie, "/admin/screens/main/preview", "json" to """{"id": "main", "x": 1}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        assertEquals(1, host.previews.size)
    }

    @Test
    fun `a live screen can be set as the home screen`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        screens.saveDraft("shows", ScreenJson.encode(ScreenDefinition("shows", title = "Shows")))
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.form(cookie, "/admin/screens/shows/home").status,
            "draft-only screen cannot be home yet",
        )

        client.form(
            cookie,
            "/admin/screens/shows/publish",
            "json" to ScreenJson.encode(ScreenDefinition("shows", title = "Shows")),
        )
        val setHome = client.form(cookie, "/admin/screens/shows/home")
        assertEquals(HttpStatusCode.SeeOther, setHome.status)
        assertEquals("/admin/screens", setHome.headers[HttpHeaders.Location])

        val list = client.get("/admin/screens") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertContains(
            list,
            "<a href=\"/admin/screens/shows\"><code>shows</code></a> <span class=\"status status-live\">home</span>",
        )
    }

    @Test
    fun `a non-home screen can be removed and the home screen cannot`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        screens.saveDraft("main", homeJson)
        client.form(cookie, "/admin/screens/main/publish", "json" to homeJson)
        screens.saveDraft("temp", ScreenJson.encode(ScreenDefinition("temp", title = "Temp")))

        val list = client.get("/admin/screens") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertContains(list, "Actions for temp")
        assertContains(list, "/admin/screens/temp/delete")

        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.form(cookie, "/admin/screens/main/delete").status,
        )
        val deleted = client.form(cookie, "/admin/screens/temp/delete")
        assertEquals(HttpStatusCode.SeeOther, deleted.status)
        assertNull(screens.get("temp"))
    }

    @Test
    fun `describe returns decoded Remote Compose output for valid json`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val ok = client.form(cookie, "/admin/screens/main/describe", "json" to homeJson).bodyAsText()
        assertContains(ok, "HEADER v1.1.0")
        val bad = client.form(cookie, "/admin/screens/main/describe", "json" to """{"id": " "}""").bodyAsText()
        assertContains(bad, "error")
    }

    @Test
    fun `past revisions can be removed from history but the live revision cannot`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        screens.publish("main", homeJson, by = "a@example.com")
        screens.publish("main", """{"id": "main", "title": "V2"}""", by = "a@example.com")
        val history = screens.history("main")
        val live = history.first()
        val older = history.last()

        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.form(cookie, "/admin/screens/main/revisions/${live.id}/delete").status,
        )
        val removed = client.form(cookie, "/admin/screens/main/revisions/${older.id}/delete")
        assertEquals(HttpStatusCode.SeeOther, removed.status)
        assertNull(screens.revision("main", older.id))
        assertEquals(HttpStatusCode.NotFound, client.page(cookie, "/admin/screens/main/revisions/${older.id}").status)
    }
}
