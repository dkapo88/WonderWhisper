package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.offline.OFFLINE_TRANSCRIPTION_OPTION_LABEL
import com.slumdog88.dictationkeyboardai.offline.OfflineModelAvailability
import com.slumdog88.dictationkeyboardai.offline.OfflineModelUiState
import com.slumdog88.dictationkeyboardai.ui.components.*

data class OpenRouterModel(
    val id: String,
    val name: String,
    val provider: String
)

@Composable
fun AiModelsScreenDM(
    transcriptionServices: List<String>,
    aiModels: List<String>,
    selectedTranscriptionIndex: Int,
    selectedAiModelIndex: Int,
    openRouterModels: List<OpenRouterModel>,
    selectedOpenRouterModelId: String,
    isPostProcessingEnabled: Boolean,
    isParagraphFormattingEnabled: Boolean,
    isScreenContextEnabled: Boolean,
    isLlmStreamingEnabled: Boolean,
    isRefreshingModels: Boolean,
    offlineModels: List<OfflineModelUiState>,
    selectedOfflineModelId: String,
    // Notepad-specific selections
    selectedNotepadAiModelIndex: Int,
    selectedNotepadOpenRouterModelId: String,
    // OpenRouter prioritization
    selectedOpenRouterPrioritization: String,
    // Soniox Language
    sonioxLanguage: String,
    onTranscriptionServiceChange: (Int) -> Unit,
    onAiModelChange: (Int) -> Unit,
    onOpenRouterModelChange: (String) -> Unit,
    onPostProcessingToggle: (Boolean) -> Unit,
    onParagraphFormattingToggle: (Boolean) -> Unit,
    onScreenContextToggle: (Boolean) -> Unit,
    onLlmStreamingToggle: (Boolean) -> Unit,
    onRefreshOpenRouterModels: () -> Unit,
    onOfflineModelChange: (String) -> Unit,
    onManageOfflineModels: () -> Unit,
    // Notepad-specific handlers
    onNotepadAiModelChange: (Int) -> Unit,
    onNotepadOpenRouterModelChange: (String) -> Unit,
    // OpenRouter prioritization handler
    onOpenRouterPrioritizationChange: (String) -> Unit,
    // Soniox language handler
    onSonioxLanguageChange: (String) -> Unit,
    // Optional: Pro-mode helper to set defaults
    onSetDefaults: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val accentColor = Color(0xFFFF7F00) // Orange accent for AI/settings context

    // OpenRouter search state
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    // Filter OpenRouter models based on search query
    val filteredOpenRouterModels = remember(openRouterModels, searchQuery.text) {
        if (searchQuery.text.isEmpty()) {
            openRouterModels
        } else {
            openRouterModels.filter { model ->
                fuzzyMatch(searchQuery.text.lowercase(), model.name.lowercase()) ||
                fuzzyMatch(searchQuery.text.lowercase(), model.provider.lowercase()) ||
                fuzzyMatch(searchQuery.text.lowercase(), model.id.lowercase())
            }
        }
    }

    // Check if AssemblyAI is selected for paragraph formatting visibility
    val showParagraphFormatting = transcriptionServices.getOrNull(selectedTranscriptionIndex) == "AssemblyAI"

    // Check if Soniox is selected for language dropdown visibility
    val showSonioxLanguage = transcriptionServices.getOrNull(selectedTranscriptionIndex) == "Soniox Real-Time"

    // Check if OpenRouter is selected for search section visibility
    val showOpenRouterSection = aiModels.getOrNull(selectedAiModelIndex) == "OpenRouter"

    val showOfflineModelSection = transcriptionServices.getOrNull(selectedTranscriptionIndex) == OFFLINE_TRANSCRIPTION_OPTION_LABEL

    // Notepad-specific OpenRouter visibility
    val showNotepadOpenRouterSection = aiModels.getOrNull(selectedNotepadAiModelIndex) == "OpenRouter"

    val bg = MaterialTheme.colorScheme.background
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.tertiary

    // Unified switch color scheme so the thumb stays visible against the track
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header
        Text(
            text = "AI MODELS & SETTINGS",
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = hi,
                letterSpacing = 0.5.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = TextAlign.Center
        )

        // Standalone Set Defaults button (no card/explainer)
        if (onSetDefaults != null) {
            Button(
                onClick = onSetDefaults,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp)
            ) { Text("SET DEFAULTS") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // VOICE TRANSCRIPTION SECTION
        Text(
            text = "VOICE TRANSCRIPTION",
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = hi,
                letterSpacing = 0.3.sp
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Transcription Service Dropdown
        LabeledDropdown(
            label = "Transcription Service",
            options = transcriptionServices,
            selectedIndex = selectedTranscriptionIndex,
            onSelectionChange = onTranscriptionServiceChange
        )

        // Soniox Language Dropdown
        if (showSonioxLanguage) {
            val sonioxLanguages = mapOf(
                "Afrikaans" to "af", "Albanian" to "sq", "Arabic" to "ar", "Azerbaijani" to "az",
                "Basque" to "eu", "Belarusian" to "be", "Bengali" to "bn", "Bosnian" to "bs",
                "Bulgarian" to "bg", "Catalan" to "ca", "Chinese" to "zh", "Croatian" to "hr",
                "Czech" to "cs", "Danish" to "da", "Dutch" to "nl", "English" to "en",
                "Estonian" to "et", "Finnish" to "fi", "French" to "fr", "Galician" to "gl",
                "German" to "de", "Greek" to "el", "Gujarati" to "gu", "Hebrew" to "he",
                "Hindi" to "hi", "Hungarian" to "hu", "Indonesian" to "id", "Italian" to "it",
                "Japanese" to "ja", "Kannada" to "kn", "Kazakh" to "kk", "Korean" to "ko",
                "Latvian" to "lv", "Lithuanian" to "lt", "Macedonian" to "mk", "Malay" to "ms",
                "Malayalam" to "ml", "Marathi" to "mr", "Norwegian" to "no", "Persian" to "fa",
                "Polish" to "pl", "Portuguese" to "pt", "Punjabi" to "pa", "Romanian" to "ro",
                "Russian" to "ru", "Serbian" to "sr", "Slovak" to "sk", "Slovenian" to "sl",
                "Spanish" to "es", "Swahili" to "sw", "Swedish" to "sv", "Tagalog" to "tl",
                "Tamil" to "ta", "Telugu" to "te", "Thai" to "th", "Turkish" to "tr",
                "Ukrainian" to "uk", "Urdu" to "ur", "Vietnamese" to "vi", "Welsh" to "cy"
            )
            
            val sortedLanguages = sonioxLanguages.keys.sorted()
            val currentLangName = sonioxLanguages.entries.find { it.value == sonioxLanguage }?.key ?: "English"
            val selectedLangIndex = sortedLanguages.indexOf(currentLangName).coerceAtLeast(0)

            LabeledDropdown(
                label = "Soniox Language",
                options = sortedLanguages,
                selectedIndex = selectedLangIndex,
                onSelectionChange = { index ->
                    val selectedName = sortedLanguages[index]
                    sonioxLanguages[selectedName]?.let { onSonioxLanguageChange(it) }
                }
            )
            
            Text(
                text = "ℹ Select the primary language for accurate real-time transcription.",
                style = TextStyle(fontSize = 12.sp, color = dim),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (showOfflineModelSection) {
            Spacer(modifier = Modifier.height(16.dp))
            OfflineModelSectionDM(
                offlineModels = offlineModels,
                selectedOfflineModelId = selectedOfflineModelId,
                onOfflineModelChange = onOfflineModelChange,
                onManageOfflineModels = onManageOfflineModels
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Smart Paragraph Formatting (AssemblyAI only)
        if (showParagraphFormatting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Smart Paragraph Formatting",
                    style = TextStyle(fontSize = 14.sp, color = hi, fontWeight = FontWeight.Medium)
                )
                Switch(
                    checked = isParagraphFormattingEnabled,
                    onCheckedChange = onParagraphFormattingToggle,
                    colors = switchColors
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // POST-PROCESSING SECTION
        Text(
            text = "AI POST-PROCESSING",
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = hi,
                letterSpacing = 0.3.sp
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Enable AI Post-Processing Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Enable AI Post-Processing",
                style = TextStyle(fontSize = 14.sp, color = hi, fontWeight = FontWeight.Medium)
            )
            Switch(
                checked = isPostProcessingEnabled,
                onCheckedChange = onPostProcessingToggle,
                colors = switchColors
            )
        }

        // AI Model Selection
        // Prefix provider for clarity when stored model ids omit the vendor.
        fun isGroqModel(model: String): Boolean {
            val lower = model.lowercase()
            return lower.startsWith("groq/") ||
                lower == "openai/gpt-oss-120b" ||
                lower == "gpt-oss-120b" ||
                lower.contains("gpt-oss") ||
                lower == "mistral-saba-24b" ||
                lower.contains("maverick") ||
                lower.startsWith("moonshotai/kimi-k2-instruct")
        }

        fun providerPrefixed(model: String): String {
            if (model == "OpenRouter") return "OpenRouter (bring your own key)"
            val lower = model.lowercase()
            val isGroqModel = isGroqModel(model)

            val providerLabel = when {
                // Respect explicit provider prefixes first
                lower.startsWith("cerebras/") -> "Cerebras/" + model.substringAfter('/')
                lower.startsWith("groq/") -> "Groq/" + model.substringAfter('/')
                // Heuristic mapping (only if no explicit prefix matched)
                isGroqModel -> "Groq/" + model.substringAfter('/')
                lower.startsWith("openai/") -> "OpenAI/" + model.substringAfter('/')
                lower.startsWith("google/") || lower.startsWith("gemini") -> "Google/" + model.substringAfter('/')
                lower.startsWith("anthropic/") -> "Anthropic/" + model.substringAfter('/')
                lower.startsWith("mistral/") || lower.startsWith("mistralai/") -> "Mistral/" + model.substringAfter('/')
                lower.startsWith("meta-llama/") -> "Meta/" + model.substringAfter('/')
                // Heuristics when provider prefix is omitted in the stored value
                lower.contains("gpt") -> "OpenAI/" + model
                lower.contains("claude") -> "Anthropic/" + model
                else -> model
            }

            return providerLabel
        }
        val aiModelsDisplay = remember(aiModels) { aiModels.map { providerPrefixed(it) } }
        LabeledDropdown(
            label = "AI Model (for Post-Processing)",
            options = aiModelsDisplay,
            selectedIndex = selectedAiModelIndex,
            onSelectionChange = onAiModelChange
        )
        Text(
            text = "ℹ Groq models require your Groq API key unless this build is configured to use a hosted proxy.",
            style = TextStyle(fontSize = 12.sp, color = dim),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // OpenRouter Model Search Section
        if (showOpenRouterSection) {
            OpenRouterSearchSectionDM(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                filteredModels = filteredOpenRouterModels,
                selectedModelId = selectedOpenRouterModelId,
                onModelSelect = onOpenRouterModelChange,
                onRefresh = onRefreshOpenRouterModels,
                isLoading = isRefreshingModels
            )
            
            // OpenRouter Prioritization Dropdown
            val prioritizationOptions = listOf("Automatic", "Throughput", "Latency")
            val selectedPrioritizationIndex = prioritizationOptions.indexOf(
                selectedOpenRouterPrioritization.replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase() else it.toString() 
                }
            ).takeIf { it >= 0 } ?: 0
            
            LabeledDropdown(
                label = "Provider Prioritization",
                options = prioritizationOptions,
                selectedIndex = selectedPrioritizationIndex,
                onSelectionChange = { index ->
                    val prioritization = prioritizationOptions[index].lowercase()
                    onOpenRouterPrioritizationChange(prioritization)
                }
            )
        }

        // Screen Context Toggle (belongs to AI Post-Processing)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Include Screen Context",
                style = TextStyle(fontSize = 14.sp, color = hi, fontWeight = FontWeight.Medium)
            )
            Switch(
                checked = isScreenContextEnabled,
                onCheckedChange = onScreenContextToggle,
                colors = switchColors
            )
        }

        // LLM Streaming Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Stream LLM responses (faster time-to-first-token)",
                style = TextStyle(fontSize = 14.sp, color = hi, fontWeight = FontWeight.Medium)
            )
            Switch(
                checked = isLlmStreamingEnabled,
                onCheckedChange = onLlmStreamingToggle,
                colors = switchColors
            )
        }

        Text(
            text = "ℹ Streams partial tokens for supported providers (Groq). Final text is still inserted once complete.",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = dim
            ),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Screen Context Info
        Text(
            text = "ℹ When enabled, visible screen text is sent to AI for better accuracy. Only activates when you press record - never in background.",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = dim
            ),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // NOTEPAD REFORMAT SECTION
        Text(
            text = "NOTEPAD REFORMAT (EDITOR)",
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = hi,
                letterSpacing = 0.3.sp
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LabeledDropdown(
            label = "AI Model (Notepad Reformat)",
            options = aiModelsDisplay,
            selectedIndex = selectedNotepadAiModelIndex,
            onSelectionChange = onNotepadAiModelChange
        )
        Text(
            text = "ℹ Notepad reformat uses the same API key or hosted proxy configuration as dictation.",
            style = TextStyle(fontSize = 12.sp, color = dim),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (showNotepadOpenRouterSection) {
            OpenRouterSearchSectionDM(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                filteredModels = filteredOpenRouterModels,
                selectedModelId = selectedNotepadOpenRouterModelId,
                onModelSelect = onNotepadOpenRouterModelChange,
                onRefresh = onRefreshOpenRouterModels,
                isLoading = isRefreshingModels
            )
        }



        // Back Button
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text("BACK")
        }

        // Model Recommendations Section
        ModelRecommendationsSection(
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun OpenRouterSearchSectionDM(
    searchQuery: TextFieldValue,
    onSearchQueryChange: (TextFieldValue) -> Unit,
    filteredModels: List<OpenRouterModel>,
    selectedModelId: String,
    onModelSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // Search section header with refresh button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Search OpenRouter Models",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = hi
                    )
                )
                Text(
                    text = if (isLoading) "Refreshing models..." else "${filteredModels.size} models available",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isLoading) hi else dim
                    )
                )
            }

            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) { Text("REFRESH") }
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search models (e.g., claude, gpt, llama)...") },
            label = { Text("Search") },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // Models dropdown
        val displayNames = filteredModels.map { "OpenRouter/${it.id}" }
        val selectedIndex = filteredModels.indexOfFirst { it.id == selectedModelId }.takeIf { it >= 0 } ?: 0

        PromptDropdown(
            label = "Select OpenRouter Model",
            options = displayNames,
            selectedIndex = selectedIndex,
            onSelectionChange = { index ->
                if (index < filteredModels.size) {
                    onModelSelect(filteredModels[index].id)
                }
            }
        )

        // Popular models info
        Text(
            text = "ℹ Popular models: Claude 3.5 Sonnet, GPT-4, Gemini Pro, Llama 3.1, Mistral Large",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = dim
            )
        )
    }
}

@Composable
private fun OfflineModelSectionDM(
    offlineModels: List<OfflineModelUiState>,
    selectedOfflineModelId: String,
    onOfflineModelChange: (String) -> Unit,
    onManageOfflineModels: () -> Unit
) {
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = "ON-DEVICE WHISPER MODELS",
        style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = hi,
            letterSpacing = 0.3.sp
        ),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (offlineModels.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "No offline models configured yet. Tap MANAGE OFFLINE MODELS to download one.",
                style = TextStyle(fontSize = 14.sp, color = dim),
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onManageOfflineModels,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("MANAGE OFFLINE MODELS")
        }
        return
    }

    val safeIndex = offlineModels.indexOfFirst { it.definition.id == selectedOfflineModelId }
        .takeIf { it >= 0 } ?: 0

    LabeledDropdown(
        label = "Offline Whisper Model",
        options = offlineModels.map { it.definition.displayName },
        selectedIndex = safeIndex,
        onSelectionChange = { index ->
            offlineModels.getOrNull(index)?.let { onOfflineModelChange(it.definition.id) }
        }
    )

    val selectedModel = offlineModels.getOrNull(safeIndex)
    if (selectedModel != null) {
        val statusText = when (selectedModel.availability) {
            OfflineModelAvailability.READY -> "Ready for transcription"
            OfflineModelAvailability.DOWNLOADING -> {
                val pct = selectedModel.progress?.times(100)?.toInt() ?: 0
                "Downloading… $pct%"
            }
            OfflineModelAvailability.MISSING -> "Model not downloaded"
            OfflineModelAvailability.ERROR -> "Download error"
            OfflineModelAvailability.UNKNOWN -> "Status unknown"
        }

        val statusColor = when (selectedModel.availability) {
            OfflineModelAvailability.READY -> MaterialTheme.colorScheme.tertiary
            OfflineModelAvailability.DOWNLOADING -> MaterialTheme.colorScheme.primary
            OfflineModelAvailability.ERROR -> MaterialTheme.colorScheme.error
            OfflineModelAvailability.MISSING -> MaterialTheme.colorScheme.secondary
            OfflineModelAvailability.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.12f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = statusText,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                )
                Text(
                    text = selectedModel.statusMessage ?: selectedModel.definition.description,
                    style = TextStyle(fontSize = 13.sp, color = dim),
                    modifier = Modifier.padding(top = 4.dp)
                )
                val languages = selectedModel.definition.supportedLanguages
                val languageSummary = if (languages.size <= 4) {
                    languages.joinToString(", ")
                } else {
                    languages.take(4).joinToString(", ") + ", …"
                }
                Text(
                    text = "Approx. ${"%.1f".format(selectedModel.definition.approxSizeMb)} MB • Languages: $languageSummary",
                    style = TextStyle(fontSize = 12.sp, color = dim),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onManageOfflineModels,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("MANAGE OFFLINE MODELS")
    }
}

@Composable
private fun ModelRecommendationsSection(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Main section title
        Text(
            text = "MODEL RECOMMENDATIONS",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.3.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Voice Transcription Models Section
        Text(
            text = "🎤 VOICE TRANSCRIPTION MODELS",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00F5FF),
                fontFamily = FontFamily.SansSerif
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "• Groq Whisper v3 Large: Best balance of accuracy and speed\n" +
                    "• Groq Distil/Turbo: Cheapest options for basic transcription\n" +
                    "• AssemblyAI: Most accurate but slower processing\n" +
                    "• Gemini 2.5 Flash: Good balance, relatively cheap with free tier\n" +
                    "• Gemini 2.5 models: High quality but can be slower\n" +
                    "• ElevenLabs Scribe: Fast and accurate for short content\n" +
                    "• OpenAI Whisper: Solid all-rounder, but Groq is cheaper",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFCCCCCC),
                fontFamily = FontFamily.SansSerif
            ),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // AI Post-Processing Models Section
        Text(
            text = "🤖 AI POST-PROCESSING MODELS",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00F5FF),
                fontFamily = FontFamily.SansSerif
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "Top recommended models:\n" +
                    "• Llama4 Maverick: ⭐ Best choice - Enhanced speed and instruction following\n" +
                    "• GPT-5.4: Best OpenAI option for top-end post-processing\n" +
                    "• Gemini 3.1 Flash Lite Preview: Fast and cheap for lightweight cleanup\n" +
                    "• Claude Sonnet 4.6: Strong long-form rewriting and polish\n\n" +
                    "Budget options for simple prompts:\n" +
                    "• GPT-5 Mini/Nano: Basic reformatting only\n" +
                    "• Other models: May compromise on speed or intelligence",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFCCCCCC),
                fontFamily = FontFamily.SansSerif
            )
        )
    }
}

// Fuzzy matching function (copied from original Activity)
/* ====== Material3 Dropdown with full-field clickable anchor ====== */
@Composable
private fun LabeledDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        PromptDropdown(
            label = label,
            options = options,
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectionChange
        )
    }
}

@Composable
private fun PromptDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentIndex = if (options.isNotEmpty()) selectedIndex.coerceIn(0, options.lastIndex) else 0

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            readOnly = true,
            value = options.getOrNull(currentIndex) ?: "",
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = "Expand")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier.fillMaxWidth()
        )
        // Full-field clickable overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = !expanded }
        )
        // Build sectioned menu entries grouped by provider (prefix before '/')
        data class MenuEntry(val isHeader: Boolean, val title: String, val label: String? = null, val originalIndex: Int = -1)
        fun buildSectioned(options: List<String>): List<MenuEntry> {
            if (options.isEmpty()) return emptyList()
            // Group by provider prefix; treat "OpenRouter (Unified API)" specially
            val groups = linkedMapOf<String, MutableList<Pair<String, Int>>>()
            options.forEachIndexed { idx, text ->
                val provider = when {
                    text.startsWith("OpenRouter (Unified API)") -> "OpenRouter"
                    text.contains("/") -> text.substringBefore("/")
                    else -> "Other"
                }
                val list = groups.getOrPut(provider) { mutableListOf() }
                list += text to idx
            }
            val entries = mutableListOf<MenuEntry>()
            groups.forEach { (provider, items) ->
                entries += MenuEntry(isHeader = true, title = provider.uppercase())
                items.forEach { (labelText, originalIndex) ->
                    entries += MenuEntry(isHeader = false, title = provider.uppercase(), label = labelText, originalIndex = originalIndex)
                }
            }
            return entries
        }

        val entries = remember(options) { buildSectioned(options) }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 360.dp)
        ) {
            // Make menu scrollable to avoid clipping off-screen on small displays
            val menuScroll = rememberScrollState()
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(menuScroll)) {
                entries.forEach { entry ->
                    if (entry.isHeader) {
                        DropdownMenuItem(
                            text = { Text(entry.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(entry.label ?: "", maxLines = 2) },
                            onClick = {
                                onSelectionChange(entry.originalIndex)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun fuzzyMatch(query: String, target: String): Boolean {
    if (query.isEmpty()) return true
    if (target.contains(query)) return true

    // Simple fuzzy matching: check if all characters of query appear in order in target
    var queryIndex = 0
    for (char in target) {
        if (queryIndex < query.length && char == query[queryIndex]) {
            queryIndex++
        }
    }
    return queryIndex == query.length
}
