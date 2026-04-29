package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Backspace button with three-stage accelerating delete behavior.
 *
 * Delete acceleration stages:
 * 1. **Initial press:** Immediate single character delete, then 400ms pause to confirm intent
 * 2. **Accelerating character delete:** Starts at 100ms intervals, accelerates to 30ms minimum
 *    - Each interval is multiplied by 0.9 (100 -> 90 -> 81 -> 73 -> ...)
 *    - Per-character haptic feedback
 * 3. **Word deletion mode:** After 15 character deletes, switches to word-level deletion
 *    - Fixed 200ms interval for control
 *    - Stronger haptic feedback to warn user
 *
 * Visual feedback:
 * - Press scale animation (0.92f) matching UtilityButton
 * - 48dp touch target with 24dp icon
 *
 * @param onDelete Callback for single character deletion
 * @param onDeleteWord Callback for word deletion
 * @param modifier Optional modifier for layout (supports weight distribution)
 */
@Composable
fun BackspaceButton(
    onDelete: () -> Unit,
    onDeleteWord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = KeyboardPalette.colors
    val scope = rememberCoroutineScope()

    var isPressed by remember { mutableStateOf(false) }
    var deleteJob by remember { mutableStateOf<Job?>(null) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "backspaceScale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        isPressed = true

                        // Immediate first delete on press
                        onDelete()
                        HapticUtils.performKeyClick(context)

                        // Start acceleration job
                        deleteJob = scope.launch {
                            // Stage 1: Wait 400ms before repeat (confirm intent)
                            delay(400L)

                            // Stage 2: Accelerating character delete
                            var interval = 100L
                            var deleteCount = 0
                            val wordModeThreshold = 15

                            while (deleteCount < wordModeThreshold) {
                                onDelete()
                                HapticUtils.performKeyClick(context)
                                deleteCount++

                                // Accelerate: 100 -> 90 -> 81 -> 73 -> ... -> min 30ms
                                delay(interval)
                                interval = (interval * 0.9f).toLong().coerceAtLeast(30L)
                            }

                            // Stage 3: Word deletion with slower pace and stronger haptic
                            while (true) {
                                onDeleteWord()
                                HapticUtils.performGesturalFeedback(context)
                                delay(200L)
                            }
                        }

                        // Wait for release
                        waitForUpOrCancellation()
                        isPressed = false
                        deleteJob?.cancel()
                        deleteJob = null
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_backspace),
                contentDescription = "Backspace",
                tint = colors.keyTextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
