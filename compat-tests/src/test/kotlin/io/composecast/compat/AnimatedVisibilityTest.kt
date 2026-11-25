package io.composecast.compat

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
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
import io.composecast.protocol.Dimension
import io.composecast.protocol.Visibility
import io.composecast.protocol.VisibilityTransition
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.composeCast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import io.composecast.protocol.Modifier as CcModifier

/**
 * Verifies on-device animated visibility on the real `ComposeCastScreen` player under Robolectric:
 * tapping a `Toggle` / `Show` / `Hide` button hides and reveals another component locally with zero
 * host action callbacks.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AnimatedVisibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `clicking toggle button hides and shows target card on device with zero host callbacks`() {
        val registry = composeCast {
            screen("vis") {
                button(
                    "Toggle panel",
                    Action.Toggle("panel"),
                    modifier = CcModifier(height = Dimension.Dp(48)),
                )
                card(
                    modifier = CcModifier(
                        height = Dimension.Dp(120),
                        background = "#1971C2",
                        visibility = Visibility(
                            id = "panel",
                            transition = VisibilityTransition.Fade,
                            durationMs = 200,
                        ),
                    ),
                ) {
                    text("Visible panel", color = "#FFFFFF")
                }
            }
        }
        val rendered = registry.render("vis", RenderContext())!!
        val doc = ScreenDocument.fromBytes(rendered.id, rendered.title, rendered.document)
        val hostActions = mutableListOf<Action>()
        compose.setContent {
            ComposeCastScreen(doc, Modifier.fillMaxSize(), onAction = { hostActions += it })
        }
        compose.waitForIdle()

        val density = compose.density.density
        val initial = compose.onRoot().captureToImage().asAndroidBitmap()
        val panelTop = (70f * density).toInt()
        val panelBottom = (180f * density).toInt()
        val initialBlue = countBluePixels(initial, panelTop, panelBottom)
        assertTrue("Expected panel card to be visible initially, bluePixels=$initialBlue", initialBlue > 5000)

        // Tap the Toggle button (screen padding 16dp, button height 48dp -> center y = 40dp).
        val buttonY = (16f + 24f) * density
        compose.onRoot().performTouchInput { click(Offset(width / 2f, buttonY)) }
        // Frame 1 creates AnimateMeasure(startTime = System.currentTimeMillis()); wait > durationMs then draw again.
        compose.onRoot().captureToImage()
        Thread.sleep(250)
        compose.waitForIdle()

        val afterHide = compose.onRoot().captureToImage().asAndroidBitmap()
        val hiddenBlue = countBluePixels(afterHide, panelTop, panelBottom)
        assertEquals("Toggle must stay on-device (no host action)", emptyList<Action>(), hostActions)
        assertTrue(
            "Expected panel card to be hidden after first toggle, bluePixels=$hiddenBlue (was $initialBlue)",
            hiddenBlue < initialBlue / 10,
        )

        // Tap Toggle again -> panel should reappear!
        compose.onRoot().performTouchInput { click(Offset(width / 2f, buttonY)) }
        compose.onRoot().captureToImage()
        Thread.sleep(250)
        compose.waitForIdle()

        val afterShow = compose.onRoot().captureToImage().asAndroidBitmap()
        val shownBlue = countBluePixels(afterShow, panelTop, panelBottom)
        assertTrue(
            "Expected panel card to reappear after second toggle, bluePixels=$shownBlue",
            shownBlue > initialBlue / 2,
        )
        assertEquals(emptyList<Action>(), hostActions)
    }

    @Test
    fun `show and hide actions control a component that starts hidden`() {
        val registry = composeCast {
            screen("vis2") {
                button("Show", Action.Show("secret"), modifier = CcModifier(height = Dimension.Dp(48)))
                button("Hide", Action.Hide("secret"), modifier = CcModifier(height = Dimension.Dp(48)))
                card(
                    modifier = CcModifier(
                        height = Dimension.Dp(120),
                        background = "#1971C2",
                        visibility = Visibility(
                            id = "secret",
                            hidden = true,
                            transition = VisibilityTransition.None,
                        ),
                    ),
                ) {
                    text("Secret card", color = "#FFFFFF")
                }
            }
        }
        val rendered = registry.render("vis2", RenderContext())!!
        val doc = ScreenDocument.fromBytes(rendered.id, rendered.title, rendered.document)
        val hostActions = mutableListOf<Action>()
        compose.setContent {
            ComposeCastScreen(doc, Modifier.fillMaxSize(), onAction = { hostActions += it })
        }
        compose.waitForIdle()

        val density = compose.density.density
        val panelTop = (115f * density).toInt()
        val panelBottom = (230f * density).toInt()

        val initialBlue = countBluePixels(compose.onRoot().captureToImage().asAndroidBitmap(), panelTop, panelBottom)
        assertEquals("Card with hidden=true must start hidden", 0, initialBlue)

        // Click Show (y = 16 + 24 = 40dp)
        compose.onRoot().performTouchInput { click(Offset(width / 2f, (16f + 24f) * density)) }
        compose.waitForIdle()
        val shownBlue = countBluePixels(compose.onRoot().captureToImage().asAndroidBitmap(), panelTop, panelBottom)
        assertTrue("Card should be visible after Show, bluePixels=$shownBlue", shownBlue > 5000)

        // Click Hide (y = 16 + 48 + 24 = 88dp)
        compose.onRoot().performTouchInput { click(Offset(width / 2f, (16f + 48f + 24f) * density)) }
        compose.waitForIdle()
        val hiddenBlue = countBluePixels(compose.onRoot().captureToImage().asAndroidBitmap(), panelTop, panelBottom)
        assertEquals("Card should be hidden after Hide", 0, hiddenBlue)
        assertEquals(emptyList<Action>(), hostActions)
    }

    private fun countBluePixels(bitmap: Bitmap, topY: Int, bottomY: Int): Int {
        var count = 0
        for (y in topY.coerceAtLeast(0) until bottomY.coerceAtMost(bitmap.height)) {
            for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (b > 150 && r < 80 && g > 80) count++
            }
        }
        return count
    }
}
