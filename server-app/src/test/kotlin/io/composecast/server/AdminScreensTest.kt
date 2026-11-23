package io.composecast.server

import io.composecast.protocol.Headers
import io.composecast.protocol.ScreenJson
import io.composecast.server.admin.AdminAuth
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.ScreenRegistry
import io.composecast.server.store.ScreenStore
import io.composecast.server.store.UserStore
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.copyToRecursively
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** T-007 A1: admin mode serves exactly what files mode served, from the database. */
class AdminScreensTest {
    private val fakeImages = ImageLoader { _ ->
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }
    private val fixtureData = DataLoader { """{"current":{"temperature_2m":21.4,"weather_code":2}}""" }
    private val demo = Path.of(System.getProperty("composecast.examples"), "demo")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private val dataDir: Path = Files.createTempDirectory("composecast-admin").resolve("data").also {
        demo.copyToRecursively(it, followLinks = false)
    }

    private fun admin() = AdminScreens.open(dataDir, fakeImages, fixtureData)

    private fun documents(registry: ScreenRegistry) =
        registry.ids().associateWith { registry.render(it, RenderContext(emptyMap()))!!.document.toList() }

    @Test
    fun `first start imports the data directory and serves byte-identical documents`() {
        val files = loadScreens(dataDir, fakeImages, fixtureData)
        val admin = admin()
        assertEquals(files.ids().size, admin.importIfEmpty(dataDir))
        val fromDatabase = admin.load()

        assertEquals(files.brand, fromDatabase.brand)
        assertEquals(files.ids(), fromDatabase.ids())
        val expected = documents(files)
        documents(fromDatabase).forEach { (id, bytes) ->
            assertContentEquals(expected.getValue(id), bytes, "screen '$id' differs from files mode")
        }
    }

    @Test
    fun `later starts keep the database and ignore the files`() {
        admin().importIfEmpty(dataDir)
        val ids = admin().load().ids()

        dataDir.resolve("screens/main.json").writeText("""{"id": "main", "title": "Changed on disk"}""")
        val restarted = admin()
        assertEquals(0, restarted.importIfEmpty(dataDir), "an existing database is never re-imported")
        assertEquals(ids, restarted.load().ids())
        assertEquals("Home", restarted.load().definitions().getValue("main").title)
    }

    @Test
    fun `the first admin comes from the environment, which is required until an account exists`() {
        val auth = AdminAuth(UserStore(AdminScreens.openDatabase(dataDir)), bcryptCost = 4)
        val missing = assertFailsWith<IllegalArgumentException> { auth.bootstrapFromEnv(null, null) }
        assertContains(missing.message!!, "COMPOSECAST_ADMIN_EMAIL")

        auth.bootstrapFromEnv("owner@example.com", "correct horse battery")
        auth.bootstrapFromEnv(null, null) // accounts exist: nothing needed
        assertNotNull(auth.login("owner@example.com", "correct horse battery"))
    }

    @Test
    fun `admin mode mounts the panel at admin, files mode does not`() {
        val database = AdminScreens.openDatabase(dataDir)
        val auth = AdminAuth(UserStore(database), bcryptCost = 4)
        val screens = LiveScreens(loadScreens(dataDir, fakeImages, fixtureData))
        for ((admin, expected) in listOf(
            AdminPanelConfig(auth, ScreenStore(database), AdminPublishing(admin(), screens)) to HttpStatusCode.SeeOther,
            null to HttpStatusCode.NotFound,
        )) {
            testApplication {
                application { module(screens, admin) }
                val client = createClient { followRedirects = false }
                assertEquals(expected, client.get("/admin").status, "admin = $admin")
                assertEquals(HttpStatusCode.OK, client.get("/screens/main").status)
            }
        }
    }

    /** Admin mode on the test host after the first-start import, logged in as the env admin. */
    private fun adminMode(test: suspend ApplicationTestBuilder.(HttpClient, String, ScreenStore) -> Unit) {
        val admin = admin()
        admin.importIfEmpty(dataDir)
        val database = AdminScreens.openDatabase(dataDir)
        val auth = AdminAuth(UserStore(database), bcryptCost = 4)
        auth.bootstrapAdmin("owner@example.com", "correct horse battery")
        val live = LiveScreens(admin.load())
        val store = ScreenStore(database)
        testApplication {
            application { module(live, AdminPanelConfig(auth, store, AdminPublishing(admin, live))) }
            val client = createClient { followRedirects = false }
            val login = client.submitForm(
                "/admin/login",
                parameters {
                    append("email", "owner@example.com")
                    append("password", "correct horse battery")
                },
            )
            test(client, login.headers[HttpHeaders.SetCookie]!!.substringBefore(';'), store)
        }
    }

    private suspend fun HttpClient.post(cookie: String, path: String, name: String, value: String) =
        submitForm(path, parameters { append(name, value) }) { header(HttpHeaders.Cookie, cookie) }

    @Test
    fun `a draft saved in the admin panel does not change what apps get`() = adminMode { client, cookie, store ->
        val before = client.get("/screens/main").readRawBytes()
        val saved = client.post(cookie, "/admin/screens/main", "json", """{"id": "main", "title": "Draft"}""")
        assertEquals(HttpStatusCode.SeeOther, saved.status)
        assertEquals("Draft", ScreenJson.decode(store.get("main")!!.draftJson!!, "draft").title)
        assertContentEquals(before, client.get("/screens/main").readRawBytes())
    }

    @Test
    fun `publish serves the new document without a restart, rollback restores the old bytes`() =
        adminMode { client, cookie, store ->
            val before = client.get("/screens/main").readRawBytes()
            val published = client.post(
                cookie,
                "/admin/screens/main/publish",
                "json",
                """{"id": "main", "title": "Published"}""",
            )
            assertEquals(HttpStatusCode.SeeOther, published.status)
            val after = client.get("/screens/main")
            assertEquals("Published", after.headers[Headers.SCREEN_TITLE])
            assertFalse(before.contentEquals(after.readRawBytes()))

            val imported = store.history("main").last()
            client.post(cookie, "/admin/screens/main/rollback", "revision", imported.id.toString())
            assertContentEquals(before, client.get("/screens/main").readRawBytes(), "the imported bytes again")
            assertEquals(3, store.history("main").size, "history is append-only")
            assertEquals(admin().load().ids(), (store.live().keys.sorted()), "a restart serves the same screens")
        }

    @Test
    fun `a screen that does not build is not published and nothing changes`() = adminMode { client, cookie, store ->
        val before = client.get("/screens/main").readRawBytes()
        val liveBefore = store.get("main")!!.liveJson
        val secret = """{"id": "main", "data": {"x": {"url": "https://example.com/?k={{secret.SURELY_NOT_SET_42}}"}},
            "components": [{"type": "text", "text": "{{x.a}}"}]}"""
        val failed = client.post(cookie, "/admin/screens/main/publish", "json", secret)
        assertEquals(HttpStatusCode.UnprocessableEntity, failed.status)
        assertContains(failed.bodyAsText(), "Not published, nothing changed")
        assertContains(failed.bodyAsText(), "SURELY_NOT_SET_42")
        assertEquals(1, store.history("main").size)
        assertContentEquals(before, client.get("/screens/main").readRawBytes())
        assertEquals(liveBefore, store.get("main")!!.liveJson, "database unchanged")
    }

    @Test
    fun `a new screen becomes available to apps once published`() = adminMode { client, cookie, _ ->
        client.post(cookie, "/admin/screens", "id", "settings")
        assertEquals(HttpStatusCode.NotFound, client.get("/screens/settings").status)
        client.post(cookie, "/admin/screens/settings/publish", "json", """{"id": "settings", "title": "Settings"}""")
        assertEquals(HttpStatusCode.OK, client.get("/screens/settings").status)
        assertContains(client.get("/screens").bodyAsText(), "settings")
    }
}
