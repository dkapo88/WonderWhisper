package com.slumdog88.dictationkeyboardai.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.PastelBlue
import com.slumdog88.dictationkeyboardai.ui.theme.Surface1
import com.slumdog88.dictationkeyboardai.ui.theme.TextDim
import com.slumdog88.dictationkeyboardai.ui.theme.TextHi

/**
 * Voice recognizer state for the floating popup
 */
enum class VoiceRecognizerState {
    LISTENING,
    PROCESSING,
    ERROR
}

/**
 * Voice Recognizer Screen - Bottom bar UI for external voice recognition requests.
 * This screen is shown when other keyboards (like SwiftKey) trigger the RECOGNIZE_SPEECH intent.
 * 
 * The UI appears at the bottom of the screen as a minimal horizontal bar, allowing users
 * to see more of their context while dictating.
 */
@Composable
fun VoiceRecognizerScreen(
    state: VoiceRecognizerState,
    amplitude: Int = 0,
    errorMessage: String? = null,
    onCancel: () -> Unit,
    onStop: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Tap outside to cancel
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCancel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bottom bar container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Don't propagate clicks on bar */ },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Surface1,
            shadowElevation = 16.dp
        ) {
            // Main content based on state
            when (state) {
                VoiceRecognizerState.LISTENING -> {
                    ListeningContent(
                        amplitude = amplitude,
                        onStop = onStop,
                        onCancel = onCancel
                    )
                }
                VoiceRecognizerState.PROCESSING -> {
                    ProcessingContent(onCancel = onCancel)
                }
                VoiceRecognizerState.ERROR -> {
                    ErrorContent(
                        message = errorMessage ?: "An error occurred",
                        onRetry = onRetry,
                        onCancel = onCancel
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningContent(
    amplitude: Int,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    // Animated pulse for the mic button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Calculate normalized amplitude (0-1)
    val normalizedAmplitude = (amplitude.coerceIn(0, 32767) / 32767f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cancel button (small X)
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                contentDescription = "Cancel",
                tint = TextDim,
                modifier = Modifier.size(20.dp)
            )
        }

        // Status text and amplitude visualization
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Listening...",
                color = TextHi,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            // Simple amplitude bar visualization
            AmplitudeBar(
                normalizedAmplitude = normalizedAmplitude,
                modifier = Modifier.weight(1f)
            )
        }

        // Large stop/mic button
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(PastelBlue)
                .clickable { onStop() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_mic_white),
                contentDescription = "Tap to stop recording",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun AmplitudeBar(
    normalizedAmplitude: Float,
    modifier: Modifier = Modifier
) {
    // Simple animated bar showing audio amplitude
    val barWidth = 100.dp * (0.3f + normalizedAmplitude * 0.7f)
    
    Canvas(
        modifier = modifier
            .height(8.dp)
    ) {
        // Background track
        drawRoundRect(
            color = PastelBlue.copy(alpha = 0.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        // Active level
        drawRoundRect(
            color = PastelBlue.copy(alpha = 0.6f + normalizedAmplitude * 0.4f),
            size = size.copy(width = size.width * (0.3f + normalizedAmplitude * 0.7f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
    }
}

@Composable
private fun ProcessingContent(onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cancel button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                contentDescription = "Cancel",
                tint = TextDim,
                modifier = Modifier.size(20.dp)
            )
        }

        // Status text
        Text(
            text = "Processing...",
            color = TextHi,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        // Spinner in place of mic button
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = PastelBlue,
                strokeWidth = 3.dp
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)?,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cancel/close button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                contentDescription = "Close",
                tint = TextDim,
                modifier = Modifier.size(20.dp)
            )
        }

        // Error icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_warning),
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }

        // Error message
        Text(
            text = message,
            color = TextDim,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Retry button (if available)
        if (onRetry != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PastelBlue)
                    .clickable { onRetry() }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Retry",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
