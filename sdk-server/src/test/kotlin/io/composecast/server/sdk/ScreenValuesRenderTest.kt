package io.composecast.server.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** On-device screen values (`values`) and `slider` → `gauge` / `text` rendering. */
class ScreenValuesRenderTest {

    @Test
    fun `slider and gauge above it share one float id via TouchExpression and TextFromFloat`() {
        val registry = composeCast {
            screen("interactive") {
                value("volume", default = 40f, min = 0f, max = 100f)
                gauge("{{values.volume}}", label = "{{values.volume | round}}%")
                slider("volume")
                text("Level: {{values.volume | round:1}}")
            }
        }
        val dump = registry.render("interactive")!!.describe()
        assertTrue("Height(EXACT, 0.0)" !in dump, "unexpected zero height in:\n$dump")
        assertTrue("TouchExpression" in dump, "missing TouchExpression in:\n$dump")
        assertTrue("TextFromFloat" in dump, "missing TextFromFloat in:\n$dump")
        assertTrue("TextMerge" in dump, "missing TextMerge in:\n$dump")
        // Track + live value arc on the gauge canvas.
        assertEquals(2, Regex("DrawArc").findAll(dump).count(), dump)
        // Track + active track on the slider canvas.
        assertEquals(2, Regex("DrawLine").findAll(dump).count(), dump)
        assertEquals(1, Regex("DrawCircle").findAll(dump).count(), dump)
    }

    @Test
    fun `server-bound default resolves through Binder while preserving {{values}} for the renderer`() {
        val loader = DataLoader { """{"temp": 22.5}""" }
        val registry = composeCast(dataLoader = loader) {
            screen("thermostat") {
                data("device", url = "https://example.com/device")
                value("temp", default = "{{device.temp}}", min = 15f, max = 30f)
                gauge("{{values.temp}}", min = 15f, max = 30f, label = "{{values.temp | round:1}}°C")
                slider("temp", step = 0.5f)
            }
        }
        val screen = registry.render("thermostat")!!
        assertEquals("22.5", screen.definition.values["temp"]?.default)
        val dump = screen.describe()
        assertTrue("TouchExpression" in dump, dump)
        assertTrue("TextFromFloat" in dump, dump)
    }

    @Test
    fun `animated screen value emits animationTime float expression for gauge and text`() {
        val registry = composeCast {
            screen("anim") {
                value(
                    "pulse",
                    min = 0f,
                    max = 100f,
                    animate = io.composecast.protocol.ValueAnimation(
                        durationMs = 2000,
                        mode = io.composecast.protocol.AnimationMode.PingPong,
                        easing = io.composecast.protocol.AnimationEasing.EaseInOut,
                    ),
                )
                gauge("{{values.pulse}}", label = "{{values.pulse | round}}%")
            }
        }
        val dump = registry.render("anim")!!.describe()
        assertTrue("FloatExpression" in dump, dump)
        assertTrue("TextFromFloat" in dump, dump)
        assertTrue("TouchExpression" !in dump, dump)
    }
}
