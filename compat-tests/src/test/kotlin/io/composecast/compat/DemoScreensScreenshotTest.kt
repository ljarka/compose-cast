package io.composecast.compat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ScreenDocument
import io.composecast.server.demoScreens
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.DataRequest
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.ScreenRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * End to end, without network or emulator: the server's demo screens are rendered to Remote
 * Compose documents by sdk-server and played by sdk-android's player (Robolectric native
 * graphics). The screenshots are golden files and are shown in the README.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DemoScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val registry = demoScreens(imageLoader = GeneratedImage, dataLoader = FixtureForecast)

    @Test
    fun main() = capture("main")

    @Test
    fun onboarding() = capture("onboarding")

    @Test
    fun actions() = capture("actions")

    /** Containers and modifiers (T-012): weighted cards, spaceBetween, circle, bordered box. */
    @Test
    fun layouts() = capture("layouts")

    /** A dynamic screen, rendered from query parameters (T-003). */
    @Test
    fun echo() = capture("echo", mapOf("word" to "zażółć gęślą jaźń"))

    /** Data sources and bindings (T-006) with fixture data: current conditions + a zipped repeat. */
    @Test
    fun data() = capture("data")

    /** The same screen when its data source fails: the screen's `error` components. */
    @Test
    fun dataError() = capture(
        "data",
        file = "data-error",
        registry = demoScreens(imageLoader = GeneratedImage, dataLoader = { error("HTTP 503") }),
    )

    @OptIn(ExperimentalMaterial3Api::class)
    private fun capture(
        id: String,
        params: Map<String, String> = emptyMap(),
        file: String = id,
        registry: ScreenRegistry = this.registry,
    ) {
        val screen = requireNotNull(registry.render(id, RenderContext(params))) { "no demo screen '$id'" }
        val document = ScreenDocument.fromBytes(screen.id, screen.title, screen.document)
        compose.setContent {
            // Same frame as app-template: top app bar with the screen title + the player.
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text(document.title) }) }) { padding ->
                    ComposeCastScreen(document, Modifier.fillMaxSize().padding(padding))
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png", roborazziOptions = OPTIONS)
    }

    private companion object {
        /**
         * Goldens are recorded on macOS, CI verifies on Linux. Tolerate up to 0.02% of pixels
         * differing (edge anti-aliasing); changing two words on a button is ~0.14%.
         */
        val OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0002f),
        )
    }

    /** An Open-Meteo-shaped response, so the screenshot never depends on the network or the weather. */
    private object FixtureForecast : DataLoader {
        override fun load(request: DataRequest): String = """
            {"current":{"temperature_2m":21.4,"weather_code":2},
             "current_units":{"temperature_2m":"°C"},
             "daily":{"time":["2026-11-08","2026-11-09","2026-11-10"],
                      "temperature_2m_max":[22.1,18.5,16.0],"temperature_2m_min":[11.2,9.6,7.0]}}
        """.trimIndent()
    }

    /** Deterministic stand-in for remote images: a gradient in the demo brand colors. */
    private object GeneratedImage : ImageLoader {
        override fun load(url: String): ByteArray {
            val image = BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB)
            with(image.createGraphics()) {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                paint = GradientPaint(0f, 0f, Color(0x62, 0x00, 0xEE), 800f, 400f, Color(0x03, 0xDA, 0xC6))
                fillRect(0, 0, 800, 400)
                color = Color(255, 255, 255, 70)
                fillOval(520, 60, 220, 220)
                fillOval(80, 230, 140, 140)
                dispose()
            }
            return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }
    }
}
