package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Screen-level on-device values (`values`), `animate`, and `slider` validation & JSON round-trip. */
class ScreenValuesTest {

    @Test
    fun `screen with values, slider, gauge and dynamic text round-trips through JSON`() {
        val json = """
            {
              "id": "interactive",
              "title": "Interactive",
              "values": {
                "volume": { "default": "40", "min": 0, "max": 100 },
                "temp": { "default": "21.5", "min": 15, "max": 30 }
              },
              "components": [
                { "type": "gauge", "value": "{{values.volume}}", "label": "{{values.volume | round}}%" },
                { "type": "slider", "controls": "volume" },
                { "type": "text", "text": "Target: {{values.temp | round:1}}°C" },
                { "type": "slider", "controls": "temp", "step": 0.5, "color": "#E8590C" }
              ]
            }
        """.trimIndent()
        val decoded = ScreenJson.decode(json, "interactive.json")
        assertEquals(2, decoded.values.size)
        assertEquals(ScreenValue(default = "40", min = 0f, max = 100f), decoded.values["volume"])
        assertEquals(Component.Slider(controls = "volume"), decoded.components[1])
        assertEquals(Component.Slider(controls = "temp", step = 0.5f, color = "#E8590C"), decoded.components[3])
        assertEquals(decoded, ScreenJson.decode(ScreenJson.encode(decoded), "round-trip"))
    }

    @Test
    fun `invalid values and slider configurations report clear paths`() {
        val screen = ScreenDefinition(
            id = "bad",
            values = mapOf(
                "ok" to ScreenValue(default = "50", min = 0f, max = 100f),
                "badRange" to ScreenValue(default = "5", min = 10f, max = 10f),
                "badDefault" to ScreenValue(default = "abc", min = 0f, max = 100f),
            ),
            components = listOf(
                Component.Slider(controls = "missing"),
                Component.Slider(controls = "ok", step = -1f, color = "red"),
                Component.Slider(controls = "ok", step = 200f),
                Component.Text(text = "{{values.missing}}"),
                Component.Text(text = "{{values.ok | upper}}"),
            ),
        )
        val problems = screen.problems()
        assertTrue(problems.any { "values.badRange.max" in it }, "$problems")
        assertTrue(problems.any { "values.badDefault.default" in it }, "$problems")
        assertTrue(problems.any { "components[0].controls" in it && "unknown value 'missing'" in it }, "$problems")
        assertTrue(problems.any { "components[1].step" in it }, "$problems")
        assertTrue(problems.any { "components[1].color" in it }, "$problems")
        assertTrue(problems.any { "components[2].controls" in it && "already controlled" in it }, "$problems")
        assertTrue(problems.any { "components[2].step" in it && "range" in it }, "$problems")
        assertTrue(problems.any { "components[3].text" in it && "unknown value 'missing'" in it }, "$problems")
        assertTrue(problems.any { "components[4].text" in it && "only support the 'round' filter" in it }, "$problems")
    }

    @Test
    fun `value default may bind to a server data source`() {
        val screen = ScreenDefinition(
            id = "thermostat",
            data = mapOf("device" to DataSource(url = "https://example.com/device")),
            values = mapOf("temp" to ScreenValue(default = "{{device.temp}}", min = 15f, max = 30f)),
            components = listOf(
                Component.Gauge(value = "{{values.temp}}", min = 15f, max = 30f, label = "{{values.temp | round:1}}°C"),
                Component.Slider(controls = "temp", step = 0.5f),
            ),
        )
        assertEquals(emptyList(), screen.problems())
        assertTrue(screen.isBound)
    }

    @Test
    fun `animated screen value round-trips and rejects slider conflict or bad duration`() {
        val json = """
            {
              "id": "pulse",
              "values": {
                "p": {
                  "min": 0,
                  "max": 100,
                  "animate": { "durationMs": 3000, "mode": "loop", "easing": "linear" }
                }
              },
              "components": [
                { "type": "gauge", "value": "{{values.p}}", "label": "{{values.p | round}}%" }
              ]
            }
        """.trimIndent()
        val decoded = ScreenJson.decode(json, "pulse.json")
        assertEquals(
            ValueAnimation(durationMs = 3000, mode = AnimationMode.Loop, easing = AnimationEasing.Linear),
            decoded.values["p"]?.animate,
        )
        assertEquals(decoded, ScreenJson.decode(ScreenJson.encode(decoded), "round-trip"))

        val bad = ScreenDefinition(
            id = "bad-anim",
            values = mapOf("p" to ScreenValue(animate = ValueAnimation(durationMs = 10))),
            components = listOf(Component.Slider(controls = "p")),
        )
        val problems = bad.problems()
        assertTrue(problems.any { "values.p.animate.durationMs" in it }, "$problems")
        assertTrue(problems.any { "components[0].controls" in it && "animated" in it }, "$problems")
    }
}
