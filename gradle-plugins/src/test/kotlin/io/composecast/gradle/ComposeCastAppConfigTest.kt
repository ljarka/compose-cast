package io.composecast.gradle

import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeCastAppConfigTest {
    private val dir: File = Files.createTempDirectory("composecast-config").toFile()

    private fun config(extra: String, secrets: Map<String, String> = emptyMap()): ComposeCastAppConfig {
        val file = dir.resolve("compose-cast.properties")
        file.writeText(
            """
            app.name=Weather
            app.applicationId=io.composecast.example.weather
            app.serverUrl=https://weather.example.com
            app.frame.primaryColor=#1565C0
            app.frame.backgroundColor=#F4F8FC
            $extra
            """.trimIndent(),
        )
        return ComposeCastAppConfig.load(file) { secrets[it] }
    }

    @Test
    fun `no signing keys - unsigned release as before`() {
        assertNull(config("").signing)
    }

    @Test
    fun `signing - keystore relative to the file, passwords from env or gradle properties (T-044)`() {
        dir.resolve("release.jks").writeText("x")
        val fromEnv = config(
            "app.signing.storeFile=release.jks\napp.signing.keyAlias=weather",
            mapOf("COMPOSECAST_SIGNING_STORE_PASSWORD" to "s", "COMPOSECAST_SIGNING_KEY_PASSWORD" to "k"),
        ).signing!!
        assertEquals(dir.resolve("release.jks").absoluteFile, fromEnv.storeFile)
        assertEquals(listOf("weather", "s", "k"), listOf(fromEnv.keyAlias, fromEnv.storePassword, fromEnv.keyPassword))

        val fromProperties = config(
            "app.signing.storeFile=release.jks\napp.signing.keyAlias=weather",
            mapOf("composecast.signing.storePassword" to "ps"),
        ).signing!!
        assertEquals("ps", fromProperties.storePassword)
        assertNull(fromProperties.keyPassword, "missing password fails only release builds, in the plugin")
    }

    @Test
    fun `partial or unsafe signing setup names the key`() {
        fun message(extra: String) = assertFailsWith<GradleException> { config(extra) }.message!!
        assertTrue("'app.signing.keyAlias'" in message("app.signing.storeFile=release.jks"))
        assertTrue("'app.signing.storeFile'" in message("app.signing.keyAlias=weather"))
        assertTrue("file not found" in message("app.signing.storeFile=missing.jks\napp.signing.keyAlias=a"))
        assertTrue("must not be in the file" in message("app.signing.storePassword=secret"))
    }
}
