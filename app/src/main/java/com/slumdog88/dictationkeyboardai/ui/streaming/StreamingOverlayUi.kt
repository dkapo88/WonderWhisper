package com.slumdog88.dictationkeyboardai.ui.streaming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.transcription.streaming.StreamingUiState

data class StreamingUiActions(
    val onStop: () -> Unit,
    val onCancel: () -> Unit,
    val onCopy: () -> Unit
)

@Composable
fun StreamingOverlayContent(
    state: StreamingUiState,
    actions: StreamingUiActions
) {
    val colors = MaterialTheme.colorScheme
    val statusText = when {
        state.statusMessage.isNotBlank() -> state.statusMessage
        state.isRecording -> "Listening…"
        else -> ""
    }
    val mainText = state.formattedTranscript.ifBlank { "Transcript will appear here once processing completes." }

    Surface(
        modifier = Modifier
            .widthIn(min = 320.dp, max = 420.dp)
            .clip(RoundedCornerShape(20.dp))
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        color = colors.surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.35f))
                    .padding(16.dp)
                    .heightIn(min = 140.dp, max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = actions.onStop,
                    enabled = state.controlsEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Stop and paste"
                    )
                }

                Button(
                    onClick = actions.onCancel,
                    enabled = state.controlsEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceVariant,
                        contentColor = colors.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Cancel recording"
                    )
                }

                Button(
                    onClick = actions.onCopy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceVariant,
                        contentColor = colors.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = "Copy transcript"
                    )
                }
            }
        }
    }
}
