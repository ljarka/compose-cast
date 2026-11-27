package io.composecast.compat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ScreenDocument
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentDesign
import io.composecast.protocol.ComponentJson
import io.composecast.protocol.DesignAnchor
import io.composecast.protocol.DesignElement
import io.composecast.protocol.DesignElementType
import io.composecast.server.sdk.CustomComponents
import io.composecast.server.sdk.composeCast
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies that custom components switch colors locally on `player.setTheme(LIGHT / DARK)` for
 * both explicit `darkFill` overrides and `{{brand.*}}` tokens (via `$colors.*` + `addThemedColor`).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CustomComponentThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `designed custom component switches fill and brand colors between light and dark mode`() {
        val definition = ComponentJson.decode(
            ComponentJson.encode(
                ComponentDefinition(
                    id = "themed-box",
                    rcVersion = CustomComponents.RC_VERSION,
                    design = ComponentDesign(
                        width = 360f,
                        height = 200f,
                        elements = listOf(
                            DesignElement(
                                id = "explicit",
                                type = DesignElementType.Rect,
                                x = 0f,
                                y = 0f,
                                w = 360f,
                                h = 100f,
                                anchor = DesignAnchor.Stretch,
                                fill = "#FFFF0000",
                                darkFill = "#FF0000FF",
                            ),
                            DesignElement(
                                id = "brandBox",
                                type = DesignElementType.Rect,
                                x = 0f,
                                y = 100f,
                                w = 360f,
                                h = 100f,
                                anchor = DesignAnchor.Stretch,
                                fill = "{{brand.surfaceColor}}",
                            ),
                        ),
                    ),
                ),
            ),
            "test",
        )
        val registry = composeCast {
            brand(
                BrandConfig(
                    name = "Test",
                    primaryColor = "#3B5BDB",
                    secondaryColor = "#03DAC6",
                    surfaceColor = "#FF00FF00",
                    darkSurfaceColor = "#FF223344",
                ),
            )
            component(definition)
            screen("s") { custom("themed-box") }
        }
        val rendered = registry.get("s")!!
        val document = ScreenDocument.fromBytes(rendered.id, rendered.title, rendered.document)

        var dark by mutableStateOf(false)
        compose.setContent { ComposeCastScreen(document, Modifier.fillMaxSize(), darkTheme = dark) }
        compose.waitForIdle()
        val lightBmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val density = compose.density.density
        val cx = lightBmp.width / 2
        // Top box (~16dp screen padding + 50dp = 66dp); bottom box (~16dp + 150dp = 166dp).
        val topY = (66f * density).toInt()
        val bottomY = (166f * density).toInt()

        assertEquals(0xFFFF0000.toInt(), lightBmp.getPixel(cx, topY))
        assertEquals(0xFF00FF00.toInt(), lightBmp.getPixel(cx, bottomY))

        dark = true
        compose.waitForIdle()
        val darkBmp = compose.onRoot().captureToImage().asAndroidBitmap()

        assertEquals(0xFF0000FF.toInt(), darkBmp.getPixel(cx, topY))
        assertEquals(0xFF223344.toInt(), darkBmp.getPixel(cx, bottomY))
    }
}
