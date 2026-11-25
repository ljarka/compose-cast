package io.composecast.server.sdk.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcCanvasScope
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcTextOverflow
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import io.composecast.protocol.Component
import io.composecast.protocol.Dimension
import io.composecast.protocol.NumberList
import io.composecast.server.sdk.render.RemoteComposeRenderer.Companion.parseColor

// Dashboard components drawn on an RC Canvas (T-047, ADR 0029). Every data value is its own
// float/text/array constant so a data-update document (DOC_DATA_UPDATE) can replace it by id on
// live screens. Rules from docs/remote-compose.md: anim() only on float expressions, never on
// array elements (behavior 19); texts whose value changes get a full width (behavior 20).

/** Seconds a gauge takes to sweep to a new value on a live screen. */
private const val VALUE_ANIMATION_S = 0.8f

/** Ring: track circle, then an arc from 12 o'clock over the value's fraction; label centered on top. */
internal fun RcScope.renderGauge(env: RenderEnv, node: Component.Gauge, slot: Slot) {
    val liveValue = resolveValueRef(env, node.value)
    val staticValue = if (liveValue == null) NumberList.single(node.value) ?: node.min else node.min
    val staticFraction = ((staticValue - node.min) / (node.max - node.min)).coerceIn(0f, 1f)
    val color = node.color?.let(::parseColor) ?: env.primary
    val track = node.trackColor?.let(::parseColor) ?: env.outline
    val stroke = node.thickness.toFloat()
    val range = node.max - node.min
    Box(
        modifier = boxModel(
            node.modifier,
            slot,
            defaultWidth = Dimension.Dp(node.size),
            defaultHeight = Dimension.Dp(node.size),
        ),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = ringBounds(stroke)
            paint {
                color(track)
                style(RcPaintStyle.Stroke)
                strokeWidth(stroke)
                strokeCap(RcStrokeCap.Round)
            }
            drawArc(ring.left, ring.top, ring.right, ring.bottom, remoteFloat(0f), remoteFloat(FULL_CIRCLE))
            if (liveValue != null) {
                paint { color(color) }
                val fraction = min(max((liveValue - node.min) / range, 0f), 1f)
                drawArc(ring.left, ring.top, ring.right, ring.bottom, remoteFloat(START_ANGLE), fraction * FULL_CIRCLE)
            } else if (staticFraction > 0f) {
                paint { color(color) }
                val sweep = (remoteFloat(staticFraction) * FULL_CIRCLE).anim(VALUE_ANIMATION_S)
                drawArc(ring.left, ring.top, ring.right, ring.bottom, remoteFloat(START_ANGLE), sweep)
            }
        }
        if (node.label.isNotBlank()) {
            val fontSize = sp((node.size / GAUGE_LABEL_DIVISOR).coerceAtLeast(MIN_LABEL_SP))
            val dynamicLabel = resolveDynamicText(env, node.label)
            if (dynamicLabel != null) {
                Text(
                    dynamicLabel,
                    modifier = Modifier.fillMaxWidth(),
                    color = env.text,
                    fontSize = fontSize,
                    fontWeight = RcFontWeight.W700,
                    textAlign = RcTextAlign.Center,
                    maxLines = 1,
                )
            } else {
                Text(
                    node.label,
                    modifier = Modifier.fillMaxWidth(),
                    color = env.text,
                    fontSize = fontSize,
                    fontWeight = RcFontWeight.W700,
                    textAlign = RcTextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private class Ring(val left: RcFloat, val top: RcFloat, val right: RcFloat, val bottom: RcFloat)

/** The largest centered square of the canvas, inset by half the stroke so the ring is not clipped. */
private fun RcCanvasScope.ringBounds(stroke: Float): Ring {
    val w = componentWidth()
    val h = componentHeight()
    val side = w.min(h) - stroke
    val left = (w - side) / 2f
    val top = (h - side) / 2f
    return Ring(left, top, left + side, top + side)
}

/**
 * Polyline through the values scaled into the canvas; the values are normalized to 0..1 on the
 * server (min/max given or their own range), so the device only multiplies by the height.
 */
internal fun RcScope.renderSparkline(env: RenderEnv, node: Component.Sparkline, slot: Slot) {
    val values = NumberList.parse(node.values)
    val low = node.min ?: values.minOrNull() ?: 0f
    val high = node.max ?: values.maxOrNull() ?: 1f
    val range = (high - low).takeIf { it > 0f } ?: 1f
    val normalized = values.map { ((it - low) / range).coerceIn(0f, 1f) }.toFloatArray()
    val stroke = node.thickness.toFloat()
    Canvas(
        boxModel(node.modifier, slot, defaultWidth = Dimension.Fill, defaultHeight = Dimension.Dp(node.height)),
    ) {
        if (normalized.size < 2) return@Canvas
        val points = remoteFloatArray(normalized)
        val w = componentWidth() - stroke
        val h = componentHeight() - stroke
        val inset = stroke / 2f
        paint {
            color(node.color?.let(::parseColor) ?: env.primary)
            style(RcPaintStyle.Stroke)
            strokeWidth(stroke)
            strokeCap(RcStrokeCap.Round)
        }
        val last = normalized.size - 1f
        for (i in 0 until normalized.size - 1) {
            drawLine(
                w * (i / last) + inset,
                h - points[i] * h + inset,
                w * ((i + 1) / last) + inset,
                h - points[i + 1] * h + inset,
            )
        }
    }
}

/** Label (muted), the value (large, bold), caption (muted, small); texts full width for live updates. */
internal fun RcScope.renderKpi(env: RenderEnv, node: Component.Kpi, slot: Slot) {
    val muted = (env.text and RGB_MASK) or MUTED_ALPHA
    Column(boxModel(node.modifier, slot, defaultWidth = Dimension.Fill)) {
        if (node.label.isNotBlank()) {
            val dynamicLabel = resolveDynamicText(env, node.label)
            if (dynamicLabel != null) {
                Text(dynamicLabel, Modifier.fillMaxWidth(), color = muted, fontSize = sp(KPI_LABEL_SP), maxLines = 1)
            } else {
                Text(node.label, Modifier.fillMaxWidth(), color = muted, fontSize = sp(KPI_LABEL_SP), maxLines = 1)
            }
        }
        val valueColor = node.color?.let(::parseColor) ?: env.text
        val dynamicValue = resolveDynamicText(env, node.value)
        if (dynamicValue != null) {
            Text(
                dynamicValue,
                Modifier.fillMaxWidth(),
                color = valueColor,
                fontSize = sp(KPI_VALUE_SP),
                fontWeight = RcFontWeight.W700,
                maxLines = 1,
                overflow = RcTextOverflow.Ellipsis,
            )
        } else {
            Text(
                node.value,
                Modifier.fillMaxWidth(),
                color = valueColor,
                fontSize = sp(KPI_VALUE_SP),
                fontWeight = RcFontWeight.W700,
                maxLines = 1,
                // A value wider than the card ends in "…" rather than being cut mid-character.
                overflow = RcTextOverflow.Ellipsis,
            )
        }
        if (node.caption.isNotBlank()) {
            val dynamicCaption = resolveDynamicText(env, node.caption)
            if (dynamicCaption != null) {
                Text(
                    dynamicCaption,
                    Modifier.fillMaxWidth(),
                    color = muted,
                    fontSize = sp(KPI_CAPTION_SP),
                    maxLines = 1,
                )
            } else {
                Text(node.caption, Modifier.fillMaxWidth(), color = muted, fontSize = sp(KPI_CAPTION_SP), maxLines = 1)
            }
        }
    }
}

private const val FULL_CIRCLE = 360f

/** Arcs start at 3 o'clock in RC (Android canvas); -90° starts the value at 12 o'clock. */
private const val START_ANGLE = -90f
private const val GAUGE_LABEL_DIVISOR = 5
private const val MIN_LABEL_SP = 12
private const val KPI_LABEL_SP = 14
private const val KPI_VALUE_SP = 32
private const val KPI_CAPTION_SP = 12
private const val RGB_MASK = 0x00FFFFFF
private const val MUTED_ALPHA = 0xA6000000.toInt()
