package io.composecast.server.sdk.render

import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.height
import io.composecast.protocol.Component
import io.composecast.protocol.Dimension
import kotlin.math.roundToInt

// Images and icons are fetched on the server and embedded, named `image:<url>`; a failed or
// rejected fetch renders the alt text (ADR 0003).

internal fun RcScope.renderImage(env: RenderEnv, node: Component.Image, slot: Slot) {
    val image = env.image(node.url)
    if (image == null) {
        Text(
            node.alt.ifBlank { node.url },
            modifier = boxModel(env, node.modifier, slot),
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
            boxModel(env, node.modifier, slot, defaultWidth = defaultWidth)
        }
        aspectRatio != null -> when {
            widthMod is Dimension.Dp -> boxModel(
                env,
                node.modifier,
                slot,
                defaultHeight = Dimension.Dp((widthMod.value / aspectRatio).roundToInt()),
            )
            widthMod == Dimension.Wrap ||
                (widthMod == null && slot.axis == Axis.Horizontal && node.modifier.weight == null) -> boxModel(
                env,
                node.modifier,
                slot,
                defaultHeight = Dimension.Dp((image.width / aspectRatio).roundToInt()),
            )
            else -> {
                val contentWidthDp = windowWidth() / density() - 2 * SCREEN_PADDING
                boxModel(env, node.modifier, slot).height(written(contentWidthDp / aspectRatio))
            }
        }
        else -> {
            val ratio = image.height.toFloat() / image.width
            val contentWidthDp = windowWidth() / density() - 2 * SCREEN_PADDING
            boxModel(env, node.modifier, slot)
                .height(written(min(contentWidthDp * ratio, image.height.toFloat())))
        }
    }
    Image(
        remoteNamedBitmap("$IMAGE_NAME_PREFIX${node.url}", image),
        modifier = modifier,
        contentDescription = node.modifier.imageDescription(node.alt),
        contentScale = node.contentScale?.toRc() ?: RcContentScale.Fit,
        alpha = node.alpha ?: 1f,
    )
}

internal fun RcScope.renderIcon(env: RenderEnv, node: Component.Icon, slot: Slot) {
    val image = env.image(node.url)
    val sizeDp = Dimension.Dp(node.size)
    val modifier = boxModel(env, node.modifier, slot, defaultWidth = sizeDp, defaultHeight = sizeDp)
    if (image != null) {
        Icon(
            remoteNamedBitmap("$IMAGE_NAME_PREFIX${node.url}", image),
            modifier = modifier,
            contentDescription = node.modifier.imageDescription(node.alt),
        )
    } else {
        Text(node.alt.ifBlank { node.url }, modifier = modifier, color = PLACEHOLDER_COLOR, fontSize = sp(12))
    }
}
