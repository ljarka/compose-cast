package io.composecast.renderer

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ScreenDocument
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.time.Duration

/**
 * Plays Remote Compose documents with sdk-android's [ComposeCastScreen] (the real player, exactly
 * as in the app) and captures them as PNGs. One instance per Robolectric sandbox; call [render]
 * on the test's main thread only.
 */
class Renderer(private val compose: ComposeContentTestRule) {
    private var current by mutableStateOf<ScreenDocument?>(null)
    private var started = false

    /** Renders [bytes] (an RC document); throws [IllegalArgumentException] if they do not decode. */
    fun render(bytes: ByteArray): ByteArray {
        val document = try {
            ScreenDocument.fromBytes(id = "preview", title = "", bytes = bytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("not a Remote Compose document: ${e.message}", e)
        }
        if (!started) {
            // A fresh player per document (key): no scroll position or state from the previous one.
            compose.setContent { current?.let { key(it) { ComposeCastScreen(it, Modifier.fillMaxSize()) } } }
            started = true
        }
        current = document
        compose.waitForIdle()
        // RC animations (gauges, anim() expressions) run on the player's clock: let them finish
        // so the preview shows the end state, not the first frame.
        shadowOf(Looper.getMainLooper()).idleFor(SETTLE)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private companion object {
        val SETTLE: Duration = Duration.ofSeconds(3)
    }
}
