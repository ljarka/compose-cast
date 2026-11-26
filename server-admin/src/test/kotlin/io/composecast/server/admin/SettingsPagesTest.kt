package io.composecast.server.admin

import io.composecast.protocol.BrandConfig
import io.composecast.server.store.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** T-007 A5: app settings, brand colors, logo. */
class SettingsPagesTest {
    private val host = AdminTestHost()
    private val settings = host.settings

    private val valid = mapOf(
        "name" to "Acme",
        "applicationId" to "com.acme.app",
        "serverUrl" to "https://app.acme.com/",
        "primaryColor" to "#112233",
        "secondaryColor" to "#445566",
        "backgroundColor" to "#FFFFFF",
        "surfaceColor" to "",
        "textColor" to "#FF101010",
        "outlineColor" to "",
    )

    private fun png(width: Int, height: Int) = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    private suspend fun HttpClient.save(cookie: String, fields: Map<String, String>, logo: ByteArray? = null) =
        submitFormWithBinaryData(
            "/admin/settings",
            formData {
                fields.forEach { (k, v) -> append(k, v) }
                if (logo != null) {
                    append(
                        "logo",
                        logo,
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/png")
                            append(HttpHeaders.ContentDisposition, "filename=\"logo.png\"")
                        },
                    )
                }
            },
        ) { header(HttpHeaders.Cookie, cookie) }

    private fun brand() = Json.decodeFromString(BrandConfig.serializer(), settings[SettingsStore.BRAND]!!)

    @Test
    fun `an admin saves the brand and the app settings`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.ADMIN) }
        val logo = png(64, 64)
        val saved = client.save(cookie, valid, logo)
        assertEquals(HttpStatusCode.SeeOther, saved.status)
        assertEquals("/admin/settings?saved", saved.headers[HttpHeaders.Location])

        assertEquals(BrandConfig("Acme", "#112233", "#445566", "#FFFFFF", textColor = "#FF101010"), brand())
        assertEquals("Acme", settings[AppKeys.NAME])
        assertEquals("com.acme.app", settings[AppKeys.APPLICATION_ID])
        assertEquals("https://app.acme.com", settings[AppKeys.SERVER_URL], "trailing slash dropped")
        assertContentEquals(logo, Base64.getDecoder().decode(settings[AppKeys.LOGO]))

        val page = client.get("/admin/settings?saved") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertContains(page, "Saved.")
        assertContains(page, "value=\"#FF101010\"")
        val served = client.get("/admin/settings/logo") { header(HttpHeaders.Cookie, cookie) }
        assertEquals("image/png", served.headers[HttpHeaders.ContentType])
        assertContentEquals(logo, served.readRawBytes())

        client.save(cookie, valid + ("removeLogo" to "on"))
        assertEquals("", settings[AppKeys.LOGO])
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/admin/settings/logo") {
                header(HttpHeaders.Cookie, cookie)
            }.status,
        )
    }

    @Test
    fun `invalid values are rejected with the field named and nothing is saved`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.ADMIN) }
        val response = client.save(
            cookie,
            valid +
                mapOf(
                    "primaryColor" to "red",
                    "outlineColor" to "#12",
                    "applicationId" to "acme",
                    "serverUrl" to "ftp://x",
                ),
            logo = png(64, 32),
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        listOf(
            "primaryColor: expected #RRGGBB",
            "outlineColor:",
            "applicationId:",
            "serverUrl:",
            "logo: must be square",
        ).forEach {
            assertContains(body, it)
        }
        assertContains(body, "value=\"red\"", message = "the entered values stay in the form")
        assertNull(settings[SettingsStore.BRAND])
        assertNull(settings[AppKeys.APPLICATION_ID])

        val notImage = client.save(cookie, valid, logo = "not an image".toByteArray())
        assertContains(notImage.bodyAsText(), "logo: must be a PNG or JPEG image")
    }

    @Test
    fun `a failed rebuild changes nothing`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.ADMIN) }
        host.failWith = "screen 'main': something"
        val response = client.save(cookie, valid)
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), "Not saved, nothing changed")
        assertNull(settings[AppKeys.NAME])
    }

    @Test
    fun `only admins change settings, everyone logged in can read them`() = host.run { client ->
        val editor = with(host) { client.loginAs(Role.EDITOR) }
        assertEquals(HttpStatusCode.Forbidden, client.save(editor, valid).status)
        val page = client.get("/admin/settings") { header(HttpHeaders.Cookie, editor) }.bodyAsText()
        assertContains(page, "Only admins can change settings.")
        assertContains(page, "#6200EE", message = "defaults without an imported brand")
        assertFalse("Save settings" in page)
        assertNull(settings[SettingsStore.BRAND])
    }

    @Test
    fun `debug mode toggle shows and hides AI reference ID buttons across admin pages`() = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.ADMIN) }

        val offSettings = client.get("/admin/settings") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertFalse("data-ui-id=" in offSettings, "debug buttons hidden by default")
        val offScreens = client.get("/admin/screens") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertFalse("data-ui-id=" in offScreens)

        val enabled = client.save(cookie, valid + ("debugMode" to "on"))
        assertEquals(HttpStatusCode.SeeOther, enabled.status)
        assertEquals("true", settings[AppKeys.DEBUG_MODE])

        val onSettings = client.get("/admin/settings") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertContains(onSettings, """data-ui-id="admin:settings.header"""")
        assertContains(onSettings, """data-ui-id="admin:settings.developer"""")
        val onScreens = client.get("/admin/screens") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertContains(onScreens, """data-ui-id="admin:screens.header"""")
        assertContains(onScreens, """data-ui-id="admin:screens.new"""")

        val disabled = client.save(cookie, valid)
        assertEquals(HttpStatusCode.SeeOther, disabled.status)
        assertEquals("", settings[AppKeys.DEBUG_MODE])
        val offAgain = client.get("/admin/screens") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertFalse("data-ui-id=" in offAgain)
    }
}
