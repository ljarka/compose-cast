package io.composecast.server.admin

import io.composecast.protocol.ComponentJson
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

        val created = client.post(cookie, "/admin/components", "id" to "promo")
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
        assertEquals(HttpStatusCode.Forbidden, client.post(cookie, "/admin/components", "id" to "x").status)
    }
}
