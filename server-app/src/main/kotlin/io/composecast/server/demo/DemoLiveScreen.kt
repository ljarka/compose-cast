package io.composecast.server.demo

import io.composecast.protocol.Alignment
import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.TextAlign
import io.composecast.server.sdk.RegistryBuilder
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A live dashboard (T-047, ADR 0029): rendered again every second, apps get only what changed.
 * The metrics are simulated from the clock, so the demo moves without an external API; a real
 * screen binds the same components to a data source (`"{{metrics.cpu}}"`) and sets `live`.
 */
internal fun RegistryBuilder.liveScreen(clock: () -> Long = System::currentTimeMillis) {
    dynamicScreen("live") {
        val t = clock() / MS_PER_S
        val cpu = (CPU_MID + CPU_SWING * sin(t / 3) + CPU_JITTER * sin(t * 1.7)).roundToInt()
        val memory = MEM_MID + MEM_SWING * sin(t / 7)
        val requests = (RPS_MID + RPS_SWING * sin(t / 5)).roundToInt()
        val errors = ERR_MID + ERR_SWING * sin(t / 4)
        val latency = (0 until HISTORY).map { i ->
            val s = t - (HISTORY - 1 - i)
            (LAT_MID + LAT_SWING * sin(s / 2) + LAT_JITTER * sin(s * 2.3)).roundToInt()
        }
        title = "Live metrics"
        live(everyMs = 1000)
        text("Service health, live", style = "h2")
        spacer(12)
        row(spacing = 12) {
            card(modifier = Modifier(weight = 1f)) {
                column(horizontalAlignment = Alignment.Center, modifier = Modifier(width = Dimension.Fill)) {
                    gauge("$cpu", label = "$cpu%")
                    spacer(8)
                    text("CPU", style = "caption", textAlign = TextAlign.Center)
                }
            }
            card(modifier = Modifier(weight = 1f)) {
                column(horizontalAlignment = Alignment.Center, modifier = Modifier(width = Dimension.Fill)) {
                    gauge(format("%.1f", memory), max = 8f, label = format("%.1f GB", memory), color = "#E8590C")
                    spacer(8)
                    text("Memory of 8 GB", style = "caption", textAlign = TextAlign.Center)
                }
            }
        }
        spacer(12)
        row(spacing = 12) {
            card(modifier = Modifier(weight = 1f)) {
                kpi(String.format(Locale.US, "%,d", requests), label = "Requests / s", caption = "simulated")
            }
            card(modifier = Modifier(weight = 1f)) {
                kpi(format("%.2f%%", errors), label = "Errors", caption = "SLO 1%", color = "#2B8A3E")
            }
        }
        spacer(12)
        card {
            text("Latency, last ${HISTORY}s (ms)", style = "h3")
            spacer(8)
            sparkline(latency.joinToString(", "), min = 0f, max = LAT_AXIS_MAX, height = 96, thickness = 3)
        }
    }
}

private fun format(pattern: String, value: Double) = String.format(Locale.US, pattern, value)

private const val MS_PER_S = 1000.0
private const val HISTORY = 24
private const val CPU_MID = 45.0
private const val CPU_SWING = 30.0
private const val CPU_JITTER = 8.0
private const val MEM_MID = 5.2
private const val MEM_SWING = 1.2
private const val RPS_MID = 1200.0
private const val RPS_SWING = 300.0
private const val ERR_MID = 0.25
private const val ERR_SWING = 0.15
private const val LAT_MID = 140.0
private const val LAT_SWING = 30.0
private const val LAT_JITTER = 12.0
private const val LAT_AXIS_MAX = 220f
