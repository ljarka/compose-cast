package io.composecast.server.sdk.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.width
import io.composecast.protocol.Component
import io.composecast.protocol.Padding

// Layout containers (T-012), adaptive containers (T-015), Flow and Grid (T-014), Card (T-013).
// `weight` is a member of the Row/Column scope in RC, so each Slot captures it in that scope.

internal fun RcScope.renderCard(env: RenderEnv, node: Component.Card, slot: Slot) = Column(
    modifier = boxModel(
        env,
        node.modifier,
        slot,
        defaultPadding = Padding(CARD_PADDING),
        defaultBackground = env.surface,
        defaultCornerRadius = CARD_RADIUS,
    ),
) {
    renderChildren(env, node.children, Slot(Axis.Vertical) { m, w -> m.weight(w) }, node.spacing)
}

internal fun RcScope.renderColumn(env: RenderEnv, node: Component.Column, slot: Slot) = if (node.collapsible) {
    CollapsibleColumn(
        modifier = boxModel(env, node.modifier, slot),
        horizontal = node.horizontalAlignment.horizontal(),
        vertical = node.verticalArrangement.vertical(),
    ) {
        renderChildren(env, node.children, Slot(Axis.Vertical) { m, w -> m.weight(w) }, node.spacing)
    }
} else {
    Column(
        modifier = boxModel(env, node.modifier, slot),
        horizontal = node.horizontalAlignment.horizontal(),
        vertical = node.verticalArrangement.vertical(),
    ) {
        renderChildren(env, node.children, Slot(Axis.Vertical) { m, w -> m.weight(w) }, node.spacing)
    }
}

internal fun RcScope.renderRow(env: RenderEnv, node: Component.Row, slot: Slot) = if (node.collapsible) {
    CollapsibleRow(
        modifier = boxModel(env, node.modifier, slot),
        horizontal = node.horizontalAlignment(),
        vertical = node.verticalAlignment.vertical(),
    ) {
        renderChildren(env, node.children, Slot(Axis.Horizontal) { m, w -> m.weight(w) }, node.spacing)
    }
} else {
    Row(
        modifier = boxModel(env, node.modifier, slot),
        horizontal = node.horizontalAlignment(),
        vertical = node.verticalAlignment.vertical(),
    ) {
        renderChildren(env, node.children, Slot(Axis.Horizontal) { m, w -> m.weight(w) }, node.spacing)
    }
}

internal fun RcScope.renderBox(env: RenderEnv, node: Component.Box, slot: Slot) = Box(
    modifier = boxModel(env, node.modifier, slot),
    horizontal = node.horizontalAlignment.horizontal(),
    vertical = node.verticalAlignment.vertical(),
) {
    renderChildren(env, node.children, Slot(Axis.Stack))
}

internal fun RcScope.renderFitBox(env: RenderEnv, node: Component.FitBox, slot: Slot) = FitBox(
    modifier = boxModel(env, node.modifier, slot),
    horizontal = node.horizontalAlignment.horizontal(),
    vertical = node.verticalAlignment.vertical(),
) {
    renderChildren(env, node.children, Slot(Axis.Stack))
}

internal fun RcScope.renderFlow(env: RenderEnv, node: Component.Flow, slot: Slot) = Flow(
    modifier = boxModel(env, node.modifier, slot),
    horizontal = node.horizontalAlignment.horizontal(),
    vertical = node.verticalAlignment.vertical(),
    maxItemsInEachRow = node.maxItemsInEachRow ?: Int.MAX_VALUE,
    maxLines = node.maxLines ?: Int.MAX_VALUE,
) {
    renderChildren(env, node.children, Slot(Axis.Horizontal) { m, w -> m.weight(w) })
}

/** A grid is rows of `columns` equal-weight cells; missing cells of the last row are spacers. */
internal fun RcScope.renderGrid(env: RenderEnv, node: Component.Grid, slot: Slot) = Column(
    modifier = boxModel(env, node.modifier, slot),
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
                        renderNode(env, rowItems[col], Slot(Axis.Vertical))
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
