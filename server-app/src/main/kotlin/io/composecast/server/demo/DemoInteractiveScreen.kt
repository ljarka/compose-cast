package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.protocol.Alignment
import io.composecast.protocol.ButtonVariant
import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.TextAlign
import io.composecast.protocol.Visibility
import io.composecast.protocol.VisibilityTransition
import io.composecast.server.sdk.RegistryBuilder

/**
 * Interactive on-device controls: sliders driving gauges, KPIs and text locally on the phone with
 * no server round-trip (`values` + `slider`).
 */
internal fun RegistryBuilder.interactiveScreen() {
    screen("interactive") {
        title = "Interactive"
        value("volume", default = 40f, min = 0f, max = 100f)
        value("temp", default = 21f, min = 15f, max = 30f)

        text("On-device controls", style = "h2")
        text(
            "Drag either slider — the gauges and labels update locally with zero network requests.",
            style = "caption",
        )
        spacer(12)

        card(spacing = 12) {
            column(horizontalAlignment = Alignment.Center, modifier = Modifier(width = Dimension.Fill)) {
                gauge("{{values.volume}}", min = 0f, max = 100f, label = "{{values.volume | round}}%", size = 140)
                spacer(8)
                text("Volume: {{values.volume | round}}%", style = "h3", textAlign = TextAlign.Center)
            }
            slider("volume")
        }

        spacer(12)

        card(spacing = 12) {
            row(verticalAlignment = Alignment.Center, spacing = 16) {
                gauge(
                    "{{values.temp}}",
                    min = 15f,
                    max = 30f,
                    label = "{{values.temp | round:1}}°",
                    color = "#E8590C",
                    size = 108,
                    thickness = 10,
                )
                kpi(
                    value = "{{values.temp | round:1}}°C",
                    label = "Thermostat",
                    caption = "Snaps in 0.5° steps",
                    color = "#E8590C",
                    modifier = Modifier(weight = 1f),
                )
            }
            slider("temp", step = 0.5f, color = "#E8590C")
        }

        spacer(16)
        text("Animated visibility", style = "h2")
        text("Tap a button to show or hide another element with an on-device transition.", style = "caption")
        spacer(8)
        row(spacing = 8, modifier = Modifier(width = Dimension.Fill)) {
            button("Toggle card", Action.Toggle("details"), modifier = Modifier(weight = 1f))
            button("Hide", Action.Hide("details"), variant = ButtonVariant.Outlined)
        }
        spacer(8)
        card(
            spacing = 6,
            modifier = Modifier(
                visibility = Visibility(
                    id = "details",
                    transition = VisibilityTransition.Fade,
                    durationMs = 300,
                ),
            ),
        ) {
            text("Animated panel", style = "h3")
            text("This card fades in and out locally on the device while sibling layout reflows smoothly.")
        }
    }
}
