package com.slumdog88.dictationkeyboardai.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardLayoutRepository
import com.slumdog88.dictationkeyboardai.ui.theme.AppThemeMode
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import com.slumdog88.dictationkeyboardai.ui.theme.Radii
import com.slumdog88.dictationkeyboardai.ui.theme.ThemeManager

@Composable
fun KeyboardSettingsScreenDM(
    onBack: () -> Unit
) {
    val sentenceEndAutocorrectKey = "keyboard_ai_autocorrect_on_sentence_end"
    val sentenceEndAutocorrectLegacyKey = "keyboard_ai_autocorrect_on_period"
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    // Theme State
    var currentTheme by remember { mutableStateOf(ThemeManager.themeMode) }

    // Layout State
    val layoutRepo = remember { KeyboardLayoutRepository(context) }
    val availableLayouts = remember {
        layoutRepo.availableLayouts()
            .filter { it.category == "alphabet" }
            .sortedBy { it.name }
    }
    val keyboardPrefs = remember { context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE) }
    var currentLayoutName by remember {
        mutableStateOf(keyboardPrefs.getString("keyboard_layout_name", "DefaultQwerty") ?: "DefaultQwerty")
    }
    var heightScale by remember {
        mutableFloatStateOf(keyboardPrefs.getFloat("keyboard_height_scale", 1.0f))
    }
    var opacity by remember {
        mutableFloatStateOf(keyboardPrefs.getFloat("keyboard_opacity", 0.85f))
    }
    var hapticStrength by remember {
        mutableFloatStateOf(keyboardPrefs.getFloat("keyboard_haptic_strength", 1.0f))
    }
    var bottomPadding by remember {
        mutableFloatStateOf(keyboardPrefs.getFloat("keyboard_bottom_padding", 0f))
    }

    var showNumberRow by remember {
        mutableStateOf(keyboardPrefs.getBoolean("keyboard_show_number_row", false))
    }

    var keyboardButtonReversed by remember {
        mutableStateOf(keyboardPrefs.getBoolean("keyboard_button_reversed", false))
    }
    val initialSentenceEndAutocorrectEnabled = remember {
        if (keyboardPrefs.contains(sentenceEndAutocorrectKey)) {
            keyboardPrefs.getBoolean(sentenceEndAutocorrectKey, false)
        } else {
            val legacyValue = keyboardPrefs.getBoolean(sentenceEndAutocorrectLegacyKey, false)
            if (keyboardPrefs.contains(sentenceEndAutocorrectLegacyKey)) {
                keyboardPrefs.edit()
                    .putBoolean(sentenceEndAutocorrectKey, legacyValue)
                    .remove(sentenceEndAutocorrectLegacyKey)
                    .apply()
            }
            legacyValue
        }
    }
    var aiAutocorrectOnPeriod by remember {
        mutableStateOf(initialSentenceEndAutocorrectEnabled)
    }

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
                .verticalScroll(scrollState)
                .padding(16.dp),
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
                Text(
                    text = "KEYBOARD",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onBackground
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Theme Selection Card
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Keyboard Theme",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose the visual style of your keyboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var expanded by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = currentTheme.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Theme") },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(
                                            id = if (expanded) com.slumdog88.dictationkeyboardai.R.drawable.ic_arrow_up else com.slumdog88.dictationkeyboardai.R.drawable.ic_arrow_down
                                        ),
                                        contentDescription = "Expand"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outline
                            )
                        )
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(colors.surfaceContainer)
                        ) {
                            AppThemeMode.values().forEach { theme ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = theme.name,
                                            color = if (theme == currentTheme) colors.primary else colors.onSurface
                                        )
                                    },
                                    onClick = {
                                        ThemeManager.setMode(context, theme)
                                        currentTheme = theme
                                        HapticUtils.performHapticFeedback(context)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Autocorrect on Full Stop Toggle
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newValue = !aiAutocorrectOnPeriod
                            aiAutocorrectOnPeriod = newValue
                            keyboardPrefs.edit().putBoolean(sentenceEndAutocorrectKey, newValue).apply()
                            HapticUtils.performHapticFeedback(context)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Autocorrect on Sentence End",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "When you type '.', '?' or '!', only that sentence segment is AI-corrected and safely replaced on exact match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aiAutocorrectOnPeriod,
                        onCheckedChange = {
                            aiAutocorrectOnPeriod = it
                            keyboardPrefs.edit().putBoolean(sentenceEndAutocorrectKey, it).apply()
                            HapticUtils.performHapticFeedback(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primaryContainer,
                            uncheckedThumbColor = colors.outline,
                            uncheckedTrackColor = colors.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Number Row Toggle
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newValue = !showNumberRow
                            showNumberRow = newValue
                            keyboardPrefs.edit().putBoolean("keyboard_show_number_row", newValue).apply()
                            HapticUtils.performHapticFeedback(context)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Number Row",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Show a dedicated row of numbers at the top",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showNumberRow,
                        onCheckedChange = {
                            showNumberRow = it
                            keyboardPrefs.edit().putBoolean("keyboard_show_number_row", it).apply()
                            HapticUtils.performHapticFeedback(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primaryContainer,
                            uncheckedThumbColor = colors.outline,
                            uncheckedTrackColor = colors.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keyboard Button Behavior Toggle
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newValue = !keyboardButtonReversed
                            keyboardButtonReversed = newValue
                            keyboardPrefs.edit().putBoolean("keyboard_button_reversed", newValue).apply()
                            HapticUtils.performHapticFeedback(context)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keyboard Button Action",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (keyboardButtonReversed) 
                                "Tap: Open built-in keyboard • Hold: Switch to other keyboard" 
                            else 
                                "Tap: Switch to other keyboard • Hold: Open built-in keyboard",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = keyboardButtonReversed,
                        onCheckedChange = {
                            keyboardButtonReversed = it
                            keyboardPrefs.edit().putBoolean("keyboard_button_reversed", it).apply()
                            HapticUtils.performHapticFeedback(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primaryContainer,
                            uncheckedThumbColor = colors.outline,
                            uncheckedTrackColor = colors.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Height Selection Card
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Keyboard Height",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust the vertical size of the keyboard keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = heightScale,
                            onValueChange = { heightScale = it },
                            onValueChangeFinished = {
                                keyboardPrefs.edit().putFloat("keyboard_height_scale", heightScale).apply()
                            },
                            valueRange = 0.7f..1.4f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${(heightScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transparency Selection Card
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Background Transparency",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust how see-through the keyboard background appears",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            onValueChangeFinished = {
                                keyboardPrefs.edit()
                                    .putFloat("keyboard_opacity", opacity)
                                    .apply()
                            },
                            valueRange = 0.2f..1f, // Min 20% to ensure visibility
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${(opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                            modifier = Modifier.width(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inline preview
                    DictationBarPreview(opacity = opacity)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Haptic Strength Card
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Haptic Strength",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Control how strong keyboard vibration feedback feels",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = hapticStrength,
                            onValueChange = { hapticStrength = it },
                            onValueChangeFinished = {
                                HapticUtils.setHapticStrength(context, hapticStrength)
                                HapticUtils.performKeyClick(context)
                            },
                            valueRange = 0f..2f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${(hapticStrength * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Padding Card
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Bottom Padding",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add extra space below keyboard for devices with gesture navigation",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = bottomPadding,
                            onValueChange = { bottomPadding = it },
                            onValueChangeFinished = {
                                keyboardPrefs.edit()
                                    .putFloat("keyboard_bottom_padding", bottomPadding)
                                    .apply()
                            },
                            valueRange = 0f..48f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${bottomPadding.toInt()}dp",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Layout Selection Card
            if (availableLayouts.isNotEmpty()) {
                Card(
                    shape = Radii.large,
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Keyboard Layout",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select your preferred language layout",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        availableLayouts.forEach { layout ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentLayoutName = layout.name
                                        keyboardPrefs.edit().putString("keyboard_layout_name", layout.name).apply()
                                        HapticUtils.performHapticFeedback(context)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (layout.name == currentLayoutName),
                                    onClick = {
                                        currentLayoutName = layout.name
                                        keyboardPrefs.edit().putString("keyboard_layout_name", layout.name).apply()
                                        HapticUtils.performHapticFeedback(context)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colors.primary,
                                        unselectedColor = colors.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = layout.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // How to Use Card
            Card(
                shape = Radii.large,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "How to Use",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Quick tips for gestures and dictation controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Section 1: Gestures
                    Text(
                        text = "Gestures & Shortcuts",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    InstructionRow(
                        icon = com.slumdog88.dictationkeyboardai.R.drawable.ic_arrow_down,
                        text = "Swipe DOWN on keys to insert their secondary symbol instantly (e.g. Numbers on top row)."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionRow(
                        icon = com.slumdog88.dictationkeyboardai.R.drawable.ic_arrow_up,
                        text = "Swipe UP on specific keys for actions:\n• A: Select All\n• X: Cut\n• C: Copy\n• V: Paste"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionRow(
                        icon = com.slumdog88.dictationkeyboardai.R.drawable.ic_key,
                        text = "Long press keys to view more character variants."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section 2: Dictation
                    Text(
                        text = "Dictation Bar",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionRow(
                        icon = com.slumdog88.dictationkeyboardai.R.drawable.ic_mic,
                        text = "Tap to Record. Long Press to Paste the last transcription."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionRow(
                        icon = com.slumdog88.dictationkeyboardai.R.drawable.ic_delete, // Using standard delete icon as trash
                        text = "When recording, tap the Trash/Delete icon to cancel."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionRow(
                        icon = com.slumdog88.dictationkeyboardai.R.drawable.ic_refresh,
                        text = "Select text to reveal the Reprocess button. Tap it to process the selection with AI."
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InstructionRow(
    icon: Int,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Simplified preview of dictation bar for settings transparency slider.
 * Shows mic icon centered on a background with the current opacity value.
 */
@Composable
private fun DictationBarPreview(opacity: Float) {
    val keyboardColors = KeyboardPalette.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = keyboardColors.keyboardBackground.copy(alpha = opacity),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = keyboardColors.keyTextPrimary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_mic),
            contentDescription = null,
            tint = keyboardColors.keyTextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}
