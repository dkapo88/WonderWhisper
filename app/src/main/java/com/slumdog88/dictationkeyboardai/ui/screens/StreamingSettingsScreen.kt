package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StreamingSettingsScreenDM(settingsManager: SettingsManager) {
    val context = LocalContext.current
    // Removed amplitudeRange
    val minSpeechRange = 100f..1000f
    val hangoverRange = 200f..1500f
    
    var vadMode by remember {
        mutableStateOf(settingsManager.getVadMode())
    }
    var minSpeechDuration by remember {
        mutableFloatStateOf(
            settingsManager.getVadMinSpeechDuration().toFloat()
                .coerceIn(minSpeechRange.start, minSpeechRange.endInclusive)
        )
    }
    var hangoverDuration by remember {
        mutableFloatStateOf(
            settingsManager.getVadHangoverDuration().toFloat()
                .coerceIn(hangoverRange.start, hangoverRange.endInclusive)
        )
    }
    var customInstructions by remember {
        mutableStateOf(settingsManager.getStreamingCustomInstructions())
    }
    var isAlwaysStreaming by remember {
        mutableStateOf(settingsManager.isAlwaysUseStreamingDictationEnabled())
    }

    var tuningExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Streaming Dictation",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Instructions and Toggle Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                     Icon(
                        imageVector = ImageVector.vectorResource(id = com.slumdog88.dictationkeyboardai.R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "To start a streaming dictation session, double-tap on the microphone button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Always use streaming dictation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAlwaysStreaming) 
                                "Single tap and double tap will both start streaming dictation." 
                            else 
                                "Single tap starts standard dictation. Double tap starts streaming.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = isAlwaysStreaming,
                        onCheckedChange = { enabled ->
                            isAlwaysStreaming = enabled
                            settingsManager.setAlwaysUseStreamingDictationEnabled(enabled)
                            HapticUtils.performHapticFeedback(context)
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        Text(
            text = "• Voice-driven mode with live LLM clean-up and formatting.\n" +
                "• Speak natural dictation or command phrases (e.g. \"command change intro\").\n" +
                "• Each chunk is transcribed, sent to the LLM, and merged back into the document.\n" +
                "• Custom AI Instructions layer on top of the default rules—bullet lists work best.\n" +
                "• Screen context + vocabulary are passed to the LLM for smarter spelling and names.\n" +
                "• Use the fastest transcription and LLM models available for the snappiest experience.\n" +
                "• Recommended combos: Groq Whisper Turbo + OSS-120B; Cerebras models are great too. Claude Haiku 4.5 or Gemini Flash 2.0 balance quality and speed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Custom AI Instructions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Optional extra guidance the formatter should follow (tone, structure, domain-specific rules).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = customInstructions,
            onValueChange = { value ->
                customInstructions = value
                settingsManager.setStreamingCustomInstructions(value)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            placeholder = { Text("e.g. Use British spelling and keep paragraphs under five sentences.") },
            supportingText = {
                Text("Leave blank to use only the default rules.")
            },
            minLines = 4,
            maxLines = 8
        )

        Card(
            onClick = {
                tuningExpanded = !tuningExpanded
                HapticUtils.performHapticFeedback(context)
            },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Streaming Detection Tuning",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Adjust sensitivity, minimum speech duration, and hangover window. Defaults: 0.010 RMS · 100 ms · 400 ms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (tuningExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (tuningExpanded) "Collapse detection settings" else "Expand detection settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (tuningExpanded) {
                    Text(
                        text = "Fine-tune how voice activity detection captures each chunk. Higher confidence reduces false positives; longer hangover waits longer before closing a chunk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VadSettingCard(
                        title = "Detection Confidence",
                        subtitle = vadMode.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("normal", "aggressive", "very_aggressive").forEach { mode ->
                                val isSelected = vadMode.equals(mode, ignoreCase = true)
                                val label = when(mode) {
                                    "very_aggressive" -> "Very Agg."
                                    else -> mode.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                                }
                                Button(
                                    onClick = {
                                        vadMode = mode
                                        settingsManager.setVadMode(mode)
                                        HapticUtils.performHapticFeedback(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Controls how strict the neural network is about speech patterns.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VadSettingCard(
                        title = "Minimum Speech Duration",
                        subtitle = "${minSpeechDuration.roundToInt()} ms"
                    ) {
                        Slider(
                            value = minSpeechDuration,
                            onValueChange = { raw ->
                                val snapped = ((raw / 50f).roundToInt() * 50).toFloat()
                                minSpeechDuration = snapped.coerceIn(minSpeechRange.start, minSpeechRange.endInclusive)
                            },
                            onValueChangeFinished = {
                                settingsManager.setVadMinSpeechDuration(minSpeechDuration.roundToInt())
                            },
                            valueRange = minSpeechRange,
                            steps = ((minSpeechRange.endInclusive - minSpeechRange.start) / 50f).roundToInt() - 1
                        )
                        Text(
                            text = "Time the detector waits before committing that speech really started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VadSettingCard(
                        title = "Endpoint Hangover",
                        subtitle = "${hangoverDuration.roundToInt()} ms"
                    ) {
                        Slider(
                            value = hangoverDuration,
                            onValueChange = { raw ->
                                val snapped = ((raw / 50f).roundToInt() * 50).toFloat()
                                hangoverDuration = snapped.coerceIn(hangoverRange.start, hangoverRange.endInclusive)
                            },
                            onValueChangeFinished = {
                                settingsManager.setVadHangoverDuration(hangoverDuration.roundToInt())
                            },
                            valueRange = hangoverRange,
                            steps = ((hangoverRange.endInclusive - hangoverRange.start) / 50f).roundToInt() - 1
                        )
                        Text(
                            text = "How long silence is allowed before we close a chunk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                settingsManager.resetVadTuning()
                vadMode = "normal"
                minSpeechDuration = SettingsManager.DEFAULT_VAD_MIN_SPEECH_MS.toFloat()
                hangoverDuration = SettingsManager.DEFAULT_VAD_HANGOVER_MS.toFloat()
                HapticUtils.performHapticFeedback(context)
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Reset to Defaults")
        }

        Text(
            text = "Changes apply the next time you start a streaming session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VadSettingCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}
