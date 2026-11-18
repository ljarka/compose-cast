package io.composecast.server.sdk.render

// Default look of components (Material-like). Sizes are dp, see the units note in Modifiers.kt.

internal const val DEFAULT_PRIMARY = "#6200EE"
internal const val DEFAULT_BACKGROUND = "#FFFFFF"
internal const val TEXT_COLOR = 0xFF1C1B1F.toInt()
internal const val SURFACE_COLOR = 0xFFF3EDF7.toInt()
internal const val DIVIDER_COLOR = 0xFFD0C8DC.toInt()
internal const val PLACEHOLDER_COLOR = 0xFF79747E.toInt()
internal const val ON_PRIMARY = 0xFFFFFFFF.toInt()
internal const val BUTTON_RADIUS = 12f
internal const val BUTTON_PADDING = 14
internal const val BUTTON_ICON_SIZE = 18f
internal const val BUTTON_ICON_GAP = 8f
internal const val CARD_RADIUS = 12f
internal const val CARD_PADDING = 16
internal const val MARQUEE_DELAY_MS = 1200f
internal const val MARQUEE_SPACING_DP = 24f
internal const val MARQUEE_VELOCITY_DP_S = 30f

/** dp; larger than half of any component, so the rounded clip becomes a circle/pill. */
internal const val CIRCLE_RADIUS = 10_000f
internal const val IMAGE_NAME_PREFIX = "image:"
internal const val SCREEN_PADDING = 16f
