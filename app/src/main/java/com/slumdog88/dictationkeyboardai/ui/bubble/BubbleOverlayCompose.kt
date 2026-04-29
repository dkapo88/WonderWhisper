package com.slumdog88.dictationkeyboardai.ui.bubble

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.PastelBlue
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPink
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPurple
import com.slumdog88.dictationkeyboardai.ui.theme.Surface1
import com.slumdog88.dictationkeyboardai.ui.theme.TextHi
import kotlin.math.roundToInt

/**
 * Main bubble overlay composable combining all visual components.
 *
 * @param state Current bubble state
 * @param config Bubble configuration (size, opacity, position)
 * @param isAIEnabled Whether AI processing is enabled
 * @param onTap Callback for tap gesture
 * @param onLongPress Callback for long-press gesture
 * @param onDrag Callback for drag gesture with delta offset
 * @param onDragEnd Callback when drag ends
 * @param onQuickAction Callback when a quick action is selected
 * @param modifier Modifier for the root container
 * @param bubbleSize Base size of the bubble
 */
@Composable
fun BubbleOverlayCompose(
    state: BubbleState,
    config: BubbleConfig,
    isAIEnabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
    bubbleSize: Dp = 56.dp
) {
    val context = LocalContext.current
    val isRecording = state is BubbleState.Recording
    val isMenuOpen = state is BubbleState.MenuOpen
    val isProcessing = state is BubbleState.Processing

    // Get amplitude history for ring visualization
    val amplitudeHistory = when (state) {
        is BubbleState.Recording -> state.amplitudeHistory
        else -> emptyList()
    }

    // Animation for recording pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Background color animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isRecording -> Surface1.copy(alpha = 0.9f)
            isProcessing -> Surface1.copy(alpha = 0.95f)
            else -> Surface1
        },
        animationSpec = tween(200),
        label = "backgroundColor"
    )

    // Glow color animation
    val glowColor by animateColorAsState(
        targetValue = when {
            isRecording -> PastelPink
            isProcessing -> PastelPurple
            else -> PastelBlue.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "glowColor"
    )

    // Icon tint animation
    val iconTint by animateColorAsState(
        targetValue = when {
            isRecording -> PastelPink
            isProcessing -> PastelPurple
            else -> TextHi
        },
        animationSpec = tween(200),
        label = "iconTint"
    )

    // Track drag state to prevent tap during drag
    var isDragging by remember { mutableStateOf(false) }

    // Get menu direction from state
    val menuDirection = (state as? BubbleState.MenuOpen)?.menuDirection

    // Container size: compact (90dp) to fit amplitude ring, expanded (200dp) when menu open
    // Amplitude ring = (bubbleRadius + maxBarHeight) * 2 = (28 + 14) * 2 = 84dp + shadow
    val compactSize = 90.dp
    val expandedSize = 200.dp
    val containerSize = if (isMenuOpen) expandedSize * config.scale else compactSize * config.scale

    // Bubble alignment: centered in compact mode, offset when menu is open
    val bubbleAlignment = when {
        isMenuOpen && menuDirection == MenuDirection.LEFT -> Alignment.CenterEnd
        isMenuOpen && menuDirection == MenuDirection.RIGHT -> Alignment.CenterStart
        else -> Alignment.Center  // Compact mode - always centered
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .alpha(config.opacity)
            .pointerInput(isMenuOpen) {
                // Only allow drag when menu is closed
                if (!isMenuOpen) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(Offset(dragAmount.x, dragAmount.y))
                        },
                        onDragEnd = {
                            isDragging = false
                            onDragEnd()
                        },
                        onDragCancel = { isDragging = false }
                    )
                }
            },
        contentAlignment = bubbleAlignment
    ) {
        // Quick action menu (only when expanded)
        if (isMenuOpen && menuDirection != null) {
            QuickActionMenu(
                isExpanded = true,
                isRecording = (state as? BubbleState.MenuOpen)?.previousState is BubbleState.Recording,
                isAIEnabled = isAIEnabled,
                direction = menuDirection,
                onAction = onQuickAction
            )
        }

        // Amplitude ring visualization (behind bubble)
        AmplitudeRingVisualizer(
            amplitudeHistory = amplitudeHistory,
            isActive = isRecording,
            bubbleRadius = (bubbleSize * config.scale) / 2,
            maxBarHeight = 14.dp * config.scale,
            accentColor = PastelPink,
            modifier = Modifier.scale(if (isRecording) pulseScale else 1f)
        )

        // Main bubble button
        Box(
            modifier = Modifier
                .size(bubbleSize * config.scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!isDragging) {
                                HapticUtils.performHapticFeedback(context)
                                onTap()
                            }
                        },
                        onLongPress = {
                            if (!isDragging) {
                                HapticUtils.performHapticFeedback(context)
                                onLongPress()
                            }
                        }
                    )
                }
                .scale(if (isRecording) pulseScale else 1f)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = glowColor.copy(alpha = 0.6f),
                    ambientColor = glowColor.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .background(
                    brush = if (isRecording || isProcessing) {
                        Brush.radialGradient(
                            colors = listOf(
                                backgroundColor,
                                if (isRecording) PastelPink.copy(alpha = 0.15f)
                                else PastelPurple.copy(alpha = 0.15f)
                            )
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(backgroundColor, backgroundColor)
                        )
                    }
                )
                .semantics {
                    contentDescription = when {
                        isRecording -> "Recording in progress. Tap to stop."
                        isProcessing -> "Processing transcription"
                        else -> "Tap to start recording"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(bubbleSize * 0.5f * config.scale)
            )
        }
    }
}

/**
 * Simplified bubble for idle state without menu capabilities.
 */
@Composable
fun BubbleCore(
    isRecording: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    bubbleSize: Dp = 56.dp,
    scale: Float = 1f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "core_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulseScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isRecording -> Surface1.copy(alpha = 0.9f)
            isProcessing -> Surface1.copy(alpha = 0.95f)
            else -> Surface1
        },
        animationSpec = tween(200),
        label = "coreBackgroundColor"
    )

    val glowColor = when {
        isRecording -> PastelPink
        isProcessing -> PastelPurple
        else -> PastelBlue.copy(alpha = 0.5f)
    }

    val iconTint by animateColorAsState(
        targetValue = when {
            isRecording -> PastelPink
            isProcessing -> PastelPurple
            else -> TextHi
        },
        animationSpec = tween(200),
        label = "coreIconTint"
    )

    Box(
        modifier = modifier
            .size(bubbleSize * scale)
            .scale(if (isRecording) pulseScale else 1f)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = glowColor.copy(alpha = 0.6f),
                ambientColor = glowColor.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_mic),
            contentDescription = if (isRecording) "Recording" else "Microphone",
            tint = iconTint,
            modifier = Modifier.size(bubbleSize * 0.5f * scale)
        )
    }
}
