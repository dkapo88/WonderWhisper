package com.slumdog88.dictationkeyboardai.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.Radii
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults

@Composable
private fun BubbleOpacityCard(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Bubble Opacity",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Slider(
                value = value.coerceAtLeast(10f),
                onValueChange = { newValue ->
                    val v = newValue.coerceAtLeast(10f)
                    onValueChange(v)
                },
                valueRange = 0f..100f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceVariant
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${value.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Adjust how transparent the bubble appears",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BubbleSizeCard(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Bubble Size",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 50f..150f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceVariant
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${value.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Adjust the size of the bubble (50% - 150%)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SimpleSettingsScreenDM(
    sharedPreferences: SharedPreferences,
    onNavigateToNotepad: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val isSimpleMode = remember { settingsManager.isSimpleMode() }

    // Load state from SharedPreferences
    var aiPostProcessing by remember {
        mutableStateOf(sharedPreferences.getBoolean("enable_postprocess", false))
    }
    var screenContext by remember {
        mutableStateOf(sharedPreferences.getBoolean("include_screen_context", false))
    }
    var keyboardAwareBubble by remember {
        mutableStateOf(sharedPreferences.getBoolean("keyboard_aware_bubble", true))
    }
    var englishVariant by remember {
        mutableStateOf(sharedPreferences.getString("english_variant", "british") ?: "british")
    }
    var dictationMode by remember {
        mutableStateOf(sharedPreferences.getString("simple_dictation_mode", "fast") ?: "fast")
    }
    var bubbleOverlayEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("bubble_overlay_enabled", true))
    }
    var bubbleOpacity by remember {
        mutableFloatStateOf(sharedPreferences.getInt("bubble_opacity", 80).toFloat())
    }
    var bubbleSize by remember {
        mutableFloatStateOf(sharedPreferences.getInt("bubble_size", 100).toFloat())
    }
    var commandWord by remember {
        mutableStateOf(sharedPreferences.getString("command_word", "command") ?: "command")
    }
    var streamingDictationEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("streaming_dictation_enabled", false))
    }

    LaunchedEffect(commandWord) {
        if (commandWord.isNotBlank()) {
            sharedPreferences.edit().putString("command_word", commandWord.trim()).apply()
        }
    }

    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.background,
                        colors.surfaceVariant,
                        colors.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.onBackground
                    )
                }
                // Back action placeholder: use onBack when you want a top-app bar
                Spacer(Modifier.height(0.dp))
            }

            Spacer(Modifier.height(12.dp))

            // English Variant (British/American) — moved to top for visibility
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "English Variant",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    val ctx = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                englishVariant = "british"
                                sharedPreferences.edit()
                                    .putString("english_variant", "british")
                                    .putString("selected_dictation_prompt_id", "default_dictation")
                                    .apply()
                                HapticUtils.performHapticFeedback(ctx)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (englishVariant == "british") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (englishVariant == "british") Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("British") }

                        Button(
                            onClick = {
                                englishVariant = "american"
                                sharedPreferences.edit()
                                    .putString("english_variant", "american")
                                    .putString("selected_dictation_prompt_id", "default_dictation")
                                    .apply()
                                HapticUtils.performHapticFeedback(ctx)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (englishVariant == "american") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (englishVariant == "american") Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("American") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Affects Simple Mode dictation prompt (spelling and style).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Keyboard Settings
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Keyboard Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Customize keyboard theme and layout.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            com.slumdog88.dictationkeyboardai.KeyboardSettingsActivity.start(context)
                            HapticUtils.performHapticFeedback(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Configure Keyboard", color = Color.Black)
                    }
                }
            }
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Dictation Style",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    val ctx = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (dictationMode != "fast") {
                                    dictationMode = "fast"
                                    sharedPreferences.edit()
                                        .putString("simple_dictation_mode", "fast")
                                        .putString("selected_dictation_prompt_id", "default_dictation")
                                        .apply()
                                    HapticUtils.performHapticFeedback(ctx)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dictationMode == "fast") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (dictationMode == "fast") Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("Fast") }

                        Button(
                            onClick = {
                                if (dictationMode != "accurate") {
                                    dictationMode = "accurate"
                                    sharedPreferences.edit()
                                        .putString("simple_dictation_mode", "accurate")
                                        .putString("selected_dictation_prompt_id", "default_dictation")
                                        .apply()
                                    HapticUtils.performHapticFeedback(ctx)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dictationMode == "accurate") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (dictationMode == "accurate") Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("Accurate") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Choose between Dictation Fast for speed or Dictation Accurate for thorough formatting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bubble Overlay (master enable)
            SettingSwitchCard(
                title = "Bubble Overlay",
                subtitle = "Enable or disable the floating mic bubble",
                isChecked = bubbleOverlayEnabled,
                onCheckedChange = {
                    bubbleOverlayEnabled = it
                    sharedPreferences.edit().putBoolean("bubble_overlay_enabled", it).apply()
                }
            )

            Spacer(Modifier.height(12.dp))

            // AI Post-Processing
            SettingSwitchCard(
                title = "AI Post-Processing",
                subtitle = "Improves grammar, punctuation, formatting",
                isChecked = aiPostProcessing,
                onCheckedChange = {
                    aiPostProcessing = it
                    sharedPreferences.edit().putBoolean("enable_postprocess", it).apply()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Screen Context
            SettingSwitchCard(
                title = "Screen Context",
                subtitle = "Read screen content for better AI suggestions",
                isChecked = screenContext,
                onCheckedChange = {
                    screenContext = it
                    sharedPreferences.edit().putBoolean("include_screen_context", it).apply()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Keyboard Aware Bubble
            SettingSwitchCard(
                title = "Keyboard-Aware Bubble",
                subtitle = "Auto-show bubble when keyboard opens",
                isChecked = keyboardAwareBubble,
                onCheckedChange = {
                    keyboardAwareBubble = it
                    sharedPreferences.edit().putBoolean("keyboard_aware_bubble", it).apply()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Bubble Opacity
            BubbleOpacityCard(
                value = bubbleOpacity,
                onValueChange = { newValue ->
                    bubbleOpacity = newValue
                    sharedPreferences.edit().putInt("bubble_opacity", newValue.toInt()).apply()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Bubble Size
            BubbleSizeCard(
                value = bubbleSize,
                onValueChange = { newValue ->
                    bubbleSize = newValue
                    sharedPreferences.edit().putInt("bubble_size", newValue.toInt()).apply()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Command Word
            CommandWordCard(
                value = commandWord,
                onValueChange = {
                    commandWord = it
                }
            )

            Spacer(Modifier.height(12.dp))

            StreamingModeCard(
                isChecked = streamingDictationEnabled,
                isSimpleMode = isSimpleMode,
                onCheckedChange = { enabled ->
                    if (isSimpleMode) return@StreamingModeCard
                    streamingDictationEnabled = enabled
                    sharedPreferences.edit()
                        .putBoolean("streaming_dictation_enabled", enabled)
                        .apply()
                    if (enabled && sharedPreferences.getString("streaming_ai_model", null).isNullOrBlank()) {
                        sharedPreferences.edit()
                            .putString("streaming_ai_model", "groq/openai/gpt-oss-20b")
                            .apply()
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // Notepad quick action
            NotepadCard(
                onOpenNotepad = onNavigateToNotepad
            )

            Spacer(Modifier.height(12.dp))

            // Tips
            TipsCard()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = colors.onSurfaceVariant,
                        uncheckedTrackColor = colors.surfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun StreamingModeCard(
    isChecked: Boolean,
    isSimpleMode: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Streaming Dictation (Preview)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Pseudo-streaming speech-to-text with chunked Groq Whisper Turbo and real-time AI formatting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isChecked,
                    onCheckedChange = {
                        if (!isSimpleMode) {
                            onCheckedChange(it)
                        }
                    },
                    enabled = !isSimpleMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = colors.onSurfaceVariant,
                        uncheckedTrackColor = colors.surfaceVariant
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isSimpleMode) {
                    "Available in Pro mode. Switch to Pro to configure streaming dictation."
                } else {
                    "Requires Groq Whisper Turbo for transcription and Groq or Cerebras LLMs for formatting."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommandWordCard(
    value: String,
    onValueChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Command Word",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("command") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceVariant,
                    unfocusedContainerColor = colors.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = colors.primary,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedPlaceholderColor = colors.onSurfaceVariant,
                    unfocusedPlaceholderColor = colors.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = Radii.medium,
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Start your speech with this word to trigger AI command mode.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotepadCard(
    onOpenNotepad: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Voice Notes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Create voice notes that you can edit, copy, and share. Use the notification 'Take Note' button to start recording.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenNotepad,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Open Notepad", color = Color.Black)
            }
        }
    }
}

@Composable
private fun TipsCard() {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Quick Tips",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "• Speak clearly and pause between sentences\n" +
                    "• Select text before dictating to replace it\n" +
                    "• Use custom vocabulary for better accuracy\n" +
                    "• AI processing works best with natural speech\n" +
                    "• Command mode can answer questions directly\n" +
                    "• The bubble works in ANY app with text fields",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}
