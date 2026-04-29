package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Three dots pulsing sequentially like a chat typing indicator.
 *
 * Animation pattern:
 * - Duration: 800ms total cycle
 * - Each dot phases: 0.3f -> 1.0f -> 0.3f alpha
 * - Sequential offset: 200ms between each dot start
 * - Uses graphicsLayer for draw-phase optimization
 *
 * @param color The color for the dots
 * @param modifier Optional modifier for the indicator row
 */
@Composable
fun TypingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val dotSize = 8.dp
    val delayUnit = 200

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typingDot$index")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = delayUnit * 4  // 800ms total
                        0.3f at 0
                        1f at delayUnit
                        0.3f at delayUnit * 2
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * delayUnit)
                ),
                label = "typingAlpha$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer { this.alpha = alpha }
                    .background(color, CircleShape)
            )
        }
    }
}
