package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Options of components (text, image, button); JSON names are the @SerialName values.

/** Text styles of [Component.Text]; [key] is the JSON / DSL name. */
@Serializable
public enum class TextStyle(public val key: String) {
    @SerialName("h1")
    H1("h1"),

    @SerialName("h2")
    H2("h2"),

    @SerialName("h3")
    H3("h3"),

    @SerialName("body")
    Body("body"),

    @SerialName("caption")
    Caption("caption"),
    ;

    public companion object {
        public fun fromKey(key: String): TextStyle {
            val allowed = entries.joinToString { it.key }
            return entries.firstOrNull { it.key == key }
                ?: throw IllegalArgumentException("Unknown text style '$key'. Allowed: $allowed")
        }
    }
}

@Serializable
public enum class FontWeight {
    @SerialName("thin")
    Thin,

    @SerialName("extraLight")
    ExtraLight,

    @SerialName("light")
    Light,

    @SerialName("normal")
    Normal,

    @SerialName("medium")
    Medium,

    @SerialName("semiBold")
    SemiBold,

    @SerialName("bold")
    Bold,

    @SerialName("extraBold")
    ExtraBold,

    @SerialName("black")
    Black,
}

@Serializable
public enum class TextAlign {
    @SerialName("start")
    Start,

    @SerialName("center")
    Center,

    @SerialName("end")
    End,

    @SerialName("left")
    Left,

    @SerialName("right")
    Right,

    @SerialName("justify")
    Justify,
}

@Serializable
public enum class TextOverflow {
    @SerialName("clip")
    Clip,

    @SerialName("visible")
    Visible,

    @SerialName("ellipsis")
    Ellipsis,

    @SerialName("startEllipsis")
    StartEllipsis,

    @SerialName("middleEllipsis")
    MiddleEllipsis,
}

@Serializable
public enum class ContentScale {
    @SerialName("fit")
    Fit,

    @SerialName("crop")
    Crop,

    @SerialName("fillBounds")
    FillBounds,

    @SerialName("fillWidth")
    FillWidth,

    @SerialName("fillHeight")
    FillHeight,

    @SerialName("inside")
    Inside,

    @SerialName("none")
    None,
}

@Serializable
public enum class ButtonVariant {
    @SerialName("filled")
    Filled,

    @SerialName("outlined")
    Outlined,

    @SerialName("text")
    Text,
}
