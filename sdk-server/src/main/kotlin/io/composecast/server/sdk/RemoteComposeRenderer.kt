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
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcTextOverflow
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.heightIn
import androidx.compose.remote.creation.dsl.horizontalCollapsiblePriority
import androidx.compose.remote.creation.dsl.horizontalScroll
import androidx.compose.remote.creation.dsl.marquee
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalCollapsiblePriority
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.dsl.widthIn
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import androidx.compose.remote.creation.profile.Profile
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Alignment
import io.composecast.protocol.Arrangement
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ButtonVariant
import io.composecast.protocol.Component
import io.composecast.protocol.ContentScale
import io.composecast.protocol.Dimension
import io.composecast.protocol.FontWeight
import io.composecast.protocol.Padding
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScrollDirection
import io.composecast.protocol.Shape
import io.composecast.protocol.TextAlign
import io.composecast.protocol.TextOverflow
import io.composecast.protocol.TextStyle
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import javax.imageio.ImageIO
import kotlin.math.roundToInt
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
    private val profiles = ConcurrentHashMap<Pair<Int, Int>, RcProfile>()

    fun render(screen: ScreenDefinition, apiLevel: Int = MAX_API_LEVEL, profiles: Int = DEFAULT_PROFILES): ByteArray {
        if (apiLevel < MIN_API_LEVEL) {
            throw UnsupportedClientVersionException(
                MIN_API_LEVEL,
                "Client RC API level $apiLevel is below minimum supported $MIN_API_LEVEL",
            )
        }
        val targetLevel = minOf(apiLevel, MAX_API_LEVEL)
        val targetProfiles = (profiles and DEFAULT_PROFILES).let { if (it == 0) DEFAULT_PROFILES else it }
        val profile = this.profiles.computeIfAbsent(targetLevel to targetProfiles) { (level, mask) ->
            RcProfile(
                Profile(level, mask, PlatformServices) { info, p, tag -> RemoteComposeWriter(info, null, p, tag) },
                false,
            )
        }
        return try {
            createRcBuffer(profile, *HEADER_TAGS) {
                Column(Modifier.fillMaxSize().background(background).verticalScroll().padding(SCREEN_PADDING)) {
                    renderChildren(screen.components, Slot(Axis.Vertical) { m, w -> m.weight(w) })
                }
            }
        } catch (e: Exception) {
            if (targetLevel < MAX_API_LEVEL) {
                throw UnsupportedClientVersionException(
                    targetLevel + 1,
                    "Screen '${screen.id}' requires a newer RC API level than $targetLevel: ${e.message}",
                    e,
                )
            }
            throw e
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
        defaultBackground: Int? = null,
        defaultCornerRadius: Float? = null,
        decorate: (Modifier) -> Modifier = { it },
    ): Modifier {
        var r: Modifier = Modifier
        m.weight?.let { weight -> r = slot.weigh?.invoke(r, weight) ?: r }
        m.collapsePriority?.let { p ->
            r = when (slot.axis) {
                Axis.Horizontal -> r.horizontalCollapsiblePriority(p)
                Axis.Vertical -> r.verticalCollapsiblePriority(p)
                Axis.Stack -> r
            }
        }
        r = if (m.fillFraction != null && m.width == null) {
            r.fillMaxWidth(m.fillFraction!!)
        } else {
            r.dimension(m.width ?: defaultWidth, horizontal = true)
        }
        r = r.dimension(m.height ?: defaultHeight, horizontal = false)
        if (m.minWidth != null || m.maxWidth != null) {
            r = r.widthIn(min = (m.minWidth ?: 0).toFloat(), max = m.maxWidth?.toFloat() ?: Float.MAX_VALUE)
        }
        if (m.minHeight != null || m.maxHeight != null) {
            r = r.heightIn(min = (m.minHeight ?: 0).toFloat(), max = m.maxHeight?.toFloat() ?: Float.MAX_VALUE)
        }
        val radius = if (m.shape == Shape.Circle) null else m.cornerRadius?.toFloat() ?: defaultCornerRadius
        if (radius != null) {
            r = r.clip(RoundedRectShape(radius, radius, radius, radius))
        } else if (m.shape == Shape.Circle) {
            // alpha23 drops CircleShape when writing ClipModifier (only Rect/RoundedRect are
            // encoded); an oversized corner radius is clamped by the canvas into a circle/pill.
            r = r.clip(RoundedRectShape(CIRCLE_RADIUS, CIRCLE_RADIUS, CIRCLE_RADIUS, CIRCLE_RADIUS))
        }
        (m.background?.let(::parseColor) ?: defaultBackground)?.let { r = r.background(it) }
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
        when (m.scroll) {
            ScrollDirection.Vertical -> r = r.verticalScroll()
            ScrollDirection.Horizontal -> r = r.horizontalScroll()
            null -> Unit
        }
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
                val (styleSize, styleWeight) = when (node.style) {
                    TextStyle.H1 -> 32 to RcFontWeight.W700
                    TextStyle.H2 -> 24 to RcFontWeight.W700
                    TextStyle.H3 -> 20 to RcFontWeight.W500
                    TextStyle.Body -> 16 to RcFontWeight.W400
                    TextStyle.Caption -> 12 to RcFontWeight.W400
                }
                Text(
                    node.text,
                    modifier = boxModel(node.modifier, slot) { m ->
                        if (node.marquee) {
                            m.marquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = 0,
                                repeatDelayMillis = MARQUEE_DELAY_MS,
                                initialDelayMillis = MARQUEE_DELAY_MS,
                                spacing = MARQUEE_SPACING_DP,
                                velocity = MARQUEE_VELOCITY_DP_S,
                            )
                        } else {
                            m
                        }
                    },
                    color = node.color?.let(::parseColor) ?: TEXT_COLOR,
                    fontSize = sp(node.fontSize ?: styleSize),
                    fontWeight = node.fontWeight?.toRc() ?: styleWeight,
                    textAlign = node.textAlign?.toRc() ?: RcTextAlign.Start,
                    overflow = node.overflow?.toRc() ?: RcTextOverflow.Clip,
                    maxLines = node.maxLines ?: Int.MAX_VALUE,
                )
            }

            is Component.Image -> renderImage(node, slot)

            is Component.Icon -> {
                val image = images.get(node.url, ::fetch)
                val sizeDp = Dimension.Dp(node.size)
                val modifier = boxModel(node.modifier, slot, defaultWidth = sizeDp, defaultHeight = sizeDp)
                if (image != null) {
                    Icon(
                        remoteNamedBitmap("$IMAGE_NAME_PREFIX${node.url}", image),
                        modifier = modifier,
                        contentDescription = node.alt.ifBlank { null },
                    )
                } else {
                    Text(
                        node.alt.ifBlank { node.url },
                        modifier = modifier,
                        color = PLACEHOLDER_COLOR,
                        fontSize = sp(12),
                    )
                }
            }

            is Component.Divider -> Box(
                modifier = boxModel(
                    node.modifier,
                    slot,
                    defaultWidth = Dimension.Fill,
                    defaultHeight = Dimension.Dp(node.thickness),
                    defaultBackground = node.color?.let(::parseColor) ?: DIVIDER_COLOR,
                ),
            ) {}

            is Component.Card -> Column(
                modifier = boxModel(
                    node.modifier,
                    slot,
                    defaultPadding = Padding(CARD_PADDING),
                    defaultBackground = SURFACE_COLOR,
                    defaultCornerRadius = CARD_RADIUS,
                ),
            ) {
                renderChildren(node.children, Slot(Axis.Vertical) { m, w -> m.weight(w) }, node.spacing)
            }

            is Component.Button -> renderButton(node, slot)

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

            is Component.Column -> if (node.collapsible) {
                CollapsibleColumn(
                    modifier = boxModel(node.modifier, slot),
                    horizontal = node.horizontalAlignment.horizontal(),
                    vertical = node.verticalArrangement.vertical(),
                ) {
                    renderChildren(node.children, Slot(Axis.Vertical) { m, w -> m.weight(w) }, node.spacing)
                }
            } else {
                Column(
                    modifier = boxModel(node.modifier, slot),
                    horizontal = node.horizontalAlignment.horizontal(),
                    vertical = node.verticalArrangement.vertical(),
                ) {
                    renderChildren(node.children, Slot(Axis.Vertical) { m, w -> m.weight(w) }, node.spacing)
                }
            }

            is Component.Row -> if (node.collapsible) {
                CollapsibleRow(
                    modifier = boxModel(node.modifier, slot),
                    horizontal = node.horizontalAlignment(),
                    vertical = node.verticalAlignment.vertical(),
                ) {
                    renderChildren(node.children, Slot(Axis.Horizontal) { m, w -> m.weight(w) }, node.spacing)
                }
            } else {
                Row(
                    modifier = boxModel(node.modifier, slot),
                    horizontal = node.horizontalAlignment(),
                    vertical = node.verticalAlignment.vertical(),
                ) {
                    renderChildren(node.children, Slot(Axis.Horizontal) { m, w -> m.weight(w) }, node.spacing)
                }
            }

            is Component.Box -> Box(
                modifier = boxModel(node.modifier, slot),
                horizontal = node.horizontalAlignment.horizontal(),
                vertical = node.verticalAlignment.vertical(),
            ) {
                renderChildren(node.children, Slot(Axis.Stack))
            }

            is Component.FitBox -> FitBox(
                modifier = boxModel(node.modifier, slot),
                horizontal = node.horizontalAlignment.horizontal(),
                vertical = node.verticalAlignment.vertical(),
            ) {
                renderChildren(node.children, Slot(Axis.Stack))
            }

            is Component.Flow -> Flow(
                modifier = boxModel(node.modifier, slot),
                horizontal = node.horizontalAlignment.horizontal(),
                vertical = node.verticalAlignment.vertical(),
                maxItemsInEachRow = node.maxItemsInEachRow ?: Int.MAX_VALUE,
                maxLines = node.maxLines ?: Int.MAX_VALUE,
            ) {
                renderChildren(node.children, Slot(Axis.Horizontal) { m, w -> m.weight(w) })
            }

            is Component.Grid -> Column(
                modifier = boxModel(node.modifier, slot),
            ) {
                val rows = node.children.chunked(node.columns)
                rows.forEachIndexed { rowIndex, rowItems ->
                    if (rowIndex > 0 && node.spacing > 0) {
                        Spacer(Modifier.height(node.spacing.toFloat()))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until node.columns) {
                            if (col > 0 && node.spacing > 0) {
                                Spacer(Modifier.width(node.spacing.toFloat()))
                            }
                            if (col < rowItems.size) {
                                Box(modifier = Modifier.weight(1f)) {
                                    renderNode(rowItems[col], Slot(Axis.Vertical))
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // BoundScreen expands repeats before rendering (T-006).
            is Component.Repeat -> error("repeat must be expanded before rendering")
        }
    }

    private fun RcScope.renderImage(node: Component.Image, slot: Slot) {
        val image = images.get(node.url, ::fetch)
        if (image == null) {
            Text(
                node.alt.ifBlank { node.url },
                modifier = boxModel(node.modifier, slot),
                color = PLACEHOLDER_COLOR,
                fontSize = sp(12),
            )
            return
        }
        val aspectRatio = node.aspectRatio
        val widthMod = node.modifier.width
        val heightMod = node.modifier.height
        val modifier = when {
            heightMod != null -> {
                val defaultWidth = if (widthMod == null && aspectRatio != null && heightMod is Dimension.Dp) {
                    Dimension.Dp((heightMod.value * aspectRatio).roundToInt())
                } else {
                    defaultWidth(slot)
                }
                boxModel(node.modifier, slot, defaultWidth = defaultWidth)
            }
            aspectRatio != null -> when {
                widthMod is Dimension.Dp -> boxModel(
                    node.modifier,
                    slot,
                    defaultHeight = Dimension.Dp((widthMod.value / aspectRatio).roundToInt()),
                )
                widthMod == Dimension.Wrap ||
                    (widthMod == null && slot.axis == Axis.Horizontal && node.modifier.weight == null) -> boxModel(
                    node.modifier,
                    slot,
                    defaultHeight = Dimension.Dp((image.width / aspectRatio).roundToInt()),
                )
                else -> {
                    val contentWidthDp = windowWidth() / density() - 2 * SCREEN_PADDING
                    boxModel(node.modifier, slot).height(written(contentWidthDp / aspectRatio))
                }
            }
            else -> {
                val ratio = image.height.toFloat() / image.width
                val contentWidthDp = windowWidth() / density() - 2 * SCREEN_PADDING
                boxModel(node.modifier, slot)
                    .height(written(min(contentWidthDp * ratio, image.height.toFloat())))
            }
        }
        Image(
            remoteNamedBitmap("$IMAGE_NAME_PREFIX${node.url}", image),
            modifier = modifier,
            contentDescription = node.alt.ifBlank { null },
            contentScale = node.contentScale?.toRc() ?: RcContentScale.Fit,
            alpha = node.alpha ?: 1f,
        )
    }

    private fun RcScope.renderButton(node: Component.Button, slot: Slot) {
        val (bg, contentColor, hasBorder) = when (node.variant) {
            ButtonVariant.Filled -> Triple(primary, ON_PRIMARY, false)
            ButtonVariant.Outlined -> Triple(null, primary, true)
            ButtonVariant.Text -> Triple(null, primary, false)
        }
        val iconImage = node.icon?.let { images.get(it, ::fetch) }
        Box(
            modifier = boxModel(
                node.modifier,
                slot,
                defaultPadding = Padding(BUTTON_PADDING),
                defaultBackground = bg,
                defaultCornerRadius = BUTTON_RADIUS,
            ) {
                var r = it
                if (hasBorder && node.modifier.border == null) {
                    r = r.border(1f, BUTTON_RADIUS, primary, ShapeType.ROUNDED_RECTANGLE)
                }
                if (node.modifier.action == null) {
                    r = r.onClick { hostAction(ActionCodec.encode(node.action)) }
                }
                r
            },
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            if (iconImage != null && node.icon != null) {
                Row(
                    horizontal = RcRowHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Icon(
                        remoteNamedBitmap("$IMAGE_NAME_PREFIX${node.icon}", iconImage),
                        modifier = Modifier.width(BUTTON_ICON_SIZE).height(BUTTON_ICON_SIZE),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(BUTTON_ICON_GAP))
                    Text(node.label, color = contentColor, fontSize = sp(16), fontWeight = RcFontWeight.W500)
                }
            } else {
                Text(node.label, color = contentColor, fontSize = sp(16), fontWeight = RcFontWeight.W500)
            }
        }
    }

    private fun FontWeight.toRc(): RcFontWeight = when (this) {
        FontWeight.Thin -> RcFontWeight.Thin
        FontWeight.ExtraLight -> RcFontWeight.ExtraLight
        FontWeight.Light -> RcFontWeight.Light
        FontWeight.Normal -> RcFontWeight.Normal
        FontWeight.Medium -> RcFontWeight.Medium
        FontWeight.SemiBold -> RcFontWeight.SemiBold
        FontWeight.Bold -> RcFontWeight.Bold
        FontWeight.ExtraBold -> RcFontWeight.ExtraBold
        FontWeight.Black -> RcFontWeight.Black
    }

    private fun TextAlign.toRc(): RcTextAlign = when (this) {
        TextAlign.Start -> RcTextAlign.Start
        TextAlign.Center -> RcTextAlign.Center
        TextAlign.End -> RcTextAlign.End
        TextAlign.Left -> RcTextAlign.Left
        TextAlign.Right -> RcTextAlign.Right
        TextAlign.Justify -> RcTextAlign.Justify
    }

    private fun TextOverflow.toRc(): RcTextOverflow = when (this) {
        TextOverflow.Clip -> RcTextOverflow.Clip
        TextOverflow.Visible -> RcTextOverflow.Visible
        TextOverflow.Ellipsis -> RcTextOverflow.Ellipsis
        TextOverflow.StartEllipsis -> RcTextOverflow.StartEllipsis
        TextOverflow.MiddleEllipsis -> RcTextOverflow.MiddleEllipsis
    }

    private fun ContentScale.toRc(): RcContentScale = when (this) {
        ContentScale.Fit -> RcContentScale.Fit
        ContentScale.Crop -> RcContentScale.Crop
        ContentScale.FillBounds -> RcContentScale.FillBounds
        ContentScale.FillWidth -> RcContentScale.FillWidth
        ContentScale.FillHeight -> RcContentScale.FillHeight
        ContentScale.Inside -> RcContentScale.Inside
        ContentScale.None -> RcContentScale.None
    }

    private fun Arrangement.vertical(): RcColumnVerticalPositioning = when (this) {
        Arrangement.Start -> RcColumnVerticalPositioning.Top
        Arrangement.Center -> RcColumnVerticalPositioning.Center
        Arrangement.End -> RcColumnVerticalPositioning.Bottom
        Arrangement.SpaceBetween -> RcColumnVerticalPositioning.SpaceBetween
        Arrangement.SpaceAround -> RcColumnVerticalPositioning.SpaceAround
        Arrangement.SpaceEvenly -> RcColumnVerticalPositioning.SpaceEvenly
    }

    private fun Component.Row.horizontalAlignment(): RcRowHorizontalPositioning = when (horizontalArrangement) {
        Arrangement.Start -> RcRowHorizontalPositioning.Start
        Arrangement.Center -> RcRowHorizontalPositioning.Center
        Arrangement.End -> RcRowHorizontalPositioning.End
        Arrangement.SpaceBetween -> RcRowHorizontalPositioning.SpaceBetween
        Arrangement.SpaceAround -> RcRowHorizontalPositioning.SpaceAround
        Arrangement.SpaceEvenly -> RcRowHorizontalPositioning.SpaceEvenly
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
        private const val DIVIDER_COLOR = 0xFFD0C8DC.toInt()
        private const val PLACEHOLDER_COLOR = 0xFF79747E.toInt()
        private const val ON_PRIMARY = 0xFFFFFFFF.toInt()
        private const val BUTTON_RADIUS = 12f
        private const val BUTTON_PADDING = 14
        private const val BUTTON_ICON_SIZE = 18f
        private const val BUTTON_ICON_GAP = 8f
        private const val CARD_RADIUS = 12f
        private const val CARD_PADDING = 16
        private const val MARQUEE_DELAY_MS = 1200f
        private const val MARQUEE_SPACING_DP = 24f
        private const val MARQUEE_VELOCITY_DP_S = 30f

        /** dp; larger than half of any component, so the rounded clip becomes a circle/pill. */
        private const val CIRCLE_RADIUS = 10_000f
        private const val IMAGE_NAME_PREFIX = "image:"
        private const val SCREEN_PADDING = 16f

        /**
         * `Header.DOC_DENSITY_BEHAVIOR` requires API level 8+ in RC alpha23
         * (`RemoteComposeWriter.filterAndValidateTags`), so 8 is the minimum supported level.
         */
        const val MIN_API_LEVEL = 8
        const val MAX_API_LEVEL = CoreDocument.DOCUMENT_API_LEVEL
        const val DEFAULT_PROFILES = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL

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
