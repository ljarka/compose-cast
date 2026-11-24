package io.composecast.server.demo

import io.composecast.protocol.Alignment
import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.TextAlign
import io.composecast.server.sdk.RegistryBuilder

/**
 * Dashboard components drawn on an RC canvas (T-047): gauges, key figures and a sparkline.
 * Static values here; T-047 step 2 binds them to a metrics source and makes the screen live.
 */
internal fun RegistryBuilder.dashboardScreen() {
    screen("dashboard") {
        title = "Dashboard"
        text("Service health", style = "h2")
        spacer(12)
        row(spacing = 12) {
            card(modifier = Modifier(weight = 1f)) {
                column(horizontalAlignment = Alignment.Center, modifier = Modifier(width = Dimension.Fill)) {
                    gauge("42", label = "42%")
                    spacer(8)
                    text("CPU", style = "caption", textAlign = TextAlign.Center)
                }
            }
            card(modifier = Modifier(weight = 1f)) {
                column(horizontalAlignment = Alignment.Center, modifier = Modifier(width = Dimension.Fill)) {
                    gauge("6.1", max = 8f, label = "6.1 GB", color = "#E8590C")
                    spacer(8)
                    text("Memory of 8 GB", style = "caption", textAlign = TextAlign.Center)
                }
            }
        }
        spacer(12)
        row(spacing = 12) {
            card(modifier = Modifier(weight = 1f)) {
                kpi("1,284", label = "Requests / s", caption = "+4% vs last hour")
            }
            card(modifier = Modifier(weight = 1f)) {
                kpi("0.3%", label = "Errors", caption = "SLO 1%", color = "#2B8A3E")
            }
        }
        spacer(12)
        card {
            text("Latency, last 24 h (ms)", style = "h3")
            spacer(8)
            sparkline(
                "120, 118, 125, 131, 128, 140, 162, 158, 149, 137, 129, 124, " +
                    "121, 119, 126, 138, 171, 190, 176, 150, 139, 131, 127, 122",
                height = 96,
                thickness = 3,
            )
        }
    }
}
