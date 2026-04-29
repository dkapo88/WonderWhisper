package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

class SonioxStreamingClient(
    private val client: OkHttpClient,
    private val gson: Gson
) : WebSocketListener() {

    private var webSocket: WebSocket? = null
    private val _events = Channel<SonioxEvent>(Channel.BUFFERED)
    val events: Flow<SonioxEvent> = _events.receiveAsFlow()

    private var isConnected = false
    @Volatile private var isReady = false  // true after config is sent and early buffer flushed
    private val earlyAudioBuffer = ArrayDeque<ByteArray>()
    @Volatile private var earlyAudioBufferBytes = 0
    @Volatile private var eventsClosed = false

    private val url = "wss://stt-rt.soniox.com/transcribe-websocket"

    fun connect(apiKey: String, sampleRate: Int, languageHints: List<String> = emptyList(), terms: List<String> = emptyList()) {
        val request = Request.Builder()
            .url(url)
            .build()

        // Create a dedicated client for WebSocket
        // Important: Set readTimeout to 0 (no timeout) for WebSockets
        val wsClient = client.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        Log.d(TAG, "Connecting to Soniox V4 WebSocket: $url")
        webSocket = wsClient.newWebSocket(request, this)

        // V4: language_hints_strict forces the model to only consider hinted languages
        val hasExplicitLanguage = languageHints.isNotEmpty() &&
            languageHints.none { it.equals("auto", ignoreCase = true) }

        this.pendingConfig = SonioxConfig(
            api_key = apiKey,
            sample_rate = sampleRate,
            language_hints = if (hasExplicitLanguage) languageHints else emptyList(),
            language_hints_strict = hasExplicitLanguage,
            context = if (terms.isNotEmpty()) SonioxContext(terms) else null
        )
    }

    private var pendingConfig: SonioxConfig? = null

    fun sendAudio(samples: ShortArray) {
        if (webSocket == null) return

        val bytes = samplesToBytes(samples)

        if (!isReady) {
            bufferEarlyAudio(bytes)
            return
        }

        webSocket?.send(bytes.toByteString(0, bytes.size))
    }

    private fun bufferEarlyAudio(bytes: ByteArray) {
        synchronized(earlyAudioBuffer) {
            while (
                earlyAudioBuffer.size >= MAX_EARLY_AUDIO_BUFFER_CHUNKS ||
                earlyAudioBufferBytes + bytes.size > MAX_EARLY_AUDIO_BUFFER_BYTES
            ) {
                if (earlyAudioBuffer.isEmpty()) {
                    break
                }
                val dropped = earlyAudioBuffer.removeFirst()
                earlyAudioBufferBytes -= dropped.size
            }
            earlyAudioBuffer.addLast(bytes)
            earlyAudioBufferBytes += bytes.size
        }
    }

    private fun closeEvents() {
        if (!eventsClosed) {
            eventsClosed = true
            _events.close()
        }
    }

    private fun samplesToBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            bytes[i * 2] = (s and 0x00FF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0x00FF).toByte()
        }
        return bytes
    }

    fun stopStream() {
        if (!isConnected || webSocket == null) return

        try {
            // V4: Send finalize command to flush all pending tokens and get final results
            val finalizeMsg = gson.toJson(mapOf("type" to "finalize"))
            webSocket?.send(finalizeMsg)
            Log.d(TAG, "Sent finalize message")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending finalize message", e)
        }
    }

    fun close() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket", e)
        } finally {
            webSocket = null
            isConnected = false
            isReady = false
            pendingConfig = null
            synchronized(earlyAudioBuffer) {
                earlyAudioBuffer.clear()
                earlyAudioBufferBytes = 0
            }
            closeEvents()
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "WebSocket Connected")
        isConnected = true

        pendingConfig?.let { config ->
            val json = gson.toJson(config)
            webSocket.send(json)
            Log.d(TAG, "Sent config: $json")
            pendingConfig = null
        }

        // Flush any audio that arrived while the WebSocket was connecting
        synchronized(earlyAudioBuffer) {
            if (earlyAudioBuffer.isNotEmpty()) {
                Log.d(TAG, "Flushing ${earlyAudioBuffer.size} buffered audio chunks")
                for (bytes in earlyAudioBuffer) {
                    webSocket.send(bytes.toByteString(0, bytes.size))
                }
                earlyAudioBuffer.clear()
                earlyAudioBufferBytes = 0
            }
        }
        isReady = true

        _events.trySend(SonioxEvent.Connected)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val response = gson.fromJson(text, SonioxResponse::class.java)
            
            if (response.error_code != null) {
                _events.trySend(SonioxEvent.Error(response.error_message ?: "Unknown error ${response.error_code}"))
                close()
                return
            }

            if (response.finished == true) {
                _events.trySend(SonioxEvent.Finished)
                return
            }

            if (!response.tokens.isNullOrEmpty()) {
                // V4: Filter out the special <fin> marker token that signals segment boundaries
                val hasFin = response.tokens.any { it.text == "<fin>" }
                val filteredTokens = response.tokens.filter { it.text != "<fin>" }

                if (filteredTokens.isNotEmpty()) {
                    _events.trySend(SonioxEvent.Transcript(filteredTokens))
                }
                if (hasFin) {
                    _events.trySend(SonioxEvent.SegmentFinalized)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: $text", e)
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        // Soniox sends JSON text, not binary responses usually.
        onMessage(webSocket, bytes.utf8())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket Closing: $code $reason")
        isConnected = false
        isReady = false
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket Failure", t)
        isConnected = false
        isReady = false
        _events.trySend(SonioxEvent.Error(t.message ?: "Connection failure"))
        closeEvents()
    }

    companion object {
        private const val TAG = "SonioxClient"
        private const val MAX_EARLY_AUDIO_BUFFER_CHUNKS = 150
        private const val MAX_EARLY_AUDIO_BUFFER_BYTES = 512 * 1024
    }

    // Data classes matching Soniox API
    
    private data class SonioxConfig(
        val api_key: String,
        val model: String = "stt-rt-v4",
        val audio_format: String = "pcm_s16le",
        val sample_rate: Int,
        val num_channels: Int = 1,
        val enable_endpoint_detection: Boolean = false,
        val enable_speaker_diarization: Boolean = false,
        val enable_language_identification: Boolean = true,
        val language_hints: List<String> = emptyList(),
        val language_hints_strict: Boolean = false,
        val context: SonioxContext? = null
    )
    
    private data class SonioxContext(
        val terms: List<String>? = null
    )

    data class SonioxResponse(
        val tokens: List<Token>?,
        val finished: Boolean?,
        val error_code: Int?,
        val error_message: String?,
        val final_audio_proc_ms: Long?,
        val total_audio_proc_ms: Long?
    )

    data class Token(
        val text: String,
        val start_ms: Long?,
        val end_ms: Long?,
        val confidence: Double?,
        val is_final: Boolean,
        val speaker: String?,
        val language: String?
    )
}

sealed class SonioxEvent {
    object Connected : SonioxEvent()
    data class Transcript(val tokens: List<SonioxStreamingClient.Token>) : SonioxEvent()
    /** V4: Emitted when a `<fin>` marker is received, signalling a segment boundary. */
    object SegmentFinalized : SonioxEvent()
    object Finished : SonioxEvent()
    data class Error(val message: String) : SonioxEvent()
}
