package com.slumdog88.dictationkeyboardai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.CommandPrompt
import com.slumdog88.dictationkeyboardai.DictationPrompt
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils
import com.slumdog88.dictationkeyboardai.ui.theme.PastelBlue
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPurple
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPink
import com.slumdog88.dictationkeyboardai.ui.theme.PastelGreen
import com.slumdog88.dictationkeyboardai.ui.theme.Bg
import kotlinx.coroutines.launch

data class AiPromptUiState(
    val dictationPrompts: List<DictationPrompt> = emptyList(),
    val selectedDictationPrompt: DictationPrompt? = null,
    val dictationPromptText: String = "",
    val isDictationModified: Boolean = false,

    val commandPrompts: List<CommandPrompt> = emptyList(),
    val selectedCommandPrompt: CommandPrompt? = null,
    val commandPromptText: String = "",
    val isCommandModified: Boolean = false,

    val commandWord: String = "",
    val useCommandPromptForSelectedText: Boolean = false,

    val userMessageTemplate: String = TextProcessingUtils.getDefaultUserMessageTemplate(),
    val savedUserMessageTemplate: String = TextProcessingUtils.getDefaultUserMessageTemplate(),
    val isUserMessageModified: Boolean = false,

    val isLoading: Boolean = false,
    val showDictationDialog: DialogType? = null,
    val showCommandDialog: DialogType? = null,
    val dialogInputText: String = ""
)

enum class DialogType { SAVE_AS_NEW, RENAME, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPromptScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf(AiPromptUiState()) }

    LaunchedEffect(Unit) {
        loadPromptData(settingsManager, uiState) { newState ->
            uiState = newState
        }
    }

    LaunchedEffect(uiState.selectedDictationPrompt) {
        uiState.selectedDictationPrompt?.let { prompt ->
            if (prompt.id.startsWith("default_")) {
                if (uiState.dictationPromptText != prompt.promptText) {
                    uiState = uiState.copy(
                        dictationPromptText = prompt.promptText,
                        isDictationModified = false
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.selectedCommandPrompt) {
        uiState.selectedCommandPrompt?.let { prompt ->
            if (prompt.id.startsWith("default_")) {
                if (uiState.commandPromptText != prompt.promptText) {
                    uiState = uiState.copy(
                        commandPromptText = prompt.promptText,
                        isCommandModified = false
                    )
                }
            }
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // English Variant toggle (applies to default dictation prompts)
            val ctx = LocalContext.current
            val prefs = remember { SettingsManager(ctx).getRawAppSettings() }
            var englishVariant by remember { mutableStateOf(prefs.getString("english_variant", "british") ?: "british") }
            androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("English Variant (Default Prompts)", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            englishVariant = "british"
                            prefs.edit().putString("english_variant", "british").apply()
                            Toast.makeText(ctx, "Set to British English", Toast.LENGTH_SHORT).show()
                        }, colors = ButtonDefaults.buttonColors(
                            containerColor = if (englishVariant == "british") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (englishVariant == "british") Color.Black else MaterialTheme.colorScheme.onSurface
                        )) { Text("British") }
                        Button(onClick = {
                            englishVariant = "american"
                            prefs.edit().putString("english_variant", "american").apply()
                            Toast.makeText(ctx, "Set to American English", Toast.LENGTH_SHORT).show()
                        }, colors = ButtonDefaults.buttonColors(
                            containerColor = if (englishVariant == "american") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (englishVariant == "american") Color.Black else MaterialTheme.colorScheme.onSurface
                        )) { Text("American") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Used when a default dictation prompt is selected.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Pro Mode: Prompt structure documentation
            if (!settingsManager.isSimpleMode()) {
                androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Pro Mode: Prompt Structure Guide", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "User message sent to the AI contains ONLY these XML tags:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("<TRANSCRIPT> cleaned transcription input</TRANSCRIPT>", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("<ACTIVE_APPLICATION> app name or Unknown</ACTIVE_APPLICATION>", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("<SCREEN_CONTENTS> visible screen text</SCREEN_CONTENTS>", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("<SELECTED_TEXT> user selected text</SELECTED_TEXT>", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("<VOCABULARY> important names/terms, incl. custom spelling</VOCABULARY>", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "System message in Pro mode is exactly your prompt text (no backend additions). Use the tags above in your instructions, and wrap final output in <FORMATTED_TEXT>.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                UserMessageTemplateSection(
                    template = uiState.userMessageTemplate,
                    isModified = uiState.isUserMessageModified,
                    accentColor = accent,
                    onTemplateChanged = { updated ->
                        uiState = uiState.copy(
                            userMessageTemplate = updated,
                            isUserMessageModified = updated != uiState.savedUserMessageTemplate
                        )
                    },
                    onSaveTemplate = {
                        settingsManager.saveUserMessageTemplate(uiState.userMessageTemplate)
                        uiState = uiState.copy(
                            savedUserMessageTemplate = uiState.userMessageTemplate,
                            isUserMessageModified = false
                        )
                        Toast.makeText(context, "✅ User message template saved", Toast.LENGTH_SHORT).show()
                    },
                    onResetTemplate = {
                        val defaultTemplate = TextProcessingUtils.getDefaultUserMessageTemplate()
                        settingsManager.resetUserMessageTemplateToDefault()
                        uiState = uiState.copy(
                            userMessageTemplate = defaultTemplate,
                            savedUserMessageTemplate = defaultTemplate,
                            isUserMessageModified = false
                        )
                        Toast.makeText(context, "User message template reset to default", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            DictationPromptSection(
                uiState = uiState,
                accentColor = accent,
                onDictationPromptSelected = { prompt ->
                    uiState = uiState.copy(
                        selectedDictationPrompt = prompt,
                        dictationPromptText = prompt.promptText,
                        isDictationModified = false
                    )
                    // Persist selection immediately so AIProcessingManager uses it without requiring Save All
                    try { settingsManager.saveSelectedDictationPromptId(prompt.id) } catch (_: Exception) {}
                },
                onDictationTextChanged = { text ->
                    val modified = text != (uiState.selectedDictationPrompt?.promptText ?: "")
                    uiState = uiState.copy(
                        dictationPromptText = text,
                        isDictationModified = modified
                    )
                },
                onShowDictationDialog = { dialogType ->
                    uiState = uiState.copy(
                        showDictationDialog = dialogType,
                        dialogInputText = if (dialogType == DialogType.RENAME) {
                            uiState.selectedDictationPrompt?.name ?: ""
                        } else ""
                    )
                },
                onSaveDictation = {
                    coroutineScope.launch {
                        handleManualSaveDictation(settingsManager, uiState, context) { newState ->
                            uiState = newState
                        }
                    }
                }
            )

            CommandWordSection(
                commandWord = uiState.commandWord,
                accentColor = accent,
                onCommandWordChanged = { word ->
                    uiState = uiState.copy(commandWord = word)
                }
            )

            CommandPromptSection(
                uiState = uiState,
                accentColor = accent,
                onCommandPromptSelected = { prompt ->
                    uiState = uiState.copy(
                        selectedCommandPrompt = prompt,
                        commandPromptText = prompt.promptText,
                        isCommandModified = false
                    )
                    // Persist selection immediately
                    try { settingsManager.saveSelectedCommandPromptId(prompt.id) } catch (_: Exception) {}
                },
                onCommandTextChanged = { text ->
                    val modified = text != (uiState.selectedCommandPrompt?.promptText ?: "")
                    uiState = uiState.copy(
                        commandPromptText = text,
                        isCommandModified = modified
                    )
                },
                onShowCommandDialog = { dialogType ->
                    uiState = uiState.copy(
                        showCommandDialog = dialogType,
                        dialogInputText = if (dialogType == DialogType.RENAME) {
                            uiState.selectedCommandPrompt?.name ?: ""
                        } else ""
                    )
                },
                onSaveCommand = {
                    coroutineScope.launch {
                        handleManualSaveCommand(settingsManager, uiState, context) { newState ->
                            uiState = newState
                        }
                    }
                },
                onToggleSelectedTextBehavior = { enabled ->
                    uiState = uiState.copy(useCommandPromptForSelectedText = enabled)
                    settingsManager.setUseCommandPromptForSelectedTextEnabled(enabled)
                }
            )

            Spacer(modifier = Modifier.padding(top = 16.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        saveAllSettings(settingsManager, uiState, context, onBackClick)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text("SAVE ALL SETTINGS", maxLines = 1, softWrap = false)
            }

            Spacer(modifier = Modifier.padding(bottom = 32.dp))
        }

        uiState.showDictationDialog?.let { dialogType ->
            ShowDictationDialog(
                dialogType = dialogType,
                uiState = uiState,
                accentColor = accent,
                settingsManager = settingsManager,
                context = context,
                onDismiss = {
                    uiState = uiState.copy(showDictationDialog = null, dialogInputText = "")
                },
                onInputChanged = { input ->
                    uiState = uiState.copy(dialogInputText = input)
                },
                onSuccess = { newState ->
                    uiState = newState
                    coroutineScope.launch {
                        loadPromptData(settingsManager, uiState) { reloadedState ->
                            uiState = reloadedState.copy(
                                showDictationDialog = null,
                                dialogInputText = ""
                            )
                        }
                    }
                }
            )
        }

        uiState.showCommandDialog?.let { dialogType ->
            ShowCommandDialog(
                dialogType = dialogType,
                uiState = uiState,
                accentColor = accent,
                settingsManager = settingsManager,
                context = context,
                onDismiss = {
                    uiState = uiState.copy(showCommandDialog = null, dialogInputText = "")
                },
                onInputChanged = { input ->
                    uiState = uiState.copy(dialogInputText = input)
                },
                onSuccess = { newState ->
                    uiState = newState
                    coroutineScope.launch {
                        loadPromptData(settingsManager, uiState) { reloadedState ->
                            uiState = reloadedState.copy(
                                showCommandDialog = null,
                                dialogInputText = ""
                            )
                        }
                    }
                }
            )
        }
    }
}

private suspend fun loadPromptData(
    settingsManager: SettingsManager,
    currentState: AiPromptUiState,
    onStateChange: (AiPromptUiState) -> Unit
) {
    val dictationPromptManager = settingsManager.getDictationPromptManager()
    val commandPromptManager = settingsManager.getCommandPromptManager()

    val dictationPrompts = dictationPromptManager.getAllPrompts()
    val commandPrompts = commandPromptManager.getAllPrompts()

    val selectedDictationId = settingsManager.getSelectedDictationPromptId()
    val selectedCommandId = settingsManager.getSelectedCommandPromptId()

    val selectedDictation = dictationPrompts.find { it.id == selectedDictationId }
    val selectedCommand = commandPrompts.find { it.id == selectedCommandId }

    val commandWord = settingsManager.getCommandWords()
    val useCommandPromptForSelectedText = settingsManager.isUseCommandPromptForSelectedTextEnabled()
    val userMessageTemplate = settingsManager.getUserMessageTemplate()

    onStateChange(
        currentState.copy(
            dictationPrompts = dictationPrompts,
            selectedDictationPrompt = selectedDictation,
            dictationPromptText = selectedDictation?.promptText ?: "",
            commandPrompts = commandPrompts,
            selectedCommandPrompt = selectedCommand,
            commandPromptText = selectedCommand?.promptText ?: "",
            commandWord = commandWord,
            useCommandPromptForSelectedText = useCommandPromptForSelectedText,
            userMessageTemplate = userMessageTemplate,
            savedUserMessageTemplate = userMessageTemplate,
            isUserMessageModified = false
        )
    )
}

private suspend fun handleManualSaveDictation(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onStateChange: (AiPromptUiState) -> Unit
) {
    val selectedPrompt = uiState.selectedDictationPrompt ?: return
    val currentText = uiState.dictationPromptText

    if (selectedPrompt.id.startsWith("default_")) {
        Toast.makeText(context, "⚠️ Cannot save changes to default prompts. Use 'CREATE NEW' to create a custom version.", Toast.LENGTH_SHORT).show()
        return
    }

    val updatedPrompt = selectedPrompt.copy(promptText = currentText)
    settingsManager.getDictationPromptManager().saveUserPrompt(updatedPrompt)
    onStateChange(
        uiState.copy(
            selectedDictationPrompt = updatedPrompt,
            isDictationModified = false
        )
    )
    Toast.makeText(context, "✅ Dictation prompt saved", Toast.LENGTH_SHORT).show()
}

private suspend fun handleManualSaveCommand(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onStateChange: (AiPromptUiState) -> Unit
) {
    val selectedPrompt = uiState.selectedCommandPrompt ?: return
    val currentText = uiState.commandPromptText

    if (selectedPrompt.id.startsWith("default_")) {
        Toast.makeText(context, "⚠️ Cannot save changes to default prompts. Use 'CREATE NEW' to create a custom version.", Toast.LENGTH_SHORT).show()
        return
    }

    val updatedPrompt = selectedPrompt.copy(promptText = currentText)
    settingsManager.getCommandPromptManager().saveUserPrompt(updatedPrompt)
    onStateChange(
        uiState.copy(
            selectedCommandPrompt = updatedPrompt,
            isCommandModified = false
        )
    )
    Toast.makeText(context, "✅ Command prompt saved", Toast.LENGTH_SHORT).show()
}

private suspend fun saveAllSettings(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onComplete: () -> Unit
) {
    settingsManager.saveStringSetting("command_word", uiState.commandWord.trim().lowercase())

    if (!settingsManager.isSimpleMode()) {
        settingsManager.saveUserMessageTemplate(uiState.userMessageTemplate)
    }

    uiState.selectedDictationPrompt?.let {
        settingsManager.saveSelectedDictationPromptId(it.id)
    }

    uiState.selectedCommandPrompt?.let {
        settingsManager.saveSelectedCommandPromptId(it.id)
    }

    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
    onComplete()
}

@Composable
private fun DictationPromptSection(
    uiState: AiPromptUiState,
    accentColor: Color,
    onDictationPromptSelected: (DictationPrompt) -> Unit,
    onDictationTextChanged: (String) -> Unit,
    onShowDictationDialog: (DialogType) -> Unit,
    onSaveDictation: () -> Unit
) {
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "DICTATION PROMPT",
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = accentColor,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )

        Text(
            text = "Used for regular speech-to-text formatting and grammar correction:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = hi
        )

        Text(
            text = "Select Prompt:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = dim,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        PromptDropdown(
            options = uiState.dictationPrompts.map { it.name },
            selectedIndex = uiState.dictationPrompts.indexOfFirst {
                it.id == uiState.selectedDictationPrompt?.id
            }.takeIf { it >= 0 } ?: 0,
            onSelectionChange = { index ->
                val selectedPrompt = uiState.dictationPrompts.getOrNull(index)
                selectedPrompt?.let(onDictationPromptSelected)
            }
        )

        OutlinedTextField(
            value = uiState.dictationPromptText,
            onValueChange = onDictationTextChanged,
            label = { Text("Prompt Text") },
            placeholder = { Text("Enter your dictation prompt here...") },
            colors = OutlinedTextFieldDefaults.colors(),
            minLines = 6,
            maxLines = 12,
            modifier = Modifier
                .fillMaxWidth()
        )

        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pastel-tinted icon buttons for actions
            FilledTonalIconButton(
                onClick = { onShowDictationDialog(DialogType.SAVE_AS_NEW) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelGreen,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Create new dictation prompt"
                )
            }
            FilledTonalIconButton(
                onClick = onSaveDictation,
                enabled = uiState.isDictationModified,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelBlue,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Save dictation prompt"
                )
            }
            FilledTonalIconButton(
                onClick = { onShowDictationDialog(DialogType.RENAME) },
                enabled = uiState.selectedDictationPrompt?.let { !it.id.startsWith("default_") } ?: false,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelPurple,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename dictation prompt"
                )
            }
            FilledTonalIconButton(
                onClick = { onShowDictationDialog(DialogType.DELETE) },
                enabled = uiState.selectedDictationPrompt?.let { !it.id.startsWith("default_") } ?: false,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelPink,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete dictation prompt"
                )
            }
        }
    }
}

@Composable
private fun CommandWordSection(
    commandWord: String,
    accentColor: Color,
    onCommandWordChanged: (String) -> Unit
) {
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "COMMAND WORD",
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = accentColor,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )

        Text(
            text = "The word(s) that trigger AI command processing:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = hi
        )

        Text(
            text = "💡 You can add multiple trigger words separated by commas (e.g., \"command, format, summarise\")",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = dim,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        OutlinedTextField(
            value = commandWord,
            onValueChange = onCommandWordChanged,
            label = { Text("Command Trigger Words") },
            placeholder = { Text("e.g., command, format, summarise") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None
            ),
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

@Composable
private fun CommandPromptSection(
    uiState: AiPromptUiState,
    accentColor: Color,
    onCommandPromptSelected: (CommandPrompt) -> Unit,
    onCommandTextChanged: (String) -> Unit,
    onShowCommandDialog: (DialogType) -> Unit,
    onSaveCommand: () -> Unit,
    onToggleSelectedTextBehavior: (Boolean) -> Unit
) {
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "COMMAND PROMPT",
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = accentColor,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )

        Text(
            text = "Used for AI command execution and text processing:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = hi
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = "Use command prompt for selected text",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = hi
                )
                Text(
                    text = "If text is selected when starting dictation, use the command prompt instead of the dictation prompt.",
                    fontSize = 12.sp,
                    color = dim,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = uiState.useCommandPromptForSelectedText,
                onCheckedChange = onToggleSelectedTextBehavior
            )
        }

        Text(
            text = "Select Prompt:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = dim,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        PromptDropdown(
            options = uiState.commandPrompts.map { it.name },
            selectedIndex = uiState.commandPrompts.indexOfFirst {
                it.id == uiState.selectedCommandPrompt?.id
            }.takeIf { it >= 0 } ?: 0,
            onSelectionChange = { index ->
                val selectedPrompt = uiState.commandPrompts.getOrNull(index)
                selectedPrompt?.let(onCommandPromptSelected)
            }
        )

        OutlinedTextField(
            value = uiState.commandPromptText,
            onValueChange = onCommandTextChanged,
            label = { Text("Prompt Text") },
            placeholder = { Text("Enter your command prompt here...") },
            colors = OutlinedTextFieldDefaults.colors(),
            minLines = 6,
            maxLines = 12,
            modifier = Modifier
                .fillMaxWidth()
        )

        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pastel-tinted icon buttons for actions
            FilledTonalIconButton(
                onClick = { onShowCommandDialog(DialogType.SAVE_AS_NEW) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelGreen,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Create new command prompt"
                )
            }
            FilledTonalIconButton(
                onClick = onSaveCommand,
                enabled = uiState.isCommandModified,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelBlue,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Save command prompt"
                )
            }
            FilledTonalIconButton(
                onClick = { onShowCommandDialog(DialogType.RENAME) },
                enabled = uiState.selectedCommandPrompt?.let { !it.id.startsWith("default_") } ?: false,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelPurple,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename command prompt"
                )
            }
            FilledTonalIconButton(
                onClick = { onShowCommandDialog(DialogType.DELETE) },
                enabled = uiState.selectedCommandPrompt?.let { !it.id.startsWith("default_") } ?: false,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelPink,
                    contentColor = Bg
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete command prompt"
                )
            }
        }
    }
}

@Composable
private fun UserMessageTemplateSection(
    template: String,
    isModified: Boolean,
    accentColor: Color,
    onTemplateChanged: (String) -> Unit,
    onSaveTemplate: () -> Unit,
    onResetTemplate: () -> Unit
) {
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "USER MESSAGE TEMPLATE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = accentColor,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Controls the XML payload sent alongside your system prompt in Pro mode.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = hi
            )

            Text(
                text = "Placeholders: {{TRANSCRIPT}}, {{ACTIVE_APPLICATION}}, {{SCREEN_CONTENTS}}, {{SELECTED_TEXT}}, {{VOCABULARY}}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = dim
            )

            OutlinedTextField(
                value = template,
                onValueChange = onTemplateChanged,
                label = { Text("User Message Template") },
                placeholder = { Text("Edit XML template used for the user message...") },
                colors = OutlinedTextFieldDefaults.colors(),
                minLines = 10,
                maxLines = 20,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSaveTemplate,
                    enabled = isModified,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SAVE TEMPLATE")
                }

                OutlinedButton(
                    onClick = onResetTemplate,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("RESET TO DEFAULT")
                }
            }
        }
    }
}

@Composable
private fun ShowDictationDialog(
    dialogType: DialogType,
    uiState: AiPromptUiState,
    accentColor: Color,
    settingsManager: SettingsManager,
    context: android.content.Context,
    onDismiss: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSuccess: (AiPromptUiState) -> Unit
) {
    when (dialogType) {
        DialogType.SAVE_AS_NEW -> {
            var localText by remember { mutableStateOf(uiState.dialogInputText) }
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            onInputChanged(localText)
                            handleSaveDictationAsNew(
                                settingsManager = settingsManager,
                                uiState = uiState.copy(dialogInputText = localText),
                                context = context,
                                onSuccess = onSuccess,
                                onDismiss = onDismiss
                            )
                        },
                        enabled = localText.trim().isNotEmpty()
                    ) { Text("SAVE") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                },
                title = { Text("Save as New Prompt") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a name for this dictation prompt:")
                        OutlinedTextField(
                            value = localText,
                            onValueChange = { localText = it },
                            singleLine = true,
                            label = { Text("Prompt Name") },
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                    }
                }
            )
        }
        DialogType.RENAME -> {
            var localText by remember { mutableStateOf(uiState.dialogInputText) }
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            onInputChanged(localText)
                            handleRenameDictation(
                                settingsManager = settingsManager,
                                uiState = uiState.copy(dialogInputText = localText),
                                context = context,
                                onSuccess = onSuccess,
                                onDismiss = onDismiss
                            )
                        },
                        enabled = localText.trim().isNotEmpty()
                    ) { Text("RENAME") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                },
                title = { Text("Rename Prompt") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a new name for this dictation prompt:")
                        OutlinedTextField(
                            value = localText,
                            onValueChange = { localText = it },
                            singleLine = true,
                            label = { Text("New Name") },
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                    }
                }
            )
        }
        DialogType.DELETE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            handleDeleteDictation(
                                settingsManager = settingsManager,
                                uiState = uiState,
                                context = context,
                                onSuccess = onSuccess,
                                onDismiss = onDismiss
                            )
                        }
                    ) { Text("DELETE", color = Color(0xFFFF4444)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                },
                title = { Text("Delete Prompt") },
                text = {
                    Text("Are you sure you want to delete the dictation prompt '${uiState.selectedDictationPrompt?.name}'?\n\nThis action cannot be undone.")
                }
            )
        }
    }
}

@Composable
private fun ShowCommandDialog(
    dialogType: DialogType,
    uiState: AiPromptUiState,
    accentColor: Color,
    settingsManager: SettingsManager,
    context: android.content.Context,
    onDismiss: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSuccess: (AiPromptUiState) -> Unit
) {
    when (dialogType) {
        DialogType.SAVE_AS_NEW -> {
            var localText by remember { mutableStateOf(uiState.dialogInputText) }
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            onInputChanged(localText)
                            handleSaveCommandAsNew(
                                settingsManager = settingsManager,
                                uiState = uiState.copy(dialogInputText = localText),
                                context = context,
                                onSuccess = onSuccess,
                                onDismiss = onDismiss
                            )
                        },
                        enabled = localText.trim().isNotEmpty()
                    ) { Text("SAVE") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                },
                title = { Text("Save as New Prompt") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a name for this command prompt:")
                        OutlinedTextField(
                            value = localText,
                            onValueChange = { localText = it },
                            singleLine = true,
                            label = { Text("Prompt Name") },
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                    }
                }
            )
        }
        DialogType.RENAME -> {
            var localText by remember { mutableStateOf(uiState.dialogInputText) }
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            onInputChanged(localText)
                            handleRenameCommand(
                                settingsManager = settingsManager,
                                uiState = uiState.copy(dialogInputText = localText),
                                context = context,
                                onSuccess = onSuccess,
                                onDismiss = onDismiss
                            )
                        },
                        enabled = localText.trim().isNotEmpty()
                    ) { Text("RENAME") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                },
                title = { Text("Rename Prompt") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a new name for this command prompt:")
                        OutlinedTextField(
                            value = localText,
                            onValueChange = { localText = it },
                            singleLine = true,
                            label = { Text("New Name") },
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                    }
                }
            )
        }
        DialogType.DELETE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            handleDeleteCommand(
                                settingsManager = settingsManager,
                                uiState = uiState,
                                context = context,
                                onSuccess = onSuccess,
                                onDismiss = onDismiss
                            )
                        }
                    ) { Text("DELETE", color = Color(0xFFFF4444)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                },
                title = { Text("Delete Prompt") },
                text = {
                    Text("Are you sure you want to delete the command prompt '${uiState.selectedCommandPrompt?.name}'?\n\nThis action cannot be undone.")
                }
            )
        }
    }
}


@Composable
private fun PromptDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(selectedIndex) }

    // keep local index in sync when parent changes selection
    LaunchedEffect(selectedIndex, options.size) {
        currentIndex = if (options.isNotEmpty()) {
            selectedIndex.coerceIn(0, options.lastIndex)
        } else {
            0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            readOnly = true,
            value = options.getOrNull(currentIndex) ?: "",
            onValueChange = {},
            label = { Text("Select Prompt") },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Expand"
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .fillMaxWidth()
        )
        // Make entire field area clickable to expand/collapse
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = !expanded }
        )

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
            modifier = Modifier.zIndex(10f)
        ) {
            options.forEachIndexed { index, label ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, maxLines = 1, softWrap = false) },
                    onClick = {
                        currentIndex = index
                        onSelectionChange(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

/* ===== Helper functions (user prompt CRUD) ===== */

private fun handleSaveDictationAsNew(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onSuccess: (AiPromptUiState) -> Unit,
    onDismiss: () -> Unit
) {
    val promptName = uiState.dialogInputText.trim()
    val currentText = uiState.dictationPromptText

    val nameValidationError = settingsManager.getDictationPromptManager().validatePromptName(promptName)
    if (nameValidationError != null) {
        Toast.makeText(context, nameValidationError, Toast.LENGTH_LONG).show()
        return
    }

    val textValidationError = settingsManager.getDictationPromptManager().validatePromptText(currentText)
    if (textValidationError != null) {
        Toast.makeText(context, textValidationError, Toast.LENGTH_LONG).show()
        return
    }

    val newPrompt = DictationPrompt(
        name = promptName,
        description = "User-created prompt",
        promptText = currentText
    )
    settingsManager.getDictationPromptManager().saveUserPrompt(newPrompt)
    // Persist selection so processing uses this immediately
    try { settingsManager.saveSelectedDictationPromptId(newPrompt.id) } catch (_: Exception) {}
    Toast.makeText(context, "Dictation prompt '$promptName' saved", Toast.LENGTH_SHORT).show()

    onSuccess(uiState.copy(selectedDictationPrompt = newPrompt))
    onDismiss()
}

private fun handleRenameDictation(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onSuccess: (AiPromptUiState) -> Unit,
    onDismiss: () -> Unit
) {
    val newName = uiState.dialogInputText.trim()
    val selectedPrompt = uiState.selectedDictationPrompt ?: return

    if (newName.isEmpty()) {
        Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
        return
    }

    val success = settingsManager.getDictationPromptManager().renameUserPrompt(selectedPrompt.id, newName)
    if (success) {
        Toast.makeText(context, "Prompt renamed to '$newName'", Toast.LENGTH_SHORT).show()
        onSuccess(uiState)
        onDismiss()
    } else {
        Toast.makeText(context, "Failed to rename prompt. Name might already exist.", Toast.LENGTH_LONG).show()
    }
}

private fun handleDeleteDictation(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onSuccess: (AiPromptUiState) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedPrompt = uiState.selectedDictationPrompt ?: return

    settingsManager.getDictationPromptManager().deleteUserPrompt(selectedPrompt.id)
    // If the deleted prompt was selected, fall back to default
    try { settingsManager.saveSelectedDictationPromptId("default_dictation") } catch (_: Exception) {}
    Toast.makeText(context, "Prompt '${selectedPrompt.name}' deleted", Toast.LENGTH_SHORT).show()

    onSuccess(uiState.copy(selectedDictationPrompt = null, dictationPromptText = ""))
    onDismiss()
}

private fun handleSaveCommandAsNew(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onSuccess: (AiPromptUiState) -> Unit,
    onDismiss: () -> Unit
) {
    val promptName = uiState.dialogInputText.trim()
    val currentText = uiState.commandPromptText

    val nameValidationError = settingsManager.getCommandPromptManager().validatePromptName(promptName)
    if (nameValidationError != null) {
        Toast.makeText(context, nameValidationError, Toast.LENGTH_LONG).show()
        return
    }

    val textValidationError = settingsManager.getCommandPromptManager().validatePromptText(currentText)
    if (textValidationError != null) {
        Toast.makeText(context, textValidationError, Toast.LENGTH_LONG).show()
        return
    }

    val newPrompt = CommandPrompt(
        name = promptName,
        description = "User-created prompt",
        promptText = currentText
    )
    settingsManager.getCommandPromptManager().saveUserPrompt(newPrompt)
    Toast.makeText(context, "Command prompt '$promptName' saved", Toast.LENGTH_SHORT).show()

    onSuccess(uiState.copy(selectedCommandPrompt = newPrompt))
    onDismiss()
}

private fun handleRenameCommand(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onSuccess: (AiPromptUiState) -> Unit,
    onDismiss: () -> Unit
) {
    val newName = uiState.dialogInputText.trim()
    val selectedPrompt = uiState.selectedCommandPrompt ?: return

    if (newName.isEmpty()) {
        Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
        return
    }

    val success = settingsManager.getCommandPromptManager().renameUserPrompt(selectedPrompt.id, newName)
    if (success) {
        Toast.makeText(context, "Prompt renamed to '$newName'", Toast.LENGTH_SHORT).show()
        onSuccess(uiState)
        onDismiss()
    } else {
        Toast.makeText(context, "Failed to rename prompt. Name might already exist.", Toast.LENGTH_LONG).show()
    }
}

private fun handleDeleteCommand(
    settingsManager: SettingsManager,
    uiState: AiPromptUiState,
    context: android.content.Context,
    onSuccess: (AiPromptUiState) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedPrompt = uiState.selectedCommandPrompt ?: return

    settingsManager.getCommandPromptManager().deleteUserPrompt(selectedPrompt.id)
    Toast.makeText(context, "Prompt '${selectedPrompt.name}' deleted", Toast.LENGTH_SHORT).show()

    onSuccess(uiState.copy(selectedCommandPrompt = null, commandPromptText = ""))
    onDismiss()
}
