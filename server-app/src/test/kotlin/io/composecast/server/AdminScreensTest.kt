package io.composecast.server

import io.composecast.server.admin.AdminAuth
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.ScreenRegistry
import io.composecast.server.store.UserStore
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
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
        val auth = AdminAuth(UserStore(AdminScreens.openDatabase(dataDir)), bcryptCost = 4)
        val screens = LiveScreens(loadScreens(dataDir, fakeImages, fixtureData))
        for ((admin, expected) in listOf(
            AdminPanelConfig(auth) to HttpStatusCode.SeeOther,
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
}
