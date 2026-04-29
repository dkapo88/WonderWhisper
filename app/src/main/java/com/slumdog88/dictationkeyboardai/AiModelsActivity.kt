package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.slumdog88.dictationkeyboardai.offline.OfflineModelAvailability
import com.slumdog88.dictationkeyboardai.offline.OfflineModelUiState
import com.slumdog88.dictationkeyboardai.offline.OfflineModelManagerActivity
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelRegistry
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelManager
import com.slumdog88.dictationkeyboardai.offline.updateModelState
import com.slumdog88.dictationkeyboardai.ui.screens.AiModelsScreenDM
import com.slumdog88.dictationkeyboardai.ui.screens.OpenRouterModel
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.TimeUnit

data class OpenRouterApiResponse(
    val data: List<OpenRouterApiModel>
)

data class OpenRouterApiModel(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerializedName("context_length")
    val contextLength: Int? = null,
    val pricing: OpenRouterPricing? = null
)

data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null
)

class AiModelsActivity : ComponentActivity() {

    private var isRefreshingModels = mutableStateOf(false)
    private var openRouterModels = mutableStateOf(listOf(
        // Default OpenRouter models
        OpenRouterModel("anthropic/claude-sonnet-4-6", "Claude Sonnet 4.6", "Anthropic"),
        OpenRouterModel("anthropic/claude-opus-4-6", "Claude Opus 4.6", "Anthropic"),
        OpenRouterModel("anthropic/claude-haiku-4-5", "Claude Haiku 4.5", "Anthropic"),

        // OpenAI
        OpenRouterModel("openai/gpt-5.4-2026-03-05", "GPT-5.4", "OpenAI"),
        OpenRouterModel("openai/gpt-5-chat-latest", "GPT-5 Chat Latest", "OpenAI"),
        OpenRouterModel("openai/gpt-5", "GPT-5", "OpenAI"),
        OpenRouterModel("openai/gpt-5-mini", "GPT-5 Mini", "OpenAI"),
        OpenRouterModel("openai/gpt-5-nano", "GPT-5 Nano", "OpenAI"),

        // Google
        OpenRouterModel("google/gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite Preview", "Google"),
        OpenRouterModel("google/gemini-2.5-pro", "Gemini 2.5 Pro", "Google"),
        OpenRouterModel("google/gemini-2.5-flash", "Gemini 2.5 Flash", "Google"),

        // Meta Llama
        OpenRouterModel("meta-llama/llama-4-maverick-17b-128e-instruct", "Llama 4 Maverick", "Meta"),
        OpenRouterModel("meta-llama/llama-3.1-405b-instruct", "Llama 3.1 405B", "Meta"),

        // Mistral
        OpenRouterModel("mistralai/mistral-large", "Mistral Large", "Mistral"),
        OpenRouterModel("mistralai/mixtral-8x7b-instruct", "Mixtral 8x7B", "Mistral"),

        // Other Popular Models
        OpenRouterModel("perplexity/llama-3.1-sonar-large-128k-online", "Llama 3.1 Sonar Large (Online)", "Perplexity"),
        OpenRouterModel("x-ai/grok-beta", "Grok Beta", "xAI"),
        OpenRouterModel("cohere/command-r-plus", "Command R+", "Cohere")
    ))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Load cached OpenRouter models and auto-refresh if needed
        loadCachedOpenRouterModels(prefs)
        autoRefreshOpenRouterModels(prefs)

        setContent {
            AppTheme {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                            title = "AI Models & Settings",
                            onBack = {
                                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                                finish()
                            }
                        )
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    ) {
                        AiModelsScreenContainer(prefs)
                    }
                }
            }
        }
    }

    @Composable
    private fun AiModelsScreenContainer(prefs: android.content.SharedPreferences) {
        // Get string arrays
        val transcriptionServices = resources.getStringArray(R.array.transcription_services).toList()

        // Ensure "OpenRouter" is present in the AI models list (defensive, in case of variant resources)
        val aiModelsFromRes = resources.getStringArray(R.array.ai_models).toList()
        val aiModelsEffective = remember(aiModelsFromRes) {
            val m = aiModelsFromRes.toMutableList()
            val idx = m.indexOf("OpenRouter")
            when {
                idx == -1 -> m.add(0, "OpenRouter")              // ensure present and visible at top
                idx > 0   -> { m.removeAt(idx); m.add(0, "OpenRouter") } // move to top if buried
            }
            m.toList()
        }

        // State for all settings
        var selectedTranscriptionIndex by remember {
            mutableIntStateOf(
                transcriptionServices.indexOf(prefs.getString("transcription_service", transcriptionServices.getOrNull(0) ?: ""))
                    .takeIf { it >= 0 } ?: 0
            )
        }

        var selectedAiModelIndex by remember {
            mutableIntStateOf(
                aiModelsEffective.indexOf(
                    prefs.getString("ai_model", aiModelsEffective.getOrNull(0) ?: "")
                ).takeIf { it >= 0 } ?: 0
            )
        }

        var selectedOpenRouterModelId by remember {
            mutableStateOf(prefs.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6") ?: "anthropic/claude-sonnet-4-6")
        }
        
        var selectedOpenRouterPrioritization by remember {
            mutableStateOf(prefs.getString("openrouter_prioritization", "automatic") ?: "automatic")
        }

        var sonioxLanguage by remember {
            mutableStateOf(prefs.getString("soniox_language", "en") ?: "en")
        }

        // Notepad Reformat model selection (separate from general AI model)
        var selectedNotepadAiModelIndex by remember {
            mutableIntStateOf(
                aiModelsEffective.indexOf(
                    prefs.getString("notepad_ai_model", prefs.getString("ai_model", aiModelsEffective.getOrNull(0) ?: ""))
                ).takeIf { it >= 0 } ?: selectedAiModelIndex
            )
        }

        var selectedNotepadOpenRouterModelId by remember {
            mutableStateOf(
                prefs.getString("notepad_openrouter_model_id", prefs.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6"))
                    ?: "anthropic/claude-sonnet-4-6"
            )
        }

        var isPostProcessingEnabled by remember {
            mutableStateOf(prefs.getBoolean("enable_postprocess", false))
        }

        var isParagraphFormattingEnabled by remember {
            mutableStateOf(prefs.getBoolean("enable_paragraphs", false))
        }

        var isScreenContextEnabled by remember {
            mutableStateOf(prefs.getBoolean("include_screen_context", false))
        }
        var isLlmStreamingEnabled by remember {
            mutableStateOf(prefs.getBoolean("llm_streaming_enabled", false))
        }

        val offlineModelStates = remember {
            mutableStateListOf<OfflineModelUiState>().apply {
                OfflineWhisperModelRegistry.definitions.forEach { def ->
                    add(
                        OfflineModelUiState(
                            definition = def,
                            availability = OfflineModelAvailability.UNKNOWN,
                            statusMessage = "Tap manage to download or update model files."
                        )
                    )
                }
            }
        }

        var selectedOfflineModelId by remember {
            mutableStateOf(
                prefs.getString("offline_whisper_model_id", OfflineWhisperModelRegistry.defaultModelId)
                    ?: OfflineWhisperModelRegistry.defaultModelId
            )
        }

        LaunchedEffect(Unit) {
            if (offlineModelStates.none { it.definition.id == selectedOfflineModelId }) {
                val fallbackId = OfflineWhisperModelRegistry.defaultModelId
                selectedOfflineModelId = fallbackId
                prefs.edit().putString("offline_whisper_model_id", fallbackId).apply()
            }
        }

        val coroutineScope = rememberCoroutineScope()

        suspend fun refreshOfflineModelStates() {
            offlineModelStates.forEach { state ->
                val (availability, message) = OfflineWhisperModelManager.determineAvailability(this@AiModelsActivity, state.definition)
                offlineModelStates.updateModelState(
                    definition = state.definition,
                    availability = availability,
                    statusMessage = message,
                    progress = null,
                    resetProgress = true
                )
            }
        }

        LaunchedEffect(Unit) {
            refreshOfflineModelStates()
        }

        DisposableEffect(Unit) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    coroutineScope.launch {
                        refreshOfflineModelStates()
                    }
                }
            }
            this@AiModelsActivity.lifecycle.addObserver(observer)
            onDispose {
                this@AiModelsActivity.lifecycle.removeObserver(observer)
            }
        }


        AiModelsScreenDM(
            transcriptionServices = transcriptionServices,
            aiModels = aiModelsEffective,
            selectedTranscriptionIndex = selectedTranscriptionIndex,
            selectedAiModelIndex = selectedAiModelIndex,
            openRouterModels = openRouterModels.value,
            selectedOpenRouterModelId = selectedOpenRouterModelId,
            isPostProcessingEnabled = isPostProcessingEnabled,
            isParagraphFormattingEnabled = isParagraphFormattingEnabled,
            isScreenContextEnabled = isScreenContextEnabled,
            isLlmStreamingEnabled = isLlmStreamingEnabled,
            isRefreshingModels = isRefreshingModels.value,
            offlineModels = offlineModelStates,
            selectedOfflineModelId = selectedOfflineModelId,
            // Notepad-specific model state
            selectedNotepadAiModelIndex = selectedNotepadAiModelIndex,
            selectedNotepadOpenRouterModelId = selectedNotepadOpenRouterModelId,
            // OpenRouter prioritization
            selectedOpenRouterPrioritization = selectedOpenRouterPrioritization,
            // Soniox Language
            sonioxLanguage = sonioxLanguage,
            onTranscriptionServiceChange = { index ->
                selectedTranscriptionIndex = index
                prefs.edit().putString("transcription_service", transcriptionServices[index]).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "Transcription service changed to: ${transcriptionServices[index]}")
            },
            onAiModelChange = { index ->
                selectedAiModelIndex = index
                prefs.edit().putString("ai_model", aiModelsEffective[index]).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "AI model changed to: ${aiModelsEffective[index]}")
            },
            onOpenRouterModelChange = { modelId ->
                selectedOpenRouterModelId = modelId
                prefs.edit().putString("openrouter_model_id", modelId).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "OpenRouter model selected: $modelId")
            },
            onOfflineModelChange = { modelId ->
                selectedOfflineModelId = modelId
                prefs.edit().putString("offline_whisper_model_id", modelId).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "Offline Whisper model selected: $modelId")
            },
            onManageOfflineModels = {
                openOfflineModelManager()
            },
            onNotepadAiModelChange = { index ->
                selectedNotepadAiModelIndex = index
                prefs.edit().putString("notepad_ai_model", aiModelsEffective[index]).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "Notepad AI model changed to: ${aiModelsEffective[index]}")
            },
            onNotepadOpenRouterModelChange = { modelId ->
                selectedNotepadOpenRouterModelId = modelId
                prefs.edit().putString("notepad_openrouter_model_id", modelId).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "Notepad OpenRouter model selected: $modelId")
            },
            onOpenRouterPrioritizationChange = { prioritization ->
                selectedOpenRouterPrioritization = prioritization
                prefs.edit().putString("openrouter_prioritization", prioritization).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "OpenRouter prioritization changed to: $prioritization")
            },
            onSonioxLanguageChange = { language ->
                sonioxLanguage = language
                prefs.edit().putString("soniox_language", language).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "Soniox language changed to: $language")
            },
            onPostProcessingToggle = { enabled ->
                isPostProcessingEnabled = enabled
                prefs.edit().putBoolean("enable_postprocess", enabled).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "Post-processing enabled: $enabled")
            },
            onParagraphFormattingToggle = { enabled ->
                isParagraphFormattingEnabled = enabled
                prefs.edit().putBoolean("enable_paragraphs", enabled).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "Paragraph formatting enabled: $enabled")
            },
            onScreenContextToggle = { enabled ->
                isScreenContextEnabled = enabled
                prefs.edit().putBoolean("include_screen_context", enabled).apply()
                Log.d("AiModelsActivity", "Screen context enabled: $enabled")
            },
            onLlmStreamingToggle = { enabled ->
                isLlmStreamingEnabled = enabled
                prefs.edit().putBoolean("llm_streaming_enabled", enabled).apply()
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                Log.d("AiModelsActivity", "LLM streaming enabled: $enabled")
            },
            onRefreshOpenRouterModels = {
                refreshOpenRouterModels(prefs)
            },
            onSetDefaults = {
                // Set LLaMA 4 Maverick for AI post-processing and OSS120B for Notepad
                // IMPORTANT: compute indices against the effective models list shown in the dropdown
                val models = aiModelsEffective
                val llamaMaverickIdx = models.indexOfFirst { it.equals("meta-llama/llama-4-maverick-17b-128e-instruct", ignoreCase = true) }
                val ossIdx = models.indexOfFirst { it.equals("openai/gpt-oss-120b", ignoreCase = true) || it.equals("gpt-oss-120b", ignoreCase = true) }
                var applied = false
                if (llamaMaverickIdx >= 0) {
                    selectedAiModelIndex = llamaMaverickIdx
                    prefs.edit().putString("ai_model", models[llamaMaverickIdx]).apply()
                    applied = true
                }
                if (ossIdx >= 0) {
                    selectedNotepadAiModelIndex = ossIdx
                    prefs.edit().putString("notepad_ai_model", models[ossIdx]).apply()
                    applied = true
                }
                Toast.makeText(
                    this@AiModelsActivity,
                    if (applied) "Defaults applied" else "Default models not found in list",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onBack = {
                HapticUtils.performHapticFeedback(this@AiModelsActivity)
                finish()
            }
        )
    }

    private fun openOfflineModelManager() {
        try {
            val intent = Intent(this, OfflineModelManagerActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AiModelsActivity", "Failed to open offline model manager", e)
            Toast.makeText(this, "Unable to open offline model manager", Toast.LENGTH_LONG).show()
        }
    }


    // Helper functions for OpenRouter model management (preserved from original)

    private fun refreshOpenRouterModels(prefs: android.content.SharedPreferences, showToast: Boolean = true) {
        isRefreshingModels.value = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AiModelsActivity", "Fetching OpenRouter models from API...")

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/models")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val gson = Gson()
                    val apiResponse = gson.fromJson(responseBody, OpenRouterApiResponse::class.java)

                    // Convert API models to our format
                    val fetchedModels = apiResponse.data.map { apiModel ->
                        val provider = apiModel.id.split("/").firstOrNull()?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase() else it.toString()
                        } ?: "Unknown"
                        OpenRouterModel(
                            id = apiModel.id,
                            name = apiModel.name,
                            provider = provider
                        )
                    }.sortedWith(compareBy({ it.provider }, { it.name }))

                    Log.d("AiModelsActivity", "Fetched ${fetchedModels.size} models from OpenRouter API")

                    // Update UI on main thread
                    runOnUiThread {
                        // Update the models list
                        openRouterModels.value = fetchedModels

                        // Cache the models for future sessions
                        cacheOpenRouterModels(fetchedModels, prefs)

                        isRefreshingModels.value = false
                        if (showToast) {
                            Toast.makeText(this@AiModelsActivity, "✓ Refreshed ${fetchedModels.size} models", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("AiModelsActivity", "API request failed: ${response.code} - $responseBody")
                    runOnUiThread {
                        isRefreshingModels.value = false
                        if (showToast) {
                            Toast.makeText(this@AiModelsActivity, "Failed to fetch models. Check connection.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("AiModelsActivity", "Error fetching OpenRouter models", e)
                runOnUiThread {
                    isRefreshingModels.value = false
                    if (showToast) {
                        Toast.makeText(this@AiModelsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun autoRefreshOpenRouterModels(prefs: android.content.SharedPreferences) {
        val cachedTimestamp = prefs.getLong("cached_models_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - cachedTimestamp
        val cacheMaxAge = 24 * 60 * 60 * 1000L // 24 hours in milliseconds

        // Auto-refresh if cache is older than 24 hours OR if no cache exists
        if (cacheAge > cacheMaxAge || cachedTimestamp == 0L) {
            Log.d("AiModelsActivity", "Cache is ${cacheAge / (60 * 60 * 1000)}h old, auto-refreshing OpenRouter models...")
            refreshOpenRouterModels(prefs, showToast = false) // Silent auto-refresh
        } else {
            Log.d("AiModelsActivity", "Using cached OpenRouter models (${cacheAge / (60 * 60 * 1000)}h old)")
        }
    }

    private fun loadCachedOpenRouterModels(prefs: android.content.SharedPreferences) {
        val cachedModelsJson = prefs.getString("cached_openrouter_models", null)
        if (!cachedModelsJson.isNullOrBlank()) {
            try {
                val gson = Gson()
                val cachedModelsList = gson.fromJson(cachedModelsJson, Array<OpenRouterModel>::class.java).toList()
                if (cachedModelsList.isNotEmpty()) {
                    openRouterModels.value = cachedModelsList
                    Log.d("AiModelsActivity", "Loaded ${cachedModelsList.size} cached OpenRouter models")
                } else {
                    Log.d("AiModelsActivity", "Cached models list is empty, will auto-refresh")
                }
            } catch (e: Exception) {
                Log.e("AiModelsActivity", "Failed to load cached models, will auto-refresh", e)
            }
        } else {
            Log.d("AiModelsActivity", "No cached models found, will auto-refresh")
        }
    }

    private fun cacheOpenRouterModels(models: List<OpenRouterModel>, prefs: android.content.SharedPreferences) {
        try {
            val gson = Gson()
            val modelsJson = gson.toJson(models)
            prefs.edit()
                .putString("cached_openrouter_models", modelsJson)
                .putLong("cached_models_timestamp", System.currentTimeMillis())
                .apply()
            Log.d("AiModelsActivity", "Cached ${models.size} OpenRouter models")
        } catch (e: Exception) {
            Log.e("AiModelsActivity", "Failed to cache models", e)
        }
    }
}
