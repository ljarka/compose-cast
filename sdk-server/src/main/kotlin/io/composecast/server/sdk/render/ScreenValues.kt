package io.composecast.server.sdk.render

import androidx.compose.remote.core.operations.TextFromFloat
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.creation.Rc
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.touchPosX
import io.composecast.protocol.AnimationEasing
import io.composecast.protocol.AnimationMode
import io.composecast.protocol.Component
import io.composecast.protocol.Dimension
import io.composecast.protocol.NumberList
import io.composecast.protocol.PathStep
import io.composecast.protocol.Roots
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenValue
import io.composecast.protocol.Template
import io.composecast.protocol.ValueAnimation
import io.composecast.server.sdk.render.RemoteComposeRenderer.Companion.parseColor
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * On-device reactive numeric values (`screen.values`) allocated for one render pass.
 *
 * Values controlled by a [Component.Slider] get a pre-reserved float ID (`reserveFloatVariable`)
 * with no `FloatConstant`, so a consumer (e.g. a `Gauge` or `Text`) rendered *above* the slider in
 * the tree can read the exact same ID that the slider's `TouchExpression` writes to on drag — and
 * repaints never reset the dragged value. Values without a slider get a `Global` float constant.
 */
internal class ScreenValues private constructor(private val entries: Map<String, BoundValue>) {
    data class BoundValue(val id: Int, val rcFloat: RcFloat, val default: Float, val min: Float, val max: Float)

    operator fun get(name: String): BoundValue? = entries[name]

    companion object {
        val EMPTY = ScreenValues(emptyMap())

        fun allocate(scope: RcScope, writer: RemoteComposeWriter, screen: ScreenDefinition): ScreenValues {
            if (screen.values.isEmpty()) return EMPTY
            val sliderControlled = collectSliderTargets(screen.components)
            val entries = linkedMapOf<String, BoundValue>()
            val nonSlider = linkedMapOf<String, Pair<ScreenValue, Float>>()
            screen.values.forEach { (name, spec) ->
                val defaultValue = (NumberList.single(spec.default) ?: spec.min).coerceIn(spec.min, spec.max)
                if (name in sliderControlled) {
                    val nanId = writer.reserveFloatVariable()
                    entries[name] = BoundValue(
                        id = Utils.idFromNan(nanId),
                        rcFloat = RcFloat(nanId).withWriter(writer),
                        default = defaultValue,
                        min = spec.min,
                        max = spec.max,
                    )
                } else {
                    nonSlider[name] = spec to defaultValue
                }
            }
            if (nonSlider.isNotEmpty()) {
                scope.Global {
                    nonSlider.forEach { (name, pair) ->
                        val (spec, defaultValue) = pair
                        val rc = spec.animate?.let { animatedFloat(spec, it) } ?: remoteFloat(defaultValue)
                        entries[name] = BoundValue(
                            id = Utils.idFromNan(rc.toFloat()),
                            rcFloat = rc,
                            default = defaultValue,
                            min = spec.min,
                            max = spec.max,
                        )
                    }
                }
            }
            return ScreenValues(entries)
        }

        private fun RcScope.animatedFloat(spec: ScreenValue, anim: ValueAnimation): RcFloat {
            val durationSec = anim.durationMs / 1000f
            val cycles = animationTime() / durationSec
            val rawT = when (anim.mode) {
                AnimationMode.Once -> min(max(cycles, 0f), 1f)
                AnimationMode.Loop -> cycles % 1f
                AnimationMode.PingPong -> (abs((cycles % 2f) - 1f) * -1f) + 1f
            }
            val easedT = when (anim.easing) {
                AnimationEasing.Linear -> rawT
                AnimationEasing.EaseInOut -> ((cos(rawT * PI.toFloat()) * -1f) + 1f) * 0.5f
            }
            return (easedT * (spec.max - spec.min) + spec.min).flush()
        }

        private fun collectSliderTargets(components: List<Component>): Set<String> = buildSet {
            fun walk(list: List<Component>) {
                for (c in list) {
                    if (c is Component.Slider) add(c.controls)
                    c.childLists().forEach { (_, children) -> walk(children) }
                }
            }
            walk(components)
        }
    }
}

/**
 * If [raw] is a single `{{values.<name>}}` binding (e.g. for `Gauge.value`), returns the bound
 * on-device [RcFloat]; otherwise returns null.
 */
internal fun resolveValueRef(env: RenderEnv, raw: String): RcFloat? {
    if (!Template.hasBindings(raw)) return null
    val parts = Template.parse(raw).parts
    val expr = parts.singleOrNull() as? Template.Expression ?: return null
    if (expr.root != Roots.VALUES) return null
    val name = (expr.path.singleOrNull() as? PathStep.Key)?.name ?: return null
    return env.values[name]?.rcFloat
}

/**
 * If [text] contains any `{{values.<name>}}` bindings, compiles it into an on-device [RcText]
 * using `createTextFromFloat` and `textMerge`; otherwise returns null so the caller emits a plain
 * static string (byte-identical to screens without values).
 */
internal fun RcScope.resolveDynamicText(env: RenderEnv, text: String): RcText? {
    if (!Template.hasBindings(text)) return null
    val parts = Template.parse(text).parts
    if (parts.none { it is Template.Expression && it.root == Roots.VALUES }) return null
    var acc: RcText? = null
    for (part in parts) {
        val piece: RcText = when (part) {
            is Template.Literal -> if (part.text.isEmpty()) continue else remoteText(part.text)
            is Template.Expression -> {
                val name = (part.path.singleOrNull() as? PathStep.Key)?.name
                val bound = name?.let { env.values[it] }
                if (part.root == Roots.VALUES && bound != null) {
                    val digits = roundDigits(part)
                    val flags = TextFromFloat.PAD_PRE_NONE or
                        TextFromFloat.PAD_AFTER_NONE or
                        TextFromFloat.OPTIONS_ROUNDING
                    createTextFromFloat(bound.rcFloat, FLOAT_WHOLE_DIGITS, digits, flags)
                } else {
                    remoteText("{{${part.text}}}")
                }
            }
        }
        acc = if (acc == null) piece else acc + piece
    }
    return acc ?: remoteText("")
}

private fun roundDigits(expr: Template.Expression): Int {
    val round = expr.filters.lastOrNull { it.name == "round" } ?: return 0
    return round.args.firstOrNull()?.toIntOrNull()?.coerceIn(0, MAX_DECIMAL_DIGITS) ?: 0
}

/**
 * Horizontal slider on an RC canvas. Emits a `TouchExpression` writing directly to the pre-reserved
 * float ID of [Component.Slider.controls], so sibling components (gauges, texts, KPIs) above or
 * below in the tree update on every frame locally on the device.
 */
internal fun RcScope.renderSlider(env: RenderEnv, node: Component.Slider, slot: Slot) {
    val bound = requireNotNull(env.values[node.controls]) {
        "Slider controls unknown screen value '${node.controls}'"
    }
    val activeColor = node.color?.let(::parseColor) ?: env.primary
    val trackColor = node.trackColor?.let(::parseColor) ?: env.outline
    val range = bound.max - bound.min
    Canvas(
        boxModel(
            node.modifier,
            slot,
            defaultWidth = Dimension.Fill,
            defaultHeight = Dimension.Dp(SLIDER_HEIGHT_DP),
        ),
    ) {
        val w = componentWidth()
        val h = componentHeight()
        val cy = h / 2f
        val d = density()
        val pad = d * SLIDER_THUMB_RADIUS_DP
        val trackWidth = max(w - pad * 2f, 1f)

        // Delta touch expression: both touchPosX() and componentWidth() are in canvas pixels, so
        // dividing by trackWidth maps a full-track horizontal drag 1:1 to the min..max range.
        val touchExp = ((touchPosX() / trackWidth) * range).toArray()
        val step = node.step
        val stopMode = if (step != null) Rc.Touch.STOP_NOTCHES_EVEN else Rc.Touch.STOP_INSTANTLY
        val notches = step?.let { floatArrayOf((range / it).roundToInt().coerceAtLeast(1).toFloat()) }
        val haptic = if (step != null) Rc.Haptic.CLOCK_TICK else Rc.Haptic.NO_HAPTICS

        env.writer.buffer.addTouchExpression(
            bound.id,
            bound.default,
            bound.min,
            bound.max,
            0f,
            haptic,
            touchExp,
            stopMode,
            notches,
            null,
        )

        val fraction = min(max((bound.rcFloat - bound.min) / range, 0f), 1f)
        val thumbX = fraction * trackWidth + pad

        // Background track
        paint {
            color(trackColor)
            style(RcPaintStyle.Stroke)
            strokeWidth(d * SLIDER_TRACK_HEIGHT_DP)
            strokeCap(RcStrokeCap.Round)
        }
        drawLine(pad, cy, w - pad, cy)

        // Active track
        paint {
            color(activeColor)
            style(RcPaintStyle.Stroke)
            strokeWidth(d * SLIDER_TRACK_HEIGHT_DP)
            strokeCap(RcStrokeCap.Round)
        }
        drawLine(pad, cy, thumbX, cy)

        // Thumb
        paint {
            color(activeColor)
            style(RcPaintStyle.Fill)
        }
        drawCircle(thumbX, cy, pad)
    }
}

private const val SLIDER_HEIGHT_DP = 44
private const val SLIDER_TRACK_HEIGHT_DP = 5f
private const val SLIDER_THUMB_RADIUS_DP = 10f
private const val FLOAT_WHOLE_DIGITS = 6
private const val MAX_DECIMAL_DIGITS = 4
