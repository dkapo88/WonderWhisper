package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette

// Color definitions for button states
private val CancelTint = Color(0xFFFF6B6B)  // Soft red for cancel action
private val RetryTint = Color(0xFFFFB347)   // Soft orange for retry attention

/**
 * Context-aware dynamic button that morphs between Undo, Cancel, and Retry states.
 *
 * Visual states:
 * - **Undo** (default): Shows undo icon, standard styling, dimmed if canUndo is false
 * - **Cancel** (during recording): Shows X icon with red background tint
 * - **Retry** (after failure): Shows refresh icon with orange background tint, rotates during retry
 *
 * @param state Current button state from DictationBarViewModel.dynamicButtonState
 * @param canUndo Whether undo is available (affects Undo state appearance)
 * @param onUndo Callback when undo is tapped
 * @param onCancel Callback when cancel recording is tapped
 * @param onRetry Callback when retry is tapped
 * @param modifier Optional modifier (should include weight for layout)
 */
@Composable
fun DynamicButton(
    state: DynamicButtonState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Crossfade between three distinct button states
    Crossfade(
        targetState = state,
        animationSpec = tween(200),
        modifier = modifier,
        label = "dynamic-button-morph"
    ) { currentState ->
        when (currentState) {
            is DynamicButtonState.Undo -> {
                UndoButtonContent(
                    canUndo = canUndo,
                    onClick = {
                        if (canUndo) {
                            HapticUtils.performKeyClick(context)
                            onUndo()
                        }
                    }
                )
            }
            is DynamicButtonState.Cancel -> {
                CancelButtonContent(
                    onClick = {
                        HapticUtils.performKeyClick(context)
                        onCancel()
                    }
                )
            }
            is DynamicButtonState.Retry -> {
                RetryButtonContent(
                    isRetrying = currentState.isRetrying,
                    onClick = {
                        if (!currentState.isRetrying) {
                            // Haptic on entering retry
                            HapticUtils.performGesturalFeedback(context)
                            onRetry()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Undo button content - shows undo icon, dimmed when unavailable.
 */
@Composable
private fun UndoButtonContent(
    canUndo: Boolean,
    onClick: () -> Unit
) {
    val colors = KeyboardPalette.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "undoButtonScale"
    )

    val alpha = if (canUndo) 1f else 0.4f

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = canUndo,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_undo),
            contentDescription = if (canUndo) "Undo" else "Nothing to undo",
            tint = colors.keyTextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Cancel button content - shows X icon with red background.
 */
@Composable
private fun CancelButtonContent(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "cancelButtonScale"
    )

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(CancelTint.copy(alpha = 0.2f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Cancel Recording",
            tint = CancelTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Retry button content - shows refresh icon with orange background, rotates during retry.
 */
@Composable
private fun RetryButtonContent(
    isRetrying: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "retryButtonScale"
    )

    // Infinite rotation animation for retry in progress
    val infiniteTransition = rememberInfiniteTransition(label = "retryRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "retryRotationValue"
    )

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // Only apply rotation when actively retrying
                if (isRetrying) {
                    rotationZ = rotation
                }
            }
            .background(RetryTint.copy(alpha = 0.2f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isRetrying,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = if (isRetrying) "Retrying..." else "Retry Transcription",
            tint = RetryTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
