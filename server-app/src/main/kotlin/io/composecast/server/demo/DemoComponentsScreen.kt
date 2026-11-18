package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.protocol.Alignment
import io.composecast.protocol.ButtonVariant
import io.composecast.protocol.ContentScale
import io.composecast.protocol.FontWeight
import io.composecast.protocol.Modifier
import io.composecast.protocol.Shape
import io.composecast.protocol.TextAlign
import io.composecast.protocol.TextOverflow
import io.composecast.server.sdk.RegistryBuilder

/**
 * Text/image options, Icon, Divider, Card, button variants (T-013).
 */
internal fun RegistryBuilder.componentsScreen() {
    screen("components") {
        title = "Components"
        text(
            "Styled & centered headline",
            style = "h2",
            color = "#6200EE",
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        spacer(8)
        text(
            "A very long single-line caption that should truncate cleanly with an ellipsis at the end",
            style = "caption",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        spacer(4)
        text(
            "Marquee scrolling ticker text for narrow spaces or long announcements",
            fontSize = 14,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            marquee = true,
        )
        spacer(12)
        divider()
        spacer(12)
        card(spacing = 10) {
            row(spacing = 8, verticalAlignment = Alignment.Center) {
                icon(HERO_IMAGE, alt = "Badge", size = 24, modifier = Modifier(shape = Shape.Circle))
                text("Card with Icon & Cropped Image", style = "h3", modifier = Modifier(weight = 1f))
            }
            image(
                HERO_IMAGE,
                alt = "Cropped 16:9 banner",
                contentScale = ContentScale.Crop,
                alpha = 0.85f,
                aspectRatio = 16f / 9f,
                modifier = Modifier(cornerRadius = 10),
            )
            divider(thickness = 2, color = "#6200EE")
            row(spacing = 8) {
                button(
                    "Filled",
                    Action.Reload,
                    variant = ButtonVariant.Filled,
                    icon = HERO_IMAGE,
                    modifier = Modifier(weight = 1f),
                )
                button(
                    "Outlined",
                    Action.Reload,
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier(weight = 1f),
                )
                button(
                    "Text",
                    Action.Reload,
                    variant = ButtonVariant.Text,
                )
            }
        }
    }
}
