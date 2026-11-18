package io.composecast.server.sdk.render

import androidx.compose.remote.core.operations.layout.modifiers.ShapeType
import androidx.compose.remote.core.semantics.AccessibleComponent
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.ButtonVariant
import io.composecast.protocol.Component
import io.composecast.protocol.Padding

// Components the user taps: buttons (click = hostAction, T-001/T-013) and Expandable, whose
// state lives on the device (T-008).

/** A clickable Box with the brand `primary` color; variants filled / outlined / text. */
internal fun RcScope.renderButton(env: RenderEnv, node: Component.Button, slot: Slot) {
    val primary = env.primary
    val (bg, contentColor, hasBorder) = when (node.variant) {
        ButtonVariant.Filled -> Triple(primary, ON_PRIMARY, false)
        ButtonVariant.Outlined -> Triple(null, primary, true)
        ButtonVariant.Text -> Triple(null, primary, false)
    }
    val iconImage = node.icon?.let { env.image(it) }
    Box(
        modifier = boxModel(
            node.modifier,
            slot,
            defaultPadding = Padding(BUTTON_PADDING),
            defaultBackground = bg,
            defaultCornerRadius = BUTTON_RADIUS,
            defaultRole = AccessibleComponent.Role.BUTTON,
            defaultClickable = true,
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

internal fun RcScope.renderExpandable(env: RenderEnv, node: Component.Expandable, slot: Slot) {
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
            Text("+", color = env.primary, fontSize = sp(18), fontWeight = RcFontWeight.W700)
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
                Text("−", color = env.primary, fontSize = sp(18), fontWeight = RcFontWeight.W700)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(start = 14f, end = 14f, bottom = 14f)) {
                renderChildren(env, node.content, Slot(Axis.Vertical) { m, w -> m.weight(w) })
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
