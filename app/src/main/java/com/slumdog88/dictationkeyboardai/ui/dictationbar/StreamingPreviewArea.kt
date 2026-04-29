package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette

/**
 * Streaming preview area that expands above the dictation bar to show real-time
 * transcription text during streaming mode.
 *
 * Features:
 * - Animated height: Smoothly expands/collapses with spring animation
 * - Auto-scroll: Scrolls to bottom when user is near bottom (within 48dp)
 * - Status header: Shows current streaming status with cancel button
 * - Dynamic sizing: Height grows with content up to 50% of screen height
 *
 * @param isVisible Whether the preview area should be visible
 * @param text The streaming transcription text to display
 * @param status Status message (e.g., "Listening...", "Processing...")
 * @param onCancel Callback when cancel button is tapped
 * @param modifier Optional modifier for the composable
 */
@Composable
fun StreamingPreviewArea(
    isVisible: Boolean,
    text: String,
    status: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KeyboardPalette.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    // Read opacity from SharedPreferences (default 85%)
    val opacity = remember {
        context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            .getFloat("keyboard_opacity", 0.85f)
    }

    // Track the actual number of rendered lines from the Text composable
    var renderedLineCount by remember { mutableIntStateOf(1) }

    // Height calculation: header (~28dp) + padding (16dp) + text lines (20dp each)
    val headerHeight = 28.dp
    val verticalPadding = 16.dp
    val lineHeightDp = 20.dp
    val maxVisibleLines = 5

    val visibleLines = renderedLineCount.coerceIn(1, maxVisibleLines)
    val contentHeight = headerHeight + verticalPadding + (lineHeightDp * visibleLines)

    // Target height based on visibility and content
    val targetHeight = if (isVisible) contentHeight else 0.dp

    // Smooth animated height — no bounce, just a gentle ease for natural feel
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "streamingPreviewHeight"
    )

    // Auto-scroll to bottom when text changes (only if user is near bottom)
    LaunchedEffect(text) {
        val maxValue = scrollState.maxValue
        val distanceFromBottom = maxValue - scrollState.value
        val thresholdPx = with(density) { 48.dp.toPx() }
        if (distanceFromBottom <= thresholdPx) {
            scrollState.animateScrollTo(maxValue)
        }
    }

    // Only render when height > 0
    if (animatedHeight > 0.dp) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.keyBackground.copy(alpha = opacity))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header row with status and cancel button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.ifEmpty { "Listening..." },
                    color = colors.keyTextSecondary,
                    fontSize = 12.sp
                )

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Cancel streaming",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Scrollable text area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = text.ifEmpty { "..." },
                    color = colors.keyTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.verticalScroll(scrollState),
                    onTextLayout = { result ->
                        renderedLineCount = result.lineCount
                    }
                )
            }
        }
    }
}
