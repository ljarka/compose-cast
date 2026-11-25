package io.composecast.compat

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ScreenDocument
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.composeCast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies on-device slider → gauge / text interaction on the real `ComposeCastScreen` player
 * under Robolectric: dragging a slider updates a gauge rendered *above* it and dynamic text,
 * leaves a second independent slider untouched, and survives a live-screen `DOC_DATA_UPDATE` tick.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class InteractiveSliderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `dragging slider updates gauge above it, leaves second slider untouched, and survives live update`() {
        var tick = 0
        val registry = composeCast(dataLoader = DataLoader { """{"status": "Tick ${++tick}"}""" }) {
            screen("test") {
                live(everyMs = 1000)
                data("m", url = "https://example.com/m", cacheSeconds = 0)
                value("volume", default = 20f, min = 0f, max = 100f)
                value("temp", default = 20f, min = 15f, max = 30f)

                // Gauge ABOVE slider 1
                gauge("{{values.volume}}", min = 0f, max = 100f, label = "{{values.volume | round}}%", size = 120)
                slider("volume")
                // Independent slider 2 + gauge 2 below
                gauge("{{values.temp}}", min = 15f, max = 30f, label = "{{values.temp | round:1}}°", size = 100)
                slider("temp", step = 1f)
                text("{{m.status}}")
            }
        }
        val first = registry.render("test", RenderContext())!!
        var currentDoc by mutableStateOf(ScreenDocument.fromBytes(first.id, first.title, first.document))
        compose.setContent {
            ComposeCastScreen(currentDoc, Modifier.fillMaxSize())
        }
        compose.waitForIdle()

        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        val density = compose.density.density

        // Screen padding is 16dp, gauge 1 is 120dp tall -> slider 1 sits at y = 136..180dp (center 158dp).
        val slider1Y = (16f + 120f + 22f) * density
        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(120f, slider1Y),
                end = Offset(width - 120f, slider1Y),
                durationMillis = 200,
            )
        }
        compose.waitForIdle()

        val afterDrag = compose.onRoot().captureToImage().asAndroidBitmap()

        // Region 1: gauge 1 (y = 16dp..136dp) MUST have changed visually when slider 1 was dragged.
        val gauge1Diff = diffPixels(before, afterDrag, (16f * density).toInt(), (136f * density).toInt())
        assertTrue("Expected gauge 1 above slider 1 to update on drag, diff=$gauge1Diff", gauge1Diff > 200)

        // Region 2: slider 1 (y = 136dp..180dp) MUST have moved visually.
        val slider1Diff = diffPixels(before, afterDrag, (136f * density).toInt(), (180f * density).toInt())
        assertTrue("Expected slider 1 thumb/track to move on drag, diff=$slider1Diff", slider1Diff > 200)

        // Region 3: gauge 2 + slider 2 (y = 180dp..324dp) MUST NOT have moved when slider 1 was dragged.
        val secondGroupDiff = diffPixels(before, afterDrag, (180f * density).toInt(), (324f * density).toInt())
        assertEquals("Dragging slider 1 must not move gauge 2 / slider 2", 0, secondGroupDiff)

        // Live screen data update arrives: gauge 1 and slider 1 MUST keep their dragged position!
        val second = registry.render("test", RenderContext())!!
        val delta = registry.dataUpdate(second, RenderContext())
        currentDoc = ScreenDocument.fromBytes(second.id, second.title, delta)
        compose.waitForIdle()

        val afterUpdate = compose.onRoot().captureToImage().asAndroidBitmap()
        val gauge1AfterUpdateDiff =
            diffPixels(afterDrag, afterUpdate, (16f * density).toInt(), (136f * density).toInt())
        assertEquals("DOC_DATA_UPDATE must not reset dragged slider/gauge", 0, gauge1AfterUpdateDiff)
    }

    private fun diffPixels(a: Bitmap, b: Bitmap, topY: Int, bottomY: Int): Int {
        var count = 0
        for (y in topY.coerceAtLeast(0) until bottomY.coerceAtMost(a.height)) {
            for (x in 0 until a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) count++
            }
        }
        return count
    }
}
