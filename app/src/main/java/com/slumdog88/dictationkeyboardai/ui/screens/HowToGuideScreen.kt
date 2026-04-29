package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.ui.theme.Radii
import com.slumdog88.dictationkeyboardai.utils.SettingsManager

@Composable
fun HowToGuideScreen() {
    val colors = MaterialTheme.colorScheme
    var testFieldText by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val isSimpleDefault = remember { SettingsManager(context).isSimpleMode() }
    var selectedTab by remember { mutableStateOf(if (isSimpleDefault) 0 else 1) }

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
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Header + Tabs
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "HOW-TO\nGUIDE",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onBackground
                )
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Simple Mode") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Pro Mode") }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selectedTab == 0) {
                SimpleGuideContent(testFieldText) { testFieldText = it }
            } else {
                ProGuideContent(testFieldText) { testFieldText = it }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SimpleGuideContent(
    testFieldText: String,
    onTestChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Try it now
    SectionCard(
        title = "🎤 TRY IT NOW",
        accent = colors.tertiary
    ) {
        Text(
            text = "Tap the text field to practice dictation:",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = testFieldText,
            onValueChange = onTestChange,
            placeholder = { Text("Tap here to try WonderWhisper dictation...") },
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
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            shape = Radii.medium
        )
    }

    Spacer(Modifier.height(12.dp))

    SectionCard(
        title = "🔑 API KEYS",
        accent = colors.primary
    ) {
        Text(
            text = """
Cloud transcription and AI cleanup require your own provider API key unless your build is configured with a hosted proxy.

Start here:
• Open API Keys from the main menu
• Add a Groq key for the default Simple Mode models
• Add other provider keys only if you switch models in Pro Mode
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // How it works (concise)
    SectionCard(
        title = "📖 HOW IT WORKS",
        accent = colors.secondary
    ) {
        Text(
            text = """
1) Tap in any text field → keyboard appears
2) Tap mic to start/stop speaking
3) Your text is inserted automatically

Bubble basics:
• 👁 Long‑press bubble (idle): Hide
• 🔔 Notification: Enable/Disable, Show/Hide, Take Note
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Keyboard features
    SectionCard(
        title = "⌨️ KEYBOARD FEATURES",
        accent = colors.primary
    ) {
        Text(
            text = """
Long‑press actions:
• ⌨ Keyboard button: Expand full QWERTY
• ,!? button: Toggle number row
• Number keys: Insert symbol (1→!, 2→@, etc.)

Smart features:
• Return key changes by field (Search, Go, Send, etc.)
• Number row auto‑shows for numeric fields
• Email row (@, .com) appears for email fields
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Dictation vs AI (concise)
    SectionCard(
        title = "🤖 DICTATION VS AI",
        accent = colors.tertiary
    ) {
        Text(
            text = """
📝 Dictation: raw speech → text
🧠 AI: cleans grammar, punctuation, structure
• Toggle in Settings or on the keyboard (red = on)
• Optional: Include Screen Context (only while recording)
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Command mode (clarify multi-word support)
    SectionCard(
        title = "⚡ COMMAND MODE",
        accent = colors.tertiary
    ) {
        Text(
            text = """
Say one of your command words FIRST (default: command, format, summarise):
• "format this paragraph"
• "summarise the selected text"
• "what is the population of Japan?"

Tip: Configure more words in Settings → Command Word (comma‑separated).
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Voice Notes & Notepad (simple)
    SectionCard(
        title = "🗒️ VOICE NOTES & NOTEPAD",
        accent = colors.tertiary
    ) {
        Text(
            text = """
Use the notification “Take Note” to start a quick voice note. Notes appear in Notepad where you can edit, copy, share, and apply Reformat.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Quick tips (minimal)
    SectionCard(
        title = "💡 QUICK TIPS",
        accent = colors.secondary
    ) {
        Text(
            text = """
• Speak naturally; brief pauses help
• Undo button reverts last dictation
• Select All → Reprocess to apply AI to text
• Backspace turns to Cancel while recording
• Use Custom Vocabulary for names/terms
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }
}

@Composable
private fun ProGuideContent(
    testFieldText: String,
    onTestChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Try it now (keep parity)
    SectionCard(
        title = "🎤 TRY IT NOW",
        accent = colors.tertiary
    ) {
        Text(
            text = "Tap the text field to practice dictation:",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = testFieldText,
            onValueChange = onTestChange,
            placeholder = { Text("Tap here to try WonderWhisper dictation...") },
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
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            shape = Radii.medium
        )
    }

    Spacer(Modifier.height(12.dp))

    // Transcription & Models
    SectionCard(
        title = "🎙️ TRANSCRIPTION & MODELS",
        accent = colors.secondary
    ) {
        Text(
            text = """
Choose transcription provider (Groq Whisper, GPT‑4o Transcribe, Gemini, Deepgram, Mistral, AssemblyAI). Optional: paragraph formatting (AssemblyAI).
Enable AI Post‑Processing and pick a model (including OpenRouter). You can search/select OpenRouter models.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Context & Prompts
    SectionCard(
        title = "🎯 CONTEXT & PROMPTS",
        accent = colors.primary
    ) {
        Text(
            text = """
Include Screen Context (reads visible text only while recording). Manage Dictation/Command prompt libraries. Command words are comma‑separated and must be first spoken.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Voice Notes & Notepad
    SectionCard(
        title = "🗒️ VOICE NOTES & NOTEPAD",
        accent = colors.tertiary
    ) {
        Text(
            text = """
Use notification “Take Note” to start/stop a voice note. In Notepad, apply Reformat with your chosen model; supports OpenRouter selection.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // History & Audio
    SectionCard(
        title = "📚 HISTORY & AUDIO",
        accent = colors.secondary
    ) {
        Text(
            text = """
Play/share audio (Downloads/ WonderWhisper), reprocess entries after changing providers or prompts, and set a history limit. Delete single entries or all.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Keyboard Features
    SectionCard(
        title = "⌨️ KEYBOARD FEATURES",
        accent = colors.primary
    ) {
        Text(
            text = """
Long‑press actions:
• ⌨ Keyboard button: Expand full QWERTY keyboard
• ,!? button: Toggle persistent number row
• Number keys (1‑0): Insert secondary symbol (!, @, #, $, %, ^, &, *, (, ))

Dynamic buttons:
• Return key morphs by field type (Search, Go, Send, Next, Done, New Line)
• Undo button → Cancel (during recording) → Retry (on failure)
• Select All → Reprocess (when text selected)

Smart rows:
• Number row auto‑appears for numeric input fields
• Email/URL row (@, .com, .org) for email/URL fields
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Bubble & Appearance
    SectionCard(
        title = "🫧 BUBBLE & APPEARANCE",
        accent = colors.tertiary
    ) {
        Text(
            text = """
Keyboard‑aware bubble positioning, master enable/disable, size & opacity sliders. Customize keyboard height, opacity, and bottom padding in Keyboard Settings.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    // Accuracy & Debug
    SectionCard(
        title = "🧰 ACCURACY & DEBUG",
        accent = colors.secondary
    ) {
        Text(
            text = """
Custom Vocabulary & Spelling improve names/terms across providers. Debug & Testing includes permission checks, starting the bubble, and battery optimization tips.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    accent: Color,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
