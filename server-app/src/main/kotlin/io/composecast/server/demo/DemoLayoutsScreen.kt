package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.protocol.Alignment
import io.composecast.protocol.Arrangement
import io.composecast.protocol.Border
import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.Padding
import io.composecast.protocol.Shape
import io.composecast.server.sdk.RegistryBuilder

/**
 * Containers and modifiers (T-012, ADR 0020): rows, columns, boxes; weight, padding,
 * background, rounded/circle clip, border, alignment, tap on any component.
 */
internal fun RegistryBuilder.layoutsScreen() {
    screen("layouts") {
        title = "Layouts"
        text("Rows, columns and boxes", style = "h2")
        spacer(12)
        row(spacing = 12) {
            featureCard(
                "Mountains",
                "Tap the card",
                HERO_IMAGE,
                Action.Navigate("onboarding"),
            )
            featureCard("Lakes", "Weight 1 each", HERO_IMAGE, Action.Custom("lakes"))
        }
        spacer(12)
        row(
            modifier = Modifier(background = SURFACE, cornerRadius = 12, padding = Padding(12)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Center,
        ) {
            text("spaceBetween", style = "h3")
            text("end", style = "caption")
        }
        spacer(12)
        row(spacing = 12, verticalAlignment = Alignment.Center) {
            box(
                modifier = Modifier(
                    width = Dimension.Dp(48),
                    height = Dimension.Dp(48),
                    shape = Shape.Circle,
                    background = "#03DAC6",
                ),
                horizontalAlignment = Alignment.Center,
                verticalAlignment = Alignment.Center,
            ) {
                text("CC", style = "h3", modifier = Modifier(width = Dimension.Wrap))
            }
            column(modifier = Modifier(weight = 1f)) {
                text("Circle + weighted column", style = "h3")
                text("Text fills the rest of the row", style = "caption")
            }
        }
        spacer(12)
        box(
            modifier = Modifier(height = Dimension.Dp(96), cornerRadius = 16, border = Border(2, "#6200EE")),
            horizontalAlignment = Alignment.Center,
            verticalAlignment = Alignment.Center,
        ) {
            text("Centered in a bordered box", modifier = Modifier(width = Dimension.Wrap))
        }
        spacer(12)
        row(horizontalArrangement = Arrangement.End) {
            button("Back home", Action.Navigate("main"), modifier = Modifier(width = Dimension.Wrap))
        }
    }
}
