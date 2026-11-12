package io.composecast.server.sdk

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.layout.modifiers.ShapeType
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcSp
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import androidx.compose.remote.creation.profile.Profile
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Alignment
import io.composecast.protocol.Arrangement
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.Component
import io.composecast.protocol.Dimension
import io.composecast.protocol.Padding
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.Shape
import io.composecast.protocol.TextStyle
import java.io.ByteArrayInputStream
import java.util.logging.Logger
import javax.imageio.ImageIO
import io.composecast.protocol.Modifier as CcModifier

/**
 * The only place in compose-cast that talks to the Remote Compose creation API.
 *
 * As of remote-compose 1.0.0-alpha20 the whole creation API is `@RestrictTo(LIBRARY_GROUP)`
 * and may change between alphas, so it is deliberately kept behind this one internal class.
 */
internal class RemoteComposeRenderer(brand: BrandConfig?, private val imageLoader: ImageLoader) {
    private val primary = parseColor(brand?.primaryColor ?: DEFAULT_PRIMARY)
    private val background = parseColor(brand?.backgroundColor ?: DEFAULT_BACKGROUND)

    /** Images fetched so far, shared by all (concurrent) renders of one registry. */
    private val images = ImageCache<EncodedImage>()

    fun render(screen: ScreenDefinition): ByteArray = createRcBuffer(PROFILE, *HEADER_TAGS) {
        Column(Modifier.fillMaxSize().background(background).verticalScroll().padding(SCREEN_PADDING)) {
            renderChildren(screen.components, Slot(Axis.Vertical) { m, w -> m.weight(w) })
        }
    }

    /** Direction of the list a node is rendered in; [weigh] applies `weight` (rows/columns only). */
    private enum class Axis { Vertical, Horizontal, Stack }

    private class Slot(val axis: Axis, val weigh: ((Modifier, Float) -> Modifier)? = null)

    private fun RcScope.renderChildren(children: List<Component>, slot: Slot, spacing: Int = 0) {
        children.forEachIndexed { i, child ->
            if (i > 0 && spacing > 0) {
                Spacer(
                    when (slot.axis) {
                        Axis.Horizontal -> Modifier.width(spacing.toFloat())
                        Axis.Vertical, Axis.Stack -> Modifier.height(spacing.toFloat())
                    },
                )
            }
            renderNode(child, slot)
        }
    }

    /**
     * The box model of ADR 0020 as a fixed RC modifier chain: weight → width → height → clip →
     * background → border → click → [decorate] (component's own styling) → padding. Alpha23 sums
     * all paddings into the content inset and keeps the first width/height, so the order only
     * matters for painting: decorations before the padding cover the whole size.
     */
    private fun RcScope.boxModel(
        m: CcModifier,
        slot: Slot,
        defaultWidth: Dimension? = defaultWidth(slot),
        defaultHeight: Dimension? = null,
        defaultPadding: Padding? = null,
        decorate: (Modifier) -> Modifier = { it },
    ): Modifier {
        var r: Modifier = Modifier
        m.weight?.let { weight -> r = slot.weigh?.invoke(r, weight) ?: r }
        r = r.dimension(m.width ?: defaultWidth, horizontal = true)
        r = r.dimension(m.height ?: defaultHeight, horizontal = false)
        val radius = m.cornerRadius?.toFloat()
        if (radius != null) {
            r = r.clip(RoundedRectShape(radius, radius, radius, radius))
        } else if (m.shape == Shape.Circle) {
            // alpha23 drops CircleShape when writing ClipModifier (only Rect/RoundedRect are
            // encoded); an oversized corner radius is clamped by the canvas into a circle/pill.
            r = r.clip(RoundedRectShape(CIRCLE_RADIUS, CIRCLE_RADIUS, CIRCLE_RADIUS, CIRCLE_RADIUS))
        }
        m.background?.let { r = r.background(parseColor(it)) }
        m.border?.let { border ->
            val shape = when {
                radius != null -> ShapeType.ROUNDED_RECTANGLE
                m.shape == Shape.Circle -> ShapeType.CIRCLE
                else -> ShapeType.RECTANGLE
            }
            r = r.border(border.width.toFloat(), radius ?: 0f, parseColor(border.color), shape)
        }
        m.action?.let { action -> r = r.onClick { hostAction(ActionCodec.encode(action)) } }
        r = decorate(r)
        (m.padding ?: defaultPadding)?.let { p ->
            r =
                r.padding(
                    start = p.start.toFloat(),
                    top = p.top.toFloat(),
                    end = p.end.toFloat(),
                    bottom = p.bottom.toFloat(),
                )
        }
        return r
    }

    private fun Modifier.dimension(d: Dimension?, horizontal: Boolean): Modifier = when (d) {
        null, Dimension.Wrap -> this
        Dimension.Fill -> if (horizontal) fillMaxWidth() else fillMaxHeight()
        is Dimension.Dp -> if (horizontal) width(d.value.toFloat()) else height(d.value.toFloat())
    }

    /** Children of columns (and the screen) fill the width by default, children of rows wrap. */
    private fun defaultWidth(slot: Slot): Dimension = if (slot.axis ==
        Axis.Horizontal
    ) {
        Dimension.Wrap
    } else {
        Dimension.Fill
    }

    private fun RcScope.renderNode(node: Component, slot: Slot) {
        when (node) {
            is Component.Text -> {
                val (size, weight) = when (node.style) {
                    TextStyle.H1 -> 32 to RcFontWeight.W700
                    TextStyle.H2 -> 24 to RcFontWeight.W700
                    TextStyle.H3 -> 20 to RcFontWeight.W500
                    TextStyle.Body -> 16 to RcFontWeight.W400
                    TextStyle.Caption -> 12 to RcFontWeight.W400
                }
                Text(
                    node.text,
                    modifier = boxModel(node.modifier, slot),
                    color = TEXT_COLOR,
                    fontSize = sp(size),
                    fontWeight = weight,
                )
            }

            is Component.Image -> {
                val image = images.get(node.url, ::fetch)
                if (image != null) {
                    // Without a height: the screen's content width, keeping aspect ratio, but never
                    // upscaled past the natural size (pixels treated as dp). Height is a
                    // device-side expression in dp (the player multiplies heights by density in
                    // DP mode). Only right at full width — nested images should set a height.
                    val ratio = image.height.toFloat() / image.width
                    val contentWidthDp = windowWidth() / density() - 2 * SCREEN_PADDING
                    val modifier = if (node.modifier.height != null) {
                        boxModel(node.modifier, slot)
                    } else {
                        boxModel(node.modifier, slot)
                            .height(written(min(contentWidthDp * ratio, image.height.toFloat())))
                    }
                    Image(
                        remoteNamedBitmap("$IMAGE_NAME_PREFIX${node.url}", image),
                        modifier = modifier,
                        contentDescription = node.alt.ifBlank { null },
                        contentScale = RcContentScale.Fit,
                    )
                } else {
                    // Keep the screen usable when an image cannot be fetched.
                    Text(
                        node.alt.ifBlank { node.url },
                        modifier = boxModel(node.modifier, slot),
                        color = PLACEHOLDER_COLOR,
                        fontSize = sp(12),
                    )
                }
            }

            is Component.Button -> Box(
                modifier = boxModel(node.modifier, slot, defaultPadding = Padding(BUTTON_PADDING)) {
                    it.clip(RoundedRectShape(BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS))
                        .background(primary)
                        .onClick { hostAction(ActionCodec.encode(node.action)) }
                },
                horizontal = RcHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                Text(node.label, color = ON_PRIMARY, fontSize = sp(16), fontWeight = RcFontWeight.W500)
            }

            is Component.Spacer -> {
                val size = Dimension.Dp(node.height)
                Spacer(
                    if (slot.axis == Axis.Horizontal) {
                        boxModel(node.modifier, slot, defaultWidth = size)
                    } else {
                        boxModel(node.modifier, slot, defaultWidth = null, defaultHeight = size)
                    },
                )
            }

            is Component.Expandable -> renderExpandable(node, slot)

            is Component.Column -> Column(
                modifier = boxModel(node.modifier, slot),
                horizontal = node.horizontalAlignment.horizontal(),
                vertical = when (node.verticalArrangement) {
                    Arrangement.Start -> RcColumnVerticalPositioning.Top
                    Arrangement.Center -> RcColumnVerticalPositioning.Center
                    Arrangement.End -> RcColumnVerticalPositioning.Bottom
                    Arrangement.SpaceBetween -> RcColumnVerticalPositioning.SpaceBetween
                    Arrangement.SpaceAround -> RcColumnVerticalPositioning.SpaceAround
                    Arrangement.SpaceEvenly -> RcColumnVerticalPositioning.SpaceEvenly
                },
            ) {
                renderChildren(node.children, Slot(Axis.Vertical) { m, w -> m.weight(w) }, node.spacing)
            }

            is Component.Row -> Row(
                modifier = boxModel(node.modifier, slot),
                horizontal = when (node.horizontalArrangement) {
                    Arrangement.Start -> RcRowHorizontalPositioning.Start
                    Arrangement.Center -> RcRowHorizontalPositioning.Center
                    Arrangement.End -> RcRowHorizontalPositioning.End
                    Arrangement.SpaceBetween -> RcRowHorizontalPositioning.SpaceBetween
                    Arrangement.SpaceAround -> RcRowHorizontalPositioning.SpaceAround
                    Arrangement.SpaceEvenly -> RcRowHorizontalPositioning.SpaceEvenly
                },
                vertical = node.verticalAlignment.vertical(),
            ) {
                renderChildren(node.children, Slot(Axis.Horizontal) { m, w -> m.weight(w) }, node.spacing)
            }

            is Component.Box -> Box(
                modifier = boxModel(node.modifier, slot),
                horizontal = node.horizontalAlignment.horizontal(),
                vertical = node.verticalAlignment.vertical(),
            ) {
                renderChildren(node.children, Slot(Axis.Stack))
            }

            // BoundScreen expands repeats before rendering (T-006).
            is Component.Repeat -> error("repeat must be expanded before rendering")
        }
    }

    private fun Alignment.horizontal(): RcHorizontalPositioning = when (this) {
        Alignment.Start -> RcHorizontalPositioning.Start
        Alignment.Center -> RcHorizontalPositioning.Center
        Alignment.End -> RcHorizontalPositioning.End
    }

    private fun Alignment.vertical(): RcVerticalPositioning = when (this) {
        Alignment.Start -> RcVerticalPositioning.Top
        Alignment.Center -> RcVerticalPositioning.Center
        Alignment.End -> RcVerticalPositioning.Bottom
    }

    private fun RcScope.renderExpandable(node: Component.Expandable, slot: Slot) {
        // StateLayout always measures and paints child 0 on the first pass (it reads its state id
        // only in paint()), and does not run ComponentData children placed directly inside it.
        // Allocate the state integer in the enclosing scope initialized to 0, and emit the initial
        // branch as child 0 and the toggled branch as child 1.
        val state = remoteInteger(0)

        fun RcScope.collapsedBranch(nextState: Int) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectShape(BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS))
                    .background(SURFACE_COLOR)
                    .onClick { setValue(state, nextState) }
                    .padding(14f),
                vertical = RcVerticalPositioning.Center,
            ) {
                Text(
                    node.title,
                    modifier = Modifier.weight(1f),
                    color = TEXT_COLOR,
                    fontSize = sp(16),
                    fontWeight = RcFontWeight.W500,
                )
                Text("+", color = primary, fontSize = sp(18), fontWeight = RcFontWeight.W700)
            }
        }

        fun RcScope.expandedBranch(nextState: Int) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectShape(BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS))
                    .background(SURFACE_COLOR),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onClick { setValue(state, nextState) }
                        .padding(14f),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Text(
                        node.title,
                        modifier = Modifier.weight(1f),
                        color = TEXT_COLOR,
                        fontSize = sp(16),
                        fontWeight = RcFontWeight.W500,
                    )
                    Text("−", color = primary, fontSize = sp(18), fontWeight = RcFontWeight.W700)
                }
                Column(modifier = Modifier.fillMaxWidth().padding(start = 14f, end = 14f, bottom = 14f)) {
                    renderChildren(node.content, Slot(Axis.Vertical) { m, w -> m.weight(w) })
                }
            }
        }

        // StateLayout.paint() in alpha23 overrides Component.paint() without translating by its
        // own (mX, mY), so a StateLayout placed directly in a Column paints at (0, 0) of the
        // Column. Wrapping it in a Box puts the vertical offset on the Box (which translates
        // properly) while StateLayout sits at (0, 0) inside the Box.
        Box(modifier = boxModel(node.modifier, slot)) {
            StateLayout(state, modifier = Modifier.fillMaxWidth()) {
                if (node.expanded) {
                    expandedBranch(nextState = 1)
                    collapsedBranch(nextState = 0)
                } else {
                    collapsedBranch(nextState = 1)
                    expandedBranch(nextState = 0)
                }
            }
        }
    }

    /*
     * Units (alpha23). With DENSITY_BEHAVIOR_DP the player multiplies padding, borders, clip radii
     * and width/height (constants *and* expressions) by density, so sizes are written in dp.
     * Font sizes: constants are multiplied by density only (no font scale), expressions not at all
     * -> fonts use the sp() expression below to honor the user's font scale.
     */

    /**
     * Writes [value]'s expression to the document now. Workaround (still needed in alpha23): the
     * height(RcFloat) modifier reads the expression id without writing it, giving
     * `Height(EXACT, 0.0)`.
     */
    private fun written(value: RcFloat): RcFloat = value.also { it.toFloat() }

    /**
     * [value] sp as a font size. The player sets FONT_SIZE to 14sp in pixels (density and the
     * user's font scale applied), so `FONT_SIZE * value / 14` honors accessibility font scaling.
     */
    private fun RcScope.sp(value: Int): RcSp = RcSp((fontSize() * (value / 14f)).toFloat())

    private fun fetch(url: String): EncodedImage? = try {
        val bytes = imageLoader.load(url)
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
            ?: error("unsupported image format (supported: PNG, JPEG, GIF, BMP)")
        EncodedImage(bytes, decoded.width, decoded.height)
    } catch (e: Exception) {
        log.warning("Image '$url' not embedded, showing placeholder: ${e.message}")
        null
    }

    /** Original encoded bytes plus size; embedded as-is, so JPEGs are not re-encoded as PNG. */
    private class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)

    /** JVM platform services that understand [EncodedImage]. */
    private object PlatformServices : RcPlatformServices by JvmRcPlatformServices() {
        private val jvm = JvmRcPlatformServices()
        override fun imageToByteArray(image: Any): ByteArray? =
            (image as? EncodedImage)?.bytes ?: jvm.imageToByteArray(image)
        override fun getImageWidth(image: Any): Int = (image as? EncodedImage)?.width ?: jvm.getImageWidth(image)
        override fun getImageHeight(image: Any): Int = (image as? EncodedImage)?.height ?: jvm.getImageHeight(image)
        override fun isAlpha8Image(image: Any): Boolean = if (image is EncodedImage) false else jvm.isAlpha8Image(image)
    }

    companion object {
        private val log = Logger.getLogger(RemoteComposeRenderer::class.java.name)

        private const val DEFAULT_PRIMARY = "#6200EE"
        private const val DEFAULT_BACKGROUND = "#FFFFFF"
        private const val TEXT_COLOR = 0xFF1C1B1F.toInt()
        private const val SURFACE_COLOR = 0xFFF3EDF7.toInt()
        private const val PLACEHOLDER_COLOR = 0xFF79747E.toInt()
        private const val ON_PRIMARY = 0xFFFFFFFF.toInt()
        private const val BUTTON_RADIUS = 12f
        private const val BUTTON_PADDING = 14

        /** dp; larger than half of any component, so the rounded clip becomes a circle/pill. */
        private const val CIRCLE_RADIUS = 10_000f
        private const val IMAGE_NAME_PREFIX = "image:"
        private const val SCREEN_PADDING = 16f

        private val PROFILE = RcProfile(
            Profile(
                CoreDocument.DOCUMENT_API_LEVEL,
                RcProfiles.PROFILE_ANDROIDX,
                PlatformServices,
            ) { info, profile, tag -> RemoteComposeWriter(info, null, profile, tag) },
            false,
        )

        /** All sizes in the document are dp/sp; without this the player mixes pixels and dp. */
        private val HEADER_TAGS = arrayOf(
            RemoteComposeWriter.HTag(Header.DOC_DENSITY_BEHAVIOR, CoreDocument.DENSITY_BEHAVIOR_DP),
        )

        /** Decodes a document with the Remote Compose core reader and dumps its operation tree. */
        fun describe(bytes: ByteArray): String {
            val document = CoreDocument()
            document.initFromBuffer(RemoteComposeBuffer.fromInputStream(bytes.inputStream()))
            return document.toNestedString()
        }

        /** Parses `#RRGGBB` or `#AARRGGBB` into an ARGB int. */
        internal fun parseColor(hex: String): Int {
            val digits = hex.removePrefix("#")
            val argb = when (digits.length) {
                6 -> "FF$digits"
                8 -> digits
                else -> throw IllegalArgumentException("Invalid color '$hex', expected #RRGGBB or #AARRGGBB")
            }
            return argb.toLong(16).toInt()
        }
    }
}
