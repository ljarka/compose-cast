package io.composecast.server.sdk.render

import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.core.semantics.AccessibleComponent
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcTextOverflow
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import io.composecast.protocol.Alignment
import io.composecast.protocol.Arrangement
import io.composecast.protocol.Component
import io.composecast.protocol.ContentScale
import io.composecast.protocol.FontWeight
import io.composecast.protocol.SemanticsRole
import io.composecast.protocol.TextAlign
import io.composecast.protocol.TextOverflow
import io.composecast.protocol.VisibilityTransition

// Screen model enums → Remote Compose creation enums (one-to-one).

internal fun FontWeight.toRc(): RcFontWeight = when (this) {
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

internal fun TextAlign.toRc(): RcTextAlign = when (this) {
    TextAlign.Start -> RcTextAlign.Start
    TextAlign.Center -> RcTextAlign.Center
    TextAlign.End -> RcTextAlign.End
    TextAlign.Left -> RcTextAlign.Left
    TextAlign.Right -> RcTextAlign.Right
    TextAlign.Justify -> RcTextAlign.Justify
}

internal fun TextOverflow.toRc(): RcTextOverflow = when (this) {
    TextOverflow.Clip -> RcTextOverflow.Clip
    TextOverflow.Visible -> RcTextOverflow.Visible
    TextOverflow.Ellipsis -> RcTextOverflow.Ellipsis
    TextOverflow.StartEllipsis -> RcTextOverflow.StartEllipsis
    TextOverflow.MiddleEllipsis -> RcTextOverflow.MiddleEllipsis
}

internal fun SemanticsRole.toRc(): AccessibleComponent.Role = when (this) {
    SemanticsRole.Button -> AccessibleComponent.Role.BUTTON
    SemanticsRole.Checkbox -> AccessibleComponent.Role.CHECKBOX
    SemanticsRole.Switch -> AccessibleComponent.Role.SWITCH
    SemanticsRole.RadioButton -> AccessibleComponent.Role.RADIO_BUTTON
    SemanticsRole.Tab -> AccessibleComponent.Role.TAB
    SemanticsRole.Image -> AccessibleComponent.Role.IMAGE
    SemanticsRole.DropdownList -> AccessibleComponent.Role.DROPDOWN_LIST
    SemanticsRole.Picker -> AccessibleComponent.Role.PICKER
    SemanticsRole.Carousel -> AccessibleComponent.Role.CAROUSEL
}

internal fun ContentScale.toRc(): RcContentScale = when (this) {
    ContentScale.Fit -> RcContentScale.Fit
    ContentScale.Crop -> RcContentScale.Crop
    ContentScale.FillBounds -> RcContentScale.FillBounds
    ContentScale.FillWidth -> RcContentScale.FillWidth
    ContentScale.FillHeight -> RcContentScale.FillHeight
    ContentScale.Inside -> RcContentScale.Inside
    ContentScale.None -> RcContentScale.None
}

internal fun Arrangement.vertical(): RcColumnVerticalPositioning = when (this) {
    Arrangement.Start -> RcColumnVerticalPositioning.Top
    Arrangement.Center -> RcColumnVerticalPositioning.Center
    Arrangement.End -> RcColumnVerticalPositioning.Bottom
    Arrangement.SpaceBetween -> RcColumnVerticalPositioning.SpaceBetween
    Arrangement.SpaceAround -> RcColumnVerticalPositioning.SpaceAround
    Arrangement.SpaceEvenly -> RcColumnVerticalPositioning.SpaceEvenly
}

internal fun Component.Row.horizontalAlignment(): RcRowHorizontalPositioning = when (horizontalArrangement) {
    Arrangement.Start -> RcRowHorizontalPositioning.Start
    Arrangement.Center -> RcRowHorizontalPositioning.Center
    Arrangement.End -> RcRowHorizontalPositioning.End
    Arrangement.SpaceBetween -> RcRowHorizontalPositioning.SpaceBetween
    Arrangement.SpaceAround -> RcRowHorizontalPositioning.SpaceAround
    Arrangement.SpaceEvenly -> RcRowHorizontalPositioning.SpaceEvenly
}

internal fun Alignment.horizontal(): RcHorizontalPositioning = when (this) {
    Alignment.Start -> RcHorizontalPositioning.Start
    Alignment.Center -> RcHorizontalPositioning.Center
    Alignment.End -> RcHorizontalPositioning.End
}

internal fun Alignment.vertical(): RcVerticalPositioning = when (this) {
    Alignment.Start -> RcVerticalPositioning.Top
    Alignment.Center -> RcVerticalPositioning.Center
    Alignment.End -> RcVerticalPositioning.Bottom
}

internal fun VisibilityTransition.toRc(): Pair<AnimationSpec.ANIMATION, AnimationSpec.ANIMATION> = when (this) {
    VisibilityTransition.Fade, VisibilityTransition.None ->
        AnimationSpec.ANIMATION.FADE_IN to AnimationSpec.ANIMATION.FADE_OUT
    VisibilityTransition.SlideTop ->
        AnimationSpec.ANIMATION.SLIDE_TOP to AnimationSpec.ANIMATION.SLIDE_TOP
    VisibilityTransition.SlideBottom ->
        AnimationSpec.ANIMATION.SLIDE_BOTTOM to AnimationSpec.ANIMATION.SLIDE_BOTTOM
    VisibilityTransition.SlideLeft ->
        AnimationSpec.ANIMATION.SLIDE_LEFT to AnimationSpec.ANIMATION.SLIDE_LEFT
    VisibilityTransition.SlideRight ->
        AnimationSpec.ANIMATION.SLIDE_RIGHT to AnimationSpec.ANIMATION.SLIDE_RIGHT
}
