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
    val homeScreen: String? = null,
    val darkPrimaryColor: String? = null,
    val darkSecondaryColor: String? = null,
    val darkBackgroundColor: String? = null,
    val darkSurfaceColor: String? = null,
    val darkTextColor: String? = null,
    val darkOutlineColor: String? = null,
) {
    /**
     * Every problem, each starting with the field name (`primaryColor: expected #RRGGBB …`); empty
     * when valid. Used by the admin panel's settings (T-007 A5).
     */
    fun problems(): List<String> = buildList {
        if (name.isBlank()) add("name: must not be blank")
        listOf(
            "primaryColor" to primaryColor,
            "secondaryColor" to secondaryColor,
            "backgroundColor" to backgroundColor,
            "surfaceColor" to surfaceColor,
            "textColor" to textColor,
            "outlineColor" to outlineColor,
            "darkPrimaryColor" to darkPrimaryColor,
            "darkSecondaryColor" to darkSecondaryColor,
            "darkBackgroundColor" to darkBackgroundColor,
            "darkSurfaceColor" to darkSurfaceColor,
            "darkTextColor" to darkTextColor,
            "darkOutlineColor" to darkOutlineColor,
        ).forEach { (field, color) -> color?.let(::colorProblem)?.let { add("$field: $it") } }
    }
}
