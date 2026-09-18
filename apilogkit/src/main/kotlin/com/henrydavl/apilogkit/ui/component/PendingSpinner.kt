package com.henrydavl.apilogkit.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small indeterminate spinner that is exactly the size you ask for.
 *
 * Material3's `CircularProgressIndicator` reserves internal padding around its
 * arc, so constraining it to badge-sized dimensions leaves only a couple of dp
 * of stroke — on screen it renders as a stray sliver rather than a ring, and it
 * sits off-centre in the badge. There is no parameter to remove that padding, so
 * the arc is drawn directly here instead: at these sizes the whole box has to be
 * the arc.
 *
 * Stands in for the small `ProgressView` iOS scales down with `scaleEffect`.
 */
@Composable
internal fun PendingSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    strokeWidth: Dp = 1.5.dp,
) {
    val transition = rememberInfiniteTransition(label = "pending")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        // Inset by half the stroke so the ring's outer edge lands on the bounds
        // rather than being clipped by them.
        val inset = stroke / 2f
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                this.size.width - stroke,
                this.size.height - stroke,
            ),
            style = Stroke(width = stroke),
        )
    }
}
