package io.composecast.server.demo

import io.composecast.protocol.Alignment
import io.composecast.protocol.ContentScale
import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.Padding
import io.composecast.server.sdk.RegistryBuilder

/**
 * Scrolling gallery, Flow chips, 2- and 3-column equal-width grids (T-014).
 */
internal fun RegistryBuilder.collectionsScreen() {
    screen("collections") {
        title = "Collections"
        text("Gallery", style = "h3")
        spacer(8)
        gallery(spacing = 8) {
            for (i in 1..10) {
                image(
                    HERO_IMAGE,
                    alt = "Photo $i",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier(width = Dimension.Dp(120), height = Dimension.Dp(80), cornerRadius = 8),
                )
            }
        }
        spacer(16)
        text("Flow chips", style = "h3")
        spacer(8)
        flow(maxItemsInEachRow = 4) {
            val tags = listOf(
                "Weather", "Forecast", "Wind", "Rain", "UV", "Humidity",
                "Radar", "Alerts", "Sun", "Moon", "Air", "Pollen",
            )
            for (tag in tags) {
                box(modifier = Modifier(padding = Padding(start = 4, top = 4, end = 4, bottom = 4))) {
                    box(
                        modifier = Modifier(
                            width = Dimension.Wrap,
                            background = SURFACE,
                            cornerRadius = 16,
                            padding = Padding(start = 12, top = 6, end = 12, bottom = 6),
                        ),
                    ) {
                        text(tag, style = "caption", modifier = Modifier(width = Dimension.Wrap))
                    }
                }
            }
        }
        spacer(16)
        text("2-column grid", style = "h3")
        spacer(8)
        grid(columns = 2, spacing = 8) {
            for (i in 1..3) {
                card(spacing = 4) {
                    text("Tile $i", style = "h3")
                    text("Equal width", style = "caption")
                }
            }
        }
        spacer(16)
        text("3-column grid", style = "h3")
        spacer(8)
        grid(columns = 3, spacing = 8) {
            for (i in 1..6) {
                box(
                    modifier = Modifier(background = SURFACE, cornerRadius = 8, padding = Padding(10)),
                    horizontalAlignment = Alignment.Center,
                ) {
                    text("#$i", style = "body", modifier = Modifier(width = Dimension.Wrap))
                }
            }
        }
    }
}
