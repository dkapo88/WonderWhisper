package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

class LogDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawLog = intent.getStringExtra("rawLog") ?: ""
        setContent {
            AppTheme {
                LogDetailScreen(rawLog)
            }
        }
    }
}

private data class ParsedLog(
    val timestamp: String = "",
    val audio: String? = null,
    val transcriptionService: String? = null,
    val aiModel: String? = null,
    val app: String? = null,
    val selectedText: String? = null,
    val screen: String? = null,
    val transcription: String = "",
    val aiProcessed: String? = null,
    val llmSystem: String? = null,
    val llmUser: String? = null
)

private fun parseRawLog(raw: String): ParsedLog {
    val lines = raw.split("\n")
    var i = 0
    var result = ParsedLog()
    fun takeMultiline(afterPrefix: String, tolerateEntryDelimiter: Boolean = false): String {
        val buf = StringBuilder(afterPrefix)
        var j = i + 1
        while (j < lines.size) {
            val next = lines[j]
            if (next.startsWith("[") || (!tolerateEntryDelimiter && next == "---") ||
                next.startsWith("Audio:") || next.startsWith("App:") ||
                next.startsWith("Selected Text:") || next.startsWith("Clipboard:") ||
                next.startsWith("Screen:") || next.startsWith("Transcription:") ||
                next.startsWith("AI Processed:") || next.startsWith("Transcription Service:") ||
                next.startsWith("AI Model:") || next.startsWith("LLM Prompt (System):") ||
                next.startsWith("LLM Prompt (User):")
            ) break
            buf.append('\n').append(next)
            j++
        }
        i = j
        return buf.toString()
    }
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("[") -> {
                result = result.copy(timestamp = line.trim('[', ']').replace(" (REPROCESSED)", ""))
                i++
            }
            line.startsWith("Audio: ") -> { result = result.copy(audio = line.substring(7)); i++ }
            line.startsWith("Transcription Service: ") -> { result = result.copy(transcriptionService = line.substring(23)); i++ }
            line.startsWith("AI Model: ") -> { result = result.copy(aiModel = line.substring(10)); i++ }
            line.startsWith("App: ") -> { result = result.copy(app = line.substring(5)); i++ }
            line.startsWith("Selected Text: ") -> { result = result.copy(selectedText = takeMultiline(line.substring(15))) }
            line.startsWith("Screen: ") -> { result = result.copy(screen = takeMultiline(line.substring(8))) }
            line.startsWith("Transcription: ") -> { result = result.copy(transcription = takeMultiline(line.substring(15))) }
            line.startsWith("AI Processed: ") -> { result = result.copy(aiProcessed = takeMultiline(line.substring(14))) }
            line.startsWith("LLM Prompt (System):") -> {
                val prefix = "LLM Prompt (System):"
                val after = line.substring(prefix.length).trimStart()
                result = result.copy(llmSystem = takeMultiline(after, tolerateEntryDelimiter = true))
            }
            line.startsWith("LLM Prompt (User):") -> {
                val prefix = "LLM Prompt (User):"
                val after = line.substring(prefix.length).trimStart()
                result = result.copy(llmUser = takeMultiline(after, tolerateEntryDelimiter = true))
            }
            else -> i++
        }
    }
    return result
}

private fun reconstructPrompts(ctx: android.content.Context, parsed: ParsedLog): ParsedLog {
    val settings = com.slumdog88.dictationkeyboardai.utils.SettingsManager(ctx)
    val prefs = settings.getSettings()
    val commandWords = prefs.getString("command_word", "command") ?: "command"
    val words = parsed.transcription.trim().split("\\s+".toRegex())
    val firstWord = words.firstOrNull()?.lowercase()?.replace("[^\\w]".toRegex(), "") ?: ""
    val isCommand = commandWords.split(',').map { it.trim().lowercase() }.contains(firstWord)
    val defaultDictationPrompt = settings.getDefaultDictationPromptText()
    val baseSystem = if (isCommand) {
        prefs.getString("command_prompt", com.slumdog88.dictationkeyboardai.CommandPrompt.getDefaultPromptText())
    } else {
        prefs.getString("dictation_prompt", defaultDictationPrompt)
    } ?: defaultDictationPrompt
    val proMode = !settings.isSimpleMode()
    val customVocabulary = prefs.getString("custom_vocabulary", "") ?: ""
    val customSpelling = prefs.getString("custom_spelling", "") ?: ""
    val systemMsg = com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils.buildStructuredSystemMessage(
        baseSystem,
        customVocabulary,
        customSpelling,
        proMode
    )
    val userMsg = settings.buildUserMessage(
        parsed.transcription,
        parsed.selectedText ?: "",
        parsed.app ?: "",
        parsed.screen ?: "",
        customVocabulary,
        customSpelling
    )
    return parsed.copy(llmSystem = parsed.llmSystem ?: systemMsg, llmUser = parsed.llmUser ?: userMsg)
}

@Composable
private fun LogDetailScreen(rawLog: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var parsed = parseRawLog(rawLog)
    // Reconstruct prompts if missing
    if (parsed.llmSystem.isNullOrBlank() || parsed.llmUser.isNullOrBlank()) {
        parsed = reconstructPrompts(ctx, parsed)
    }
    val scrollState = rememberScrollState()
    val simpleMode = com.slumdog88.dictationkeyboardai.utils.PreferencesManager(ctx).isSimpleModeEnabled()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "TRANSCRIPTION DETAILS",
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
        )
        Spacer(Modifier.height(12.dp))

        // Performance summary (transcription + LLM)
        val perf = com.slumdog88.dictationkeyboardai.PerformanceMetrics.fromLogEntry(rawLog)
        if (perf != null && (perf.transcriptionTimeMs > 0 || perf.aiProcessingTimeMs > 0)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Performance", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (perf.transcriptionTimeMs > 0) {
                        val cache = if (perf.transcriptionCacheHit) " (cached)" else ""
                        Text(
                            text = "Transcription: ${"%.2f".format(perf.transcriptionTimeMs / 1000.0)}s$cache",
                            color = Color.White
                        )
                    }
                    if (perf.aiProcessingTimeMs > 0) {
                        val cache = if (perf.aiProcessingCacheHit) " (cached)" else ""
                        Text(
                            text = "LLM: ${"%.2f".format(perf.aiProcessingTimeMs / 1000.0)}s$cache",
                            color = Color.White
                        )
                    }
                    if (perf.totalProcessingTimeMs > 0) {
                        Text(
                            text = "Total: ${"%.2f".format(perf.totalProcessingTimeMs / 1000.0)}s",
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        @Composable
        fun Labeled(label: String, value: String?) {
            if (!value.isNullOrBlank()) {
                Text(label, color = Color(0xFF00F5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
            }
        }

        Labeled("Timestamp", parsed.timestamp)
        Labeled("Voice Model", parsed.transcriptionService)
        Labeled("LLM Model", parsed.aiModel)
        Labeled("App", parsed.app)
        Labeled("Selected Text", parsed.selectedText)
        Labeled("Screen Context", parsed.screen)

        val clipboard = LocalClipboardManager.current
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Raw Transcript", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(parsed.transcription))
                            Toast.makeText(ctx, "Raw transcript copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("COPY") }
                }
                SelectionContainer { Text(parsed.transcription, color = Color.White) }
                if (!parsed.aiProcessed.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Processed", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(parsed.aiProcessed ?: ""))
                                Toast.makeText(ctx, "AI output copied", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("COPY") }
                    }
                    SelectionContainer { Text(parsed.aiProcessed ?: "", color = Color.White) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Show LLM prompts only in Pro mode
        if (!simpleMode && (!parsed.llmSystem.isNullOrBlank() || !parsed.llmUser.isNullOrBlank())) {
            val fullPrompt = buildString {
                if (!parsed.llmSystem.isNullOrBlank()) {
                    append("[SYSTEM]\n")
                    append(parsed.llmSystem)
                    append("\n\n")
                }
                if (!parsed.llmUser.isNullOrBlank()) {
                    append("[USER]\n")
                    append(parsed.llmUser)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("LLM Prompt", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(fullPrompt))
                                Toast.makeText(ctx, "Prompt copied", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("COPY") }
                    }
                    if (!simpleMode && !parsed.llmSystem.isNullOrBlank()) {
                        Text("System", color = Color(0xFFAAAAAA), fontWeight = FontWeight.SemiBold)
                        SelectionContainer { Text(parsed.llmSystem ?: "", color = Color.White) }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!parsed.llmUser.isNullOrBlank()) {
                        Text("User", color = Color(0xFFAAAAAA), fontWeight = FontWeight.SemiBold)
                        SelectionContainer { Text(parsed.llmUser ?: "", color = Color.White) }
                    }
                }
            }
        }
    }
}
