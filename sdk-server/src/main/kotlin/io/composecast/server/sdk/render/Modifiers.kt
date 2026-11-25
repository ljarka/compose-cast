package io.composecast.server.sdk.render

import androidx.compose.remote.core.operations.layout.modifiers.ShapeType
import androidx.compose.remote.core.semantics.AccessibleComponent
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcSp
import androidx.compose.remote.creation.dsl.animationSpec
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.heightIn
import androidx.compose.remote.creation.dsl.horizontalCollapsiblePriority
import androidx.compose.remote.creation.dsl.horizontalScroll
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.semantics
import androidx.compose.remote.creation.dsl.verticalCollapsiblePriority
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.dsl.widthIn
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Dimension
import io.composecast.protocol.Padding
import io.composecast.protocol.ScrollDirection
import io.composecast.protocol.Shape
import io.composecast.protocol.VisibilityTransition
import io.composecast.server.sdk.render.RemoteComposeRenderer.Companion.parseColor
import androidx.compose.remote.core.operations.layout.Component as RcComponent
import io.composecast.protocol.Modifier as CcModifier

/** Direction of the list a node is rendered in; [Slot.weigh] applies `weight` (rows/columns only). */
internal enum class Axis { Vertical, Horizontal, Stack }

internal class Slot(val axis: Axis, val weigh: ((Modifier, Float) -> Modifier)? = null)

/**
 * The box model of ADR 0020 as a fixed RC modifier chain: weight → width → height → clip →
 * background → border → click → semantics → [decorate] (component's own styling) → scroll →
 * padding. Alpha23 sums all paddings into the content inset and keeps the first width/height, so
 * the order only matters for painting: decorations before the padding cover the whole size.
 */
internal fun RcScope.boxModel(
    env: RenderEnv,
    m: CcModifier,
    slot: Slot,
    defaultWidth: Dimension? = defaultWidth(slot),
    defaultHeight: Dimension? = null,
    defaultPadding: Padding? = null,
    defaultBackground: Int? = null,
    defaultCornerRadius: Float? = null,
    defaultRole: AccessibleComponent.Role? = null,
    defaultClickable: Boolean = false,
    decorate: (Modifier) -> Modifier = { it },
): Modifier {
    var r: Modifier = Modifier
    m.visibility?.let { vis ->
        env.visibility[vis.id]?.let { bound ->
            r = r.visibility(bound.state)
            r = if (vis.transition == VisibilityTransition.None) {
                r.animationSpec(animationId = 0)
            } else {
                val (enter, exit) = vis.transition.toRc()
                r.animationSpec(
                    motionDuration = vis.durationMs.toFloat(),
                    visibilityDuration = vis.durationMs.toFloat(),
                    enterAnimation = enter,
                    exitAnimation = exit,
                )
            }
        }
    }
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
    m.action?.let { action -> r = r.applyAction(env, action) }
    r = r.semantics(m, defaultRole, hasAction = m.action != null || defaultClickable)
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

/** Applies [action] as a click handler: on-device RC state mutations stay local, others call the host. */
internal fun Modifier.applyAction(env: RenderEnv, action: Action): Modifier = when (action) {
    is Action.Toggle -> env.visibility[action.target]?.let { v ->
        onClick { setValue(v.state, v.toggled) }
    } ?: this
    is Action.Show -> env.visibility[action.target]?.let { v ->
        onClick { setValue(v.state, RcComponent.Visibility.VISIBLE) }
    } ?: this
    is Action.Hide -> env.visibility[action.target]?.let { v ->
        onClick { setValue(v.state, RcComponent.Visibility.GONE) }
    } ?: this
    else -> onClick { hostAction(ActionCodec.encode(action)) }
}

/** Accessibility semantics (T-016): hidden → cleared; otherwise description, state, role, clickable. */
private fun Modifier.semantics(m: CcModifier, defaultRole: AccessibleComponent.Role?, hasAction: Boolean): Modifier {
    val s = m.semantics
    return if (s?.hidden == true) {
        semantics { mode(AccessibleComponent.Mode.CLEAR_AND_SET) }
    } else if (s != null || hasAction || defaultRole != null) {
        semantics {
            s?.description?.let { contentDescription(it) }
            s?.stateDescription?.let { stateDescription(it) }
            s?.enabled?.let { enabled(it) }
            val role = s?.role?.toRc() ?: defaultRole ?: if (hasAction) AccessibleComponent.Role.BUTTON else null
            role?.let { role(it) }
            if (hasAction) clickable(true)
        }
    } else {
        this
    }
}

/** The content description of an image/icon: none when hidden, else semantics, else alt text. */
internal fun CcModifier.imageDescription(alt: String): String? =
    if (semantics?.hidden == true) null else semantics?.description ?: alt.ifBlank { null }

private fun Modifier.dimension(d: Dimension?, horizontal: Boolean): Modifier = when (d) {
    null, Dimension.Wrap -> this
    Dimension.Fill -> if (horizontal) fillMaxWidth() else fillMaxHeight()
    is Dimension.Dp -> if (horizontal) width(d.value.toFloat()) else height(d.value.toFloat())
}

/** Children of columns (and the screen) fill the width by default, children of rows wrap. */
internal fun defaultWidth(slot: Slot): Dimension = if (slot.axis == Axis.Horizontal) Dimension.Wrap else Dimension.Fill

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
internal fun written(value: RcFloat): RcFloat = value.also { it.toFloat() }

/**
 * [value] sp as a font size. The player sets FONT_SIZE to 14sp in pixels (density and the
 * user's font scale applied), so `FONT_SIZE * value / 14` honors accessibility font scaling.
 */
internal fun RcScope.sp(value: Int): RcSp = RcSp((fontSize() * (value / 14f)).toFloat())
