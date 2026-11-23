package io.composecast.protocol

import kotlinx.serialization.Serializable

/**
 * Visual identity settings served by `/brand`; the client uses these to skin the UI
 * consistently with the server-defined color palette and typography.
 *
 * @param name            Human-readable brand name, e.g. `"Acme Corp"`.
 * @param primaryColor     `#RRGGBB` or `#AARRGGBB` primary brand color (buttons, highlights).
 * @param secondaryColor   `#RRGGBB` or `#AARRGGBB` secondary accent color.
 * @param backgroundColor  Page background color; defaults to white (`"#FFFFFF"`).
 * @param fontFamily       Font family name as registered on the client; `"default"` uses the system font.
 * @param surfaceColor     Default background of cards and expandables; `null` = the built-in light purple.
 * @param textColor        Default text color; `null` = the built-in near-black.
 * @param outlineColor     Default divider color; `null` = the built-in light grey-purple.
 *
 * The three optional colors (T-040) are left out of `/brand` when null, so older apps (which ignore
 * unknown keys anyway) see the same JSON as before.
 */
@Serializable
data class BrandConfig(
    val name: String,
    val primaryColor: String,
    val secondaryColor: String,
    val backgroundColor: String = "#FFFFFF",
    val fontFamily: String = "default",
    val surfaceColor: String? = null,
    val textColor: String? = null,
    val outlineColor: String? = null,
)
