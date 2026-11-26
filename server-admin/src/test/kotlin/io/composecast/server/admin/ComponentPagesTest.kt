package io.composecast.server.admin

import io.composecast.protocol.Component
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentJson
import io.composecast.protocol.ComponentParam
import io.composecast.protocol.ComponentParamType
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T-049: custom components in the panel — list, editor with the full check, publish, palette. */
class ComponentPagesTest {
    private val host = AdminTestHost()

    private suspend fun HttpClient.page(cookie: String, path: String) = get(path) { header(HttpHeaders.Cookie, cookie) }

    private suspend fun HttpClient.post(cookie: String, path: String, vararg fields: Pair<String, String>) = submitForm(
        path,
        parameters {
            fields.forEach { (k, v) -> append(k, v) }
        },
    ) { header(HttpHeaders.Cookie, cookie) }

    @Test
    fun `without the component store there is no Components page`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        assertFalse("/admin/components" in client.page(cookie, "/admin/screens").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.page(cookie, "/admin/components").status)
    }

    @Test
    fun `create, check while editing, publish, and use from the visual palette`() = host.run(
        withComponents = true,
        phoneRenderer = { document -> "png:${document.decodeToString()}".toByteArray() },
    ) { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        assertContains(client.page(cookie, "/admin/screens").bodyAsText(), "href=\"/admin/components\"")

        val created = client.post(cookie, "/admin/components", "id" to "promo", "start" to "json")
        assertEquals(HttpStatusCode.SeeOther, created.status)
        val draft = assertNotNull(host.components.get("promo")?.draftJson)
        val editor = client.page(cookie, "/admin/components/promo").bodyAsText()
        assertContains(editor, "Valid.")
        assertContains(editor, "&quot;component&quot;: &quot;promo&quot;") // the usage snippet
        assertContains(editor, "src=\"/admin/components/promo/phone.png\"")

        // The whitelist and the trial parse run while typing and on save.
        val bad = draft.replace("\"type\": \"box\"", "\"type\": \"image\"")
        val checked = client.post(cookie, "/admin/components/promo/validate", "json" to bad).bodyAsText()
        assertContains(checked, "component &#39;image&#39; is not allowed")
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.post(cookie, "/admin/components/promo", "json" to bad).status,
        )

        val published = client.post(cookie, "/admin/components/promo/publish", "json" to draft)
        assertEquals(HttpStatusCode.SeeOther, published.status)
        assertEquals("promo", ComponentJson.decode(host.components.live().getValue("promo"), "t").id)

        val image = client.page(cookie, "/admin/components/promo/phone.png")
        assertEquals(ContentType.Image.PNG, image.contentType())
        assertTrue(image.bodyAsText().startsWith("png:rc-component:"))

        // The visual editor offers it under "My components" and adds it with its required args.
        host.screens.saveDraft("main", ScreenJson.encode(ScreenDefinition(id = "main")))
        assertContains(client.page(cookie, "/admin/screens/main/visual").bodyAsText(), "value=\"custom:promo\"")
        val added = client.post(
            cookie,
            "/admin/screens/main/visual/edit?node=",
            "op" to "add-end",
            "type" to "custom:promo",
        )
        assertEquals(HttpStatusCode.SeeOther, added.status, added.bodyAsText())
        assertContains(host.screens.get("main")!!.draftJson!!, "\"component\": \"promo\"")
    }

    @Test
    fun `a viewer can read but not change components`() = host.run(withComponents = true) { client ->
        val cookie = with(host) { client.loginAs(Role.VIEWER) }
        assertEquals(HttpStatusCode.OK, client.page(cookie, "/admin/components").status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post(cookie, "/admin/components", "id" to "x", "start" to "json").status,
        )
    }

    @Test
    fun `a custom use gets a form generated from the component's params`() = host.run(withComponents = true) { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        fun definition(id: String, params: Map<String, ComponentParam>) = ComponentJson.encode(
            ComponentDefinition(
                id = id,
                description = "A promo card",
                rcVersion = "1.0.0-alpha23",
                params = params,
                body = Json.parseToJsonElement("""{ "type": "text", "text": "x" }""").jsonObject,
            ),
        )
        host.components.publish(
            "promo",
            definition(
                "promo",
                mapOf(
                    "title" to ComponentParam(description = "big text"),
                    "accent" to ComponentParam(ComponentParamType.Color, default = "#FFCD00"),
                    "action" to ComponentParam(ComponentParamType.Action, default = "reload:"),
                ),
            ),
            by = "a@x",
        )
        host.components.publish(
            "badge",
            definition("badge", mapOf("count" to ComponentParam(ComponentParamType.Number))),
            by = "a@x",
        )
        val use = Component.Custom("promo", mapOf("title" to "Hi"))
        host.screens.saveDraft("main", ScreenJson.encode(ScreenDefinition(id = "main", components = listOf(use))))

        val form = client.page(cookie, "/admin/screens/main/visual?node=components.0").bodyAsText()
        assertContains(form, "name=\"args.title\" value=\"Hi\"")
        assertContains(form, "big text · required")
        assertContains(form, "name=\"args.accent\"")
        assertContains(form, "#RRGGBB or #AARRGGBB · empty = #FFCD00")
        assertContains(form, "<option value=\"badge\"")
        assertFalse("name=\"args\"" in form, "no raw JSON field for a known component")

        // Empty optional args are left out (their defaults apply).
        val saved = client.post(
            cookie,
            "/admin/screens/main/visual?node=components.0",
            "component" to "promo",
            "args.title" to "Witaj",
            "args.accent" to "",
            "args.action" to "navigate:detail",
        )
        assertEquals(HttpStatusCode.SeeOther, saved.status, saved.bodyAsText())
        val stored = ScreenJson.decode(host.screens.get("main")!!.draftJson!!, "t").components.single()
        assertEquals(
            Component.Custom("promo", mapOf("title" to "Witaj", "action" to "navigate:detail")),
            stored,
        )

        // Picking another component starts from its own params, not the old ones.
        client.post(cookie, "/admin/screens/main/visual?node=components.0", "component" to "badge", "args.title" to "x")
        val switched = ScreenJson.decode(host.screens.get("main")!!.draftJson!!, "t").components.single()
        assertEquals(Component.Custom("badge", mapOf("count" to "1")), switched)
    }

    @Test
    fun `component history shows pending draft with revert, rollback and revision removal`() =
        host.run(withComponents = true) { client ->
            val cookie = with(host) { client.loginAs(Role.EDITOR) }
            val v1 = ComponentJson.encode(
                ComponentDefinition(
                    id = "promo",
                    title = "V1",
                    rcVersion = "1.0.0-alpha23",
                    body = Json.parseToJsonElement("""{ "type": "text", "text": "v1" }""").jsonObject,
                ),
            )
            val v2 = ComponentJson.encode(
                ComponentDefinition(
                    id = "promo",
                    title = "V2",
                    rcVersion = "1.0.0-alpha23",
                    body = Json.parseToJsonElement("""{ "type": "text", "text": "v2" }""").jsonObject,
                ),
            )
            host.components.publish("promo", v1, by = "a@x")
            host.components.publish("promo", v2, by = "a@x")
            host.components.saveDraft("promo", v2.replace("v2", "draft-edit"))

            // Both the JSON editor and the designer show the pending Draft row with Revert and past revisions
            val editorHtml = client.page(cookie, "/admin/components/promo").bodyAsText()
            assertContains(editorHtml, "/admin/components/promo/draft/revert")
            val designerHtml = client.page(cookie, "/admin/components/promo/design").bodyAsText()
            assertContains(designerHtml, "/admin/components/promo/draft/revert")
            assertContains(designerHtml, "Roll back to this")

            // Revert draft from the designer returns to /design and clears the draft
            val reverted = client.post(cookie, "/admin/components/promo/draft/revert", "from" to "design")
            assertEquals(HttpStatusCode.SeeOther, reverted.status)
            assertEquals("/admin/components/promo/design?draftReverted", reverted.headers[HttpHeaders.Location])
            assertEquals(null, host.components.get("promo")!!.draftJson)

            // View and roll back to v1 from the designer
            val history = host.components.history("promo")
            val oldest = history.last()
            val latest = history.first()
            val revPage = client.page(cookie, "/admin/components/promo/revisions/${oldest.id}")
            assertEquals(HttpStatusCode.OK, revPage.status)
            assertContains(revPage.bodyAsText(), "v1")

            val rolledBack = client.post(
                cookie,
                "/admin/components/promo/rollback",
                "revision" to oldest.id.toString(),
                "from" to "design",
            )
            assertEquals(HttpStatusCode.SeeOther, rolledBack.status)
            assertEquals("/admin/components/promo/design?rolledback", rolledBack.headers[HttpHeaders.Location])
            assertEquals(v1, host.components.live()["promo"])

            // Remove an older revision, refuse removing the currently live revision
            val currentLive = host.components.history("promo").first()
            assertEquals(
                HttpStatusCode.UnprocessableEntity,
                client.post(cookie, "/admin/components/promo/revisions/${currentLive.id}/delete").status,
            )
            val removed = client.post(
                cookie,
                "/admin/components/promo/revisions/${latest.id}/delete",
                "from" to "design",
            )
            assertEquals(HttpStatusCode.SeeOther, removed.status)
            assertEquals("/admin/components/promo/design?revisionRemoved", removed.headers[HttpHeaders.Location])
            assertEquals(null, host.components.revision("promo", latest.id))
        }
}
