package io.composecast.server.admin

import io.composecast.protocol.ScreenJson
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** T-031 W2: the wizard page — probe, pre-filled choices, screens created as drafts, no raw keys. */
class WizardPagesTest {
    private val host = AdminTestHost()
    private val shows = "https://api.tvmaze.com/shows"

    init {
        host.apiResponses[shows] = javaClass.getResource("/fixtures/apis/tvmaze-shows.json")!!.readText()
    }

    private suspend fun HttpClient.wizard(cookie: String, vararg fields: Pair<String, String>) = submitForm(
        "/admin/wizard",
        parameters {
            fields.forEach { (k, v) -> append(k, v) }
        },
    ) { header(HttpHeaders.Cookie, cookie) }

    @Test
    fun `probing shows the list, its fields and the guesses`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        assertEquals(HttpStatusCode.OK, client.get("/admin/wizard") { header(HttpHeaders.Cookie, cookie) }.status)
        val page = client.wizard(cookie, "url" to shows, "step" to "probe").bodyAsText()
        assertContains(page, "(the response itself) — 4 items")
        assertContains(page, "<code>image.medium</code>")
        assertContains(page, "<option value=\"name\" selected>name</option>")
        assertContains(page, "<option value=\"image.medium\" selected>image.medium</option>")
        assertContains(page, "name=\"listScreen\" value=\"shows\"")
        assertContains(page, "value=\"https://api.tvmaze.com/shows/{{params.id}}\"")
    }

    @Test
    fun `creating makes a list and a detail screen as drafts and opens the visual editor`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val created = client.wizard(cookie, "url" to shows, "step" to "create")
        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertEquals("/admin/screens/shows/visual?node=&saved", created.headers[HttpHeaders.Location])
        val list = ScreenJson.decode(host.screens.get("shows")!!.draftJson!!, "shows")
        val detail = ScreenJson.decode(host.screens.get("shows-detail")!!.draftJson!!, "detail")
        assertEquals(shows, list.data.getValue("shows").url)
        assertEquals("https://api.tvmaze.com/shows/{{params.id}}", detail.data.getValue("detail").url)
        assertEquals("1", detail.params.getValue("id").default)
        assertNull(host.screens.get("shows")!!.liveJson, "apps do not see it before publishing")

        val again = client.wizard(cookie, "url" to shows, "step" to "create")
        assertEquals(HttpStatusCode.UnprocessableEntity, again.status)
        assertContains(again.bodyAsText(), "A screen &#39;shows&#39; already exists")
    }

    @Test
    fun `raw keys are refused, secrets are fine`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        host.apiResponses["$shows?apikey=abc123"] = host.apiResponses.getValue(shows)
        val inUrl = client.wizard(cookie, "url" to "$shows?apikey=abc123", "step" to "create")
        assertContains(inUrl.bodyAsText(), "Keys must not be stored in screens")
        val inHeader = client.wizard(
            cookie,
            "url" to shows,
            "headerName" to "X-Api-Key",
            "headerValue" to "abc123",
            "step" to "create",
        )
        assertContains(inHeader.bodyAsText(), "Keys must not be stored in screens")
        assertNull(host.screens.get("shows"))
    }

    @Test
    fun `an answer without a list or an unreachable API is explained`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        host.apiResponses["https://api.example.com/one"] = """{"name": "only one"}"""
        assertContains(client.wizard(cookie, "url" to "https://api.example.com/one").bodyAsText(), "no list of objects")
        assertContains(
            client.wizard(cookie, "url" to "https://api.example.com/missing").bodyAsText(),
            "the API answered HTTP 404",
        )
    }
}
