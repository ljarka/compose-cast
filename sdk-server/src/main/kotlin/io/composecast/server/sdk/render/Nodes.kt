package io.composecast.server.sdk.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcTextOverflow
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.marquee
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import io.composecast.protocol.Component
import io.composecast.protocol.Dimension
import io.composecast.protocol.TextStyle
import io.composecast.server.sdk.render.RemoteComposeRenderer.Companion.parseColor

/** Renders [children] in order, with [spacing] dp spacers between them along the slot's axis. */
internal fun RcScope.renderChildren(env: RenderEnv, children: List<Component>, slot: Slot, spacing: Int = 0) {
    children.forEachIndexed { i, child ->
        if (i > 0 && spacing > 0) {
            val baseSpacer = when (slot.axis) {
                Axis.Horizontal -> Modifier.width(spacing.toFloat())
                Axis.Vertical, Axis.Stack -> Modifier.height(spacing.toFloat())
            }
            val visState = child.modifier.visibility?.id?.let { env.visibility[it]?.state }
            Spacer(if (visState != null) baseSpacer.visibility(visState) else baseSpacer)
        }
        renderNode(env, child, slot)
    }
}

/** One component → RC operations. Groups live in Containers.kt, Images.kt and Interactive.kt. */
internal fun RcScope.renderNode(env: RenderEnv, node: Component, slot: Slot) {
    when (node) {
        is Component.Text -> renderText(env, node, slot)
        is Component.Image -> renderImage(env, node, slot)
        is Component.Icon -> renderIcon(env, node, slot)
        is Component.Divider -> Box(
            modifier = boxModel(
                env,
                node.modifier,
                slot,
                defaultWidth = Dimension.Fill,
                defaultHeight = Dimension.Dp(node.thickness),
                defaultBackground = node.color?.let(::parseColor) ?: env.outline,
            ),
        ) {}
        is Component.Spacer -> {
            val size = Dimension.Dp(node.height)
            Spacer(
                if (slot.axis == Axis.Horizontal) {
                    boxModel(env, node.modifier, slot, defaultWidth = size)
                } else {
                    boxModel(env, node.modifier, slot, defaultWidth = null, defaultHeight = size)
                },
            )
        }
        is Component.Button -> renderButton(env, node, slot)
        is Component.Expandable -> renderExpandable(env, node, slot)
        is Component.Card -> renderCard(env, node, slot)
        is Component.Column -> renderColumn(env, node, slot)
        is Component.Row -> renderRow(env, node, slot)
        is Component.Box -> renderBox(env, node, slot)
        is Component.FitBox -> renderFitBox(env, node, slot)
        is Component.Flow -> renderFlow(env, node, slot)
        is Component.Grid -> renderGrid(env, node, slot)
        is Component.Gauge -> renderGauge(env, node, slot)
        is Component.Sparkline -> renderSparkline(env, node, slot)
        is Component.Kpi -> renderKpi(env, node, slot)
        is Component.Slider -> renderSlider(env, node, slot)
        // BoundScreen expands repeats before rendering (T-006).
        is Component.Repeat -> error("repeat must be expanded before rendering")
    }
}

private fun RcScope.renderText(env: RenderEnv, node: Component.Text, slot: Slot) {
    val (styleSize, styleWeight) = when (node.style) {
        TextStyle.H1 -> 32 to RcFontWeight.W700
        TextStyle.H2 -> 24 to RcFontWeight.W700
        TextStyle.H3 -> 20 to RcFontWeight.W500
        TextStyle.Body -> 16 to RcFontWeight.W400
        TextStyle.Caption -> 12 to RcFontWeight.W400
    }
    val modifier = boxModel(env, node.modifier, slot) { m ->
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
    }
    val color = node.color?.let(::parseColor) ?: env.text
    val fontSize = sp(node.fontSize ?: styleSize)
    val fontWeight = node.fontWeight?.toRc() ?: styleWeight
    val textAlign = node.textAlign?.toRc() ?: RcTextAlign.Start
    val overflow = node.overflow?.toRc() ?: RcTextOverflow.Clip
    val maxLines = node.maxLines ?: Int.MAX_VALUE
    val dynamic = resolveDynamicText(env, node.text)
    if (dynamic != null) {
        Text(
            dynamic,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            overflow = overflow,
            maxLines = maxLines,
        )
    } else {
        Text(
            node.text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            overflow = overflow,
            maxLines = maxLines,
        )
    }
}
