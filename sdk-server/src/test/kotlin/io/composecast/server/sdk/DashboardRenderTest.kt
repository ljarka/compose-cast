package io.composecast.server.sdk

import io.composecast.protocol.Component
import io.composecast.protocol.NumberList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Dashboard components (T-047): gauge, sparkline and kpi on an RC canvas. */
class DashboardRenderTest {

    @Test
    fun `gauge, sparkline and kpi emit canvas arcs, lines over a float array and texts`() {
        val registry = composeCast {
            screen("d") {
                gauge("25", label = "25%")
                sparkline("10, 30, 20, 40")
                kpi("1,284", label = "Requests", caption = "+4%")
            }
        }
        val dump = registry.get("d")!!.describe()
        assertTrue("Height(EXACT, 0.0)" !in dump, "unexpected zero height in:\n$dump")
        assertEquals(2, Regex("CANVAS").findAll(dump).count(), dump)
        assertEquals(2, Regex("DrawArc").findAll(dump).count(), "track + value arc:\n$dump")
        assertEquals(3, Regex("DrawLine").findAll(dump).count(), "4 points → 3 segments:\n$dump")
        // The value is its own constant (replaceable by a data update), normalized to 0..1.
        assertTrue("= 0.25" in dump, "gauge fraction constant in:\n$dump")
        assertTrue("DataListFloat" in dump && "[0.0, 0.6666667, 0.33333334, 1.0]" in dump, dump)
        listOf("\"25%\"", "\"1,284\"", "\"Requests\"", "\"+4%\"").forEach {
            assertTrue("TextData" in dump && it.trim('"').take(7) in dump, "no $it in:\n$dump")
        }
    }

    @Test
    fun `gauge clamps to min and max and draws no value arc at zero`() {
        val registry = composeCast {
            screen("over") { gauge("150") }
            screen("zero") { gauge("0") }
        }
        assertTrue("= 1.0" in registry.get("over")!!.describe())
        assertEquals(1, Regex("DrawArc").findAll(registry.get("zero")!!.describe()).count())
    }

    @Test
    fun `bound values come from the data source (array renders as JSON, then parsed)`() {
        val loader = DataLoader { """{"cpu": 80, "history": [1, 2, 3, 4, 5]}""" }
        val registry = composeCast(dataLoader = loader) {
            screen("live") {
                data("m", url = "https://example.com/metrics")
                gauge("{{m.cpu}}", label = "{{m.cpu}}%")
                sparkline("{{m.history}}")
            }
        }
        val screen = registry.render("live")!!
        val components = screen.definition.components
        assertEquals("80", (components[0] as Component.Gauge).value)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), NumberList.parse((components[1] as Component.Sparkline).values))
        val dump = screen.describe()
        assertTrue("= 0.8" in dump, dump)
        assertEquals(4, Regex("DrawLine").findAll(dump).count(), dump)
    }

    @Test
    fun `invalid dashboard components fail at build time`() {
        listOf<ComponentsBuilder.() -> Unit>(
            { gauge("lots") },
            { gauge("5", min = 10f, max = 10f) },
            { gauge("5", size = 40, thickness = 20) },
            { gauge("5", color = "red") },
            { sparkline("7") },
            { sparkline("1, 2", min = 5f, max = 1f) },
            { sparkline("1, 2", height = 0) },
            { kpi("1", color = "#12") },
        ).forEachIndexed { i, content ->
            assertFailsWith<IllegalArgumentException>("case $i") { composeCast { screen("a") { content(this) } } }
        }
    }

    @Test
    fun `number list reads separated numbers and JSON arrays`() {
        assertEquals(listOf(3f, 5.5f, -2f), NumberList.parse("3, 5.5, -2"))
        assertEquals(listOf(1f, 20f, 300f), NumberList.parse("[1,2e1,3.0E2]"))
        assertEquals(42.5f, NumberList.single(" 42.5 "))
        assertEquals(null, NumberList.single("42%"))
    }
}
