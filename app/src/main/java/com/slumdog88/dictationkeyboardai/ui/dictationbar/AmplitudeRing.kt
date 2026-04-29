package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Amplitude-reactive ring that expands/contracts based on audio level.
 *
 * Features:
 * - Ring radius varies with amplitude (0-8dp expansion)
 * - Quantizes amplitude to 10 discrete levels to reduce animation jitter
 * - Smooth animation between amplitude levels
 * - Coral-pink color matching recording glow theme
 *
 * @param amplitude Normalized amplitude value (0.0-1.0)
 * @param isRecording Whether recording is active (ring only shows during recording)
 * @param modifier Modifier for sizing (should match mic button size + padding)
 */
@Composable
fun AmplitudeRing(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    // Ring color: coral-pink matching recording glow
    val ringColor = Color(0xFFFF6B8A)

    // Quantize amplitude to 10 discrete levels (0.0, 0.1, 0.2, ... 1.0)
    val quantizedAmplitude = (amplitude * 10).toInt() / 10f

    // Animate between quantized levels for smooth visual
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isRecording) quantizedAmplitude else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "amplitudeRing"
    )

    // Ring visibility animation - show when recording (even at low amplitude for visual presence)
    val ringAlpha by animateFloatAsState(
        targetValue = if (isRecording) 0.7f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "ringAlpha"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val baseRadius = (size.minDimension / 2) - strokeWidth

        // Amplitude expansion: 0dp at amplitude 0, up to 8dp at amplitude 1
        val expansionDp = 8.dp.toPx() * animatedAmplitude
        val ringRadius = baseRadius + expansionDp

        // Draw the amplitude ring
        drawCircle(
            color = ringColor.copy(alpha = ringAlpha),
            radius = ringRadius,
            style = Stroke(width = strokeWidth)
        )
    }
}
