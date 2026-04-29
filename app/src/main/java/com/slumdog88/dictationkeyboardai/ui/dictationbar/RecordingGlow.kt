package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Animated edge glow overlay for the dictation bar during recording.
 *
 * Features:
 * - Soft red/pink edge glow around bar perimeter
 * - Gentle breathing pulse animation (0.2 -> 0.5 alpha over 1.5s)
 * - Smooth fade in/out on recording state change
 * - Subtle intensity - noticeable but not distracting
 *
 * Design decisions from CONTEXT.md:
 * - Color: subtle red/pink tint (traditional recording indicator)
 * - Location: full bar edge glow (soft glow around entire perimeter)
 * - Animation: gentle pulse (breathing, not strobe)
 * - Intensity: subtle (soft diffused light, not alarming)
 *
 * @param isRecording Whether recording is currently active
 * @param modifier Optional modifier for positioning
 */
@Composable
fun RecordingBarGlow(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    // Glow color: soft coral-pink (traditional recording indicator)
    val glowColor = Color(0xFFFF6B8A)

    // Infinite breathing pulse: 0.4 -> 0.8 alpha over 1.5s (more visible)
    val infiniteTransition = rememberInfiniteTransition(label = "recordingGlow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Animate visibility in/out
    val visibility by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "glowVisibility"
    )

    // Only render when visible (optimization)
    if (visibility > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    val effectiveAlpha = pulseAlpha * visibility

                    // Top edge glow - gradient from top (increased depth and alpha)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                glowColor.copy(alpha = effectiveAlpha),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 24.dp.toPx()
                        )
                    )

                    // Bottom edge glow - gradient from bottom (increased depth and alpha)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                glowColor.copy(alpha = effectiveAlpha)
                            ),
                            startY = size.height - 24.dp.toPx(),
                            endY = size.height
                        )
                    )

                    // Left edge glow (increased depth and alpha)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                glowColor.copy(alpha = effectiveAlpha * 0.7f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 20.dp.toPx()
                        )
                    )

                    // Right edge glow (increased depth and alpha)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                glowColor.copy(alpha = effectiveAlpha * 0.7f)
                            ),
                            startX = size.width - 20.dp.toPx(),
                            endX = size.width
                        )
                    )
                }
        )
    }
}
