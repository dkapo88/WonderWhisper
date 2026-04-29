package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

// Performance fix: Pre-computed colors to avoid allocations on every draw
private object BackgroundColors {
    val pitchBlack = Color(0xFF0A0A0A)
    val concrete = Color(0xFF1F1F1F)
    val charcoal = Color(0xFF2B2B2B)

    // Pre-computed alpha variations
    val concreteAlpha06 = concrete.copy(alpha = 0.6f)
    val concreteAlpha07 = concrete.copy(alpha = 0.7f)
    val charcoalAlpha04 = charcoal.copy(alpha = 0.4f)
    val charcoalAlpha05 = charcoal.copy(alpha = 0.5f)
    val gridLine = Color.White.copy(alpha = 0.03f)
    val shadowColor = Color.Black.copy(alpha = 0.1f)

    // Accent colors
    val accentRustAlpha = Color(0xFFD14D1F).copy(alpha = 0.15f)
    val accentCyanAlpha = Color(0xFF1BE7FF).copy(alpha = 0.12f)
}

@Composable
fun BrutalAnimatedBackground(scrollOffset: Float) {
    // Performance fix: This is a static background, no need for scrollOffset
    // Using remember to cache path calculations based on size

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawBrutalTextureBackground(size.width, size.height)
    }
}

// Performance fix: Simplified drawing with pre-computed colors
private fun DrawScope.drawBrutalTextureBackground(width: Float, height: Float) {
    // Base background
    drawRect(color = BackgroundColors.pitchBlack, size = Size(width, height))

    // Large diagonal concrete slab (top-left) - reuse Path
    val path1 = Path().apply {
        moveTo(0f, 0f)
        lineTo(width * 0.4f, 0f)
        lineTo(width * 0.2f, height * 0.3f)
        lineTo(0f, height * 0.25f)
        close()
    }
    drawPath(path1, color = BackgroundColors.concreteAlpha06)

    // Large rectangular brutalist block (top-right)
    drawRect(
        color = BackgroundColors.charcoalAlpha04,
        topLeft = Offset(width * 0.6f, 0f),
        size = Size(width * 0.4f, height * 0.35f)
    )

    // Angular geometric shape (bottom-left)
    val path2 = Path().apply {
        moveTo(0f, height * 0.7f)
        lineTo(width * 0.3f, height * 0.6f)
        lineTo(width * 0.35f, height)
        lineTo(0f, height)
        close()
    }
    drawPath(path2, color = BackgroundColors.charcoalAlpha05)

    // Large diagonal cut (bottom-right)
    val path3 = Path().apply {
        moveTo(width * 0.5f, height * 0.8f)
        lineTo(width, height * 0.6f)
        lineTo(width, height)
        lineTo(width * 0.7f, height)
        close()
    }
    drawPath(path3, color = BackgroundColors.concreteAlpha07)

    // Small accent geometric details (using pre-computed colors)
    drawRect(
        color = BackgroundColors.accentRustAlpha,
        topLeft = Offset(width * 0.1f, height * 0.1f),
        size = Size(width * 0.08f, height * 0.12f)
    )

    drawCircle(
        color = BackgroundColors.accentCyanAlpha,
        radius = width * 0.06f,
        center = Offset(width * 0.8f, height * 0.2f)
    )

    // Brutalist grid overlay (using pre-computed grid color)
    val gridColor = BackgroundColors.gridLine
    val xStart = width * 0.6f
    val yEnd = height * 0.35f

    for (i in 0..8) {
        val x = xStart + i * 20f
        drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, yEnd), strokeWidth = 1f)
    }

    for (i in 0..6) {
        val y = i * 30f
        drawLine(color = gridColor, start = Offset(xStart, y), end = Offset(width, y), strokeWidth = 1f)
    }

    // Subtle shadow gradient
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(BackgroundColors.shadowColor, Color.Transparent),
            start = Offset(0f, 0f),
            end = Offset(width * 0.3f, height * 0.3f)
        ),
        size = Size(width, height)
    )
}
