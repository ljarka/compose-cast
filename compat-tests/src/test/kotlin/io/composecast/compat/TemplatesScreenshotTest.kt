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
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.composeCast
import io.composecast.templates.AppTemplates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen of every bundled app template (T-030, ADR 0031), rendered with the template's
 * brand and its fixtures (no network) — what a user sees right after picking a template.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TemplatesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun news() = capture("news")

    @Test
    fun shop() = capture("shop")

    @Test
    fun events() = capture("events")

    @Test
    fun weather() = capture("weather")

    @Test
    fun blank() = capture("blank")

    @OptIn(ExperimentalMaterial3Api::class)
    private fun capture(id: String) {
        val template = requireNotNull(AppTemplates[id]) { "no template '$id'" }
        val data =
            DataLoader { request -> requireNotNull(template.fixture(request.url)) { "no fixture for ${request.url}" } }
        val registry = composeCast(GeneratedImage, data, env = { null }) {
            template.brand?.let { brand(it) }
            template.screens.forEach { screen(it) }
        }
        val home = requireNotNull(registry.render(template.manifest.home, RenderContext()))
        val document = ScreenDocument.fromBytes(home.id, home.title, home.document)
        compose.setContent {
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text(document.title) }) }) { padding ->
                    ComposeCastScreen(document, Modifier.fillMaxSize().padding(padding))
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/template-$id.png", roborazziOptions = OPTIONS)
    }

    private companion object {
        val OPTIONS = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0002f))
    }
}
