package io.composecast.renderer

import android.graphics.BitmapFactory
import androidx.compose.ui.test.junit4.createComposeRule
import io.composecast.server.sdk.composeCast
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
class RendererTest {
    @get:Rule
    val compose = createComposeRule()

    private fun document(title: String) = composeCast(imageLoader = { error("no images") }) {
        screen("s") {
            text(title, style = "h1")
            gauge("72", label = "CPU")
            kpi("1 284", label = "Orders")
        }
    }.render("s")!!.document

    @Test
    fun rendersPhoneSizedPngWithAnimationsSettled() {
        val renderer = Renderer(compose)
        val first = renderer.render(document("Dashboard"))
        val other = renderer.render(document("Changed"))
        val again = renderer.render(document("Dashboard"))
        File("build/renderer-test.png").writeBytes(first)

        val bitmap = BitmapFactory.decodeByteArray(first, 0, first.size)
        assertEquals(1078, bitmap.width) // 411 dp × 2.625
        assertEquals(2399, bitmap.height)
        // The gauge animates to its value: a settled render is the same every time.
        assertArrayEquals(first, again)
        assertFalse(first.contentEquals(other))
    }

    @Test
    fun rejectsBytesThatAreNotADocument() {
        val renderer = Renderer(compose)
        assertThrows(IllegalArgumentException::class.java) { renderer.render("hello".toByteArray()) }
    }
}
