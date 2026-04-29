package com.slumdog88.dictationkeyboardai.ai

import android.content.Context
import android.util.Log
import com.slumdog88.dictationkeyboardai.PerformanceMetrics
import com.slumdog88.dictationkeyboardai.PerformanceMetricsBuilder
import com.slumdog88.dictationkeyboardai.SecureApiKeyManager
import com.slumdog88.dictationkeyboardai.network.GroqProxyConfig
import com.slumdog88.dictationkeyboardai.network.NetworkManager
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils
import com.slumdog88.dictationkeyboardai.ReformatPrompt
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Manager class for handling all AI processing services including caching,
 * provider selection, and context management.
 */
class AIProcessingManager(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val settingsManager: SettingsManager,
    private val secureApiKeyManager: SecureApiKeyManager
) {
    private fun resolveGroqApiKey(resolvedModel: String): String? {
        val userApiKey = secureApiKeyManager.getApiKey("groq_api_key") ?: ""
        if (userApiKey.isNotBlank()) {
            Log.d("AIProcessingManager", "Using user's Groq API key for model '$resolvedModel'")
            return userApiKey
        }

        if (GroqProxyConfig.isConfigured()) {
            Log.d("AIProcessingManager", "Using hosted Groq proxy for model '$resolvedModel'")
            return ""
        }

        Log.e("AIProcessingManager", "No Groq API key available for model '$resolvedModel'")
        return null
    }

    /**
     * Check whether Simple Mode should restrict advanced provider selection.
     */
    private fun isSimpleModeForcedFallback(): Boolean {
        return settingsManager.isSimpleMode()
    }

    private val gson = Gson()

    // Response caching for AI processing to avoid repeated API calls
    private data class CachedResponse(
        val result: String,
        val timestamp: Long,
        val ttl: Long = 300_000 // 5 minutes TTL
    )

    data class StreamingCompletion(
        val assistantContent: String?,
        val formattedText: String?
    )

    private val aiResponseCache = object : LinkedHashMap<String, CachedResponse>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResponse>?): Boolean {
            return size > 100 || (eldest?.value?.let {
                System.currentTimeMillis() - it.timestamp > it.ttl
            } ?: false)
        }
    }


    // Last-built prompts for logging in history details
    @Volatile private var lastSystemMessage: String? = null
    @Volatile private var lastUserMessage: String? = null

    fun getLastSystemMessage(): String? = lastSystemMessage
    fun getLastUserMessage(): String? = lastUserMessage

    /**
     * Main AI processing method that handles provider selection and caching
     */
    suspend fun processWithAI(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val (result, _) = processWithAIAndMetrics(transcription, context, screenContext, currentAppContext, isCommandMode)
        return@withContext result
    }

    suspend fun processStreamingPrompt(
        systemMessage: String,
        userMessage: String,
        overrideModel: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val configuredModel = settingsManager.getStreamingAiModel()
            val aiModel = overrideModel ?: configuredModel.takeIf { it.isNotBlank() } ?: settingsManager.getAIModel()
            val lowerModel = aiModel.lowercase().trim()
            val resolvedModel = when {
                lowerModel.startsWith("groq/") -> aiModel.substringAfter("groq/")
                lowerModel.startsWith("openrouter/") -> aiModel.substringAfter("openrouter/")
                lowerModel.startsWith("cerebras/") -> aiModel.substringAfter("cerebras/")
                lowerModel == "openai/gpt-oss-120b" -> "openai/gpt-oss-120b"
                else -> aiModel
            }

            return@withContext when {
                lowerModel.startsWith("groq/") || lowerModel == "openai/gpt-oss-120b" -> {
                    processWithGroqSpecificPrompt("", systemMessage, userMessage, resolvedModel)
                }
                lowerModel.startsWith("openrouter/") || aiModel == "OpenRouter" -> {
                    processWithOpenRouterSpecificPrompt("", systemMessage, userMessage)
                }
                lowerModel.startsWith("cerebras/") -> {
                    processWithCerebrasSpecificPrompt("", systemMessage, userMessage, resolvedModel)
                }
                aiModel.contains("gpt", ignoreCase = true) -> {
                    processWithChatGPTSpecificPrompt("", systemMessage, userMessage, resolvedModel)
                }
                aiModel.contains("claude", ignoreCase = true) -> {
                    processWithClaudeSpecificPrompt("", systemMessage, userMessage)
                }
                aiModel.contains("gemini", ignoreCase = true) -> {
                    processWithGeminiSpecificPrompt("", systemMessage, userMessage)
                }
                else -> {
                    processWithGroqSpecificPrompt("", systemMessage, userMessage, resolvedModel)
                }
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Streaming prompt processing failed", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    suspend fun processStreamingConversation(
        systemMessage: String,
        history: List<Pair<String, String>>,
        overrideModel: String? = null
    ): StreamingCompletion? = withContext(Dispatchers.IO) {
        try {
            val configuredModel = settingsManager.getStreamingAiModel()
            val aiModel = overrideModel ?: configuredModel.takeIf { it.isNotBlank() } ?: settingsManager.getAIModel()
            val lowerModel = aiModel.lowercase().trim()
            val resolvedModel = when {
                lowerModel.startsWith("groq/") -> aiModel.substringAfter("groq/")
                lowerModel.startsWith("openrouter/") -> aiModel.substringAfter("openrouter/")
                lowerModel.startsWith("cerebras/") -> aiModel.substringAfter("cerebras/")
                lowerModel == "openai/gpt-oss-120b" -> "openai/gpt-oss-120b"
                else -> aiModel
            }

            lastSystemMessage = systemMessage
            lastUserMessage = history.lastOrNull { it.first == "user" }?.second

            val messages = buildList {
                add(mapOf("role" to "system", "content" to systemMessage))
                history.forEach { (role, content) ->
                    add(mapOf("role" to role, "content" to content))
                }
            }

            val rawContent = when {
                lowerModel.startsWith("groq/") || lowerModel == "openai/gpt-oss-120b" -> {
                    performGroqChat(messages, resolvedModel)
                }
                lowerModel.startsWith("openrouter/") || aiModel == "OpenRouter" -> {
                    performOpenRouterChat(messages)
                }
                lowerModel.startsWith("cerebras/") -> {
                    performCerebrasChat(messages, resolvedModel)
                }
                aiModel.contains("gpt", ignoreCase = true) -> {
                    performChatGPTChat(messages, resolvedModel)
                }
                aiModel.contains("claude", ignoreCase = true) -> {
                    performClaudeChat(messages)
                }
                aiModel.contains("gemini", ignoreCase = true) -> {
                    performGeminiChat(messages)
                }
                else -> {
                    performGroqChat(messages, resolvedModel)
                }
            }

            if (rawContent == null) {
                return@withContext null
            }

            if (rawContent.startsWith("Processing failed") || rawContent.startsWith("Error:")) {
                return@withContext StreamingCompletion(rawContent, null)
            }

            val formatted = TextProcessingUtils.extractXmlTagContent(rawContent, "FORMATTED_TEXT")
            return@withContext StreamingCompletion(rawContent, formatted)
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Streaming conversation processing failed", e)
            return@withContext null
        }
    }

    /**
     * Process text using a specific reformat prompt (for automatic note processing)
     */
    suspend fun processWithReformatPrompt(
        transcription: String,
        reformatPrompt: ReformatPrompt,
        overrideModel: String? = null
    ): String? = withContext(Dispatchers.IO) {
        return@withContext processWithSpecificPrompt(transcription, reformatPrompt.promptText, reformatPrompt.name, overrideModel)
    }

    /**
     * Process text with a specific prompt (bypasses settings-based prompt selection)
     */
    private suspend fun processWithSpecificPrompt(
        transcription: String,
        systemPrompt: String,
        promptName: String,
        overrideModel: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val aiModel = overrideModel ?: settingsManager.getAIModel()
            val lowerModel = aiModel.lowercase().trim()
            val resolvedModel = when {
                // Explicit provider prefixes take precedence over keyword heuristics
                lowerModel.startsWith("groq/") -> aiModel.substringAfter("groq/")
                lowerModel.startsWith("openrouter/") -> aiModel.substringAfter("openrouter/")
                // Handle Groq models with different prefixes
                lowerModel == "openai/gpt-oss-120b" -> "openai/gpt-oss-120b"
                else -> aiModel
            }

            // Check cache first with prompt-specific key
            val cacheKey = getCacheKeyWithPrompt(transcription, systemPrompt, aiModel)
            val cachedResponse = getCachedAIResponseWithPrompt(transcription, systemPrompt, aiModel)
            if (cachedResponse != null) {
                Log.d("AIProcessingManager", "Using cached AI response for prompt '$promptName': ${transcription.take(50)}...")
                return@withContext cachedResponse
            }

            Log.d("AIProcessingManager", "Processing with specific prompt '$promptName'")

            // Get cached custom language configuration for processing
            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Build structured system message
            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                systemPrompt,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )
            val userMessage = settingsManager.buildUserMessage(
                transcription,
                "",
                "",
                "",
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )
            lastSystemMessage = systemMessage
            lastUserMessage = userMessage

            // Route to appropriate AI service based on model
            val result = when {
                // Explicit prefix routing first
                lowerModel.startsWith("groq/") -> {
                    Log.d("AIProcessingManager", "Routing specific prompt via explicit 'groq/' prefix to model='${resolvedModel}'")
                    processWithGroqSpecificPrompt(transcription, systemMessage, userMessage, resolvedModel)
                }
                lowerModel.startsWith("openrouter/") || aiModel == "OpenRouter" -> {
                    Log.d("AIProcessingManager", "Routing specific prompt via OpenRouter")
                    processWithOpenRouterSpecificPrompt(transcription, systemMessage, userMessage)
                }
                lowerModel.startsWith("cerebras/") -> {
                    Log.d("AIProcessingManager", "Routing specific prompt via Cerebras to model='${resolvedModel}'")
                    processWithCerebrasSpecificPrompt(transcription, systemMessage, userMessage, resolvedModel)
                }
                // Handle Groq models with different prefixes
                lowerModel == "openai/gpt-oss-120b" -> {
                    Log.d("AIProcessingManager", "Routing specific prompt for 'openai/gpt-oss-120b' to Groq")
                    processWithGroqSpecificPrompt(transcription, systemMessage, userMessage, resolvedModel)
                }
                // Keyword family heuristics next
                aiModel.contains("gpt", ignoreCase = true) -> {
                    processWithChatGPTSpecificPrompt(transcription, systemMessage, userMessage, resolvedModel)
                }
                aiModel.contains("claude", ignoreCase = true) -> {
                    processWithClaudeSpecificPrompt(transcription, systemMessage, userMessage)
                }
                aiModel.contains("gemini", ignoreCase = true) -> {
                    processWithGeminiSpecificPrompt(transcription, systemMessage, userMessage)
                }
                else -> {
                    processWithGroqSpecificPrompt(transcription, systemMessage, userMessage, resolvedModel)
                }
            }

            // Cache successful results
            if (result != null && !result.startsWith("Processing failed") && !result.startsWith("Error:")) {
                cacheAIResponseWithPrompt(transcription, systemPrompt, aiModel, result)
            }

            return@withContext result
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "AI processing with specific prompt failed", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    /**
     * Main AI processing method with performance metrics tracking
     */
    suspend fun processWithAIAndMetrics(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean = false,
        overrideModel: String? = null
    ): Pair<String?, PerformanceMetrics> = withContext(Dispatchers.IO) {
        val metricsBuilder = PerformanceMetricsBuilder()
        try {
            val aiModel = overrideModel ?: settingsManager.getAIModel()
            val lowerModel = aiModel.lowercase().trim()
            val resolvedModel = when {
                // Explicit provider prefixes take precedence over keyword heuristics
                lowerModel.startsWith("groq/") -> aiModel.substringAfter("groq/")
                lowerModel.startsWith("openrouter/") -> aiModel.substringAfter("openrouter/")
                // Handle Groq models with different prefixes
                lowerModel == "openai/gpt-oss-120b" -> "openai/gpt-oss-120b"
                else -> aiModel
            }
            metricsBuilder.setAIModel(aiModel)

            // Check cache first
            val cacheKey = getCacheKey(transcription, context, aiModel)
            val cachedResponse = getCachedAIResponse(transcription, context, aiModel)
            if (cachedResponse != null) {
                Log.d("AIProcessingManager", "Using cached AI response for: ${transcription.take(50)}...")
                metricsBuilder.setAIProcessingCacheHit(true)
                val metrics = metricsBuilder.build()
                return@withContext Pair(cachedResponse, metrics)
            }

            metricsBuilder.setAIProcessingCacheHit(false)
            metricsBuilder.startAIProcessing()

            var result = when {
                // Explicit prefix routing first
                lowerModel.startsWith("groq/") -> {
                    Log.d("AIProcessingManager", "Routing via explicit 'groq/' prefix to model='${resolvedModel}'")
                    processWithGroq(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
                lowerModel.startsWith("openrouter/") || aiModel == "OpenRouter" -> {
                    Log.d("AIProcessingManager", "Routing via OpenRouter (explicit prefix or selection)")
                    processWithOpenRouter(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
                lowerModel.startsWith("cerebras/") -> {
                    Log.d("AIProcessingManager", "Routing via Cerebras to model='${resolvedModel}'")
                    processWithCerebras(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
                // Handle Groq models with different prefixes
                lowerModel == "openai/gpt-oss-120b" -> {
                    Log.d("AIProcessingManager", "Routing 'openai/gpt-oss-120b' to Groq")
                    processWithGroq(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
                // Keyword family heuristics next
                aiModel.contains("gpt", ignoreCase = true) -> {
                    processWithChatGPT(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
                aiModel.contains("claude", ignoreCase = true) -> {
                    processWithClaude(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
                aiModel.contains("gemini", ignoreCase = true) -> {
                    processWithGemini(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
                else -> {
                    processWithGroq(transcription, context, screenContext, currentAppContext, isCommandMode)
                }
            }

            // No automatic fallback: return the original error/result as-is

            metricsBuilder.endAIProcessing()

            // Cache successful results
            if (result != null && !result.startsWith("Processing failed") && !result.startsWith("Error:")) {
                cacheAIResponse(transcription, context, aiModel, result)
            }

            val metrics = metricsBuilder.build()
            return@withContext Pair(result, metrics)
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "AI processing failed", e)
            val metrics = metricsBuilder.build()
            return@withContext Pair("Processing failed: ${e.message}", metrics)
        }
    }

    /**
     * ChatGPT/OpenAI processing
     */
    private suspend fun processWithChatGPT(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean
    ): String? {
        try {
            val aiModel = settingsManager.getAIModel()
            val resolvedModel = aiModel
            val userApiKey = secureApiKeyManager.getApiKey("openai_api_key") ?: ""

            // Simple Mode does not embed fallback provider keys.
            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: OpenAI requires switching to Pro mode and configuring a key")
                return "Error: OpenAI not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "OpenAI API key is missing")
                return "Error: OpenAI API key not configured"
            }

            Log.d("AIProcessingManager", "Starting ChatGPT processing with model: $resolvedModel")

            // Get prompts and vocabulary
            val dictationPrompt = settingsManager.getDictationPrompt()
            val commandPrompt = settingsManager.getCommandPrompt()
            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Determine which prompt to use
            val baseSystemMessage = if (isCommandMode) commandPrompt else dictationPrompt

            // Build structured system message
            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                baseSystemMessage,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )

            // Build structured user message
            val userMessage = settingsManager.buildUserMessage(
                transcription,
                context,
                currentAppContext,
                screenContext,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )
            lastSystemMessage = systemMessage
            lastUserMessage = userMessage

            // Decide API based on model family
            val isGpt5Responses = aiModel.lowercase().startsWith("gpt-5") && aiModel.lowercase() != "gpt-5-chat-latest"
            val client = networkManager.aiProcessingHttpClient
            val response = if (isGpt5Responses) {
                // Responses API for GPT-5
                val fullPrompt = "$systemMessage\n\n$userMessage"
                val responsesBody = mapOf(
                    "model" to aiModel,
                    "input" to fullPrompt
                )
                val jsonBody = gson.toJson(responsesBody)
                Log.d("AIProcessingManager", "Using Responses API for $aiModel with payload length=${jsonBody.length}")
                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/responses")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.executeWithTimeoutOrNull(request, 45_000)
            } else {
                // Chat Completions API for other models (with fallback mapping)
                val chatBody = mapOf(
                    "model" to aiModel,
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to systemMessage),
                        mapOf("role" to "user", "content" to userMessage)
                    ),
                    "max_tokens" to 1000,
                    "temperature" to 0.3
                )
                val jsonBody = gson.toJson(chatBody)
                Log.d("AIProcessingManager", "Using Chat Completions API for $aiModel with payload length=${jsonBody.length}")
                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.executeWithTimeoutOrNull(request, 45_000)
            }

            response?.use {
                val responseBody = it.body?.string()
                Log.d("AIProcessingManager", "ChatGPT response: $responseBody")

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val resultText: String = if (isGpt5Responses) {
                        // Responses API: prefer top-level output_text if present
                        val outputText = jsonResponse["output_text"] as? String
                        if (!outputText.isNullOrBlank()) outputText else {
                            // Fallback: try output array aggregation
                            val output = jsonResponse["output"] as? List<Map<String, Any>>
                            val firstText = output?.firstOrNull { (it["type"] as? String) == "output_text" }?.get("text") as? String
                            firstText ?: ""
                        }
                    } else {
                        // Chat Completions
                        val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                        val message = choices?.firstOrNull()?.get("message") as? Map<String, Any>
                        message?.get("content") as? String ?: ""
                    }

                    val extractedContent = TextProcessingUtils.extractXmlTagContent(resultText, "FORMATTED_TEXT")
                    val finalText = if (!extractedContent.isNullOrBlank()) extractedContent else resultText
                    Log.d("AIProcessingManager", "OpenAI processing successful: '${finalText.take(100)}...'")
                    return finalText
                } else {
                    // Try to surface the server error message for 400s and others
                    val msg = try {
                        val err = gson.fromJson(responseBody ?: "", Map::class.java) as? Map<*, *>
                        val errorObj = err?.get("error") as? Map<*, *>
                        errorObj?.get("message")?.toString()
                    } catch (_: Exception) { null }
                    Log.e("AIProcessingManager", "OpenAI API error: ${it.code} - ${msg ?: responseBody}")
                    return "Error: OpenAI processing failed (${it.code})${if (!msg.isNullOrBlank()) ": $msg" else ""}"
                }
            } ?: run {
                Log.e("AIProcessingManager", "ChatGPT request timeout")
                return "Error: ChatGPT request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "ChatGPT processing error", e)
            return "Processing failed: ${e.message}"
        }
    }

    // Placeholder methods for other AI services
    // These would be implemented similarly to the ChatGPT method

    private suspend fun processWithGemini(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val aiModel = settingsManager.getAIModel()

            // Check cache first
            val cachedResult = getCachedAIResponse(transcription, "$context|$screenContext", aiModel)
            if (cachedResult != null) {
                return@withContext cachedResult
            }

            val userApiKey = secureApiKeyManager.getApiKey("google_api_key") ?: ""

            // Simple Mode does not embed fallback provider keys.
            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: Google Gemini requires switching to Pro mode and configuring a key")
                return@withContext "Error: Google Gemini not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "Google API key is missing")
                return@withContext "Error: Google API key not configured"
            }

            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Get the appropriate prompt and processed transcription
            val (processedTranscription, baseSystemMessage) = if (isCommandMode) {
                // Remove command word and get command prompt
                val words = transcription.trim().split("\\s+".toRegex())
                val commandTranscription = words.drop(1).joinToString(" ").trim()
                val commandPrompt = settingsManager.getCommandPrompt()
                Pair(commandTranscription, commandPrompt)
            } else {
                // Use full transcription and dictation prompt
                val dictationPrompt = settingsManager.getDictationPrompt()
                Pair(transcription, dictationPrompt)
            }

            Log.d("AIProcessingManager", "=== GEMINI PROCESSING DEBUG ===")
            Log.d("AIProcessingManager", "Mode detected: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Original transcription: '$transcription'")
            Log.d("AIProcessingManager", "Processed transcription: '$processedTranscription'")
            Log.d("AIProcessingManager", "=== END GEMINI DEBUG ===")

            // Build the structured system and user messages
            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                baseSystemMessage,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )
            val userMessage = settingsManager.buildUserMessage(
                processedTranscription,
                context,
                currentAppContext,
                screenContext,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )
            lastSystemMessage = systemMessage
            lastUserMessage = userMessage

            // Combine system and user messages for Gemini
            val fullPrompt = "$systemMessage\n\n$userMessage"

            Log.d("AIProcessingManager", "=== COMPLETE PROMPT BEING SENT TO GEMINI ===")
            Log.d("AIProcessingManager", fullPrompt)
            Log.d("AIProcessingManager", "=== END COMPLETE PROMPT ===")

            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to fullPrompt)
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.3,
                    "maxOutputTokens" to 1000,
                    "topP" to 0.95
                )
            )

            val jsonBody = gson.toJson(requestBody)

            Log.d("AIProcessingManager", "=== GEMINI API CALL DEBUG ===")
            Log.d("AIProcessingManager", "Model: $aiModel")
            Log.d("AIProcessingManager", "Mode: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Complete JSON Payload: $jsonBody")
            Log.d("AIProcessingManager", "=== END DEBUG ===")

            val client = networkManager.aiProcessingHttpClient

            val request = okhttp3.Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$aiModel:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d("AIProcessingManager", "Sending to Gemini - Model: $aiModel")

            val response = client.executeWithTimeoutOrNull(request, 45_000)

            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Gemini API error: ${it.code} $responseBody")
                    return@withContext "Processing failed: Gemini API error ${it.code}"
                }

                if (it.isSuccessful && !responseBody.isNullOrBlank()) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val candidates = jsonResponse["candidates"] as? List<Map<String, Any>>
                    val content = candidates?.get(0)?.get("content") as? Map<String, Any>
                    val parts = content?.get("parts") as? List<Map<String, Any>>
                    val text = parts?.get(0)?.get("text") as? String

                    if (!text.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "Gemini processing successful: '${text.take(100)}...'")
                        // Extract content from XML tags
                        val extractedContent = TextProcessingUtils.extractXmlTagContent(text, "FORMATTED_TEXT")

                        // Cache the successful result
                        cacheAIResponse(transcription, "$context|$screenContext", aiModel, extractedContent)

                        return@withContext extractedContent
                    } else {
                        Log.e("AIProcessingManager", "Gemini returned empty content")
                        return@withContext "Processing failed: Empty response"
                    }
                } else {
                    Log.e("AIProcessingManager", "Gemini API response was unsuccessful or empty")
                    return@withContext "Processing failed: Invalid response"
                }
            } ?: run {
                Log.e("AIProcessingManager", "Gemini request timeout")
                return@withContext "Error: Gemini request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Gemini processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithClaude(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val aiModel = settingsManager.getAIModel()

            // Check cache first
            val cachedResult = getCachedAIResponse(transcription, "$context|$screenContext", aiModel)
            if (cachedResult != null) {
                return@withContext cachedResult
            }

            val userApiKey = secureApiKeyManager.getApiKey("anthropic_api_key") ?: ""

            // Simple Mode does not embed fallback provider keys.
            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: Claude requires switching to Pro mode and configuring a key")
                return@withContext "Error: Claude not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "API Key is missing for Claude processing")
                return@withContext "Processing failed: No API key"
            }

            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Get the appropriate prompt and processed transcription
            val (processedTranscription, baseSystemMessage) = if (isCommandMode) {
                // Remove command word and get command prompt
                val words = transcription.trim().split("\\s+".toRegex())
                val commandTranscription = words.drop(1).joinToString(" ").trim()
                val commandPrompt = settingsManager.getCommandPrompt()
                Pair(commandTranscription, commandPrompt)
            } else {
                // Use full transcription and dictation prompt
                val dictationPrompt = settingsManager.getDictationPrompt()
                Pair(transcription, dictationPrompt)
            }

            Log.d("AIProcessingManager", "=== CLAUDE PROCESSING DEBUG ===")
            Log.d("AIProcessingManager", "Mode detected: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Original transcription: '$transcription'")
            Log.d("AIProcessingManager", "Processed transcription: '$processedTranscription'")
            Log.d("AIProcessingManager", "=== END CLAUDE DEBUG ===")

            // Build the structured system and user messages
            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                baseSystemMessage,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )
            val userMessage = settingsManager.buildUserMessage(
                processedTranscription,
                context,
                currentAppContext,
                screenContext,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )
            lastSystemMessage = systemMessage
            lastUserMessage = userMessage

            val requestBody = mapOf(
                "model" to aiModel,
                "max_tokens" to 1000,
                "system" to listOf(
                    mapOf(
                        "type" to "text",
                        "text" to systemMessage,
                        "cache_control" to mapOf("type" to "ephemeral")
                    )
                ),
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "text",
                                "text" to userMessage,
                                "cache_control" to mapOf("type" to "ephemeral")
                            )
                        )
                    )
                )
            )

            val jsonBody = gson.toJson(requestBody)

            Log.d("AIProcessingManager", "=== CLAUDE API CALL DEBUG ===")
            Log.d("AIProcessingManager", "Model: $aiModel")
            Log.d("AIProcessingManager", "Mode: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Complete JSON Payload: $jsonBody")
            Log.d("AIProcessingManager", "=== END DEBUG ===")

            val client = networkManager.aiProcessingHttpClient

            val request = okhttp3.Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d("AIProcessingManager", "Sending to Claude - Model: $aiModel")

            val response = client.executeWithTimeoutOrNull(request, 45_000)

            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Claude API error: ${it.code} $responseBody")
                    return@withContext "Processing failed: Claude API error ${it.code}"
                }

                if (it.isSuccessful && !responseBody.isNullOrBlank()) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val content = jsonResponse["content"] as? List<Map<String, Any>>
                    val text = content?.get(0)?.get("text") as? String

                    if (!text.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "Claude processing successful: '${text.take(100)}...'")
                        // Extract content from XML tags
                        val extractedContent = TextProcessingUtils.extractXmlTagContent(text, "FORMATTED_TEXT")

                        // Cache the successful result
                        cacheAIResponse(transcription, "$context|$screenContext", aiModel, extractedContent)

                        return@withContext extractedContent
                    } else {
                        Log.e("AIProcessingManager", "Claude returned empty content")
                        return@withContext "Processing failed: Empty response"
                    }
                } else {
                    Log.e("AIProcessingManager", "Claude API response was unsuccessful or empty")
                    return@withContext "Processing failed: Invalid response"
                }
            } ?: run {
                Log.e("AIProcessingManager", "Claude request timeout")
                return@withContext "Error: Claude request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Claude processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithGroq(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val aiModel = settingsManager.getAIModel()
            val lower = aiModel.lowercase().trim()
            val resolvedModel = if (lower.startsWith("groq/")) aiModel.substringAfter("groq/") else aiModel

            // Check cache first
            val cachedResult = getCachedAIResponse(transcription, "$context|$screenContext", aiModel)
            if (cachedResult != null) {
                return@withContext cachedResult
            }

            val apiKey = resolveGroqApiKey(resolvedModel)
                ?: return@withContext "Processing failed: No API key"

            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Get the appropriate prompt and processed transcription
            val (processedTranscription, baseSystemMessage) = if (isCommandMode) {
                // Remove command word and get command prompt
                val words = transcription.trim().split("\\s+".toRegex())
                val commandTranscription = words.drop(1).joinToString(" ").trim()
                val commandPrompt = settingsManager.getCommandPrompt()
                Pair(commandTranscription, commandPrompt)
            } else {
                // Use full transcription and dictation prompt
                val dictationPrompt = settingsManager.getDictationPrompt()
                Pair(transcription, dictationPrompt)
            }

            Log.d("AIProcessingManager", "=== GROQ PROCESSING DEBUG ===")
            Log.d("AIProcessingManager", "Mode detected: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Original transcription: '$transcription'")
            Log.d("AIProcessingManager", "Processed transcription: '$processedTranscription'")
            Log.d("AIProcessingManager", "=== END GROQ DEBUG ===")

            // Build the structured system and user messages
            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                baseSystemMessage,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )
            val userMessage = settingsManager.buildUserMessage(
                processedTranscription,
                context,
                currentAppContext,
                screenContext,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )
            lastSystemMessage = systemMessage
            lastUserMessage = userMessage

            val requestBody = mapOf(
                "model" to resolvedModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemMessage),
                    mapOf("role" to "user", "content" to userMessage)
                ),
                "max_tokens" to 1000,
                "temperature" to 0.3
            )

            val jsonBody = gson.toJson(requestBody)

            Log.d("AIProcessingManager", "=== GROQ API CALL DEBUG ===")
            Log.d("AIProcessingManager", "Model: $resolvedModel (raw='$aiModel')")
            Log.d("AIProcessingManager", "Mode: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Complete JSON Payload: $jsonBody")
            Log.d("AIProcessingManager", "=== END DEBUG ===")

            val client = networkManager.aiProcessingHttpClient

            val streamingEnabled = settingsManager.getBooleanSetting("llm_streaming_enabled", false)

            if (streamingEnabled) {
                val useProxy = GroqProxyConfig.shouldUseProxy(apiKey)
                // Streaming via SSE (OpenAI-compatible)
                val streamBody = gson.toJson(
                    mapOf(
                        "model" to resolvedModel,
                        "messages" to listOf(
                            mapOf("role" to "system", "content" to systemMessage),
                            mapOf("role" to "user", "content" to userMessage)
                        ),
                        "max_tokens" to 1000,
                        "temperature" to 0.3,
                        "stream" to true
                    )
                )

                val requestBuilder = okhttp3.Request.Builder()
                    .url(GroqProxyConfig.endpoint("/openai/v1/chat/completions", useProxy))
                    .addHeader("Content-Type", "application/json")
                    .post(streamBody.toRequestBody("application/json".toMediaTypeOrNull()))

                GroqProxyConfig.applyHeaders(requestBuilder, apiKey, useProxy)

                Log.d("AIProcessingManager", "Sending STREAMING request to Groq - Model: $aiModel")

                val response = executeGroqRequest(requestBuilder, 45_000)

                response?.use { r ->
                    if (!r.isSuccessful || r.body == null) {
                        val body = r.body?.string()
                        Log.e("AIProcessingManager", "Groq streaming API error: ${r.code} $body")
                        return@withContext "Processing failed: API error ${r.code}"
                    }

                    val source = r.body!!.source()
                    val sb = StringBuilder()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val payload = line.removePrefix("data: ").trim()
                            if (payload == "[DONE]") break
                            try {
                                val chunk = gson.fromJson(payload, Map::class.java) as Map<String, Any>
                                val choices = chunk["choices"] as? List<Map<String, Any>>
                                val delta = choices?.firstOrNull()?.get("delta") as? Map<String, Any>
                                val contentPiece = delta?.get("content") as? String
                                if (!contentPiece.isNullOrEmpty()) {
                                    sb.append(contentPiece)
                                }
                            } catch (_: Exception) {
                                // Ignore chunk parse errors; continue
                            }
                        }
                    }

                    val full = sb.toString()
                    if (full.isNotBlank()) {
                        val extracted = TextProcessingUtils.extractXmlTagContent(full, "FORMATTED_TEXT")
                        val result = extracted ?: full
                        cacheAIResponse(transcription, "$context|$screenContext", aiModel, result)
                        return@withContext result
                    } else {
                        Log.e("AIProcessingManager", "Groq streaming returned no content")
                        return@withContext "Processing failed: Empty response"
                    }
                } ?: run {
                    Log.e("AIProcessingManager", "Groq streaming request timeout")
                    return@withContext "Error: Groq request timeout"
                }
            } else {
                val useProxy = GroqProxyConfig.shouldUseProxy(apiKey)
                // Non-streaming fallback (existing behavior)
                val nonStreamBody = gson.toJson(
                    mapOf(
                        "model" to resolvedModel,
                        "messages" to listOf(
                            mapOf("role" to "system", "content" to systemMessage),
                            mapOf("role" to "user", "content" to userMessage)
                        ),
                        "max_tokens" to 1000,
                        "temperature" to 0.3
                    )
                )

                val requestBuilder = okhttp3.Request.Builder()
                    .url(GroqProxyConfig.endpoint("/openai/v1/chat/completions", useProxy))
                    .addHeader("Content-Type", "application/json")
                    .post(nonStreamBody.toRequestBody("application/json".toMediaTypeOrNull()))

                GroqProxyConfig.applyHeaders(requestBuilder, apiKey, useProxy)

                Log.d("AIProcessingManager", "Sending to Groq - Model: $aiModel")

                val response = executeGroqRequest(requestBuilder, 45_000)

                response?.use {
                    val responseBody = it.body?.string()

                    Log.d("AIProcessingManager", "Groq AI response code: ${it.code}")
                    Log.d("AIProcessingManager", "Groq AI response body: $responseBody")

                    if (!it.isSuccessful) {
                        Log.e("AIProcessingManager", "Groq API error: ${it.code} $responseBody")
                        return@withContext "Processing failed: API error ${it.code}"
                    }

                    if (it.isSuccessful && !responseBody.isNullOrBlank()) {
                        val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                        val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                        val message = choices?.get(0)?.get("message") as? Map<String, Any>
                        val content = message?.get("content") as? String

                        if (!content.isNullOrBlank()) {
                            Log.d("AIProcessingManager", "Groq processing successful: '${content.take(100)}...'")
                            val extractedContent = TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                            cacheAIResponse(transcription, "$context|$screenContext", aiModel, extractedContent)
                            return@withContext extractedContent
                        } else {
                            Log.e("AIProcessingManager", "Groq returned empty content")
                            return@withContext "Processing failed: Empty response"
                        }
                    } else {
                        Log.e("AIProcessingManager", "Groq API response was unsuccessful or empty")
                        return@withContext "Processing failed: Invalid response"
                    }
                } ?: run {
                    Log.e("AIProcessingManager", "Groq request timeout")
                    return@withContext "Error: Groq request timeout"
                }
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Groq processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithOpenRouter(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Get selected OpenRouter model (stored as "openrouter_model_id")
            val selectedModel = settingsManager.getOpenRouterModelId()

            // Check cache first
            val cachedResult = getCachedAIResponse(transcription, "$context|$screenContext", selectedModel)
            if (cachedResult != null) {
                return@withContext cachedResult
            }

            val userApiKey = secureApiKeyManager.getApiKey("openrouter_api_key") ?: ""

            // Simple Mode does not embed fallback provider keys.
            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: OpenRouter requires switching to Pro mode and configuring a key")
                return@withContext "Error: OpenRouter not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "OpenRouter API Key is missing")
                return@withContext "Processing failed: No API key"
            }

            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Get the appropriate prompt and processed transcription
            val (processedTranscription, baseSystemMessage) = if (isCommandMode) {
                // Remove command word and get command prompt
                val words = transcription.trim().split("\\s+".toRegex())
                val commandTranscription = words.drop(1).joinToString(" ").trim()
                val commandPrompt = settingsManager.getCommandPrompt()
                Pair(commandTranscription, commandPrompt)
            } else {
                // Use full transcription and dictation prompt
                val dictationPrompt = settingsManager.getDictationPrompt()
                Pair(transcription, dictationPrompt)
            }

            Log.d("AIProcessingManager", "=== OPENROUTER PROCESSING DEBUG ===")
            Log.d("AIProcessingManager", "Mode detected: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Original transcription: '$transcription'")
            Log.d("AIProcessingManager", "Processed transcription: '$processedTranscription'")
            Log.d("AIProcessingManager", "Selected model: '$selectedModel'")
            Log.d("AIProcessingManager", "=== END OPENROUTER DEBUG ===")

            // Build structured messages
            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                baseSystemMessage,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )
            val userMessage = settingsManager.buildUserMessage(
                processedTranscription,
                context,
                currentAppContext,
                screenContext,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )
            lastSystemMessage = systemMessage
            lastUserMessage = userMessage

            // OpenRouter uses OpenAI-compatible API format
            val requestBody = mapOf(
                "model" to selectedModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemMessage),
                    mapOf("role" to "user", "content" to userMessage)
                ),
                "max_tokens" to 1000,
                "temperature" to 0.3
            )

            val jsonBody = gson.toJson(requestBody)

            Log.d("AIProcessingManager", "=== OPENROUTER API CALL DEBUG ===")
            Log.d("AIProcessingManager", "Model: $selectedModel")
            Log.d("AIProcessingManager", "Mode: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("AIProcessingManager", "Complete JSON Payload: $jsonBody")
            Log.d("AIProcessingManager", "=== END DEBUG ===")

            val client = networkManager.aiProcessingHttpClient

            val request = okhttp3.Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://wonderwhisper.app") // Optional: for OpenRouter analytics
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d("AIProcessingManager", "Sending to OpenRouter - Model: $selectedModel")

            val response = client.executeWithTimeoutOrNull(request, 45_000)

            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "OpenRouter API error: ${it.code} $responseBody")
                    return@withContext "Processing failed: API error ${it.code}"
                }

                if (it.isSuccessful && !responseBody.isNullOrBlank()) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                    val message = choices?.get(0)?.get("message") as? Map<String, Any>
                    val content = message?.get("content") as? String

                    if (!content.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "OpenRouter processing successful: '${content.take(100)}...'")
                        // Extract content from XML tags
                        val extractedContent = TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")

                        // Cache the successful result
                        cacheAIResponse(transcription, "$context|$screenContext", selectedModel, extractedContent)

                        return@withContext extractedContent
                    } else {
                        Log.e("AIProcessingManager", "OpenRouter returned empty content")
                        return@withContext "Processing failed: Empty response"
                    }
                } else {
                    Log.e("AIProcessingManager", "OpenRouter API response was unsuccessful or empty")
                    return@withContext "Processing failed: Invalid response"
                }
            } ?: run {
                Log.e("AIProcessingManager", "OpenRouter request timeout")
                return@withContext "Error: OpenRouter request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "OpenRouter processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    /**
     * Generate cache key for AI responses
     */
    private fun getCacheKey(text: String, context: String, aiModel: String): String {
        val combined = "$text|$context|$aiModel"
        return combined.hashCode().toString()
    }

    /**
     * Generate cache key for AI responses with specific prompt
     */
    private fun getCacheKeyWithPrompt(text: String, systemPrompt: String, aiModel: String): String {
        val combined = "$text|$systemPrompt|$aiModel"
        return combined.hashCode().toString()
    }

    /**
     * Get cached AI response
     */
    private fun getCachedAIResponse(text: String, context: String, aiModel: String): String? {
        val key = getCacheKey(text, context, aiModel)
        val cached = aiResponseCache[key]

        return if (cached != null && System.currentTimeMillis() - cached.timestamp < cached.ttl) {
            Log.d("AIProcessingManager", "Cache HIT for AI processing: ${text.take(50)}...")
            cached.result
        } else {
            if (cached != null) {
                aiResponseCache.remove(key) // Remove expired entry
            }
            Log.d("AIProcessingManager", "Cache MISS for AI processing: ${text.take(50)}...")
            null
        }
    }

    /**
     * Get cached AI response with specific prompt
     */
    private fun getCachedAIResponseWithPrompt(text: String, systemPrompt: String, aiModel: String): String? {
        val key = getCacheKeyWithPrompt(text, systemPrompt, aiModel)
        val cached = aiResponseCache[key]

        return if (cached != null && System.currentTimeMillis() - cached.timestamp < cached.ttl) {
            Log.d("AIProcessingManager", "Cache HIT for AI processing with prompt: ${text.take(50)}...")
            cached.result
        } else {
            if (cached != null) {
                aiResponseCache.remove(key) // Remove expired entry
            }
            Log.d("AIProcessingManager", "Cache MISS for AI processing with prompt: ${text.take(50)}...")
            null
        }
    }

    /**
     * Cache AI response
     */
    private fun cacheAIResponse(text: String, context: String, aiModel: String, result: String) {
        val key = getCacheKey(text, context, aiModel)
        aiResponseCache[key] = CachedResponse(result, System.currentTimeMillis())
        Log.d("AIProcessingManager", "Cached AI response for: ${text.take(50)}...")
    }

    /**
     * Cache AI response with specific prompt
     */
    private fun cacheAIResponseWithPrompt(text: String, systemPrompt: String, aiModel: String, result: String) {
        val key = getCacheKeyWithPrompt(text, systemPrompt, aiModel)
        aiResponseCache[key] = CachedResponse(result, System.currentTimeMillis())
        Log.d("AIProcessingManager", "Cached AI response with prompt for: ${text.take(50)}...")
    }

    /**
     * Get cached response (for backward compatibility)
     */
    fun getCachedResponse(text: String, context: String, aiModel: String): String? {
        return getCachedAIResponse(text, context, aiModel)
    }

    /**
     * Specific prompt processing methods for each AI service
     */

    private suspend fun processWithChatGPTSpecificPrompt(
        transcription: String,
        systemMessage: String,
        userMessage: String,
        resolvedModel: String
    ): String? {
        try {
            val userApiKey = secureApiKeyManager.getApiKey("openai_api_key") ?: ""

            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: OpenAI requires switching to Pro mode and configuring a key")
                return "Error: OpenAI not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "OpenAI API key is missing")
                return "Error: OpenAI API key not configured"
            }

            val client = networkManager.aiProcessingHttpClient
            val isGpt5Responses = resolvedModel.lowercase().startsWith("gpt-5") && resolvedModel.lowercase() != "gpt-5-chat-latest"
            val response = if (isGpt5Responses) {
                val requestBody = mapOf(
                    "model" to resolvedModel,
                    "input" to "$systemMessage\n\n$userMessage"
                )
                val jsonBody = gson.toJson(requestBody)
                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/responses")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.executeWithTimeoutOrNull(request, 45_000)
            } else {
                val requestBody = mapOf(
                    "model" to resolvedModel,
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to systemMessage),
                        mapOf("role" to "user", "content" to userMessage)
                    ),
                    "max_tokens" to 1000,
                    "temperature" to 0.3
                )
                val jsonBody = gson.toJson(requestBody)
                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.executeWithTimeoutOrNull(request, 45_000)
            }
            val responseBody = response?.body?.string()

            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val content: String? = if (isGpt5Responses) {
                    (jsonResponse["output_text"] as? String).takeIf { !it.isNullOrBlank() } ?: run {
                        val output = jsonResponse["output"] as? List<Map<String, Any>>
                        output?.firstOrNull { (it["type"] as? String) == "output_text" }?.get("text") as? String
                    }
                } else {
                    val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                    val message = choices?.firstOrNull()?.get("message") as? Map<String, Any>
                    message?.get("content") as? String
                }

                if (!content.isNullOrBlank()) {
                    val extractedContent = TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                    val finalText = if (!extractedContent.isNullOrBlank()) extractedContent else content
                    Log.d("AIProcessingManager", "OpenAI specific prompt processing successful: '${finalText.take(100)}...'")
                    return finalText
                } else {
                    Log.e("AIProcessingManager", "OpenAI returned empty content")
                    return "Processing failed: Empty response"
                }
            } else {
                val msg = try {
                    val err = gson.fromJson(responseBody ?: "", Map::class.java) as? Map<*, *>
                    val errorObj = err?.get("error") as? Map<*, *>
                    errorObj?.get("message")?.toString()
                } catch (_: Exception) { null }
                Log.e("AIProcessingManager", "OpenAI API error: ${response?.code} - ${msg ?: responseBody}")
                return "Error: OpenAI processing failed (${response?.code})${if (!msg.isNullOrBlank()) ": $msg" else ""}"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "ChatGPT specific prompt processing error", e)
            return "Processing failed: ${e.message}"
        }
    }

    private suspend fun performChatGPTChat(
        messages: List<Map<String, Any>>,
        resolvedModel: String
    ): String? {
        return try {
            val userApiKey = secureApiKeyManager.getApiKey("openai_api_key") ?: ""

            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: OpenAI requires switching to Pro mode and configuring a key")
                return "Error: OpenAI not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "OpenAI API key is missing")
                return "Error: OpenAI API key not configured"
            }

            val client = networkManager.aiProcessingHttpClient
            val requestBody = mapOf(
                "model" to resolvedModel,
                "messages" to messages,
                "max_tokens" to 1000,
                "temperature" to 0.3
            )

            val jsonBody = gson.toJson(requestBody)
            val request = okhttp3.Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)
            val responseBody = response?.body?.string()

            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                val message = choices?.firstOrNull()?.get("message") as? Map<String, Any>
                val content = message?.get("content") as? String
                if (!content.isNullOrBlank()) {
                    Log.d("AIProcessingManager", "OpenAI chat processing successful: '${content.take(100)}...'")
                    return content
                }
                return "Processing failed: Empty response"
            }

            val msg = try {
                val err = gson.fromJson(responseBody ?: "", Map::class.java) as? Map<*, *>
                val errorObj = err?.get("error") as? Map<*, *>
                errorObj?.get("message")?.toString()
            } catch (_: Exception) { null }
            Log.e("AIProcessingManager", "OpenAI API error: ${response?.code} - ${msg ?: responseBody}")
            "Error: OpenAI processing failed (${response?.code})${if (!msg.isNullOrBlank()) ": $msg" else ""}"
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "OpenAI chat processing error", e)
            "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithGeminiSpecificPrompt(
        transcription: String,
        systemMessage: String,
        userMessage: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val userApiKey = secureApiKeyManager.getApiKey("google_api_key") ?: ""

            // Simple Mode does not embed fallback provider keys.
            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: Google Gemini requires switching to Pro mode and configuring a key")
                return@withContext "Error: Google Gemini not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "Google API key is missing")
                return@withContext "Error: Google API key not configured"
            }

            val client = networkManager.aiProcessingHttpClient
            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to systemMessage),
                            mapOf("text" to userMessage)
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.3,
                    "maxOutputTokens" to 1000,
                    "topP" to 0.95
                )
            )

            val jsonBody = gson.toJson(requestBody)
            val request = okhttp3.Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${settingsManager.getAIModel()}:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)

            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Gemini API error: ${it.code} $responseBody")
                    return@withContext "Processing failed: Gemini API error ${it.code}"
                }

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val candidates = jsonResponse["candidates"] as? List<Map<String, Any>>
                    val content = candidates?.get(0)?.get("content") as? Map<String, Any>
                    val parts = content?.get("parts") as? List<Map<String, Any>>
                    val text = parts?.get(0)?.get("text") as? String

                    if (!text.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "Gemini specific prompt processing successful: '${text.take(100)}...'")
                        val extractedContent = TextProcessingUtils.extractXmlTagContent(text, "FORMATTED_TEXT")
                        return@withContext extractedContent
                    } else {
                        Log.e("AIProcessingManager", "Gemini returned empty content")
                        return@withContext "Processing failed: Empty response"
                    }
                } else {
                    Log.e("AIProcessingManager", "Gemini API response was unsuccessful or empty")
                    return@withContext "Processing failed: Invalid response"
                }
            } ?: run {
                Log.e("AIProcessingManager", "Gemini request timeout")
                return@withContext "Error: Gemini request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Gemini specific prompt processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    private suspend fun performGeminiChat(
        messages: List<Map<String, Any>>
    ): String? {
        return try {
            val userApiKey = secureApiKeyManager.getApiKey("google_api_key") ?: ""

            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: Google Gemini requires switching to Pro mode and configuring a key")
                return "Error: Google Gemini not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "Google API key is missing")
                return "Error: Google API key not configured"
            }

            val client = networkManager.aiProcessingHttpClient
            val requestBody = mapOf(
                "contents" to messages.map { message ->
                    mapOf("parts" to listOf(mapOf("text" to message["content"])))
                },
                "generationConfig" to mapOf(
                    "temperature" to 0.3,
                    "maxOutputTokens" to 1000,
                    "topP" to 0.95
                )
            )

            val jsonBody = gson.toJson(requestBody)
            val request = okhttp3.Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${settingsManager.getAIModel()}:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)
            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Gemini API error: ${it.code} $responseBody")
                    return "Processing failed: Gemini API error ${it.code}"
                }

                if (!responseBody.isNullOrBlank()) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val candidates = jsonResponse["candidates"] as? List<Map<String, Any>>
                    val content = candidates?.getOrNull(0)?.get("content") as? Map<String, Any>
                    val parts = content?.get("parts") as? List<Map<String, Any>>
                    val text = parts?.getOrNull(0)?.get("text") as? String

                    if (!text.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "Gemini chat processing successful: '${text.take(100)}...'")
                        return text
                    }
                }
                return "Processing failed: Invalid response"
            } ?: run {
                Log.e("AIProcessingManager", "Gemini request timeout")
                return "Error: Google Gemini request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Gemini chat processing error", e)
            "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithClaudeSpecificPrompt(
        transcription: String,
        systemMessage: String,
        userMessage: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val userApiKey = secureApiKeyManager.getApiKey("anthropic_api_key") ?: ""

            // Simple Mode does not embed fallback provider keys.
            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: Claude requires switching to Pro mode and configuring a key")
                return@withContext "Error: Claude not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "API Key is missing for Claude processing")
                return@withContext "Processing failed: No API key"
            }

            val requestBody = mapOf(
                "model" to settingsManager.getAIModel(),
                "max_tokens" to 1000,
                "system" to listOf(
                    mapOf(
                        "type" to "text",
                        "text" to systemMessage,
                        "cache_control" to mapOf("type" to "ephemeral")
                    )
                ),
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "text",
                                "text" to userMessage,
                                "cache_control" to mapOf("type" to "ephemeral")
                            )
                        )
                    )
                )
            )

            val jsonBody = gson.toJson(requestBody)
            val client = networkManager.aiProcessingHttpClient

            val request = okhttp3.Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)

            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Claude API error: ${it.code} $responseBody")
                    return@withContext "Processing failed: Claude API error ${it.code}"
                }

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val content = jsonResponse["content"] as? List<Map<String, Any>>
                    val text = content?.get(0)?.get("text") as? String

                    if (!text.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "Claude specific prompt processing successful: '${text.take(100)}...'")
                        val extractedContent = TextProcessingUtils.extractXmlTagContent(text, "FORMATTED_TEXT")
                        return@withContext extractedContent
                    } else {
                        Log.e("AIProcessingManager", "Claude returned empty content")
                        return@withContext "Processing failed: Empty response"
                    }
                } else {
                    Log.e("AIProcessingManager", "Claude API response was unsuccessful or empty")
                    return@withContext "Processing failed: Invalid response"
                }
            } ?: run {
                Log.e("AIProcessingManager", "Claude request timeout")
                return@withContext "Error: Claude request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Claude specific prompt processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    private suspend fun performClaudeChat(messages: List<Map<String, Any>>): String? {
        return try {
            val userApiKey = secureApiKeyManager.getApiKey("anthropic_api_key") ?: ""

            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: Claude requires switching to Pro mode and configuring a key")
                return "Error: Claude not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "API Key is missing for Claude processing")
                return "Processing failed: No API key"
            }

            val client = networkManager.aiProcessingHttpClient
            val requestBody = mapOf(
                "model" to settingsManager.getAIModel(),
                "max_tokens" to 1000,
                "system" to messages.firstOrNull { it["role"] == "system" }?.get("content"),
                "messages" to messages.filter { it["role"] != "system" }
            )

            val jsonBody = gson.toJson(requestBody)
            val request = okhttp3.Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("x-anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)
            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Claude API error: ${it.code} $responseBody")
                    return "Processing failed: Claude API error ${it.code}"
                }

                if (!responseBody.isNullOrBlank()) {
                    val json = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                    val content = json["content"] as? List<*>
                    val textPart = content?.firstOrNull() as? Map<*, *>
                    val text = textPart?.get("text") as? String
                    if (!text.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "Claude chat processing successful: '${text.take(100)}...'")
                        return text
                    }
                }
                return "Processing failed: Invalid response"
            } ?: run {
                Log.e("AIProcessingManager", "Claude request timeout")
                return "Error: Claude request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Claude chat processing error", e)
            "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithGroqSpecificPrompt(
        transcription: String,
        systemMessage: String,
        userMessage: String,
        resolvedModel: String
    ): String? = withContext(Dispatchers.IO) {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemMessage),
            mapOf("role" to "user", "content" to userMessage)
        )
        val raw = performGroqChat(messages, resolvedModel)
        if (raw.isNullOrBlank()) {
            return@withContext "Processing failed: Empty response"
        }
        if (raw.startsWith("Processing failed") || raw.startsWith("Error:")) {
            return@withContext raw
        }
        return@withContext TextProcessingUtils.extractXmlTagContent(raw, "FORMATTED_TEXT")
    }

    private suspend fun performGroqChat(
        messages: List<Map<String, Any>>,
        resolvedModel: String
    ): String? {
        return try {
            val apiKey = resolveGroqApiKey(resolvedModel)
                ?: return "Processing failed: No API key"
            val useProxy = GroqProxyConfig.shouldUseProxy(apiKey)

            val requestBody = mapOf(
                "model" to resolvedModel,
                "messages" to messages,
                "max_tokens" to 1000,
                "temperature" to 0.3
            )

            val jsonBody = gson.toJson(requestBody)

            val requestBuilder = okhttp3.Request.Builder()
                .url(GroqProxyConfig.endpoint("/openai/v1/chat/completions", useProxy))
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))

            GroqProxyConfig.applyHeaders(requestBuilder, apiKey, useProxy)

            val response = executeGroqRequest(requestBuilder, 45_000)

            response?.use {
                val responseBody = it.body?.string()

                Log.d("AIProcessingManager", "Groq AI response code: ${it.code}")
                Log.d("AIProcessingManager", "Groq AI response body: $responseBody")

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Groq API error: ${it.code} $responseBody")
                    return "Processing failed: API error ${it.code}"
                }

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                    val message = choices?.get(0)?.get("message") as? Map<String, Any>
                    val content = message?.get("content") as? String

                    if (!content.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "Groq chat processing successful: '${content.take(100)}...'")
                        return content
                    } else {
                        Log.e("AIProcessingManager", "Groq returned empty content")
                        return "Processing failed: Empty response"
                    }
                } else {
                    Log.e("AIProcessingManager", "Groq API response was unsuccessful or empty")
                    return "Processing failed: Invalid response"
                }
            } ?: run {
                Log.e("AIProcessingManager", "Groq request timeout")
                return "Error: Groq request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Groq chat processing error", e)
            return "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithOpenRouterSpecificPrompt(
        transcription: String,
        systemMessage: String,
        userMessage: String
    ): String? = withContext(Dispatchers.IO) {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemMessage),
            mapOf("role" to "user", "content" to userMessage)
        )
        val raw = performOpenRouterChat(messages)
        if (raw.isNullOrBlank()) return@withContext "Processing failed: Empty response"
        if (raw.startsWith("Processing failed") || raw.startsWith("Error:")) return@withContext raw
        return@withContext TextProcessingUtils.extractXmlTagContent(raw, "FORMATTED_TEXT")
    }

    private suspend fun performOpenRouterChat(messages: List<Map<String, Any>>): String? {
        return try {
            val selectedModel = settingsManager.getOpenRouterModelId()
            val userApiKey = secureApiKeyManager.getApiKey("openrouter_api_key") ?: ""

            val apiKey = if (isSimpleModeForcedFallback()) {
                Log.d("AIProcessingManager", "Simple mode: OpenRouter requires switching to Pro mode and configuring a key")
                return "Error: OpenRouter not available in Simple mode"
            } else if (userApiKey.isNotBlank()) {
                userApiKey
            } else {
                Log.e("AIProcessingManager", "OpenRouter API Key is missing")
                return "Processing failed: No API key"
            }

            val prioritization = settingsManager.getOpenRouterPrioritization()
            val requestBody = buildMap {
                put("model", selectedModel)
                put("messages", messages)
                put("max_tokens", 1000)
                put("temperature", 0.3)
                if (prioritization != "automatic") {
                    put("provider", mapOf("sort" to prioritization))
                }
            }

            val jsonBody = gson.toJson(requestBody)
            val client = networkManager.aiProcessingHttpClient

            val request = okhttp3.Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://wonderwhisper.app")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)

            response?.use {
                val responseBody = it.body?.string()

                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "OpenRouter API error: ${it.code} $responseBody")
                    return "Processing failed: API error ${it.code}"
                }

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                    val message = choices?.get(0)?.get("message") as? Map<String, Any>
                    val content = message?.get("content") as? String

                    if (!content.isNullOrBlank()) {
                        Log.d("AIProcessingManager", "OpenRouter chat processing successful: '${content.take(100)}...'")
                        return content
                    } else {
                        Log.e("AIProcessingManager", "OpenRouter returned empty content")
                        return "Processing failed: Empty response"
                    }
                } else {
                    Log.e("AIProcessingManager", "OpenRouter API response was unsuccessful or empty")
                    return "Processing failed: Invalid response"
                }
            } ?: run {
                Log.e("AIProcessingManager", "OpenRouter request timeout")
                return "Error: OpenRouter request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "OpenRouter chat processing error", e)
            return "Processing failed: ${e.message}"
        }
    }

    /**
     * Cerebras provider (OpenAI-compatible chat completions)
     */
    private suspend fun processWithCerebras(
        transcription: String,
        context: String,
        screenContext: String,
        currentAppContext: String,
        isCommandMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val userApiKey = secureApiKeyManager.getApiKey("cerebras_api_key") ?: ""
            if (userApiKey.isBlank()) {
                Log.e("AIProcessingManager", "Cerebras API key is missing")
                return@withContext "Processing failed: No API key"
            }

            // Build messages similar to other providers
            val dictationPrompt = settingsManager.getDictationPrompt()
            val commandPrompt = settingsManager.getCommandPrompt()
            val languageConfig = settingsManager.getCustomLanguageConfig()
            val baseSystemMessage = if (isCommandMode) commandPrompt else dictationPrompt
            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                baseSystemMessage,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )
            val userMessage = settingsManager.buildUserMessage(
                transcription,
                context,
                currentAppContext,
                screenContext,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )
            lastSystemMessage = systemMessage
            lastUserMessage = userMessage

            val client = networkManager.aiProcessingHttpClient
            // Strip explicit provider prefix if present
            val rawModel = settingsManager.getAIModel()
            val resolvedModel = rawModel.substringAfter("cerebras/")

            val requestBody = mapOf(
                "model" to resolvedModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemMessage),
                    mapOf("role" to "user", "content" to userMessage)
                ),
                // Cerebras uses max_completion_tokens
                "max_completion_tokens" to 1000,
                "temperature" to 0.3
            )

            val jsonBody = gson.toJson(requestBody)
            val request = okhttp3.Request.Builder()
                .url("https://api.cerebras.ai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $userApiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)
            response?.use {
                val responseBody = it.body?.string()
                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Cerebras API error: ${it.code} $responseBody")
                    return@withContext "Processing failed: API error ${it.code}"
                }
                if (!responseBody.isNullOrBlank()) {
                    val json = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                    val choices = json["choices"] as? List<*> ?: emptyList<Any>()
                    if (choices.isNotEmpty()) {
                        val first = choices[0] as? Map<*, *>
                        val message = first?.get("message") as? Map<*, *>
                        val content = (message?.get("content") as? String) ?: ""
                        val extracted = TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                        val result = if (!extracted.isNullOrBlank()) extracted else content
                        // Cache successful result for consistency with other providers
                        try { cacheAIResponse(transcription, "$context|$screenContext", rawModel, result) } catch (_: Exception) {}
                        return@withContext if (result.isNotBlank()) result else "Processing failed: Empty content"
                    }
                }
                return@withContext "Processing failed: Invalid response"
            } ?: run {
                Log.e("AIProcessingManager", "Cerebras request timeout")
                return@withContext "Error: Cerebras request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Cerebras processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    private suspend fun processWithCerebrasSpecificPrompt(
        transcription: String,
        systemMessage: String,
        userMessage: String,
        resolvedModel: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val userApiKey = secureApiKeyManager.getApiKey("cerebras_api_key") ?: ""
            if (userApiKey.isBlank()) {
                Log.e("AIProcessingManager", "Cerebras API key is missing")
                return@withContext "Processing failed: No API key"
            }

            val client = networkManager.aiProcessingHttpClient
            val model = resolvedModel.substringAfter("cerebras/")
            val requestBody = mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemMessage),
                    mapOf("role" to "user", "content" to userMessage)
                ),
                "max_completion_tokens" to 1000,
                "temperature" to 0.3
            )
            val jsonBody = gson.toJson(requestBody)
            val request = okhttp3.Request.Builder()
                .url("https://api.cerebras.ai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $userApiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)
            response?.use {
                val responseBody = it.body?.string()
                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Cerebras API error: ${it.code} $responseBody")
                    return@withContext "Processing failed: API error ${it.code}"
                }
                if (!responseBody.isNullOrBlank()) {
                    val json = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                    val choices = json["choices"] as? List<*> ?: emptyList<Any>()
                    if (choices.isNotEmpty()) {
                        val first = choices[0] as? Map<*, *>
                        val message = first?.get("message") as? Map<*, *>
                        val content = (message?.get("content") as? String) ?: ""
                        val extracted = TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                        val result = if (!extracted.isNullOrBlank()) extracted else content
                        return@withContext if (result.isNotBlank()) result else "Processing failed: Empty content"
                    }
                }
                return@withContext "Processing failed: Invalid response"
            } ?: run {
                Log.e("AIProcessingManager", "Cerebras request timeout")
                return@withContext "Error: Cerebras request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Cerebras specific prompt processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    private suspend fun performCerebrasChat(
        messages: List<Map<String, Any>>,
        resolvedModel: String
    ): String? {
        return try {
            val userApiKey = secureApiKeyManager.getApiKey("cerebras_api_key") ?: ""
            if (userApiKey.isBlank()) {
                Log.e("AIProcessingManager", "Cerebras API key is missing")
                return "Processing failed: No API key"
            }

            val client = networkManager.aiProcessingHttpClient
            val model = resolvedModel.substringAfter("cerebras/")
            val requestBody = mapOf(
                "model" to model,
                "messages" to messages,
                "max_completion_tokens" to 1000,
                "temperature" to 0.3
            )
            val jsonBody = gson.toJson(requestBody)
            val request = okhttp3.Request.Builder()
                .url("https://api.cerebras.ai/v1/chat-completions")
                .addHeader("Authorization", "Bearer $userApiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.executeWithTimeoutOrNull(request, 45_000)
            response?.use {
                val responseBody = it.body?.string()
                if (!it.isSuccessful) {
                    Log.e("AIProcessingManager", "Cerebras API error: ${it.code} $responseBody")
                    return "Processing failed: API error ${it.code}"
                }
                if (!responseBody.isNullOrBlank()) {
                    val json = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                    val choices = json["choices"] as? List<*> ?: emptyList<Any>()
                    if (choices.isNotEmpty()) {
                        val first = choices[0] as? Map<*, *>
                        val message = first?.get("message") as? Map<*, *>
                        val content = message?.get("content") as? String
                        if (!content.isNullOrBlank()) {
                            Log.d("AIProcessingManager", "Cerebras chat processing successful: '${content.take(100)}...'")
                            return content
                        }
                    }
                }
                return "Processing failed: Invalid response"
            } ?: run {
                Log.e("AIProcessingManager", "Cerebras request timeout")
                return "Error: Cerebras request timeout"
            }
        } catch (e: Exception) {
            Log.e("AIProcessingManager", "Cerebras chat processing error", e)
            return "Processing failed: ${e.message}"
        }
    }

    /**
     * Clear AI response cache
     */
    fun clearCache() {
        val size = aiResponseCache.size
        aiResponseCache.clear()
        Log.d("AIProcessingManager", "Cleared $size cached AI responses")
    }

    private suspend fun executeGroqRequest(
        requestBuilder: okhttp3.Request.Builder,
        timeoutMs: Long
    ): Response? {
        var response: Response? = null
        var primaryError: Exception? = null
        try {
            response = networkManager.aiProcessingHttpClient.executeWithTimeoutOrNull(
                requestBuilder.build(),
                timeoutMs
            )
        } catch (e: Exception) {
            primaryError = e
            Log.w(
                "AIProcessingManager",
                "Groq primary (HTTP/2) request failed, attempting HTTP/1.1 fallback",
                e
            )
        }

        if (response == null) {
            try {
                response = networkManager.groqAIProcessingHttp1Client.executeWithTimeoutOrNull(
                    requestBuilder.build(),
                    timeoutMs
                )
            } catch (fallbackError: Exception) {
                primaryError?.let {
                    Log.e("AIProcessingManager", "Groq primary request error", it)
                }
                throw fallbackError
            }
        }

        return response
    }

    private suspend fun OkHttpClient.executeWithTimeoutOrNull(
        request: Request,
        timeoutMs: Long
    ): Response? {
        val call = newCall(request).apply {
            timeout().timeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        return try {
            withContext(Dispatchers.IO) { call.execute() }
        } catch (e: Exception) {
            // Ensure underlying socket is torn down if anything throws
            try { call.cancel() } catch (_: Exception) {}
            // Return null when OkHttp reports the per-call timeout; rethrow others
            if (e is java.io.InterruptedIOException) null else throw e
        }
    }
}
