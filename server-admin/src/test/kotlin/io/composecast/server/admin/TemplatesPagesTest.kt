package io.composecast.server.admin

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenJson
import io.composecast.server.store.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-030: a whole app from a template, as drafts, then "Publish all" (ADR 0031). */
class TemplatesPagesTest {
    private val host = AdminTestHost()
    private val screens = host.screens

    private suspend fun HttpClient.form(cookie: String, path: String, vararg fields: Pair<String, String>) =
        submitForm(path, parameters { fields.forEach { (k, v) -> append(k, v) } }) {
            header(HttpHeaders.Cookie, cookie)
        }

    @Test
    fun `the page lists every template and an empty app is told to pick one`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        val page = client.get("/admin/templates") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        listOf("News", "Shop", "Events", "Weather", "Blank").forEach { assertContains(page, "Use $it") }
        val start = client.get("/admin") { header(HttpHeaders.Cookie, cookie) }
        assertEquals("/admin/templates", start.headers[HttpHeaders.Location], "an empty app starts at the templates")
        assertContains(page, "no screens yet")
        val screensPage = client.get("/admin/screens") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertContains(screensPage, "/admin/templates")
    }

    @Test
    fun `a template becomes drafts, its colors only when asked, then Publish all makes it the app`() =
        host.run { client ->
            val cookie = with(host) { client.loginAs(Role.EDITOR) }
            settings(BrandConfig("My app", primaryColor = "#000000", secondaryColor = "#111111", fontFamily = "serif"))

            val applied = client.form(cookie, "/admin/templates/news", "brand" to "on")
            assertEquals(HttpStatusCode.OK, applied.status)
            val page = applied.bodyAsText()
            assertContains(page, "2 drafts added")
            assertEquals(setOf("news", "story"), screens.list().map { it.id }.toSet())
            assertTrue(screens.live().isEmpty(), "nothing is live before publishing")
            val brand = Json.decodeFromString(BrandConfig.serializer(), host.settings[SettingsStore.BRAND]!!)
            val expected = BrandConfig(
                name = "My app",
                primaryColor = "#E8590C",
                secondaryColor = "#1C7ED6",
                backgroundColor = "#FAF8F5",
                surfaceColor = "#FFFFFF",
                textColor = "#1C1917",
                outlineColor = "#E7E2DA",
                darkPrimaryColor = "#FF7A33",
                darkSecondaryColor = "#58A6FF",
                darkBackgroundColor = "#141210",
                darkSurfaceColor = "#211E1B",
                darkTextColor = "#F2EFE9",
                darkOutlineColor = "#38322C",
                fontFamily = "serif",
            )
            assertEquals(expected, brand, "colors only; name and font stay")

            val published = client.form(cookie, "/admin/templates/news/publish", "prefix" to "")
            assertEquals(HttpStatusCode.SeeOther, published.status)
            assertEquals(setOf("news", "story"), screens.live().keys)
            assertEquals("news", host.settings[SettingsStore.HOME_SCREEN])
        }

    @Test
    fun `clashing ids are refused until a prefix renames the screens and the navigation between them`() =
        host.run { client ->
            val cookie = with(host) { client.loginAs(Role.EDITOR) }
            client.form(cookie, "/admin/templates/news")
            val again = client.form(cookie, "/admin/templates/news")
            assertEquals(HttpStatusCode.UnprocessableEntity, again.status)
            assertContains(again.bodyAsText(), "Screens news, story already exist")

            val bad = client.form(cookie, "/admin/templates/news", "prefix" to "Bad Prefix")
            assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
            val prefixed = client.form(cookie, "/admin/templates/news", "prefix" to "blog-")
            assertEquals(HttpStatusCode.OK, prefixed.status)
            val list = ScreenJson.decode(screens.get("blog-news")!!.draftJson!!, "draft")
            assertContains(ScreenJson.encode(list), "\"screen\": \"blog-story\"")
            assertNull(host.settings[SettingsStore.BRAND], "colors were not asked for")
        }

    @Test
    fun `viewers cannot apply templates and unknown templates are 404`() = host.run { client ->
        val viewer = with(host) { client.loginAs(Role.VIEWER) }
        assertEquals(HttpStatusCode.Forbidden, client.form(viewer, "/admin/templates/news").status)
        val editor = with(host) { client.loginAs(Role.EDITOR) }
        assertEquals(HttpStatusCode.NotFound, client.form(editor, "/admin/templates/nope").status)
        assertTrue(screens.list().isEmpty())
    }

    private fun settings(brand: BrandConfig) {
        host.settings[SettingsStore.BRAND] = Json.encodeToString(BrandConfig.serializer(), brand)
    }
}
