package com.slumdog88.dictationkeyboardai.transcription

import android.content.Context
import android.util.Log
import com.slumdog88.dictationkeyboardai.PerformanceMetrics
import com.slumdog88.dictationkeyboardai.PerformanceMetricsBuilder
import com.slumdog88.dictationkeyboardai.SecureApiKeyManager
import com.slumdog88.dictationkeyboardai.network.GroqProxyConfig
import com.slumdog88.dictationkeyboardai.network.NetworkManager
import com.slumdog88.dictationkeyboardai.offline.OFFLINE_TRANSCRIPTION_OPTION_LABEL
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperTranscriber
import com.slumdog88.dictationkeyboardai.offline.whisper.RunState
import com.slumdog88.dictationkeyboardai.utils.AudioFileManager
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils
import com.google.gson.Gson
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Manager class for handling all transcription services including caching,
 * provider selection, and result processing.
 */
class TranscriptionServiceManager(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val settingsManager: SettingsManager,
    private val audioFileManager: AudioFileManager,
    private val secureApiKeyManager: SecureApiKeyManager
) {

    /**
     * Check whether Simple Mode should use the recommended provider defaults.
     */
    private fun isSimpleModeForcedFallback(): Boolean {
        return settingsManager.isSimpleMode()
    }
    
    private val gson = Gson()
    
    // Response caching for transcription to avoid repeated API calls
    private data class CachedResponse(
        val result: String,
        val timestamp: Long,
        val ttl: Long = 300_000 // 5 minutes TTL
    )
    
    private val transcriptionCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResponse>?): Boolean {
                return size > 50 || (eldest?.value?.let {
                    System.currentTimeMillis() - it.timestamp > it.ttl
                } ?: false)
            }
        }
    )
    
    /**
     * Main transcription method that handles provider selection and caching
     */
    suspend fun performTranscription(
        audioFile: File,
        offlineSamples: FloatArray? = null,
        offlineSampleRate: Int = 16_000,
        serviceOverride: String? = null
    ): String {
        val (result, _) = performTranscriptionWithMetrics(audioFile, offlineSamples, offlineSampleRate, serviceOverride)
        return result
    }

    /**
     * Main transcription method with performance metrics tracking
     */
    suspend fun performTranscriptionWithMetrics(
        audioFile: File,
        offlineSamples: FloatArray? = null,
        offlineSampleRate: Int = 16_000,
        serviceOverride: String? = null
    ): Pair<String, PerformanceMetrics> {
        val metricsBuilder = PerformanceMetricsBuilder()

        try {
            val transcriptionService = serviceOverride ?: settingsManager.getTranscriptionService()
            Log.d("TranscriptionServiceManager", "Selected transcription_service='$transcriptionService' (override=${serviceOverride != null})")
            metricsBuilder.setTranscriptionService(transcriptionService)

            // Check transcription cache first
            val audioFileHash = audioFileManager.getAudioFileHash(audioFile)
            val cacheKey = "${transcriptionService}_$audioFileHash"
            val cachedTranscription = getCachedTranscription(cacheKey)
            if (cachedTranscription != null) {
                Log.d("TranscriptionServiceManager", "Using cached transcription for ${audioFile.name}")
                metricsBuilder.setTranscriptionCacheHit(true)
                val metrics = metricsBuilder.build()
                return Pair(cachedTranscription, metrics)
            }

            metricsBuilder.setTranscriptionCacheHit(false)
            metricsBuilder.startTranscription()
            Log.d("TranscriptionServiceManager", "Beginning transcription routing for service='$transcriptionService'")

            var transcriptionResult = when (transcriptionService) {
                "ElevenLabs Scribe" -> {
                    Log.d("TranscriptionServiceManager", "Routing to ElevenLabs Scribe")
                    performElevenLabsTranscription(audioFile)
                }
                "GPT-4o Transcribe" -> {
                    Log.d("TranscriptionServiceManager", "Routing to OpenAI GPT-4o Transcribe")
                    performGPT4oTranscription(audioFile)
                }
                "Gemini 2.0 Flash" -> {
                    Log.d("TranscriptionServiceManager", "Routing to Gemini 2.0 Flash")
                    performGeminiTranscription(audioFile, "gemini-2.0-flash")
                }
                "Gemini 2.5 Flash" -> {
                    Log.d("TranscriptionServiceManager", "Routing to Gemini 2.5 Flash")
                    performGeminiTranscription(audioFile, "gemini-2.5-flash")
                }
                "Gemini 2.5 Pro" -> {
                    Log.d("TranscriptionServiceManager", "Routing to Gemini 2.5 Pro")
                    performGeminiTranscription(audioFile, "gemini-2.5-pro")
                }
                "Deepgram Nova-3" -> {
                    Log.d("TranscriptionServiceManager", "Routing to Deepgram Nova-3")
                    performDeepgramTranscription(audioFile, "nova-3")
                }
                "Groq Whisper v3 Large" -> {
                    Log.d("TranscriptionServiceManager", "Routing to Groq Whisper v3 Large")
                    performGroqTranscription(audioFile, "whisper-large-v3")
                }
                "Groq Whisper v3 Turbo" -> {
                    Log.d("TranscriptionServiceManager", "Routing to Groq Whisper v3 Turbo")
                    performGroqTranscription(audioFile, "whisper-large-v3-turbo")
                }
                "Mistral Voxtral Mini" -> {
                    Log.d("TranscriptionServiceManager", "Routing to Mistral Voxtral Mini (voxtral-mini-latest)")
                    performMistralTranscription(audioFile, "voxtral-mini-latest")
                }
                "AssemblyAI" -> {
                    Log.d("TranscriptionServiceManager", "Routing to AssemblyAI")
                    performAssemblyAITranscription(audioFile)
                }
                OFFLINE_TRANSCRIPTION_OPTION_LABEL -> {
                    Log.d("TranscriptionServiceManager", "Routing to Offline Whisper (on-device)")
                    performOfflineWhisperTranscription(audioFile, offlineSamples, offlineSampleRate)
                }
                else -> {
                    Log.d("TranscriptionServiceManager", "Routing to OpenAI Whisper (default fallback), service='$transcriptionService'")
                    performWhisperTranscription(audioFile) // Default to OpenAI Whisper
                }
            }

            // No automatic fallback on key or network errors: return original error

            metricsBuilder.endTranscription()

            // Cache successful transcription results
            if (transcriptionResult.isNotBlank() && !transcriptionResult.contains("error", ignoreCase = true)) {
                cacheTranscription(cacheKey, transcriptionResult)
            }

            val metrics = metricsBuilder.build()
            return Pair(transcriptionResult, metrics)
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "Transcription failed", e)
            val metrics = metricsBuilder.build()
            return Pair("Transcription failed: ${e.message}", metrics)
        }
    }
    
    /**
     * OpenAI Whisper transcription
     */
    private suspend fun performWhisperTranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("openai_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "OpenAI API key is missing")
                return "Error: OpenAI API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting OpenAI Whisper transcription")

            val vocabularyItems = collectGlossaryTerms()

            val promptBuilder = StringBuilder()
            if (vocabularyItems.isNotEmpty()) {
                promptBuilder.apply {
                    // Limit to 20 words maximum for Whisper API accuracy
                    append(vocabularyItems.take(20).joinToString(", "))
                }
            }

            // Use shared HTTP client with connection pooling for better performance
            val client = networkManager.transcriptionHttpClient
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model", "whisper-1")

            if (promptBuilder.isNotEmpty()) {
                requestBuilder.addFormDataPart("prompt", promptBuilder.toString())
            }

            val requestBody = requestBuilder.build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.executeWithTimeoutOrNull(request, 70_000)

            response?.use {
                val responseBody = it.body?.string()
                Log.d("TranscriptionServiceManager", "OpenAI Whisper response: $responseBody")

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val transcription = jsonResponse["text"] as? String ?: ""

                    Log.d("TranscriptionServiceManager", "OpenAI Whisper transcription successful: '${transcription.take(100)}...'")
                    return transcription
                } else {
                    Log.e("TranscriptionServiceManager", "OpenAI Whisper API error: ${it.code} - $responseBody")
                    return "Error: OpenAI Whisper transcription failed (${it.code})"
                }
            } ?: run {
                Log.e("TranscriptionServiceManager", "OpenAI Whisper request timeout")
                return "Error: OpenAI Whisper request timeout"
            }
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "OpenAI Whisper transcription error", e)
            return "Error: ${e.message}"
        }
    }
    
    /**
     * Get cached transcription result
     */
    private fun getCachedTranscription(audioFileHash: String): String? {
        val cached = transcriptionCache[audioFileHash]
        return if (cached != null && System.currentTimeMillis() - cached.timestamp < cached.ttl) {
            Log.d("TranscriptionServiceManager", "Cache HIT for transcription")
            cached.result
        } else {
            if (cached != null) {
                transcriptionCache.remove(audioFileHash)
            }
            Log.d("TranscriptionServiceManager", "Cache MISS for transcription")
            null
        }
    }

    /**
     * Cache transcription result
     */
    private fun cacheTranscription(audioFileHash: String, result: String) {
        transcriptionCache[audioFileHash] = CachedResponse(result, System.currentTimeMillis())
        Log.d("TranscriptionServiceManager", "Cached transcription result")
    }

    private suspend fun OkHttpClient.executeWithTimeoutOrNull(
        request: Request,
        timeoutMs: Long
    ): Response? {
        val call = newCall(request).apply {
            timeout().timeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        return try {
            withContext(kotlinx.coroutines.Dispatchers.IO) { call.execute() }
        } catch (e: Exception) {
            try { call.cancel() } catch (_: Exception) {}
            if (e is java.net.SocketTimeoutException) null else throw e
        }
    }
    
    /**
     * GPT-4o transcription
     */
    private suspend fun performGPT4oTranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("openai_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "OpenAI API key is missing")
                return "Error: OpenAI API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting GPT-4o transcription")

            val languageConfig = settingsManager.getCustomLanguageConfig()
            val vocabularyItems = buildList {
                addAll(languageConfig.vocabularyItems)
                languageConfig.spellingPairs.forEach { (from, to) ->
                    add(from)
                    add(to)
                }
            }.filter { it.isNotBlank() }

            val promptBuilder = StringBuilder()
            if (vocabularyItems.isNotEmpty()) {
                promptBuilder.apply {
                    // Limit to 20 words maximum for Whisper API accuracy
                    append(vocabularyItems.take(20).joinToString(", "))
                }
            }

            // Use shared HTTP client with connection pooling for better performance
            val client = networkManager.transcriptionHttpClient
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model", "gpt-4o-transcribe")

            if (promptBuilder.isNotEmpty()) {
                requestBuilder.addFormDataPart("prompt", promptBuilder.toString())
            }

            val requestBody = requestBuilder.build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.executeWithTimeoutOrNull(request, 70_000)

            response?.use {
                val responseBody = it.body?.string()
                Log.d("TranscriptionServiceManager", "GPT-4o response: $responseBody")

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val transcription = jsonResponse["text"] as? String ?: ""

                    Log.d("TranscriptionServiceManager", "GPT-4o transcription successful: '${transcription.take(100)}...'")
                    return transcription
                } else {
                    Log.e("TranscriptionServiceManager", "GPT-4o API error: ${it.code} - $responseBody")
                    return "Error: GPT-4o transcription failed (${it.code})"
                }
            } ?: run {
                Log.e("TranscriptionServiceManager", "GPT-4o request timeout")
                return "Error: GPT-4o request timeout"
            }
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "GPT-4o transcription error", e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Groq transcription with various Whisper models
     */
    private suspend fun performGroqTranscription(audioFile: File, model: String = "whisper-large-v3"): String {
        try {
            val userApiKey = secureApiKeyManager.getApiKey("groq_api_key") ?: ""
            val useProxy = GroqProxyConfig.shouldUseProxy(userApiKey)

            val apiKey = when {
                useProxy -> {
                    Log.d("TranscriptionServiceManager", "Using hosted Groq proxy for model '$model'")
                    ""
                }
                userApiKey.isNotBlank() -> userApiKey
                else -> {
                    Log.e("TranscriptionServiceManager", "Groq API key is missing for model '$model'")
                    return "Error: Groq API key not configured"
                }
            }

            if (!useProxy && apiKey.isBlank()) {
                Log.e("TranscriptionServiceManager", "Groq API key is missing")
                return "Error: Groq API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting Groq transcription with model: $model")

            val languageConfig = settingsManager.getCustomLanguageConfig()
            val vocabularyItems = buildList {
                addAll(languageConfig.vocabularyItems)
                languageConfig.spellingPairs.forEach { (from, to) ->
                    add(from)
                    add(to)
                }
            }.filter { it.isNotBlank() }

            val promptBuilder = StringBuilder()
            if (vocabularyItems.isNotEmpty()) {
                promptBuilder.apply {
                    // Limit to 20 words maximum for Whisper API accuracy
                    append(vocabularyItems.take(20).joinToString(", "))
                }
            }

            // Groq supports multiple audio formats including m4a
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "json")

            if (promptBuilder.isNotEmpty()) {
                requestBuilder.addFormDataPart("prompt", promptBuilder.toString())
            }

            val requestBody = requestBuilder.build()

            val httpRequestBuilder = Request.Builder()
                .url(GroqProxyConfig.endpoint("/openai/v1/audio/transcriptions", useProxy))
                .addHeader("Accept", "application/json")
                .post(requestBody)

            GroqProxyConfig.applyHeaders(httpRequestBuilder, apiKey, useProxy)

            var response: Response? = null
            var primaryError: Exception? = null
            try {
                // Force HTTP/1.1 for Groq transcription to avoid HTTP/2 issues
                response = networkManager.groqTranscriptionHttp1Client.executeWithTimeoutOrNull(
                    httpRequestBuilder.build(),
                    70_000
                )
            } catch (e: Exception) {
                primaryError = e
                Log.e("TranscriptionServiceManager", "Groq transcription request failed", e)
                // No fallback, just fail
                throw e 
            }
            
            response?.use {
                val responseBody = it.body?.string()
                Log.d("TranscriptionServiceManager", "Groq response: $responseBody")

                if (it.isSuccessful && responseBody != null) {
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    var transcription = jsonResponse["text"] as? String ?: ""

                    // Apply custom vocabulary replacements for Groq (since it doesn't support prompts as well)
                    transcription = TextProcessingUtils.applyCustomVocabularyReplacements(
                        transcription,
                        languageConfig.vocabularyItems,
                        languageConfig.replacementRules
                    )

                    Log.d("TranscriptionServiceManager", "Groq transcription successful: '${transcription.take(100)}...'")
                    return transcription
                } else {
                    val code = it.code
                    Log.e("TranscriptionServiceManager", "Groq API error: $code - $responseBody")
                    if (code == 403) {
                        // Explicitly surface access restriction for UX handling upstream
                        return "Error: Groq access denied (403)"
                    }
                    return "Error: Groq transcription failed ($code)"
                }
            } ?: run {
                Log.e("TranscriptionServiceManager", "Groq request timeout")
                return "Error: Groq request timeout"
            }
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "Groq transcription error", e)
            return "Error: ${e.message}"
        }
    }

    // Placeholder methods for other transcription services
    // These would be implemented similarly to the above methods

    private suspend fun performGeminiTranscription(audioFile: File, model: String): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("google_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "Google API key is missing")
                return "Error: Google API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting Gemini transcription with model: $model")

            val languageConfig = settingsManager.getCustomLanguageConfig()
            val vocabularyItems = languageConfig.vocabularyItems

            val transcriptionPrompt = if (vocabularyItems.isNotEmpty()) {
                "Transcribe the following audio accurately. Vocabulary context: " +
                    vocabularyItems.joinToString(", ")
            } else {
                "Transcribe the following audio accurately."
            }

            // Decide mime and base64 audio
            val mimeType = "audio/m4a"
            val audioBytes = audioFile.readBytes()
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)

            // Model-specific generation configuration
            val isProModel = model.contains("pro", ignoreCase = true)
            val maxTokens = if (isProModel) 4096 else 3072
            val temperature = if (isProModel) 0.05 else 0.1

            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to transcriptionPrompt),
                            mapOf(
                                "inline_data" to mapOf(
                                    "mime_type" to mimeType,
                                    "data" to base64Audio
                                )
                            )
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to temperature,
                    "maxOutputTokens" to maxTokens,
                    "topP" to 0.95
                )
            )

            val jsonBody = gson.toJson(requestBody)

            val client = networkManager.transcriptionHttpClient
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            // Performance fix: Added timeout to prevent indefinite hangs
            val response = client.executeWithTimeoutOrNull(request, 70_000)
            if (response == null) {
                Log.e("TranscriptionServiceManager", "Gemini API timed out")
                return "Error: Gemini API timeout"
            }
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("TranscriptionServiceManager", "Gemini API error: ${response.code} $responseBody")
                return "Error: Gemini API error ${response.code}"
            }

            if (!responseBody.isNullOrBlank()) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val candidates = jsonResponse["candidates"] as? List<Map<String, Any>>
                val content = candidates?.get(0)?.get("content") as? Map<String, Any>
                val parts = content?.get("parts") as? List<Map<String, Any>>
                val text = parts?.get(0)?.get("text") as? String ?: ""
                return text
            }

            Log.e("TranscriptionServiceManager", "Gemini API response was empty")
            return "Error: Gemini empty response"
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "Gemini transcription error", e)
            return "Error: ${e.message}"
        }
    }

    private suspend fun performElevenLabsTranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("elevenlabs_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "ElevenLabs API key is missing")
                return "Error: ElevenLabs API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting ElevenLabs Scribe API transcription")

            val client = networkManager.transcriptionHttpClient

            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model_id", "scribe_v1")
                .addFormDataPart("language_code", "en")
                .addFormDataPart("tag_audio_events", "false")
                .addFormDataPart("timestamps_granularity", "word")
                .build()

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/speech-to-text")
                .addHeader("xi-api-key", apiKey)
                .post(requestBody)
                .build()

            // Performance fix: Added timeout to prevent indefinite hangs
            val response = client.executeWithTimeoutOrNull(request, 70_000)
            if (response == null) {
                Log.e("TranscriptionServiceManager", "ElevenLabs API timed out")
                return "Error: ElevenLabs API timeout"
            }
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("TranscriptionServiceManager", "ElevenLabs API error: ${response.code} $responseBody")
                return "Error: ElevenLabs API error ${response.code}"
            }

            if (!responseBody.isNullOrBlank()) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val transcribedText = jsonResponse["text"]?.toString() ?: ""
                Log.d("TranscriptionServiceManager", "ElevenLabs transcription successful: '${transcribedText.take(100)}...'")
                return transcribedText
            }

            Log.e("TranscriptionServiceManager", "ElevenLabs API response was empty")
            return "Error: ElevenLabs empty response"
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "ElevenLabs transcription error", e)
            return "Error: ${e.message}"
        }
    }

    private suspend fun performMistralTranscription(audioFile: File, model: String): String {
        Log.d("TranscriptionServiceManager", "Starting Mistral transcription path with model=$model")
        try {
            val apiKey = secureApiKeyManager.getApiKey("mistral_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "Mistral API key is missing")
                return "Error: Mistral API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting Mistral transcription with model: $model")

            // Optional vocabulary context (consistent with other providers)
            val customVocabulary = settingsManager.getCustomVocabulary()
            val vocabularyItems = if (customVocabulary.isNotBlank()) {
                customVocabulary.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else emptyList()

            val instruction = if (vocabularyItems.isNotEmpty()) {
                "Transcribe the audio accurately. Vocabulary context: ${vocabularyItems.joinToString(", ")}"
            } else {
                "Transcribe the audio accurately."
            }

            val client = networkManager.transcriptionHttpClient
            val mediaType = "audio/m4a".toMediaTypeOrNull()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("instruction", instruction)
                .build()

            val request = Request.Builder()
                .url("https://api.mistral.ai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.executeWithTimeoutOrNull(request, 70_000)

            response?.use {
                val responseBody = it.body?.string()
                Log.d("TranscriptionServiceManager", "Mistral response: $responseBody")

                if (it.isSuccessful && !responseBody.isNullOrBlank()) {
                    val json = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val text = json["text"] as? String
                        ?: json["transcript"] as? String
                        ?: json["output_text"] as? String
                        ?: ""
                    if (text.isNotBlank()) return text
                    return "Error: Mistral empty response"
                } else {
                    Log.e("TranscriptionServiceManager", "Mistral API error: ${it.code} - $responseBody")
                    return "Error: Mistral transcription failed (${it.code})"
                }
            } ?: run {
                Log.e("TranscriptionServiceManager", "Mistral request timeout")
                return "Error: Mistral request timeout"
            }
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "Mistral transcription error", e)
            return "Error: ${e.message}"
        }
    }

    private suspend fun performDeepgramTranscription(audioFile: File, model: String): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("deepgram_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "Deepgram API key is missing")
                return "Error: Deepgram API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting Deepgram transcription with model: $model")

            // Build vocabulary keyterms from existing custom vocabulary system
            val customVocabulary = settingsManager.getCustomVocabulary()
            val customSpelling = settingsManager.getCustomSpelling()

            val keyterms = if (customVocabulary.isNotBlank()) {
                customVocabulary.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(100)
            } else emptyList()

            // Build URL with parameters
            val urlBuilder = StringBuilder("https://api.deepgram.com/v1/listen")
            urlBuilder.append("?smart_format=true")
            urlBuilder.append("&language=en")
            urlBuilder.append("&model=$model")
            keyterms.forEach { term ->
                try {
                    val encodedTerm = java.net.URLEncoder.encode(term, "UTF-8")
                    urlBuilder.append("&keyterm=$encodedTerm")
                } catch (_: Exception) { }
            }
            val finalUrl = urlBuilder.toString()

            val client = networkManager.transcriptionHttpClient
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBody = audioFile.asRequestBody(mediaType)

            val request = Request.Builder()
                .url(finalUrl)
                .addHeader("Authorization", "Token $apiKey")
                .addHeader("Content-Type", "audio/m4a")
                .post(requestBody)
                .build()

            // Performance fix: Added timeout to prevent indefinite hangs
            val response = client.executeWithTimeoutOrNull(request, 70_000)
            if (response == null) {
                Log.e("TranscriptionServiceManager", "Deepgram API timed out")
                return "Error: Deepgram API timeout"
            }
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("TranscriptionServiceManager", "Deepgram API error: ${response.code} $responseBody")
                return "Error: Deepgram API error ${response.code}"
            }

            if (!responseBody.isNullOrBlank()) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val results = jsonResponse["results"] as? Map<String, Any>
                val channels = results?.get("channels") as? List<Map<String, Any>>
                val alternatives = channels?.get(0)?.get("alternatives") as? List<Map<String, Any>>
                val alternative = alternatives?.get(0)

                val paragraphs = alternative?.get("paragraphs") as? Map<String, Any>
                val transcriptionResult = if (paragraphs != null) {
                    val formattedTranscript = paragraphs["transcript"] as? String
                    if (!formattedTranscript.isNullOrBlank()) formattedTranscript else alternative?.get("transcript") as? String ?: ""
                } else {
                    alternative?.get("transcript") as? String ?: ""
                }

                // Apply post spelling replacements only (Deepgram already uses keyterms)
                val finalResult = TextProcessingUtils.applyCustomVocabularyReplacements(transcriptionResult, "", customSpelling)
                return finalResult
            }

            Log.e("TranscriptionServiceManager", "Deepgram API response was empty")
            return "Error: Deepgram empty response"
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "Deepgram transcription error", e)
            return "Error: ${e.message}"
        }
    }

    private suspend fun performAssemblyAITranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("assemblyai_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "AssemblyAI API key is missing")
                return "Error: AssemblyAI API key not configured"
            }

            Log.d("TranscriptionServiceManager", "Starting AssemblyAI transcription")

            val client = networkManager.transcriptionHttpClient

            // Step 1: Upload the audio file to AssemblyAI
            Log.d("TranscriptionServiceManager", "Uploading audio file to AssemblyAI")
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val uploadRequestBody = audioFile.asRequestBody(mediaType)

            val uploadRequest = Request.Builder()
                .url("https://api.assemblyai.com/v2/upload")
                .addHeader("authorization", apiKey)
                .addHeader("content-type", "audio/m4a")
                .post(uploadRequestBody)
                .build()

            val uploadResponse = client.newCall(uploadRequest).execute()
            val uploadResponseBody = uploadResponse.body?.string()

            if (!uploadResponse.isSuccessful) {
                Log.e("TranscriptionServiceManager", "AssemblyAI upload error: ${uploadResponse.code} $uploadResponseBody")
                return "Error: AssemblyAI upload error ${uploadResponse.code}"
            }

            val uploadJson = gson.fromJson(uploadResponseBody, Map::class.java) as Map<String, Any>
            val audioUrl = uploadJson["upload_url"]?.toString()

            if (audioUrl.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "Failed to get upload URL from AssemblyAI")
                return "Error: AssemblyAI upload failed"
            }

            Log.d("TranscriptionServiceManager", "Audio uploaded successfully. Starting transcription...")

            // Build keyterms and custom spelling from settings
            val customVocabulary = settingsManager.getCustomVocabulary()
            val customSpelling = settingsManager.getCustomSpelling()

            val keytermsPrompt = if (customVocabulary.isNotBlank()) {
                customVocabulary.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(1000)
            } else emptyList()

            // AssemblyAI custom_spelling accepts only single-word "to"
            val customSpellingList = if (customSpelling.isNotBlank()) {
                customSpelling.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.contains("=") }
                    .mapNotNull { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) {
                            val from = parts[0].trim().removePrefix("\"").removeSuffix("\"").trim()
                            val to = parts[1].trim().removePrefix("\"").removeSuffix("\"").trim()
                            if (from.isNotBlank() && to.isNotBlank() && !to.contains(" ")) {
                                mapOf("to" to to, "from" to listOf(from))
                            } else null
                        } else null
                    }
            } else emptyList()

            val transcriptionRequestBody = mutableMapOf<String, Any>(
                "audio_url" to audioUrl,
                "speech_model" to "slam-1",
                "language_code" to "en_us",
                "punctuate" to true,
                "format_text" to true,
                "auto_highlights" to false,
                "speaker_labels" to false
            )

            if (keytermsPrompt.isNotEmpty()) {
                transcriptionRequestBody["keyterms_prompt"] = keytermsPrompt
            }
            if (customSpellingList.isNotEmpty()) {
                transcriptionRequestBody["custom_spelling"] = customSpellingList
            }

            val jsonBody = gson.toJson(transcriptionRequestBody)

            val transcriptionRequest = Request.Builder()
                .url("https://api.assemblyai.com/v2/transcript")
                .addHeader("authorization", apiKey)
                .addHeader("content-type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val transcriptionResponse = client.newCall(transcriptionRequest).execute()
            val transcriptionResponseBody = transcriptionResponse.body?.string()

            if (!transcriptionResponse.isSuccessful) {
                Log.e("TranscriptionServiceManager", "AssemblyAI transcription request error: ${transcriptionResponse.code} $transcriptionResponseBody")
                return try {
                    val errorJson = gson.fromJson(transcriptionResponseBody, Map::class.java) as Map<String, Any>
                    val errorMessage = errorJson["error"]?.toString() ?: "Unknown error"
                    "Error: AssemblyAI $errorMessage"
                } catch (e: Exception) {
                    "Error: AssemblyAI transcription error ${transcriptionResponse.code}"
                }
            }

            val transcriptionJson = gson.fromJson(transcriptionResponseBody, Map::class.java) as Map<String, Any>
            val transcriptId = transcriptionJson["id"]?.toString()

            if (transcriptId.isNullOrBlank()) {
                Log.e("TranscriptionServiceManager", "Failed to get transcript ID from AssemblyAI")
                return "Error: AssemblyAI failed to start transcription"
            }

            Log.d("TranscriptionServiceManager", "Transcription started with ID: $transcriptId. Polling for results...")

            var attempts = 0
            val maxAttempts = 60
            while (attempts < maxAttempts) {
                delay(2000)
                attempts++

                val pollRequest = Request.Builder()
                    .url("https://api.assemblyai.com/v2/transcript/$transcriptId")
                    .addHeader("authorization", apiKey)
                    .get()
                    .build()

                val pollResponse = client.newCall(pollRequest).execute()
                val pollResponseBody = pollResponse.body?.string()

                if (!pollResponse.isSuccessful) {
                    Log.e("TranscriptionServiceManager", "AssemblyAI polling error: ${pollResponse.code} $pollResponseBody")
                    continue
                }

                val pollJson = gson.fromJson(pollResponseBody, Map::class.java) as Map<String, Any>
                val status = pollJson["status"]?.toString()

                when (status) {
                    "completed" -> {
                        val transcribedText = pollJson["text"]?.toString() ?: ""

                        // Optionally fetch paragraph formatting
                        val enableParagraphs = settingsManager.getSettings().getBoolean("enable_paragraphs", false)
                        if (enableParagraphs && transcribedText.length > 100) {
                            val paragraphText = fetchParagraphsFromAssemblyAI(transcriptId, apiKey)
                            if (!paragraphText.isNullOrBlank()) {
                                return paragraphText
                            }
                        }
                        return transcribedText
                    }
                    "error" -> {
                        val error = pollJson["error"]?.toString() ?: "Unknown error"
                        Log.e("TranscriptionServiceManager", "AssemblyAI transcription failed: $error")
                        return "Error: AssemblyAI transcription failed: $error"
                    }
                    "processing", "queued" -> {
                        Log.d("TranscriptionServiceManager", "AssemblyAI transcription status: $status (attempt $attempts/$maxAttempts)")
                        continue
                    }
                    else -> {
                        Log.w("TranscriptionServiceManager", "Unknown AssemblyAI status: $status")
                        continue
                    }
                }
            }

            Log.e("TranscriptionServiceManager", "AssemblyAI transcription timed out after $maxAttempts attempts")
            return "Error: AssemblyAI transcription timed out"
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "AssemblyAI transcription error", e)
            return "Error: ${e.message}"
        }
    }


    private suspend fun performOfflineWhisperTranscription(audioFile: File, pcmSamples: FloatArray?, sampleRate: Int): String {
        return try {
            val modelId = settingsManager.getOfflineWhisperModelId()
            // Limit to 20 words maximum for Whisper API accuracy
            val glossary = collectGlossaryTerms().take(20).joinToString(", ")

            OfflineWhisperTranscriber.transcribe(
                context = context,
                modelId = modelId,
                audioFile = audioFile,
                glossary = glossary,
                forceLanguage = null,
                pcmSamples = pcmSamples,
                sampleRate = sampleRate,
                onPartialResult = { partial ->
                    Log.d(
                        "TranscriptionServiceManager",
                        "Offline partial update: \"${partial.take(80)}\""
                    )
                },
                onStatusUpdate = { status ->
                    Log.d("TranscriptionServiceManager", "Offline Whisper status: $status")
                }
            )
        } catch (e: IllegalStateException) {
            Log.e("TranscriptionServiceManager", "Offline Whisper model error", e)
            "Error: ${e.message ?: "Offline model is not ready."}"
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "Offline Whisper transcription error", e)
            "Error: Offline transcription failed (${e.message ?: "unknown error"})"
        }
    }

    private fun collectGlossaryTerms(): List<String> {
        val languageConfig = settingsManager.getCustomLanguageConfig()
        val terms = mutableListOf<String>()
        terms.addAll(languageConfig.vocabularyItems)
        languageConfig.spellingPairs.forEach { (from, to) ->
            if (from.isNotBlank()) terms.add(from)
            if (to.isNotBlank()) terms.add(to)
        }
        return terms.filter { it.isNotBlank() }
    }

    private suspend fun fetchParagraphsFromAssemblyAI(
        transcriptId: String,
        apiKey: String
    ): String? {
        return try {
            val client = networkManager.transcriptionHttpClient
            val paragraphsRequest = Request.Builder()
                .url("https://api.assemblyai.com/v2/transcript/$transcriptId/paragraphs")
                .addHeader("authorization", apiKey)
                .get()
                .build()

            val paragraphsResponse = client.newCall(paragraphsRequest).execute()
            val paragraphsResponseBody = paragraphsResponse.body?.string()

            if (!paragraphsResponse.isSuccessful) {
                Log.e("TranscriptionServiceManager", "AssemblyAI paragraphs error: ${paragraphsResponse.code} $paragraphsResponseBody")
                return null
            }

            val gsonLocal = Gson()
            val paragraphsJson = gsonLocal.fromJson(paragraphsResponseBody, Map::class.java) as Map<String, Any>
            val paragraphsList = paragraphsJson["paragraphs"] as? List<Map<String, Any>>
            if (paragraphsList.isNullOrEmpty()) return null

            val paragraphsText = buildString {
                paragraphsList.forEach { para ->
                    val text = para["text"]?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        append(text)
                        append("\n\n")
                    }
                }
            }.trim()

            if (paragraphsText.isNotBlank()) paragraphsText else null
        } catch (e: Exception) {
            Log.e("TranscriptionServiceManager", "Error fetching AssemblyAI paragraphs", e)
            null
        }
    }

    /**
     * Clear transcription cache
     */
    fun clearCache() {
        val size = transcriptionCache.size
        transcriptionCache.clear()
        Log.d("TranscriptionServiceManager", "Cleared $size cached transcription results")
    }
}
