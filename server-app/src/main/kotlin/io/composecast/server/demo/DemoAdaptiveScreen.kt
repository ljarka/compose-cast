package io.composecast.server.demo

import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.Padding
import io.composecast.server.sdk.RegistryBuilder

/**
 * Adaptive layouts (T-015): FitBox, collapsible row, size constraints, fillFraction.
 */
internal fun RegistryBuilder.adaptiveScreen() {
    screen("adaptive") {
        title = "Adaptive"
        text("FitBox", style = "h3")
        spacer(8)
        fitBox {
            card(modifier = Modifier(width = Dimension.Dp(520), background = "#E8DEF8")) {
                text("Wide tablet variant (520 dp)", style = "h3")
                text("Shown only when the screen is wide enough.", style = "caption")
            }
            card {
                text("Compact phone variant", style = "h3")
                text("Selected when the 520 dp variant does not fit.", style = "caption")
            }
        }
        spacer(16)
        text("Collapsible row", style = "h3")
        spacer(8)
        row(collapsible = true, spacing = 8) {
            box(
                modifier = Modifier(
                    width = Dimension.Dp(180),
                    background = SURFACE,
                    cornerRadius = 8,
                    padding = Padding(12),
                    collapsePriority = 10f,
                ),
            ) {
                text("Primary (p=10)", style = "body")
            }
            box(
                modifier = Modifier(
                    width = Dimension.Dp(180),
                    background = SURFACE,
                    cornerRadius = 8,
                    padding = Padding(12),
                    collapsePriority = 5f,
                ),
            ) {
                text("Secondary (p=5)", style = "body")
            }
            box(
                modifier = Modifier(
                    width = Dimension.Dp(180),
                    background = SURFACE,
                    cornerRadius = 8,
                    padding = Padding(12),
                    collapsePriority = 1f,
                ),
            ) {
                text("Optional (p=1)", style = "body")
            }
        }
        spacer(16)
        text("Constraints & fillFraction", style = "h3")
        spacer(8)
        box(
            modifier = Modifier(
                fillFraction = 0.6f,
                minWidth = 140,
                maxWidth = 320,
                minHeight = 48,
                background = SURFACE,
                cornerRadius = 8,
                padding = Padding(12),
            ),
        ) {
            text("60% width (140..320 dp)", style = "caption")
        }
    }
}
