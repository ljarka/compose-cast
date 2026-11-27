package io.composecast.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Layout and decoration of any component (T-012, ADR 0020). All fields are optional; an empty
 * modifier changes nothing. The box model is fixed, not ordered like Compose modifiers:
 * the size ([width]/[height]/[weight]) includes [padding]; [background], [border] and the clip
 * ([cornerRadius]/[shape]) cover the whole size; [padding] insets the content; [action] makes the
 * whole size tappable. There is no margin: use the parent's `spacing` or padding.
 */
@Serializable
public data class Modifier(
    /** dp; JSON: a number (all sides) or `{start, top, end, bottom}`. */
    val padding: Padding? = null,
    /** dp, `"fill"` or `"wrap"`; default: fill in a column, wrap in a row. */
    val width: Dimension? = null,
    val height: Dimension? = null,
    /** Share of the free space along a row's/column's main axis; only for direct children of one. */
    val weight: Float? = null,
    /** `#RRGGBB` or `#AARRGGBB`. */
    val background: String? = null,
    /** dp; rounds the clip, background and border. */
    val cornerRadius: Int? = null,
    /** `circle`: clips, background and border as a circle/pill (instead of [cornerRadius]). */
    val shape: Shape? = null,
    val border: Border? = null,
    /** Makes the component tappable; same actions as buttons. */
    val action: Action? = null,
    /** Enables scrolling on a container (`"vertical"` or `"horizontal"`, T-014). */
    val scroll: ScrollDirection? = null,
    /** Size constraints in dp (T-015). */
    val minWidth: Int? = null,
    val maxWidth: Int? = null,
    val minHeight: Int? = null,
    val maxHeight: Int? = null,
    /** Fraction `(0, 1]` of parent width when `width` is not set (T-015). */
    val fillFraction: Float? = null,
    /** Drop priority in a `collapsible` row/column: lower values collapse first (T-015). */
    val collapsePriority: Float? = null,
    /** Accessibility semantics overrides (T-016). */
    val semantics: Semantics? = null,
    /** Animated on-device visibility controlled by `toggle` / `show` / `hide` actions. */
    val visibility: Visibility? = null,
) {
    val isEmpty: Boolean get() = this == NONE

    public companion object {
        public val NONE: Modifier = Modifier()
    }
}

/** Padding in dp per side; JSON `8` means all four sides. */
@Serializable(with = PaddingSerializer::class)
public data class Padding(val start: Int = 0, val top: Int = 0, val end: Int = 0, val bottom: Int = 0) {
    public constructor(all: Int) : this(all, all, all, all)
}

/** A width or height: exact dp, fill the available space, or wrap the content. */
@Serializable(with = DimensionSerializer::class)
public sealed interface Dimension {
    public data class Dp(val value: Int) : Dimension

    public data object Fill : Dimension

    public data object Wrap : Dimension
}

@Serializable
public data class Border(val width: Int = 1, val color: String)

@Serializable
public enum class Shape {
    @SerialName("circle")
    Circle,
}

/** Placement along an axis; for vertical axes `start` = top, `end` = bottom. */
@Serializable
public enum class Alignment {
    @SerialName("start")
    Start,

    @SerialName("center")
    Center,

    @SerialName("end")
    End,
}

/** Distribution of children along a row's/column's main axis; `start` = top in a column. */
@Serializable
public enum class Arrangement {
    @SerialName("start")
    Start,

    @SerialName("center")
    Center,

    @SerialName("end")
    End,

    @SerialName("spaceBetween")
    SpaceBetween,

    @SerialName("spaceAround")
    SpaceAround,

    @SerialName("spaceEvenly")
    SpaceEvenly,
}

@Serializable
public enum class ScrollDirection {
    @SerialName("vertical")
    Vertical,

    @SerialName("horizontal")
    Horizontal,
}

/** Accessibility semantics override for any component (T-016). */
@Serializable
public data class Semantics(
    val description: String? = null,
    val role: SemanticsRole? = null,
    val stateDescription: String? = null,
    val hidden: Boolean = false,
    val enabled: Boolean? = null,
)

@Serializable
public enum class SemanticsRole {
    @SerialName("button")
    Button,

    @SerialName("checkbox")
    Checkbox,

    @SerialName("switch")
    Switch,

    @SerialName("radioButton")
    RadioButton,

    @SerialName("tab")
    Tab,

    @SerialName("image")
    Image,

    @SerialName("dropdownList")
    DropdownList,

    @SerialName("picker")
    Picker,

    @SerialName("carousel")
    Carousel,
}

/**
 * Animated on-device visibility of a component: controlled by `Action.Toggle`, `Action.Show`, or
 * `Action.Hide` targeting [id]. Multiple components can share the same [id].
 */
@Serializable
public data class Visibility(
    val id: String,
    /** Starts hidden (`GONE`) when true; starts visible (`VISIBLE`) by default. */
    val hidden: Boolean = false,
    /** Enter/exit transition when visibility changes. */
    val transition: VisibilityTransition = VisibilityTransition.Fade,
    /** Transition and sibling-reflow duration in ms. */
    val durationMs: Int = DEFAULT_DURATION_MS,
) {
    public companion object {
        public const val DEFAULT_DURATION_MS: Int = 300
        public const val MIN_DURATION_MS: Int = 50
        public const val MAX_DURATION_MS: Int = 5_000
    }
}

@Serializable
public enum class VisibilityTransition {
    @SerialName("fade")
    Fade,

    @SerialName("slideTop")
    SlideTop,

    @SerialName("slideBottom")
    SlideBottom,

    @SerialName("slideLeft")
    SlideLeft,

    @SerialName("slideRight")
    SlideRight,

    @SerialName("none")
    None,
}

internal object PaddingSerializer : KSerializer<Padding> {
    @Serializable
    private data class Sides(val start: Int = 0, val top: Int = 0, val end: Int = 0, val bottom: Int = 0)

    override val descriptor: SerialDescriptor = Sides.serializer().descriptor

    override fun deserialize(decoder: Decoder): Padding {
        val json = decoder as? JsonDecoder ?: throw SerializationException("padding: JSON only")
        return when (val element = json.decodeJsonElement()) {
            is JsonPrimitive -> Padding(
                element.intOrNull ?: throw SerializationException("padding: expected a number of dp or an object"),
            )
            is JsonObject -> json.json.decodeFromJsonElement(Sides.serializer(), element)
                .let { Padding(it.start, it.top, it.end, it.bottom) }
            else -> throw SerializationException("padding: expected a number of dp or {start, top, end, bottom}")
        }
    }

    override fun serialize(encoder: Encoder, value: Padding) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("padding: JSON only")
        val element: JsonElement = if (setOf(value.start, value.top, value.end, value.bottom).size == 1) {
            JsonPrimitive(value.start)
        } else {
            json.json.encodeToJsonElement(Sides.serializer(), Sides(value.start, value.top, value.end, value.bottom))
        }
        json.encodeJsonElement(element)
    }
}

internal object DimensionSerializer : KSerializer<Dimension> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Dimension", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Dimension {
        val json = decoder as? JsonDecoder ?: throw SerializationException("dimension: JSON only")
        val element = json.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("dimension: expected a number of dp, \"fill\" or \"wrap\"")
        return when {
            element.isString && element.content == "fill" -> Dimension.Fill
            element.isString && element.content == "wrap" -> Dimension.Wrap
            !element.isString && element.intOrNull != null -> Dimension.Dp(element.jsonPrimitive.int)
            else -> throw SerializationException(
                "dimension: expected a number of dp, \"fill\" or \"wrap\", was $element",
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Dimension) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("dimension: JSON only")
        json.encodeJsonElement(
            when (value) {
                is Dimension.Dp -> JsonPrimitive(value.value)
                Dimension.Fill -> JsonPrimitive("fill")
                Dimension.Wrap -> JsonPrimitive("wrap")
            },
        )
    }
}
