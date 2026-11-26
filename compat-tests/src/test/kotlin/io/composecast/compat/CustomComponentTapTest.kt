package io.composecast.compat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ScreenDocument
import io.composecast.protocol.Action
import io.composecast.server.demoScreens
import io.composecast.server.sdk.RenderContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** T-049: a tap on a custom component (raw RC JSON `hostAction`) reaches the app as our [Action]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CustomComponentTapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping parcel tiles navigates to their screens`() {
        val screen = demoScreens(imageLoader = GeneratedImage).render("custom", RenderContext())!!
        val document = ScreenDocument.fromBytes(screen.id, screen.title, screen.document)
        val actions = mutableListOf<Action>()
        compose.setContent { ComposeCastScreen(document, Modifier.fillMaxSize(), onAction = { actions += it }) }
        compose.waitForIdle()
        // The RC player lays components out while drawing: draw a frame before tapping.
        compose.onRoot().captureToImage()
        val density = compose.density.density
        // custom.png without the app bar: the grid starts ~145 dp down, tiles are 150 dp high, 12 dp apart.
        compose.onRoot().performTouchInput { click(Offset(width * 0.25f, 220f * density)) }
        compose.onRoot().performTouchInput { click(Offset(width * 0.75f, 380f * density)) }
        compose.waitForIdle()
        assertEquals(listOf<Action>(Action.Navigate("data"), Action.Navigate("dashboard")), actions)
    }

    @Test
    fun `tapping the designed promo card's button runs its action, the rest of the card does not`() {
        val screen = demoScreens(imageLoader = GeneratedImage).render("custom", RenderContext())!!
        val document = ScreenDocument.fromBytes(screen.id, screen.title, screen.document)
        val actions = mutableListOf<Action>()
        compose.setContent { ComposeCastScreen(document, Modifier.fillMaxSize(), onAction = { actions += it }) }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        val density = compose.density.density
        // T-057: the card starts ~475 dp down (no app bar). Its decoration (right) and title have no
        // action; the "Send now" pill (36..156 dp across, 583..619 dp down) has the design's tap area.
        compose.onRoot().performTouchInput { click(Offset(width * 0.85f, 520f * density)) }
        compose.onRoot().performTouchInput { click(Offset(60f * density, 500f * density)) }
        compose.onRoot().performTouchInput { click(Offset(170f * density, 600f * density)) } // right of the pill
        compose.onRoot().performTouchInput { click(Offset(95f * density, 600f * density)) }
        compose.waitForIdle()
        assertEquals(listOf<Action>(Action.Navigate("data")), actions)
    }
}
