package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import android.content.SharedPreferences
import com.slumdog88.dictationkeyboardai.DictationPrompt
import com.slumdog88.dictationkeyboardai.CommandPrompt
import com.slumdog88.dictationkeyboardai.DictationPromptManager
import com.slumdog88.dictationkeyboardai.CommandPromptManager
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelRegistry

private const val PRO_USER_MESSAGE_TEMPLATE_KEY = "pro_user_message_template"
private const val KEY_STREAMING_VAD_AMPLITUDE = "streaming_vad_amplitude"
private const val KEY_STREAMING_VAD_MIN_DURATION = "streaming_vad_min_duration_ms"
private const val KEY_STREAMING_VAD_HANGOVER = "streaming_vad_hangover_ms"
private const val KEY_STREAMING_VAD_MODE = "streaming_vad_mode"
private const val KEY_OPENAI_REASONING_EFFORT = "openai_reasoning_effort"
const val KEY_USE_COMMAND_PROMPT_FOR_SELECTED_TEXT = "use_command_prompt_for_selected_text"

/**
 * Manager class for handling application settings with simple mode support.
 * Provides a centralized way to access settings with automatic simple mode overrides.
 */
class SettingsManager(private val context: Context) {
    
    private val appSettings: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val appPrefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    // Lazy initialization of prompt managers
    private val _dictationPromptManager by lazy { DictationPromptManager(context) }
    private val _commandPromptManager by lazy { CommandPromptManager(context) }
    private val logStorageManager by lazy { LogStorageManager.getInstance(context) }

    data class CustomLanguageConfig(
        val vocabularyItems: List<String>,
        val spellingPairs: List<Pair<String, String>>,
        val replacementRules: List<TextProcessingUtils.ReplacementRule>
    )

    private val languageConfigLock = Any()
    @Volatile private var cachedLanguageVocabularyRaw: String? = null
    @Volatile private var cachedLanguageSpellingRaw: String? = null
    @Volatile private var cachedLanguageConfig: CustomLanguageConfig =
        CustomLanguageConfig(emptyList(), emptyList(), emptyList())
    
    /**
     * Get settings with simple mode overrides applied
     */
    fun getSettings(): SharedPreferences {
        val isSimpleMode = appPrefs.getBoolean("is_simple_mode", true)
        
        return if (isSimpleMode) {
            // Create a wrapper that returns default values for simple mode
            object : SharedPreferences {
                override fun getString(key: String?, defValue: String?): String? {
                    return when (key) {
                        // Force Simple Mode defaults regardless of Pro selections
                        "transcription_service" -> "Groq Whisper v3 Turbo"
                        // GPT-OSS 120B for post-processing in Simple Mode
                        "ai_model" -> "openai/gpt-oss-120b"
                        "streaming_ai_model" -> "groq/openai/gpt-oss-20b"
                        "openai_reasoning_effort" -> "none"
                        // Notepad default LLM is OSS120B
                        "notepad_ai_model" -> "openai/gpt-oss-120b"
                        "openrouter_model_id" -> appSettings.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6")
                        "notepad_openrouter_model_id" -> appSettings.getString("notepad_openrouter_model_id", appSettings.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6"))
                        "dictation_prompt" -> getDictationPromptFromLibrary()
                        "command_prompt" -> getCommandPromptFromLibrary()
                        "custom_vocabulary" -> ""
                        "custom_spelling" -> ""
                        "command_word" -> appSettings.getString("command_word", "command, format, summarise") // Allow user setting
                        else -> appSettings.getString(key, defValue)
                    }
                }
                override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                    return when (key) {
                        "enable_postprocess" -> appSettings.getBoolean("enable_postprocess", true) // Allow user setting
                        "include_screen_context" -> appSettings.getBoolean("include_screen_context", true) // Allow user setting
                        "enable_paragraphs" -> false // Always disabled in simple mode
                        else -> appSettings.getBoolean(key, defValue)
                    }
                }
                // Implement other required methods by delegating to original prefs
                override fun getAll(): MutableMap<String, *> = appSettings.all
                override fun getInt(key: String?, defValue: Int): Int = appSettings.getInt(key, defValue)
                override fun getLong(key: String?, defValue: Long): Long = appSettings.getLong(key, defValue)
                override fun getFloat(key: String?, defValue: Float): Float = appSettings.getFloat(key, defValue)
                override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = appSettings.getStringSet(key, defValues)
                override fun contains(key: String?): Boolean = appSettings.contains(key)
                override fun edit(): SharedPreferences.Editor = appSettings.edit()
                override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = appSettings.registerOnSharedPreferenceChangeListener(listener)
                override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = appSettings.unregisterOnSharedPreferenceChangeListener(listener)
            }
        } else {
            appSettings // Return original prefs for pro mode
        }
    }
    
    /**
     * Get raw app settings without simple mode overrides
     */
    fun getRawAppSettings(): SharedPreferences {
        return appSettings
    }
    
    /**
     * Get app preferences (for simple mode flag, etc.)
     */
    fun getAppPrefs(): SharedPreferences {
        return appPrefs
    }
    
    /**
     * Check if simple mode is enabled
     */
    fun isSimpleMode(): Boolean {
        return appPrefs.getBoolean("is_simple_mode", true)
    }

    fun getUserMessageTemplate(): String {
        val prefs = getRawAppSettings()
        return prefs.getString(PRO_USER_MESSAGE_TEMPLATE_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: TextProcessingUtils.getDefaultUserMessageTemplate()
    }

    fun getEffectiveUserMessageTemplate(): String {
        return if (isSimpleMode()) {
            TextProcessingUtils.getDefaultUserMessageTemplate()
        } else {
            getUserMessageTemplate()
        }
    }

    fun saveUserMessageTemplate(template: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString(PRO_USER_MESSAGE_TEMPLATE_KEY, template).apply()
    }

    fun resetUserMessageTemplateToDefault() {
        val prefs = getRawAppSettings()
        prefs.edit().putString(PRO_USER_MESSAGE_TEMPLATE_KEY, TextProcessingUtils.getDefaultUserMessageTemplate()).apply()
    }
    
    /**
     * Check if keyboard-aware bubble setting is enabled
     */
    fun isKeyboardAwareBubbleEnabled(): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean("keyboard_aware_bubble", true) // Default to enabled
    }

    /**
     * Master enable/disable for the bubble overlay
     */
    fun isBubbleOverlayEnabled(): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean("bubble_overlay_enabled", true)
    }

    fun setBubbleOverlayEnabled(enabled: Boolean) {
        val prefs = getRawAppSettings()
        prefs.edit().putBoolean("bubble_overlay_enabled", enabled).apply()
    }
    
    fun isStreamingDictationEnabled(): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean("streaming_dictation_enabled", false)
    }

    fun setStreamingDictationEnabled(enabled: Boolean) {
        val prefs = getRawAppSettings()
        prefs.edit().putBoolean("streaming_dictation_enabled", enabled).apply()
    }

    fun getStreamingAiModel(): String {
        val prefs = getSettings()
        return prefs.getString("streaming_ai_model", "groq/openai/gpt-oss-20b") ?: "groq/openai/gpt-oss-20b"
    }

    fun setStreamingAiModel(modelId: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString("streaming_ai_model", modelId).apply()
    }

    fun getStreamingCustomInstructions(): String {
        val prefs = getSettings()
        return prefs.getString(KEY_STREAMING_CUSTOM_INSTRUCTIONS, "") ?: ""
    }

    fun setStreamingCustomInstructions(value: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString(KEY_STREAMING_CUSTOM_INSTRUCTIONS, value).apply()
    }
    
    fun isAlwaysUseStreamingDictationEnabled(): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean(KEY_ALWAYS_USE_STREAMING_DICTATION, false)
    }

    fun setAlwaysUseStreamingDictationEnabled(enabled: Boolean) {
        val prefs = getRawAppSettings()
        prefs.edit().putBoolean(KEY_ALWAYS_USE_STREAMING_DICTATION, enabled).apply()
    }
    
    fun getVadAmplitudeThreshold(): Double {
        val prefs = getSettings()
        return if (prefs.contains(KEY_STREAMING_VAD_AMPLITUDE)) {
            prefs.getFloat(KEY_STREAMING_VAD_AMPLITUDE, DEFAULT_VAD_AMPLITUDE.toFloat()).toDouble()
        } else {
            DEFAULT_VAD_AMPLITUDE
        }
    }

    fun setVadAmplitudeThreshold(value: Double) {
        val prefs = getRawAppSettings()
        prefs.edit().putFloat(KEY_STREAMING_VAD_AMPLITUDE, value.toFloat()).apply()
    }

    fun getVadMinSpeechDuration(): Int {
        val prefs = getSettings()
        return prefs.getInt(KEY_STREAMING_VAD_MIN_DURATION, DEFAULT_VAD_MIN_SPEECH_MS)
    }

    fun setVadMinSpeechDuration(value: Int) {
        val prefs = getRawAppSettings()
        prefs.edit().putInt(KEY_STREAMING_VAD_MIN_DURATION, value).apply()
    }

    fun getVadHangoverDuration(): Int {
        val prefs = getSettings()
        return prefs.getInt(KEY_STREAMING_VAD_HANGOVER, DEFAULT_VAD_HANGOVER_MS)
    }

    fun setVadHangoverDuration(value: Int) {
        val prefs = getRawAppSettings()
        prefs.edit().putInt(KEY_STREAMING_VAD_HANGOVER, value).apply()
    }

    fun getVadMode(): String {
        val prefs = getSettings()
        return prefs.getString(KEY_STREAMING_VAD_MODE, "normal") ?: "normal"
    }

    fun setVadMode(value: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString(KEY_STREAMING_VAD_MODE, value).apply()
    }

    fun isUseCommandPromptForSelectedTextEnabled(): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean(KEY_USE_COMMAND_PROMPT_FOR_SELECTED_TEXT, false)
    }

    fun setUseCommandPromptForSelectedTextEnabled(enabled: Boolean) {
        val prefs = getRawAppSettings()
        prefs.edit().putBoolean(KEY_USE_COMMAND_PROMPT_FOR_SELECTED_TEXT, enabled).apply()
    }

    fun resetVadTuning() {
        val prefs = getRawAppSettings()
        prefs.edit()
            .putFloat(KEY_STREAMING_VAD_AMPLITUDE, DEFAULT_VAD_AMPLITUDE.toFloat())
            .putInt(KEY_STREAMING_VAD_MIN_DURATION, DEFAULT_VAD_MIN_SPEECH_MS)
            .putInt(KEY_STREAMING_VAD_HANGOVER, DEFAULT_VAD_HANGOVER_MS)
            .putString(KEY_STREAMING_VAD_MODE, "normal")
            .apply()
    }
    
    /**
     * Get transcription service setting
     */
    fun getTranscriptionService(): String {
        val prefs = getSettings()
        return prefs.getString("transcription_service", "Groq Whisper v3 Turbo") ?: "Groq Whisper v3 Turbo"
    }

    fun getOfflineWhisperModelId(): String {
        val prefs = getSettings()
        return prefs.getString("offline_whisper_model_id", OfflineWhisperModelRegistry.defaultModelId)
            ?: OfflineWhisperModelRegistry.defaultModelId
    }
    
    /**
     * Get AI model setting
     */
    fun getAIModel(): String {
        val prefs = getSettings()
        val model = prefs.getString("ai_model", "openai/gpt-oss-120b")
            ?: "openai/gpt-oss-120b"
        return when (model) {
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-4-maverick-17b-128e-instruct",
            "moonshotai/kimi-k2-instruct-0905" -> "openai/gpt-oss-120b"
            else -> model
        }
    }

    fun getOpenAIReasoningEffort(): String {
        if (isSimpleMode()) return "none"
        val value = getRawAppSettings()
            .getString(KEY_OPENAI_REASONING_EFFORT, "none")
            ?.lowercase()
            ?.trim()
            ?: "none"
        return when (value) {
            "minimal", "low", "medium", "high", "xhigh" -> value
            else -> "none"
        }
    }
    
    /**
     * Get dictation prompt setting (using new prompt library system)
     */
    fun getDictationPrompt(): String {
        val selectedId = getSelectedDictationPromptId()
        val defaultPrompts = DictationPrompt.getDefaultPrompts()
        val matchedDefault = defaultPrompts.firstOrNull { it.id == selectedId }
        return when {
            selectedId == "default_dictation" -> getDefaultDictationPromptText()
            matchedDefault != null -> matchedDefault.promptText
            else -> getDictationPromptFromLibrary()
        }
    }
    
    /**
     * Get command prompt setting (using new prompt library system)
     */
    fun getCommandPrompt(): String {
        return getCommandPromptFromLibrary()
    }

    fun getDefaultDictationPromptText(): String {
        return resolveDefaultDictationPromptText()
    }

    fun getSimpleDictationMode(): String {
        val prefs = getSettings()
        val mode = prefs.getString("simple_dictation_mode", "fast")?.lowercase()
        return if (mode == "accurate") "accurate" else "fast"
    }

    fun setSimpleDictationMode(mode: String) {
        val prefs = getSettings()
        val normalised = if (mode.lowercase() == "accurate") "accurate" else "fast"
        prefs.edit().putString("simple_dictation_mode", normalised).apply()
    }
    
    /**
     * Get dictation prompt from the prompt library system
     */
    private fun getDictationPromptFromLibrary(): String {
        val selectedPromptId = getSelectedDictationPromptId()
        val prompt = _dictationPromptManager.getPromptById(selectedPromptId)
        return prompt?.promptText ?: resolveDefaultDictationPromptText()
    }

    private fun resolveDefaultDictationPromptText(): String {
        val prefs = getSettings()
        val variant = prefs.getString("english_variant", "british")?.lowercase()
        val mode = prefs.getString("simple_dictation_mode", "fast")?.lowercase()
        return DictationPrompt.getDefaultPromptText(
            american = variant == "american",
            accurate = mode == "accurate"
        )
    }
    
    /**
     * Get command prompt from the prompt library system
     */
    private fun getCommandPromptFromLibrary(): String {
        val selectedPromptId = getSelectedCommandPromptId()
        val prompt = _commandPromptManager.getPromptById(selectedPromptId)
        return prompt?.promptText ?: CommandPrompt.getDefaultPromptText()
    }

    fun buildUserMessage(
        processedTranscription: String,
        context: String,
        currentAppContext: String,
        screenContext: String,
        vocabularyItems: List<String>,
        spellingPairs: List<Pair<String, String>>
    ): String {
        return TextProcessingUtils.buildStructuredUserMessage(
            processedTranscription,
            context,
            currentAppContext,
            screenContext,
            vocabularyItems,
            spellingPairs,
            getEffectiveUserMessageTemplate()
        )
    }

    fun buildUserMessage(
        processedTranscription: String,
        context: String,
        currentAppContext: String,
        screenContext: String,
        customVocabulary: String,
        customSpelling: String
    ): String {
        return TextProcessingUtils.buildStructuredUserMessage(
            processedTranscription,
            context,
            currentAppContext,
            screenContext,
            customVocabulary,
            customSpelling,
            getEffectiveUserMessageTemplate()
        )
    }
    
    /**
     * Get selected dictation prompt ID
     */
    fun getSelectedDictationPromptId(): String {
        migratePromptsIfNeeded()
        val prefs = getRawAppSettings()
        return prefs.getString("selected_dictation_prompt_id", "default_dictation") ?: "default_dictation"
    }
    
    /**
     * Get selected command prompt ID
     */
    fun getSelectedCommandPromptId(): String {
        migratePromptsIfNeeded()
        val prefs = getRawAppSettings()
        return prefs.getString("selected_command_prompt_id", "default_command") ?: "default_command"
    }
    
    /**
     * Save selected dictation prompt ID
     */
    fun saveSelectedDictationPromptId(promptId: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString("selected_dictation_prompt_id", promptId).apply()
    }
    
    /**
     * Save selected command prompt ID
     */
    fun saveSelectedCommandPromptId(promptId: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString("selected_command_prompt_id", promptId).apply()
    }
    
    /**
     * Migrate old prompt text storage to new prompt library system (one-time migration)
     */
    private fun migratePromptsIfNeeded() {
        val prefs = getRawAppSettings()
        val migrationKey = "prompts_migrated_to_library"
        
        if (!prefs.getBoolean(migrationKey, false)) {
            // If user has already selected a prompt in the new system, do NOT override it
            val existingSelectedDictation = prefs.getString("selected_dictation_prompt_id", null)
            val existingSelectedCommand = prefs.getString("selected_command_prompt_id", null)

            if (existingSelectedDictation.isNullOrBlank()) {
                // Check if old prompt text exists and is different from defaults
                val oldDictationPrompt = prefs.getString("dictation_prompt", null)
                val defaultDictationText = resolveDefaultDictationPromptText()
                if (oldDictationPrompt != null && oldDictationPrompt != defaultDictationText && oldDictationPrompt.isNotBlank()) {
                    val legacyPrompt = _dictationPromptManager.createLegacyPrompt(oldDictationPrompt)
                    _dictationPromptManager.saveUserPrompt(legacyPrompt)
                    saveSelectedDictationPromptId(legacyPrompt.id)
                } else {
                    saveSelectedDictationPromptId("default_dictation")
                }
            }

            if (existingSelectedCommand.isNullOrBlank()) {
                // Check if old prompt text exists and is different from defaults
                val oldCommandPrompt = prefs.getString("command_prompt", null)
                val defaultCommandText = CommandPrompt.getDefaultPromptText()
                if (oldCommandPrompt != null && oldCommandPrompt != defaultCommandText && oldCommandPrompt.isNotBlank()) {
                    val legacyPrompt = _commandPromptManager.createLegacyPrompt(oldCommandPrompt)
                    _commandPromptManager.saveUserPrompt(legacyPrompt)
                    saveSelectedCommandPromptId(legacyPrompt.id)
                } else {
                    saveSelectedCommandPromptId("default_command")
                }
            }

            // Remove old keys and mark migration complete
            prefs.edit()
                .remove("dictation_prompt")
                .remove("command_prompt")
                .putBoolean(migrationKey, true)
                .apply()
        }
    }
    
    /**
     * Get custom vocabulary setting
     */
    fun getCustomVocabulary(): String {
        val prefs = getSettings()
        return prefs.getString("custom_vocabulary", "") ?: ""
    }
    
    /**
     * Get custom spelling setting
     */
    fun getCustomSpelling(): String {
        val prefs = getSettings()
        return prefs.getString("custom_spelling", "") ?: ""
    }

    fun getCustomLanguageConfig(): CustomLanguageConfig {
        val vocabularyRaw = getCustomVocabulary()
        val spellingRaw = getCustomSpelling()

        if (vocabularyRaw == cachedLanguageVocabularyRaw && spellingRaw == cachedLanguageSpellingRaw) {
            return cachedLanguageConfig
        }

        synchronized(languageConfigLock) {
            if (vocabularyRaw != cachedLanguageVocabularyRaw || spellingRaw != cachedLanguageSpellingRaw) {
                val vocabularyItems = TextProcessingUtils.parseCustomVocabulary(vocabularyRaw)
                val spellingPairs = TextProcessingUtils.parseCustomSpelling(spellingRaw)
                val replacementRules = TextProcessingUtils.buildReplacementRules(spellingPairs)
                cachedLanguageConfig = CustomLanguageConfig(vocabularyItems, spellingPairs, replacementRules)
                cachedLanguageVocabularyRaw = vocabularyRaw
                cachedLanguageSpellingRaw = spellingRaw
            }
            return cachedLanguageConfig
        }
    }
    
    /**
     * Get command words setting
     */
    fun getCommandWords(): String {
        val prefs = getSettings()
        return prefs.getString("command_word", "command, format, summarise") ?: "command, format, summarise"
    }
    
    /**
     * Check if post-processing is enabled
     */
    fun isPostProcessingEnabled(): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean("enable_postprocess", false)
    }
    
    /**
     * Check if screen context is enabled
     */
    fun isScreenContextEnabled(): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean("include_screen_context", false)
    }
    
    /**
     * Get OpenRouter model ID
     */
    fun getOpenRouterModelId(): String {
        val prefs = getSettings()
        val modelId = prefs.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6")
            ?: "anthropic/claude-sonnet-4-6"
        return when (modelId) {
            "anthropic/claude-3.5-sonnet" -> "anthropic/claude-sonnet-4-6"
            else -> modelId
        }
    }
    
    /**
     * Get OpenRouter prioritization setting
     */
    fun getOpenRouterPrioritization(): String {
        val prefs = getSettings()
        return prefs.getString("openrouter_prioritization", "automatic") ?: "automatic"
    }
    
    /**
     * Save OpenRouter prioritization setting
     */
    fun saveOpenRouterPrioritization(prioritization: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString("openrouter_prioritization", prioritization).apply()
    }
    
    /**
     * Get dictation logs
     */
    fun getDictationLogs(): String {
        return logStorageManager.readLogs()
    }
    
    /**
     * Save dictation logs
     */
    fun saveDictationLogs(logs: String) {
        logStorageManager.writeLogs(logs)
    }
    
    /**
     * Get audio files list
     */
    fun getAudioFilesList(): String {
        val prefs = getSettings()
        return prefs.getString("audio_files", "") ?: ""
    }
    
    /**
     * Save audio files list
     */
    fun saveAudioFilesList(audioFiles: String) {
        val prefs = getSettings()
        prefs.edit().putString("audio_files", audioFiles).apply()
    }
    
    /**
     * Check if a specific setting exists
     */
    fun hasSetting(key: String): Boolean {
        val prefs = getSettings()
        return prefs.contains(key)
    }
    
    /**
     * Get a string setting with default value
     */
    fun getStringSetting(key: String, defaultValue: String): String {
        val prefs = getSettings()
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
    
    /**
     * Get a boolean setting with default value
     */
    fun getBooleanSetting(key: String, defaultValue: Boolean): Boolean {
        val prefs = getSettings()
        return prefs.getBoolean(key, defaultValue)
    }
    
    /**
     * Get an int setting with default value
     */
    fun getIntSetting(key: String, defaultValue: Int): Int {
        val prefs = getSettings()
        return prefs.getInt(key, defaultValue)
    }
    
    /**
     * Get a long setting with default value
     */
    fun getLongSetting(key: String, defaultValue: Long): Long {
        val prefs = getSettings()
        return prefs.getLong(key, defaultValue)
    }
    
    /**
     * Save a string setting
     */
    fun saveStringSetting(key: String, value: String) {
        val prefs = getSettings()
        prefs.edit().putString(key, value).apply()
    }
    
    /**
     * Save a boolean setting
     */
    fun saveBooleanSetting(key: String, value: Boolean) {
        val prefs = getSettings()
        prefs.edit().putBoolean(key, value).apply()
    }
    
    /**
     * Save an int setting
     */
    fun saveIntSetting(key: String, value: Int) {
        val prefs = getSettings()
        prefs.edit().putInt(key, value).apply()
    }
    
    /**
     * Save a long setting
     */
    fun saveLongSetting(key: String, value: Long) {
        val prefs = getSettings()
        prefs.edit().putLong(key, value).apply()
    }

    /**
     * Get bubble opacity setting (0-100)
     */
    fun getBubbleOpacity(): Int {
        val prefs = getSettings()
        return prefs.getInt("bubble_opacity", 100) // Default to 100% opacity
    }

    /**
     * Save bubble opacity setting
     */
    fun saveBubbleOpacity(opacity: Int) {
        val prefs = getSettings()
        prefs.edit().putInt("bubble_opacity", opacity).apply()
    }

    /**
     * Get bubble size setting (50-150, representing percentage)
     */
    fun getBubbleSize(): Int {
        val prefs = getSettings()
        return prefs.getInt("bubble_size", 100) // Default to 100% size
    }

    /**
     * Save bubble size setting
     */
    fun saveBubbleSize(size: Int) {
        val prefs = getSettings()
        prefs.edit().putInt("bubble_size", size).apply()
    }

    /**
     * Save bubble's last X and Y position
     */
    fun saveBubbleLastPosition(x: Int, y: Int) {
        val prefs = getRawAppSettings()
        prefs.edit()
            .putInt("bubble_last_x", x)
            .putInt("bubble_last_y", y)
            .apply()
    }

    /**
     * Get bubble's last X position
     */
    fun getBubbleLastPositionX(): Int {
        val prefs = getRawAppSettings()
        return prefs.getInt("bubble_last_x", -1)
    }

    /**
     * Get bubble's last Y position
     */
    fun getBubbleLastPositionY(): Int {
        val prefs = getRawAppSettings()
        return prefs.getInt("bubble_last_y", -1)
    }
    
    /**
     * Get Soniox language setting (ISO code)
     */
    fun getSonioxLanguage(): String {
        val prefs = getSettings()
        return prefs.getString("soniox_language", "en") ?: "en"
    }

    /**
     * Save Soniox language setting
     */
    fun setSonioxLanguage(languageCode: String) {
        val prefs = getRawAppSettings()
        prefs.edit().putString("soniox_language", languageCode).apply()
    }
    
    /**
     * Get dictation prompt manager for UI operations
     */
    fun getDictationPromptManager(): DictationPromptManager {
        return _dictationPromptManager
    }
    
    /**
     * Get command prompt manager for UI operations
     */
    fun getCommandPromptManager(): CommandPromptManager {
        return _commandPromptManager
    }

    companion object {
        const val DEFAULT_VAD_AMPLITUDE: Double = 0.005
        const val DEFAULT_VAD_MIN_SPEECH_MS: Int = 100
        const val DEFAULT_VAD_HANGOVER_MS: Int = 400
        private const val KEY_STREAMING_CUSTOM_INSTRUCTIONS = "streaming_custom_instructions"
        private const val KEY_ALWAYS_USE_STREAMING_DICTATION = "always_use_streaming_dictation"
    }
}
