package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.protocol.Border
import io.composecast.protocol.ContentScale
import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.Padding
import io.composecast.server.sdk.ComponentsBuilder

internal const val SURFACE = "#F3EDF7"

/**
 * The demo reuses one image URL: images are fetched one by one while the server starts, so every
 * extra URL adds up to 15 s to startup when the image host is down (picsum.photos, 2026-11-12).
 */
internal const val HERO_IMAGE = "https://picsum.photos/400/200"

/** A tappable card: image on top, title and subtitle below; half of a row (weight 1). */
internal fun ComponentsBuilder.featureCard(title: String, subtitle: String, image: String, action: Action) = column(
    modifier = Modifier(
        weight = 1f,
        background = "#FFFFFF",
        cornerRadius = 16,
        border = Border(1, "#D0C8DC"),
        padding = Padding(12),
        action = action,
    ),
    spacing = 8,
) {
    image(
        image,
        alt = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier(height = Dimension.Dp(88), cornerRadius = 8),
    )
    text(title, style = "h3")
    text(subtitle, style = "caption")
}
