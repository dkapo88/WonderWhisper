package com.slumdog88.dictationkeyboardai.network

import android.content.Context
import android.util.Log
import com.slumdog88.dictationkeyboardai.network.GroqProxyConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ConnectionSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manager class for handling network operations including HTTP clients,
 * connection pooling, and connection pre-warming for optimal performance.
 */
class NetworkManager {
    
    /**
     * HTTP client optimized for transcription services with larger timeouts
     * for audio file uploads and processing
     */
    val transcriptionHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            // Optimized timeouts for transcription services
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)  // Audio upload can be large
            .readTimeout(60, TimeUnit.SECONDS)   // Transcription processing time
            .callTimeout(75, TimeUnit.SECONDS)   // Total operation timeout
            .retryOnConnectionFailure(true)
            // OkHttp automatically handles gzip compression when no manual Accept-Encoding is set
            .build()
    }
    
    /**
     * HTTP client optimized for AI processing with faster timeouts
     * for text-only, smaller payloads
     */
    val aiProcessingHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 3, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            // Faster timeouts for AI processing (text-only, smaller payloads)
            .connectTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // OkHttp automatically handles gzip compression when no manual Accept-Encoding is set
            .build()
    }

    /**
     * HTTP/1.1-only fallback clients for Groq services that have HTTP/2 issues
     */
    val groqTranscriptionHttp1Client by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val groqAIProcessingHttp1Client by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 3, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * Pre-warm network connections to improve first-request performance.
     * Only warms endpoints for the user's configured transcription and AI services.
     */
    fun preWarmConnections(scope: CoroutineScope, context: Context) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val transcriptionService = prefs.getString("transcription_service", "Groq Whisper v3 Turbo") ?: "Groq Whisper v3 Turbo"
                val aiModel = prefs.getString("ai_model", "openai/gpt-oss-120b") ?: ""

                val urls = resolvePreWarmUrls(transcriptionService, aiModel)
                Log.d("NetworkManager", "Pre-warming ${urls.size} connections for service=$transcriptionService, model=$aiModel")

                coroutineScope {
                    urls.map { (url, isTranscription) ->
                        async(Dispatchers.IO) {
                            try {
                                val client = if (isTranscription) transcriptionHttpClient else aiProcessingHttpClient
                                val request = Request.Builder().url(url).head().build()
                                withTimeoutOrNull(1500) {
                                    client.newCall(request).execute().use { response ->
                                        Log.d("NetworkManager", "Pre-warmed: $url (${response.code})")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d("NetworkManager", "Pre-warming failed for $url: ${e.message}")
                            }
                        }
                    }.awaitAll()
                }

                Log.d("NetworkManager", "Connection pre-warming completed")
            } catch (e: Exception) {
                Log.e("NetworkManager", "Error during connection pre-warming", e)
            }
        }
    }

    /**
     * Map user-configured service names to the URLs that should be pre-warmed.
     * Returns pairs of (url, isTranscription).
     */
    private fun resolvePreWarmUrls(transcriptionService: String, aiModel: String): List<Pair<String, Boolean>> {
        val urls = mutableListOf<Pair<String, Boolean>>()

        when {
            transcriptionService.contains("Offline", ignoreCase = true) ||
                transcriptionService.contains("On-Device", ignoreCase = true) -> { /* no pre-warm needed */ }
            transcriptionService.contains("Groq", ignoreCase = true) ->
                urls.add(GroqProxyConfig.modelsEndpoint(serviceUsesProxy = GroqProxyConfig.isConfigured()) to true)
            transcriptionService.contains("OpenAI", ignoreCase = true) ->
                urls.add("https://api.openai.com/v1/models" to true)
            transcriptionService.contains("Gemini", ignoreCase = true) ->
                urls.add("https://generativelanguage.googleapis.com/v1beta/models" to true)
            transcriptionService.contains("Deepgram", ignoreCase = true) ->
                urls.add("https://api.deepgram.com/v1/projects" to true)
            transcriptionService.contains("ElevenLabs", ignoreCase = true) ->
                urls.add("https://api.elevenlabs.io/v1/models" to true)
            transcriptionService.contains("Mistral", ignoreCase = true) ->
                urls.add("https://api.mistral.ai/v1/models" to true)
            transcriptionService.contains("Soniox", ignoreCase = true) ->
                urls.add("https://api.soniox.com/v1" to true)
            else ->
                urls.add(GroqProxyConfig.modelsEndpoint(serviceUsesProxy = GroqProxyConfig.isConfigured()) to true)
        }

        when {
            aiModel.contains("groq", ignoreCase = true) ||
                aiModel == "openai/gpt-oss-120b" ||
                aiModel == "mistral-saba-24b" ||
                aiModel.startsWith("meta-llama/") ->
                urls.add(GroqProxyConfig.modelsEndpoint(serviceUsesProxy = GroqProxyConfig.isConfigured()) to false)
            aiModel.contains("openai", ignoreCase = true) ->
                urls.add("https://api.openai.com/v1/models" to false)
            aiModel.contains("anthropic", ignoreCase = true) || aiModel.contains("claude", ignoreCase = true) ->
                urls.add("https://api.anthropic.com/v1/messages" to false)
            aiModel.contains("gemini", ignoreCase = true) || aiModel.contains("google", ignoreCase = true) ->
                urls.add("https://generativelanguage.googleapis.com/v1beta/models" to false)
            aiModel.contains("mistral", ignoreCase = true) ->
                urls.add("https://api.mistral.ai/v1/models" to false)
            aiModel.contains("cerebras", ignoreCase = true) ->
                urls.add("https://api.cerebras.ai/v1/models" to false)
            else ->
                urls.add("https://openrouter.ai/api/v1/models" to false)
        }

        return urls.distinctBy { it.first }
    }
    
    /**
     * Simple in-memory cache for API responses
     */
    private val responseCache = ConcurrentHashMap<String, CachedResponse>()
    
    private data class CachedResponse(
        val response: String,
        val timestamp: Long,
        val ttlMs: Long = 300_000 // 5 minutes default TTL
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMs
    }
    
    /**
     * Get cached response if available and not expired
     */
    fun getCachedResponse(key: String): String? {
        val cached = responseCache[key]
        return if (cached != null && !cached.isExpired()) {
            Log.d("NetworkManager", "Cache hit for key: ${key.take(50)}...")
            cached.response
        } else {
            if (cached != null) {
                responseCache.remove(key) // Remove expired entry
                Log.d("NetworkManager", "Cache expired for key: ${key.take(50)}...")
            }
            null
        }
    }
    
    /**
     * Cache a response with optional TTL
     */
    fun cacheResponse(key: String, response: String, ttlMs: Long = 300_000) {
        responseCache[key] = CachedResponse(response, System.currentTimeMillis(), ttlMs)
        Log.d("NetworkManager", "Cached response for key: ${key.take(50)}... (TTL: ${ttlMs}ms)")
        
        // Simple cache cleanup - remove expired entries when cache gets large
        if (responseCache.size > 100) {
            cleanupExpiredCache()
        }
    }
    
    /**
     * Remove expired entries from cache
     */
    private fun cleanupExpiredCache() {
        val iterator = responseCache.iterator()
        var removedCount = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isExpired()) {
                iterator.remove()
                removedCount++
            }
        }
        if (removedCount > 0) {
            Log.d("NetworkManager", "Cleaned up $removedCount expired cache entries")
        }
    }
    
    /**
     * Clear all cached responses
     */
    fun clearCache() {
        val size = responseCache.size
        responseCache.clear()
        Log.d("NetworkManager", "Cleared $size cached responses")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        val total = responseCache.size
        val expired = responseCache.values.count { it.isExpired() }
        val valid = total - expired
        return "Cache: $valid valid, $expired expired, $total total entries"
    }
}
