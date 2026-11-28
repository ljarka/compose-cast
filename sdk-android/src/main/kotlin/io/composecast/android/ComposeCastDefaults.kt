package io.composecast.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The default loading and error slots of [ComposeCastView] and [ComposeCastFlow] (T-059). They use
 * only Compose foundation (no Material), so they fit any design system; pass your own slots to
 * match your app.
 */
public object ComposeCastDefaults {
    /** A spinning arc, centered where it is placed. */
    @Composable
    public fun Loading(modifier: Modifier = Modifier, darkTheme: Boolean = isSystemInDarkTheme()) {
        val color = contentColor(darkTheme)
        val turn by rememberInfiniteTransition(label = "loading").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
            label = "turn",
        )
        Canvas(modifier.size(40.dp).semantics { contentDescription = "Loading" }) {
            val stroke = 4.dp.toPx()
            drawArc(
                color = color,
                startAngle = turn,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }

    /** [errorMessage] of [error] and a "Retry" button calling [retry]. */
    @Composable
    public fun Error(
        error: ComposeCastError,
        retry: () -> Unit,
        modifier: Modifier = Modifier,
        darkTheme: Boolean = isSystemInDarkTheme(),
    ) {
        val color = contentColor(darkTheme)
        Column(
            modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                errorMessage(error),
                style = TextStyle(color = color, fontSize = 16.sp, textAlign = TextAlign.Center),
            )
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .clip(shape)
                    .border(1.dp, color, shape)
                    .clickable(role = Role.Button, onClick = retry)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                BasicText("Retry", style = TextStyle(color = color, fontSize = 15.sp))
            }
        }
    }

    /** An English message for [error], as the default error slot shows it. */
    public fun errorMessage(error: ComposeCastError): String = when (error) {
        is ComposeCastError.UpgradeRequired -> "Please update the app to continue."
        // The server answered (e.g. 404 unknown screen): not a connectivity problem.
        is ComposeCastError.Http -> "Server error ${error.statusCode}."
        is ComposeCastError.Network -> "Cannot reach the server.\n${error.message}"
    }

    private fun contentColor(darkTheme: Boolean) = if (darkTheme) Color(0xFFE6E0E9) else Color(0xFF1C1B1F)
}
