package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Import AmplitudeRing for visual amplitude feedback

/**
 * Central microphone button for the dictation bar with three gesture modes.
 *
 * Gesture Modes:
 * 1. **Tap-toggle**: Quick tap (< 300ms) toggles recording on/off
 * 2. **Double-tap**: Two quick taps within 500ms triggers streaming mode
 * 3. **Hold-to-talk**: Press > 300ms starts recording, release stops recording
 *
 * Visual States:
 * 1. **Idle** (not recording, not processing):
 *    - Background: PastelBlue
 *    - Icon: Microphone, white tint
 *    - No pulse animation
 *
 * 2. **Recording** (isRecording && !isProcessing):
 *    - Background: PastelBlue (same as idle)
 *    - Icon: Microphone, white tint
 *    - Pulse animation: Base 1.0f -> 1.05f over 600ms + amplitude boost (0-15%)
 *
 * 3. **Holding** (isHolding):
 *    - Same as recording, but scaled up to 1.03f for subtle visual feedback
 *
 * 4. **Processing** (isProcessing):
 *    - Background: PastelBlue
 *    - Content: TypingIndicator (three pulsing dots)
 *    - No pulse animation
 *
 * Features:
 * - 64dp circular button
 * - Press animation: Scales to 0.92f (before hold triggers)
 * - Hold animation: Scales to 1.03f (during hold-to-talk)
 * - Haptic feedback: Strong on hold start, light tick on hold end
 * - Amplitude-reactive pulse during recording
 *
 * @param isRecording Whether dictation recording is active
 * @param isProcessing Whether transcription processing is in progress
 * @param isHolding Whether hold-to-talk is currently active (finger down > 300ms)
 * @param currentAmplitude Normalized amplitude value (0.0-1.0)
 * @param onClick Callback when button is tapped (single tap for toggle)
 * @param onDoubleTap Callback when button is double-tapped (for streaming mode)
 * @param onHoldStart Callback when hold-to-talk activates (300ms threshold reached)
 * @param onHoldEnd Callback when user releases after hold-to-talk
 * @param modifier Optional modifier for the button
 */
@Composable
fun MicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    isHolding: Boolean,
    currentAmplitude: Float,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit = {},
    onHoldStart: () -> Unit = {},
    onHoldEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Track last tap time for double-tap detection
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Track pressed state manually since we're using pointerInput instead of clickable
    var isPressed by remember { mutableStateOf(false) }

    // Track if long press was triggered to prevent tap from firing after release
    var longPressTriggered by remember { mutableStateOf(false) }

    // Press scale (applied to outer container including ring): pressed (92%) -> idle (100%) -> holding (103%)
    val targetPressScale = when {
        isPressed && !longPressTriggered -> 0.92f  // Pressing, not yet triggered
        isHolding -> 1.03f  // Holding active - slightly larger
        else -> 1f
    }
    val pressScale by animateFloatAsState(
        targetValue = targetPressScale,
        animationSpec = tween(durationMillis = 100),
        label = "micPressScale"
    )

    // Recording pulse animation (only active when recording and not processing, not during hold)
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micBasePulse"
    )

    // Calculate recording scale for inner button only (skip during hold since hold has its own scale feedback)
    // Amplitude boost adds 0% to 15% additional scale based on normalized amplitude
    val recordingScale = if (isRecording && !isHolding && !isProcessing) {
        basePulse * (1f + currentAmplitude * 0.15f)
    } else {
        1f
    }

    // Outer container: holds both ring and button, applies press scale
    Box(
        modifier = modifier
            .size(72.dp)  // Slightly larger to accommodate ring expansion
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center
    ) {
        // Amplitude ring layer (behind button)
        AmplitudeRing(
            amplitude = currentAmplitude,
            isRecording = isRecording && !isProcessing,
            modifier = Modifier.size(72.dp)
        )

        // Get theme colors for mic button
        val colors = KeyboardPalette.colors
        val micBackground = if (isRecording) colors.micActiveBackground else colors.micIdleBackground

        // Main button circle
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = recordingScale  // Recording pulse stays on button only
                    scaleY = recordingScale
                }
                .clip(CircleShape)
                .background(micBackground)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        isPressed = true
                        longPressTriggered = false

                        // Launch coroutine for 300ms long-press detection
                        val longPressJob = coroutineScope.launch {
                            delay(300L)  // Standard Android long-press threshold
                            longPressTriggered = true
                            HapticUtils.performGesturalFeedback(context)  // Strong haptic
                            onHoldStart()
                        }

                        // Wait for release or cancellation
                        val up = waitForUpOrCancellation()
                        longPressJob.cancel()
                        isPressed = false

                        if (up != null) {
                            up.consume()

                            when {
                                longPressTriggered -> {
                                    // Release from hold-to-talk
                                    HapticUtils.performToggleOff(context)  // Light tick
                                    onHoldEnd()
                                }
                                else -> {
                                    // Quick tap - check for double-tap
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastTapTime < 500) {
                                        // Double tap detected
                                        HapticUtils.performKeyClick(context)
                                        onDoubleTap()
                                        lastTapTime = 0L  // Reset to prevent triple-tap
                                    } else {
                                        // Single tap
                                        HapticUtils.performKeyClick(context)
                                        onClick()
                                        lastTapTime = currentTime
                                    }
                                }
                            }
                        } else {
                            // Gesture cancelled (finger dragged off)
                            if (longPressTriggered) {
                                // Still stop recording even if cancelled
                                HapticUtils.performToggleOff(context)
                                onHoldEnd()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isProcessing -> {
                    // Processing state: Show pulsing dots
                    TypingIndicator(color = colors.micIconColor)
                }
                else -> {
                    // Idle and Recording states: Show mic icon
                    // (Recording pulse is handled by button's graphicsLayer scale)
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = colors.micIconColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
