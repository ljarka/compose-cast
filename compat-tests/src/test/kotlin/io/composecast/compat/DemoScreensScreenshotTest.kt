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
import io.composecast.server.sdk.composeCast
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

    /** Text/image options, Icon, Divider, Card and button variants (T-013). */
    @Test
    fun components() = capture("components")

    /** Scrolling gallery, Flow chips, 2- and 3-column grids (T-014). */
    @Test
    fun collections() = capture("collections")

    /** Adaptive layouts on a phone width (T-015): compact FitBox + collapsed row. */
    @Test
    fun adaptivePhone() = capture("adaptive", file = "adaptive-phone")

    /** Adaptive layouts on a tablet width (T-015): wide FitBox + all 3 row items. */
    @Test
    @Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun adaptiveTablet() = capture("adaptive", file = "adaptive-tablet")

    /** Dashboard components on an RC canvas (T-047): gauges, key figures, sparkline. */
    @Test
    fun dashboard() = capture("dashboard")

    /** The same dashboard on a tablet width: canvases scale with the component size. */
    @Test
    @Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun dashboardTablet() = capture("dashboard", file = "dashboard-tablet")

    /** Interactive on-device sliders driving gauges, KPIs and text with no server round-trip. */
    @Test
    fun interactive() = capture("interactive")

    /** Custom components in raw RC JSON (T-049, ADR 0033): four `parcel-tile`s with vector icons. */
    @Test
    fun custom() = capture("custom")

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

    /** Brand surface, text and outline colors (T-040) replace the default purple-tinted look. */
    @Test
    fun brandColors() = capture(
        "brand",
        file = "brand-colors",
        registry = composeCast(GeneratedImage) {
            brand(
                name = "Weather",
                primaryColor = "#1565C0",
                secondaryColor = "#90CAF9",
                backgroundColor = "#F4F8FC",
                surfaceColor = "#FFFFFF",
                textColor = "#0D2A4A",
                outlineColor = "#BBDEFB",
            )
            screen("brand") {
                title = "Brand colors"
                card(spacing = 8) {
                    text("Warsaw", style = "h2")
                    text("Partly cloudy, 21°")
                }
                divider()
                expandable("Details") { text("Wind 12 km/h") }
                button("Refresh", io.composecast.protocol.Action.Reload)
            }
        },
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
}
