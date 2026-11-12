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
data class Modifier(
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
) {
    val isEmpty: Boolean get() = this == NONE

    companion object {
        val NONE = Modifier()
    }
}

/** Padding in dp per side; JSON `8` means all four sides. */
@Serializable(with = PaddingSerializer::class)
data class Padding(val start: Int = 0, val top: Int = 0, val end: Int = 0, val bottom: Int = 0) {
    constructor(all: Int) : this(all, all, all, all)
}

/** A width or height: exact dp, fill the available space, or wrap the content. */
@Serializable(with = DimensionSerializer::class)
sealed interface Dimension {
    data class Dp(val value: Int) : Dimension

    data object Fill : Dimension

    data object Wrap : Dimension
}

@Serializable
data class Border(val width: Int = 1, val color: String)

@Serializable
enum class Shape {
    @SerialName("circle")
    Circle,
}

/** Placement along an axis; for vertical axes `start` = top, `end` = bottom. */
@Serializable
enum class Alignment {
    @SerialName("start")
    Start,

    @SerialName("center")
    Center,

    @SerialName("end")
    End,
}

/** Distribution of children along a row's/column's main axis; `start` = top in a column. */
@Serializable
enum class Arrangement {
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
