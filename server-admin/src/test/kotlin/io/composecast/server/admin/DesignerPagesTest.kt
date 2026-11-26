package io.composecast.server.admin

import io.composecast.protocol.ComponentJson
import io.composecast.protocol.DesignElementType
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T-057: the component designer — start a drawing, save designs as JSON, problems come back. */
class DesignerPagesTest {
    private val host = AdminTestHost()

    private suspend fun HttpClient.page(cookie: String, path: String) = get(path) { header(HttpHeaders.Cookie, cookie) }

    private suspend fun HttpClient.saveDesign(cookie: String, id: String, json: String) =
        post("/admin/components/$id/design") {
            header(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    @Test
    fun `a component started as a drawing opens in the designer and saves designs`() =
        host.run(withComponents = true) { client ->
            val cookie = with(host) { client.loginAs(Role.EDITOR) }
            val created = client.submitForm(
                "/admin/components",
                parameters {
                    append("id", "card")
                    append("start", "design")
                },
            ) { header(HttpHeaders.Cookie, cookie) }
            assertEquals(HttpStatusCode.SeeOther, created.status)
            assertEquals("/admin/components/card/design", created.headers[HttpHeaders.Location])

            // The draft is the starter design, stored without a compiled body.
            val draft = assertNotNull(host.components.get("card")?.draftJson)
            assertFalse("\"body\"" in draft)
            val starter = ComponentJson.decode(draft, "t")
            assertEquals(DesignElementType.Text, starter.design!!.elements.last().type)
            assertEquals("box", starter.body["type"]!!.jsonPrimitive.content) // compiled on decode

            val page = client.page(cookie, "/admin/components/card/design").bodyAsText()
            assertContains(page, "<script type=\"application/json\" id=\"definition\">")
            assertContains(page, "\"{{params.title}}\"") // raw JSON in the script, not HTML-escaped
            assertContains(page, "src=\"/admin/components/designer.js\"")
            val script = client.page(cookie, "/admin/components/designer.js")
            assertEquals(ContentType.Application.JavaScript, script.contentType()?.withoutParameters())
            assertContains(script.bodyAsText(), "normalizePath")

            val design = """{"id":"card","rcVersion":"1.0.0-alpha23","design":{"height":80,"elements":[
            {"id":"bg","type":"rect","w":360,"h":80,"anchor":"stretch","fill":"#FF112233"},
            {"id":"go","type":"tap","x":10,"y":10,"w":60,"h":40,"action":"navigate:main"}]}}"""
            val saved = client.saveDesign(cookie, "card", design)
            assertEquals(HttpStatusCode.OK, saved.status)
            val answer = Json.parseToJsonElement(saved.bodyAsText()).jsonObject
            assertEquals("true", answer["saved"]!!.jsonPrimitive.content)
            assertEquals(80f, ComponentJson.decode(host.components.get("card")!!.draftJson!!, "t").design!!.height)

            // Problems come back with their path; nothing is saved.
            val bad = design.replace("#FF112233", "blue").replace("navigate:main", "dance")
            val refused = client.saveDesign(cookie, "card", bad)
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
            val problems = Json.parseToJsonElement(refused.bodyAsText()).jsonObject["problems"]!!.jsonArray
                .map { it.jsonPrimitive.content }
            assertTrue(problems.any { it.startsWith("design.elements[0].fill:") }, "$problems")
            assertTrue(problems.any { it.startsWith("design.elements[1].action:") }, "$problems")
            assertEquals(80f, ComponentJson.decode(host.components.get("card")!!.draftJson!!, "t").design!!.height)

            // Another component's id cannot be written through this one's URL.
            assertEquals(
                HttpStatusCode.UnprocessableEntity,
                client.saveDesign(cookie, "card", design.replace("\"id\":\"card\"", "\"id\":\"other\"")).status,
            )
        }

    @Test
    fun `a viewer sees the designer read-only and cannot save`() = host.run(withComponents = true) { client ->
        val editor = with(host) { client.loginAs(Role.EDITOR) }
        client.submitForm(
            "/admin/components",
            parameters {
                append("id", "card")
                append("start", "design")
            },
        ) { header(HttpHeaders.Cookie, editor) }
        val viewer = with(host) { client.loginAs(Role.VIEWER) }
        assertContains(client.page(viewer, "/admin/components/card/design").bodyAsText(), "data-can-edit=\"false\"")
        val json = host.components.get("card")!!.draftJson!!
        assertEquals(HttpStatusCode.Forbidden, client.saveDesign(viewer, "card", json).status)
    }

    @Test
    fun `the JSON editor links a designed component to the designer`() = host.run(withComponents = true) { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        client.submitForm(
            "/admin/components",
            parameters {
                append("id", "card")
                append("start", "design")
            },
        ) { header(HttpHeaders.Cookie, cookie) }
        assertContains(client.page(cookie, "/admin/components/card").bodyAsText(), "Open in the designer")
        assertContains(client.page(cookie, "/admin/components").bodyAsText(), "href=\"/admin/components/card/design\"")
    }
}
