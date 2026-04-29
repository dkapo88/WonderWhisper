package com.slumdog88.dictationkeyboardai.ui.bubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPink
import kotlin.math.cos
import kotlin.math.sin

/**
 * Displays real-time audio amplitude as bars radiating around the bubble.
 *
 * @param amplitudeHistory Recent amplitude samples (normalized 0-1) for visualization
 * @param isActive Whether the visualization should animate
 * @param modifier Modifier for the Canvas
 * @param bubbleRadius The radius of the bubble (bars start from this distance)
 * @param maxBarHeight Maximum height bars can extend outward
 * @param barCount Number of bars distributed around the circle
 * @param accentColor Color for the amplitude bars
 */
@Composable
fun AmplitudeRingVisualizer(
    amplitudeHistory: List<Float>,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    bubbleRadius: Dp = 28.dp,
    maxBarHeight: Dp = 14.dp,
    barCount: Int = 24,
    accentColor: Color = PastelPink
) {
    // Compute bar amplitudes from history - recomputes when inputs change
    val barAmplitudes = remember(amplitudeHistory, isActive, barCount) {
        if (!isActive || amplitudeHistory.isEmpty()) {
            FloatArray(barCount) { 0f }
        } else {
            FloatArray(barCount) { index ->
                val historyIndex = (index * amplitudeHistory.size / barCount)
                    .coerceIn(0, amplitudeHistory.lastIndex)
                amplitudeHistory.getOrElse(historyIndex) { 0f }
            }
        }
    }

    // Single animated multiplier for smooth fade in/out
    val activeMultiplier by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(150),
        label = "activeMultiplier"
    )

    val totalSize = (bubbleRadius + maxBarHeight) * 2

    Canvas(
        modifier = modifier.size(totalSize)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val innerRadius = bubbleRadius.toPx()
        val maxHeight = maxBarHeight.toPx()
        val barWidth = 4.dp.toPx()
        val gap = 2.dp.toPx()
        val angleStep = 360f / barCount

        barAmplitudes.forEachIndexed { index, rawAmplitude ->
            val amplitude = rawAmplitude * activeMultiplier
            if (amplitude < 0.01f) return@forEachIndexed // Skip nearly invisible bars

            val angle = index * angleStep
            val barHeight = (maxHeight * amplitude).coerceAtLeast(2.dp.toPx())

            // Calculate bar position
            val angleRad = Math.toRadians(angle.toDouble())
            val startX = centerX + (innerRadius + gap) * cos(angleRad).toFloat()
            val startY = centerY + (innerRadius + gap) * sin(angleRad).toFloat()

            // Draw bar radiating outward from center
            rotate(degrees = angle + 90f, pivot = Offset(startX, startY)) {
                // Glow layer (outer)
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.3f * amplitude),
                    topLeft = Offset(startX - barWidth / 2 - 2.dp.toPx(), startY - 2.dp.toPx()),
                    size = Size(barWidth + 4.dp.toPx(), barHeight + 4.dp.toPx()),
                    cornerRadius = CornerRadius(barWidth / 2 + 2.dp.toPx())
                )

                // Main bar
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.6f + 0.4f * amplitude),
                    topLeft = Offset(startX - barWidth / 2, startY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            }
        }
    }
}

/**
 * Preview-friendly version with static amplitude.
 */
@Composable
fun AmplitudeRingVisualizerPreview(
    staticAmplitude: Float = 0.5f,
    modifier: Modifier = Modifier,
    bubbleRadius: Dp = 28.dp,
    maxBarHeight: Dp = 14.dp,
    barCount: Int = 24,
    accentColor: Color = PastelPink
) {
    // Create varying amplitudes for visual interest
    val amplitudes = List(barCount) { index ->
        val variation = sin(index * 0.5) * 0.3
        (staticAmplitude + variation).toFloat().coerceIn(0.1f, 1f)
    }

    AmplitudeRingVisualizer(
        amplitudeHistory = amplitudes,
        isActive = true,
        modifier = modifier,
        bubbleRadius = bubbleRadius,
        maxBarHeight = maxBarHeight,
        barCount = barCount,
        accentColor = accentColor
    )
}
