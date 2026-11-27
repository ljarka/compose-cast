package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.cos
import kotlin.math.sin

/**
 * A custom component drawn in the admin's component designer (T-057, ADR 0034): shapes and texts
 * placed freely on an artboard, in dp. It is the editor's own source; [toBody] compiles it to the
 * RC JSON body that the rest of the pipeline (ADR 0033: whitelist, trial parse, publish,
 * re-validation) already handles.
 *
 * The component fills the screen's width and is [height] dp high. [width] is the artboard width the
 * owner designs on; on other widths each element follows its [DesignElement.anchor].
 */
@Serializable
data class ComponentDesign(
    val width: Float = DEFAULT_WIDTH,
    val height: Float = DEFAULT_HEIGHT,
    /** Fraction of the parent width (`0.1..1.0`): `1.0` = full width, `0.5` = half width. */
    val fillWidth: Float = 1f,
    /** Rounded corners of the whole component (dp), clipping everything inside. */
    val cornerRadius: Float = 0f,
    /** Bottom first: later elements draw over earlier ones. */
    val elements: List<DesignElement> = emptyList(),
) {
    /** Problems with the design itself; paths start with `design.`. */
    fun problems(): List<String> = buildList {
        if (width !in 40f..2000f) add("design.width: expected 40..2000 dp, was $width")
        if (height !in 8f..2000f) add("design.height: expected 8..2000 dp, was $height")
        if (fillWidth !in 0.1f..1f) add("design.fillWidth: expected 0.1..1.0, was $fillWidth")
        if (cornerRadius < 0f) add("design.cornerRadius: must be >= 0")
        if (elements.size > MAX_ELEMENTS) add("design.elements: at most $MAX_ELEMENTS, was ${elements.size}")
        val ids = mutableSetOf<String>()
        elements.forEachIndexed { i, e ->
            val at = "design.elements[$i]"
            if (!ids.add(e.id)) add("$at.id: '${e.id}' is used twice")
            // A line goes from (x, y) to (x + w, y + h): it may point up or left.
            if (e.type != DesignElementType.Line && (e.w < 0f || e.h < 0f)) add("$at: width and height must be >= 0")
            if (e.opacity !in 0f..1f) add("$at.opacity: expected 0..1, was ${e.opacity}")
            if (e.strokeWidth < 0f) add("$at.strokeWidth: must be >= 0")
            if (e.type == DesignElementType.Path && e.path.isNullOrBlank()) add("$at.path: a path needs path data")
            if (e.type == DesignElementType.Text && e.text == null) add("$at.text: a text needs a text")
            listOf(
                "fill" to e.fill,
                "darkFill" to e.darkFill,
                "fill2" to e.fill2,
                "darkFill2" to e.darkFill2,
                "stroke" to e.stroke,
                "darkStroke" to e.darkStroke,
            ).forEach { (name, c) ->
                if (c != null && !isPlaceholder(c)) colorProblem(c)?.let { add("$at.$name: $it") }
            }
            if (e.action != null && !isPlaceholder(e.action) && ActionCodec.decode(e.action) == null) {
                add("$at.action: expected an action in wire form (e.g. navigate:detail), was '${e.action}'")
            }
        }
    }

    /** The RC JSON body: a box of [height] dp with the elements as stacked layers (see class docs). */
    fun toBody(): JsonObject = buildJsonObject {
        put("type", "box")
        putJsonArray("modifiers") {
            add(obj("fillMaxWidth", if (fillWidth == 1f) JsonPrimitive(1) else JsonPrimitive(fillWidth)))
            add(obj("height", JsonPrimitive(height)))
            if (cornerRadius > 0f) add(obj("clip", JsonPrimitive(cornerRadius)))
        }
        putJsonArray("children") { layers().forEach { add(it) } }
    }

    /**
     * Consecutive shapes share one `canvas` (RC JSON canvases cannot hold children, and canvas
     * draw operations paint under any children anyway), texts are boxes between them, and elements
     * with an action get an invisible tap area on top of everything.
     */
    private fun layers(): List<JsonObject> = buildList {
        val shapes = mutableListOf<DesignElement>()
        fun flush() {
            if (shapes.isNotEmpty()) add(canvas(shapes.toList()))
            shapes.clear()
        }
        elements.filterNot { it.hidden }.forEach { e ->
            if (e.type == DesignElementType.Text) {
                flush()
                add(text(e))
            } else if (e.type != DesignElementType.Tap) {
                shapes += e
            }
        }
        flush()
        elements.filter { !it.hidden && it.action != null }.forEach { add(tapArea(it)) }
    }

    private fun canvas(shapes: List<DesignElement>) = buildJsonObject {
        put("type", "canvas")
        putJsonArray("modifiers") { add(obj("fillMaxSize", JsonPrimitive(1))) }
        putJsonArray("commands") { shapes.forEach { add(shape(it)) } }
    }

    /**
     * One shape inside `save` (restored after): move to the element's top left in pixels, scale by
     * density, then draw in the element's own dp box (0, 0, w, h). Canvas units are pixels (RC
     * JSON fact 9), so this keeps every number in the design in dp.
     */
    private fun shape(e: DesignElement): JsonObject = buildJsonObject {
        put("type", "save")
        putJsonArray("commands") {
            add(command("translate", "dx" to str(leftPx(e)), "dy" to str("${num(e.y)} * density")))
            add(command("scale", "sx" to str("density"), "sy" to str("density")))
            if (e.rotation != 0f) {
                add(
                    command(
                        "rotate",
                        "angle" to JsonPrimitive(e.rotation),
                        "pivotX" to localWidthHalf(e),
                        "pivotY" to JsonPrimitive(e.h / 2),
                    ),
                )
            }
            val pw = e.pathWidth
            val ph = e.pathHeight
            if (e.type == DesignElementType.Path && pw != null && ph != null && pw > 0f && ph > 0f) {
                add(command("scale", "sx" to JsonPrimitive(e.w / pw), "sy" to JsonPrimitive(e.h / ph)))
            }
            val right = localRight(e)
            e.fill?.let { fill ->
                add(
                    paint(
                        e,
                        color = themed(fill, e.darkFill),
                        style = "fill",
                        fill2 = e.fill2?.let { themed(it, e.darkFill2) },
                    ),
                )
                add(draw(e, right))
            }
            if (e.stroke != null && e.strokeWidth > 0f) {
                add(paint(e, color = themed(e.stroke, e.darkStroke), style = "stroke"))
                add(draw(e, right))
            }
        }
    }

    private fun paint(e: DesignElement, color: String, style: String, fill2: String? = null) = buildJsonObject {
        put("type", "paint")
        putJsonArray("ops") {
            add(obj("shader", JsonPrimitive(0)))
            add(obj("style", JsonPrimitive(style)))
            if (style == "stroke") {
                add(obj("strokeWidth", JsonPrimitive(e.strokeWidth)))
                add(obj("strokeCap", JsonPrimitive("round")))
            }
            add(obj("color", JsonPrimitive(color)))
            if (style == "fill" && fill2 != null) add(obj("linearGradient", gradient(e, color, fill2)))
            if (e.opacity < 1f) add(obj("alpha", JsonPrimitive(e.opacity)))
        }
    }

    /** From [from] to [to] across the element's box at [DesignElement.gradientAngle] (0 = left to right, 90 = top to bottom). */
    private fun gradient(e: DesignElement, from: String, to: String): JsonObject {
        // A scaled path paints in its own box (pathWidth × pathHeight).
        val w = e.pathWidth?.takeIf { e.type == DesignElementType.Path } ?: e.w
        val h = e.pathHeight?.takeIf { e.type == DesignElementType.Path } ?: e.h
        val rad = Math.toRadians(e.gradientAngle.toDouble())
        val dx = (cos(rad) * w / 2).toFloat()
        val dy = (sin(rad) * h / 2).toFloat()
        val cx = w / 2
        val cy = h / 2
        return buildJsonObject {
            putJsonArray("colors") {
                add(JsonPrimitive(from))
                add(JsonPrimitive(to))
            }
            put("x1", cx - dx)
            put("y1", cy - dy)
            put("x2", cx + dx)
            put("y2", cy + dy)
        }
    }

    private fun draw(e: DesignElement, right: JsonElement): JsonObject = when (e.type) {
        DesignElementType.Rect -> if (e.radius > 0f) {
            command(
                "drawRoundRect",
                "left" to JsonPrimitive(0),
                "top" to JsonPrimitive(0),
                "right" to right,
                "bottom" to JsonPrimitive(e.h),
                "rx" to JsonPrimitive(e.radius),
                "ry" to JsonPrimitive(e.radius),
            )
        } else {
            command(
                "drawRect",
                "left" to JsonPrimitive(0),
                "top" to JsonPrimitive(0),
                "right" to right,
                "bottom" to JsonPrimitive(e.h),
            )
        }
        DesignElementType.Ellipse ->
            command(
                "drawOval",
                "left" to JsonPrimitive(0),
                "top" to JsonPrimitive(0),
                "right" to right,
                "bottom" to JsonPrimitive(e.h),
            )
        DesignElementType.Line ->
            command(
                "drawLine",
                "x1" to JsonPrimitive(0),
                "y1" to JsonPrimitive(0),
                "x2" to right,
                "y2" to JsonPrimitive(e.h),
            )
        DesignElementType.Path -> command("drawPath", "path" to JsonPrimitive(e.path.orEmpty()))
        DesignElementType.Text, DesignElementType.Tap -> error("not a shape: ${e.type}")
    }

    /** The element's left edge in pixels, as an RC expression of the component's `width` (pixels). */
    private fun leftPx(e: DesignElement): String = when (e.anchor) {
        DesignAnchor.Left, DesignAnchor.Stretch -> "${num(e.x)} * density"
        DesignAnchor.Right -> "width - ${num(width - e.x)} * density"
        DesignAnchor.Center -> "width / 2 + ${num(e.x - width / 2)} * density"
    }

    /** The element's right edge in its own dp box: [DesignElement.w], or growing with the component. */
    private fun localRight(e: DesignElement): JsonElement =
        if (e.anchor == DesignAnchor.Stretch) str("width / density - ${num(width - e.w)}") else JsonPrimitive(e.w)

    private fun localWidthHalf(e: DesignElement): JsonElement = if (e.anchor ==
        DesignAnchor.Stretch
    ) {
        str("(width / density - ${num(width - e.w)}) / 2")
    } else {
        JsonPrimitive(e.w / 2)
    }

    /** A text: a full-size box aligned by the anchor, the text offset inside it to its place. */
    private fun text(e: DesignElement) = buildJsonObject {
        put("type", "box")
        put("horizontalAlignment", align(e.anchor))
        put("verticalAlignment", "top")
        putJsonArray("modifiers") { add(obj("fillMaxSize", JsonPrimitive(1))) }
        putJsonArray("children") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", e.text.orEmpty())
                    put("color", themed(e.fill ?: "#FF000000", e.darkFill))
                    put("fontSize", e.fontSize)
                    if (e.bold) put("fontWeight", 700)
                    put("textAlign", e.textAlign.json)
                    put("overflow", "ellipsis")
                    put("maxLines", e.maxLines)
                    putJsonArray("modifiers") { placement(e).forEach { add(it) } }
                },
            )
        }
    }

    /** An invisible box over the element that runs its action on tap. */
    private fun tapArea(e: DesignElement) = buildJsonObject {
        put("type", "box")
        put("horizontalAlignment", align(e.anchor))
        put("verticalAlignment", "top")
        putJsonArray("modifiers") { add(obj("fillMaxSize", JsonPrimitive(1))) }
        putJsonArray("children") {
            add(
                // The click covers its box's whole bounds (padding included), so the padding
                // is on an outer box and the click on an inner one of exactly the element's size.
                buildJsonObject {
                    put("type", "box")
                    putJsonArray("modifiers") { placement(e).forEach { add(it) } }
                    putJsonArray("children") {
                        add(
                            buildJsonObject {
                                put("type", "box")
                                putJsonArray("modifiers") {
                                    add(obj("fillMaxWidth", JsonPrimitive(1)))
                                    add(obj("height", JsonPrimitive(e.h)))
                                    add(
                                        obj(
                                            "onClick",
                                            buildJsonObject {
                                                put("type", "hostAction")
                                                put("name", e.action!!)
                                            },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    /**
     * Padding + width of a child in a full-size box aligned by [DesignElement.anchor]. Padding, not
     * `offset`: RC alpha23 moves the drawing by `offset` but not the touch area (RC JSON fact 14).
     * Texts and tap areas cannot start above the top or outside the sides (padding >= 0).
     */
    private fun placement(e: DesignElement): List<JsonObject> {
        val center = e.x + e.w / 2 - width / 2
        val (start, end) = when (e.anchor) {
            DesignAnchor.Left -> e.x to 0f
            DesignAnchor.Right -> 0f to width - e.x - e.w
            DesignAnchor.Center -> if (center >= 0f) 2 * center to 0f else 0f to -2 * center
            DesignAnchor.Stretch -> e.x to width - e.x - e.w
        }
        val padding = obj(
            "padding",
            buildJsonObject {
                put("start", start.coerceAtLeast(0f))
                put("top", e.y.coerceAtLeast(0f))
                put("end", end.coerceAtLeast(0f))
            },
        )
        val size = if (e.anchor == DesignAnchor.Stretch) {
            obj("fillMaxWidth", JsonPrimitive(1))
        } else {
            obj("width", JsonPrimitive(e.w))
        }
        return listOf(padding, size)
    }

    private fun align(anchor: DesignAnchor) = when (anchor) {
        DesignAnchor.Left, DesignAnchor.Stretch -> "start"
        DesignAnchor.Right -> "end"
        DesignAnchor.Center -> "center"
    }

    companion object {
        const val DEFAULT_WIDTH = 360f
        const val DEFAULT_HEIGHT = 160f
        const val MAX_ELEMENTS = 200

        private fun isPlaceholder(value: String) = ComponentDefinition.wholePlaceholder(value) != null

        /** Encodes a light/dark color pair for the server's RC themed color allocator. */
        private fun themed(light: String, dark: String?): String =
            if (dark != null) "{{themed:$light|$dark}}" else light

        private fun obj(key: String, value: JsonElement) = JsonObject(mapOf(key to value))

        private fun str(value: String) = JsonPrimitive(value)

        private fun command(
            type: String,
            vararg fields: Pair<String, JsonElement>,
            transform: (JsonObject) -> JsonObject = { it },
        ) = transform(JsonObject(mapOf("type" to JsonPrimitive(type)) + fields))

        /** A number for an RC expression: no trailing `.0`, no exponent. */
        private fun num(value: Float): String =
            if (value == value.toLong().toFloat()) value.toLong().toString() else value.toBigDecimal().toPlainString()
    }
}

@Serializable
data class DesignElement(
    val id: String,
    val type: DesignElementType,
    /** Top left corner and size on the artboard, in dp. */
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    val anchor: DesignAnchor = DesignAnchor.Left,
    /** Fill color (`#RRGGBB`, `#AARRGGBB`, `{{params.x}}`, `{{brand.x}}`); for a text: its color. */
    val fill: String? = null,
    /** Optional dark-mode override for [fill] (null = same as [fill], or dark brand color for `{{brand.x}}`). */
    val darkFill: String? = null,
    /** With [fill]: a linear gradient from [fill] to this color. */
    val fill2: String? = null,
    /** Optional dark-mode override for [fill2]. */
    val darkFill2: String? = null,
    /** Gradient direction in degrees: 0 = left to right, 90 = top to bottom. */
    val gradientAngle: Float = 90f,
    val stroke: String? = null,
    /** Optional dark-mode override for [stroke]. */
    val darkStroke: String? = null,
    val strokeWidth: Float = 0f,
    /** Rounded corners of a rectangle (dp). */
    val radius: Float = 0f,
    val opacity: Float = 1f,
    /** Degrees clockwise around the element's center (shapes). */
    val rotation: Float = 0f,
    /** SVG path data in the element's own dp box (absolute `M L H C S Q A Z`, RC fact 10). */
    val path: String? = null,
    /**
     * The box [path] was drawn in (e.g. an imported icon's 24×24 viewBox); the path is scaled from
     * it to [w]×[h]. Null = the path's numbers are already dp in the element's box.
     */
    val pathWidth: Float? = null,
    val pathHeight: Float? = null,
    val text: String? = null,
    val fontSize: Float = 16f,
    val bold: Boolean = false,
    val textAlign: DesignTextAlign = DesignTextAlign.Start,
    val maxLines: Int = 1,
    /** Tapping the element runs this action (wire form or `{{params.x}}`). */
    val action: String? = null,
    /** Kept in the design, not drawn. */
    val hidden: Boolean = false,
    /** Name shown in the editor's layer list. */
    val name: String = "",
)

@Serializable
enum class DesignElementType {
    @SerialName("rect")
    Rect,

    @SerialName("ellipse")
    Ellipse,

    @SerialName("line")
    Line,

    @SerialName("path")
    Path,

    @SerialName("text")
    Text,

    /** An invisible area that only runs [DesignElement.action]. */
    @SerialName("tap")
    Tap,
}

/** How an element follows the component's width when it differs from the artboard's. */
@Serializable
enum class DesignAnchor {
    /** Keeps its distance from the left edge. */
    @SerialName("left")
    Left,

    /** Keeps its distance from the right edge. */
    @SerialName("right")
    Right,

    /** Keeps its distance from the center. */
    @SerialName("center")
    Center,

    /** Keeps both distances: grows with the component. */
    @SerialName("stretch")
    Stretch,
}

@Serializable
enum class DesignTextAlign(val json: String) {
    @SerialName("start")
    Start("start"),

    @SerialName("center")
    Center("center"),

    @SerialName("end")
    End("end"),
}
