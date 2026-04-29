package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.content.Context
import android.util.Log
import com.konovalov.vad.silero.config.Mode
import com.slumdog88.dictationkeyboardai.SecureApiKeyManager
import com.slumdog88.dictationkeyboardai.network.GroqProxyConfig
import com.slumdog88.dictationkeyboardai.ai.AIProcessingManager
import com.slumdog88.dictationkeyboardai.network.NetworkManager
import com.google.gson.Gson
import com.slumdog88.dictationkeyboardai.transcription.TranscriptionServiceManager
import com.slumdog88.dictationkeyboardai.utils.AudioFileManager
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates the lifecycle of the upcoming pseudo-streaming dictation mode.
 *
 * The session owns audio capture, chunk transcription, and conversational LLM orchestration.
 * For now the implementation is a scaffolding layer that will be filled in over several stages.
 * Legacy one-shot transcription is used as a fallback until all streaming building blocks land.
 */
class StreamingDictationSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val secureApiKeyManager: SecureApiKeyManager,
    private val transcriptionServiceManager: TranscriptionServiceManager,
    private val networkManager: NetworkManager,
    private val aiProcessingManager: AIProcessingManager,
    private val contextProvider: () -> ConversationContext
) {

    private val gson = Gson()
    private var sonioxClient: SonioxStreamingClient? = null
    private var sonioxCompletionDeferred: CompletableDeferred<Unit>? = null
    private var sonioxEventsJob: Job? = null

    private var lastSonioxError: String? = null

    private val _uiState = MutableStateFlow(StreamingUiState.idle())
    val uiState: StateFlow<StreamingUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null
    private var isActive = false
    private var isStopping = false
    private val rawTranscriptBuilder = StringBuilder()
    private var latestFormattedTranscript: String = ""
    private val audioFileManager = AudioFileManager(context)
    private var audioPipeline: AudioCapturePipeline? = null
    private var captureChannel: ReceiveChannel<AudioFrame>? = null
    private var chunkChannel: Channel<QueuedChunk>? = null
    private var chunkJob: Job? = null
    private var nextChunkNumber: Int = 0
    private var conversationSession: StreamingConversationSession? = null
    private var aiFormattingEnabled: Boolean = false
    private var lastSubmittedForAi: String? = null
    private val commandWords: List<String> = settingsManager.getCommandWords()
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
    private val punctuationTrimRegex = Regex("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$")
    private val whitespaceRegex = "\\s+".toRegex()
    
    // Amplitude smoothing for consistent waveform visualization
    private val amplitudeHistory = mutableListOf<Int>()
    private var lastBroadcastTime = 0L

    fun start(serviceOverride: String? = null): StreamingStartResult {
        if (isActive || isStopping) {
            Log.w(TAG, "start() called while session already active or stopping")
            return StreamingStartResult.Started
        }

        if (!prerequisitesMet(serviceOverride)) {
            Log.d(TAG, "Streaming prerequisites not met; deferring to legacy transcription")
            return StreamingStartResult.Unsupported
        }

        if (!ENABLE_STREAMING_RUNTIME) {
            Log.d(TAG, "Streaming runtime disabled (scaffolding phase); falling back")
            return StreamingStartResult.Unsupported
        }

        Log.d(TAG, "Starting streaming dictation session scaffolding")

        val config = AudioCaptureConfig()
        val pipeline = AudioCapturePipeline(config, scope)
        val frames = try {
            pipeline.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            return StreamingStartResult.Failure(e.message ?: "Audio capture failed")
        }

        isActive = true
        lastSonioxError = null
        rawTranscriptBuilder.clear()
        latestFormattedTranscript = ""
        aiFormattingEnabled = settingsManager.getSettings().getBoolean("enable_postprocess", false)
        lastSubmittedForAi = null
        nextChunkNumber = 0
        _uiState.value = StreamingUiState.idle().copy(
            isActive = true,
            isRecording = true,
            statusMessage = "Listening…",
            controlsEnabled = true,
            stage = StreamingStage.LISTENING,
            currentChunk = 0
        )

        val effectiveService = serviceOverride ?: settingsManager.getTranscriptionService()

        if (effectiveService == "Soniox Real-Time") {
            val apiKey = secureApiKeyManager.getApiKey("soniox_api_key") ?: ""
            val client = SonioxStreamingClient(networkManager.transcriptionHttpClient, gson)
            sonioxClient = client
            sonioxCompletionDeferred = CompletableDeferred()
            
            sonioxEventsJob?.cancel()
            sonioxEventsJob = scope.launch {
                client.events.collect { event ->
                    handleSonioxEvent(event)
                }
            }
            
            // Get vocabulary for better accuracy
            val languageConfig = settingsManager.getCustomLanguageConfig()
            val terms = languageConfig.vocabularyItems.filter { it.isNotBlank() }
            val sonioxLanguage = settingsManager.getSonioxLanguage()
            
            client.connect(apiKey, config.sampleRate, listOf(sonioxLanguage), terms)
        }

        audioPipeline = pipeline
        captureChannel = frames
        val chunkQueue = Channel<QueuedChunk>(capacity = Channel.BUFFERED)
        chunkChannel = chunkQueue
        conversationSession = if (aiFormattingEnabled) {
            StreamingConversationSession(
                scope = scope,
                aiProcessingManager = aiProcessingManager,
                settingsManager = settingsManager
            ).also { it.reset() }
        } else null
        chunkJob = scope.launch {
            try {
                processChunks(chunkQueue, serviceOverride)
            } catch (cancel: CancellationException) {
                Log.d(TAG, "Chunk processor cancelled")
            } catch (t: Throwable) {
                Log.e(TAG, "Chunk processor failed", t)
                updateUiState {
                    it.copy(
                        statusMessage = "Streaming error",
                        errorMessage = t.message ?: "Streaming error",
                        controlsEnabled = false
                    )
                }
            }
        }

        sessionJob = scope.launch {
            try {
                processFrames(frames, pipeline, config, chunkQueue, serviceOverride)
            } catch (cancel: CancellationException) {
                Log.d(TAG, "Streaming loop cancelled")
            } catch (t: Throwable) {
                Log.e(TAG, "Streaming loop failed", t)
                _uiState.value = StreamingUiState.idle()
            }
        }

        return StreamingStartResult.Started
    }

    suspend fun stop(reason: StopReason): StreamingResult {
        if (!isActive && !isStopping) {
            Log.d(TAG, "stop() invoked without active session (reason=$reason)")
            return StreamingResult.Canceled
        }

        Log.d(TAG, "Stopping streaming session (reason=$reason)")
        
        isStopping = true
        
        // For Soniox, we want to wait for the "tail" of the transcription to arrive
        // instead of cutting it off immediately.
        // But only if it's a normal completion (user pressed stop), not a cancellation.
        val wasSonioxSession = sonioxClient != null
        
        // Hold a local reference so we can close it properly after capturing the transcript
        val soniox = sonioxClient

        if (wasSonioxSession && reason == StopReason.COMPLETED) {
            try {
                Log.d(TAG, "Sending finalize to Soniox and waiting for last tokens...")
                soniox?.stopStream()

                // Wait for the <fin> segment marker or Finished event — whichever comes first.
                // This normally completes in < 1s; the timeout is just a safety net.
                withTimeoutOrNull(5_000) {
                    sonioxCompletionDeferred?.await()
                }
                Log.d(TAG, "Soniox finalization complete or timed out")
            } catch (e: Exception) {
                Log.w(TAG, "Error waiting for Soniox finalization", e)
            }
        }

        // Capture state AFTER waiting for finalization to ensure we got the tail.
        val capturedSonioxTranscript = if (wasSonioxSession) _uiState.value.formattedTranscript else null

        // Close the WebSocket cleanly now that we have the transcript
        try { soniox?.close() } catch (_: Exception) {}

        val pipeline = audioPipeline
        audioPipeline = null
        val framesJob = sessionJob
        sessionJob = null
        val chunksJob = chunkJob
        chunkJob = null
        chunkChannel = null
        val conversation = conversationSession
        conversationSession = null
        captureChannel = null
        val sonioxCollectorJob = sonioxEventsJob
        sonioxEventsJob = null

        sonioxClient = null

        var finalResult: StreamingResult = StreamingResult.Canceled

        try {
            pipeline?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Audio pipeline stop failed", e)
                }
            }

            framesJob?.let {
                try {
                    it.join()
                } catch (cancel: CancellationException) {
                    Log.d(TAG, "Session job cancelled during stop")
                } catch (t: Throwable) {
                    Log.w(TAG, "Session job join failed", t)
                }
            }

            chunksJob?.let {
                try {
                    it.join()
                } catch (cancel: CancellationException) {
                    Log.d(TAG, "Chunk job cancelled during stop")
                } catch (t: Throwable) {
                    Log.w(TAG, "Chunk job join failed", t)
                }
            }

            try {
                conversation?.awaitIdle()
            } catch (cancel: CancellationException) {
                Log.d(TAG, "Conversation session cancelled during stop")
            } catch (t: Throwable) {
                Log.w(TAG, "Conversation session await failed", t)
            } finally {
                conversation?.close()
            }

            sonioxCollectorJob?.let {
                try {
                    it.cancelAndJoin()
                } catch (cancel: CancellationException) {
                    Log.d(TAG, "Soniox collector cancelled during stop")
                } catch (t: Throwable) {
                    Log.w(TAG, "Soniox collector join failed", t)
                }
            }

            finalResult = when (reason) {
                StopReason.CANCELED -> StreamingResult.Canceled
                StopReason.COMPLETED -> {
                    // For Soniox, we merged everything into formattedTranscript to avoid visual jumping.
                    // So the UI state's formattedTranscript holds (Committed + Preview).
                    // We use the value captured at the start of stop() to ensure zero latency.
                    val combined = if (wasSonioxSession) {
                        (capturedSonioxTranscript ?: "").trim()
                    } else {
                        val committed = when {
                            latestFormattedTranscript.isNotBlank() -> latestFormattedTranscript
                            rawTranscriptBuilder.isNotEmpty() -> rawTranscriptBuilder.toString()
                            else -> ""
                        }
                        (committed + _uiState.value.livePreview).trim()
                    }

                    if (combined.isNotEmpty()) {
                        StreamingResult.Completed(combined)
                    } else {
                        if (lastSonioxError != null) {
                            StreamingResult.Failed(lastSonioxError)
                        } else {
                            StreamingResult.Unsupported
                        }
                    }
                }
                StopReason.ERROR -> StreamingResult.Failed("Streaming session ended due to error")
            }
        } finally {
            lastSubmittedForAi = null
            isActive = false
            isStopping = false
            _uiState.value = StreamingUiState.idle()
        }

        return finalResult
    }

    private fun prerequisitesMet(serviceOverride: String?): Boolean {
        val transcriptionService = serviceOverride ?: settingsManager.getTranscriptionService()
        
        if (transcriptionService == "Soniox Real-Time") {
            val hasSonioxKey = secureApiKeyManager.getApiKey("soniox_api_key")?.isNotBlank() == true
            if (!hasSonioxKey) {
                Log.d(TAG, "Streaming skipped: no Soniox API key configured")
            }
            return hasSonioxKey
        }

        val hasGroqKey = secureApiKeyManager.getApiKey("groq_api_key")
            ?.takeIf { it.isNotBlank() } != null ||
            GroqProxyConfig.isConfigured()
        val usingGroqService = transcriptionService.contains("Groq", ignoreCase = true)

        if (!usingGroqService) {
            Log.d(TAG, "Streaming skipped: transcription service '$transcriptionService' is not Groq")
        }

        if (!hasGroqKey) {
            Log.d(TAG, "Streaming skipped: no Groq API key configured")
        }

        return usingGroqService && hasGroqKey
    }

    private suspend fun processFrames(
        frames: ReceiveChannel<AudioFrame>,
        pipeline: AudioCapturePipeline,
        config: AudioCaptureConfig,
        chunkQueue: Channel<QueuedChunk>,
        serviceOverride: String?
    ) {
        val effectiveService = serviceOverride ?: settingsManager.getTranscriptionService()
        if (effectiveService == "Soniox Real-Time") {
            try {
                processFramesSoniox(frames, pipeline)
            } finally {
                chunkQueue.close()
            }
            return
        }

        val modeString = settingsManager.getVadMode()
        val vadMode = when (modeString.lowercase()) {
            "aggressive" -> Mode.AGGRESSIVE
            "very_aggressive" -> Mode.VERY_AGGRESSIVE
            else -> Mode.NORMAL
        }

        val vadConfig = VadConfig(
            sampleRate = config.sampleRate,
            amplitudeThreshold = settingsManager.getVadAmplitudeThreshold(),
            minSpeechDurationMs = settingsManager.getVadMinSpeechDuration(),
            hangoverDurationMs = settingsManager.getVadHangoverDuration(),
            mode = vadMode
        )
        val vad = VoiceActivityDetector(context, vadConfig)
        val accumulator = ChunkAccumulator(audioFileManager, config.sampleRate)

        try {
            for (frame in frames) {
                if (!isActive) break

                val events = vad.processFrame(frame)
                if (events.any { it is VadEvent.SpeechStarted }) {
                    Log.d(TAG, "VAD speech started @${frame.timestampMs}ms (frame=${frame.durationMs}ms)")
                    val upcomingChunk = nextChunkNumber + 1
                    updateUiState {
                        it.copy(
                            statusMessage = "Capturing chunk…",
                            stage = StreamingStage.CAPTURING,
                            currentChunk = upcomingChunk
                        )
                    }
                }

                val chunkResult = accumulator.onFrame(frame, events)
                if (chunkResult != null) {
                    emitChunk(chunkResult, chunkQueue, isFlushed = false)
                } else if (events.any { it is VadEvent.SpeechEnded }) {
                    Log.d(TAG, "VAD speech ended @${frame.timestampMs}ms without chunk output (likely silence)")
                    updateUiState {
                        it.copy(
                            statusMessage = "Listening…",
                            livePreview = "",
                            stage = StreamingStage.LISTENING,
                            currentChunk = 0
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            if (t !is CancellationException) {
                Log.e(TAG, "Error while processing frames", t)
                updateUiState {
                    it.copy(
                        statusMessage = "Streaming error",
                        errorMessage = t.message ?: "Streaming error",
                        controlsEnabled = false,
                        stage = StreamingStage.ERROR
                    )
                }
            }
            throw t
        } finally {
            // Flush any chunk still buffered so the last spoken words are transcribed.
            val pendingChunk = accumulator.flushPendingChunk()
            if (pendingChunk != null) {
                emitChunk(pendingChunk, chunkQueue, isFlushed = true)
            }
            accumulator.reset()
            vad.reset()
            vad.close()
            try {
                pipeline.stop()
            } catch (_: Exception) {
            }
            chunkQueue.close()
        }
    }

    private fun emitChunk(chunkResult: ChunkResult, chunkQueue: Channel<QueuedChunk>, isFlushed: Boolean) {
        nextChunkNumber += 1
        val chunkNumber = nextChunkNumber
        val verb = if (isFlushed) "flushed" else "captured"
        Log.d(
            TAG,
            "Chunk $chunkNumber $verb: duration=${chunkResult.durationMs}ms frames=${chunkResult.frameCount} file=${chunkResult.file.name}"
        )
        val statusMessage = if (isFlushed) {
            "Finalizing chunk $chunkNumber – buffering…"
        } else {
            "Captured chunk $chunkNumber – buffering…"
        }
        updateUiState {
            it.copy(
                statusMessage = statusMessage,
                livePreview = "",
                controlsEnabled = true,
                stage = StreamingStage.TRANSCRIBING,
                currentChunk = chunkNumber
            )
        }

        if (!chunkQueue.trySend(QueuedChunk(chunkNumber, chunkResult)).isSuccess) {
            Log.w(TAG, "Failed to enqueue chunk $chunkNumber, discarding")
            if (!chunkResult.file.delete()) {
                Log.w(TAG, "Failed to delete temp chunk file ${chunkResult.file.absolutePath}")
            }
        }
    }

    private suspend fun processChunks(queue: ReceiveChannel<QueuedChunk>, serviceOverride: String?) {
        for (chunk in queue) {
            if (!isActive) break

            updateUiState {
                it.copy(
                    statusMessage = "Transcribing chunk ${chunk.number}…",
                    livePreview = "",
                    stage = StreamingStage.TRANSCRIBING,
                    currentChunk = chunk.number
                )
            }

            val transcription = try {
                val (text, _) = transcriptionServiceManager.performTranscriptionWithMetrics(
                    chunk.result.file,
                    serviceOverride = serviceOverride
                )
                Log.d(TAG, "Chunk ${chunk.number} transcription raw='${text.take(120)}'")
                text.trim()
            } catch (t: Throwable) {
                Log.e(TAG, "Transcription failed for chunk ${chunk.number}", t)
                null
            } finally {
                if (!chunk.result.file.delete()) {
                    Log.w(TAG, "Failed to delete chunk file ${chunk.result.file.absolutePath}")
                }
            }

            if (!isActive) break

            if (!transcription.isNullOrBlank() && !transcription.startsWith("error", ignoreCase = true)) {
                val detection = detectCommandChunk(transcription)
                val chunkBody = detection.body
                val displayChunk = if (detection.isCommand) {
                    if (chunkBody.isNotBlank()) "[Command] $chunkBody" else "[Command] ${detection.original}"
                } else chunkBody
                val isCommandChunk = aiFormattingEnabled && detection.isCommand

                if (!isCommandChunk) {
                    appendToRawTranscript(chunkBody)
                }

                if (!aiFormattingEnabled) {
                    latestFormattedTranscript = rawTranscriptBuilder.toString()
                    updateUiState {
                        it.copy(
                            statusMessage = "Streaming…",
                            formattedTranscript = latestFormattedTranscript,
                            livePreview = displayChunk,
                            controlsEnabled = true,
                            errorMessage = null,
                            stage = StreamingStage.STREAMING,
                            currentChunk = chunk.number
                        )
                    }
                } else if (isCommandChunk) {
                    val rawText = rawTranscriptBuilder.toString()
                    lastSubmittedForAi = rawText
                        val context = contextProvider()
                        val screenCtx = if (settingsManager.isScreenContextEnabled()) context.screenContext else ""
                        updateUiState {
                            it.copy(
                                statusMessage = "Command detected – formatting…",
                                formattedTranscript = latestFormattedTranscript,
                                livePreview = "",
                                controlsEnabled = true,
                                stage = StreamingStage.FORMATTING,
                                currentChunk = chunk.number
                            )
                        }
                    val payload = ConversationPayload(
                    transcript = rawText,
                    selectedText = context.selectedText,
                    screenContext = screenCtx,
                    appContext = context.appContext,
                    isCommandMode = true,
                    rawChunk = chunkBody
                    )
                    conversationSession?.submit(payload, { rawTranscriptBuilder.toString() }) { formatted, consumedTranscript ->
                    if (!isActive) return@submit
                    if (!formatted.isNullOrBlank()) {
                        // Calculate pending suffix to restore (text spoken while command was processing)
                        val currentRaw = rawTranscriptBuilder.toString()
                        val pendingSuffix = if (currentRaw.startsWith(consumedTranscript)) {
                            currentRaw.substring(consumedTranscript.length)
                        } else {
                            ""
                        }
                        
                        // Reconstruct state: The new base is the formatted command result,
                        // plus any new raw text that arrived in the meantime.
                        val newRaw = formatted + pendingSuffix
                        setRawTranscript(newRaw)
                        
                        latestFormattedTranscript = formatted
                        lastSubmittedForAi = newRaw // Prevent re-triggering immediately unless new text arrives
                        
                        updateUiState {
                            it.copy(
                                statusMessage = "Streaming…",
                                formattedTranscript = formatted,
                                livePreview = pendingSuffix,
                                controlsEnabled = true,
                                errorMessage = null,
                                stage = StreamingStage.STREAMING,
                                currentChunk = chunk.number
                            )
                        }
                    } else {
                        lastSubmittedForAi = null
                        updateUiState {
                            it.copy(
                                statusMessage = "Streaming…",
                                formattedTranscript = rawText,
                                errorMessage = "AI formatting unavailable",
                                controlsEnabled = true,
                                stage = StreamingStage.ERROR,
                                livePreview = "",
                                currentChunk = chunk.number
                            )
                        }
                    }
                    }
                    } else {
                    val rawText = rawTranscriptBuilder.toString()
                    if (lastSubmittedForAi == rawText) {
                    updateUiState {
                        it.copy(
                            statusMessage = "Streaming…",
                            formattedTranscript = latestFormattedTranscript.ifBlank { rawText },
                            livePreview = "", // We could calculate pending here too, but usually irrelevant if we didn't submit
                            controlsEnabled = true,
                            errorMessage = null,
                            stage = StreamingStage.STREAMING,
                            currentChunk = chunk.number
                        )
                    }
                    } else {
                    lastSubmittedForAi = rawText
                    val context = contextProvider()
                    val screenCtx = if (settingsManager.isScreenContextEnabled()) context.screenContext else ""
                    updateUiState {
                        it.copy(
                            statusMessage = "Formatting with AI…",
                            formattedTranscript = latestFormattedTranscript,
                            livePreview = "",
                            controlsEnabled = true,
                            stage = StreamingStage.FORMATTING,
                            currentChunk = chunk.number
                        )
                    }
                    val payload = ConversationPayload(
                        transcript = rawText,
                        selectedText = context.selectedText,
                        screenContext = screenCtx,
                        appContext = context.appContext,
                        isCommandMode = false,
                        rawChunk = chunkBody
                    )
                    conversationSession?.submit(payload, { rawTranscriptBuilder.toString() }) { formatted, consumedTranscript ->
                        if (!isActive) return@submit
                        if (!formatted.isNullOrBlank()) {
                            // Calculate pending suffix (text spoken while formatting was processing)
                            val currentRaw = rawTranscriptBuilder.toString()
                            val pendingSuffix = if (currentRaw.startsWith(consumedTranscript)) {
                                currentRaw.substring(consumedTranscript.length)
                            } else {
                                ""
                            }
                            
                            // Update source of truth with the polished version + pending raw text
                            // This ensures "Format Persistence" (e.g. Email style) is preserved in the history
                            val newRaw = formatted + pendingSuffix
                            setRawTranscript(newRaw)
                            lastSubmittedForAi = newRaw

                            latestFormattedTranscript = formatted
                            updateUiState {
                                it.copy(
                                    statusMessage = "Streaming…",
                                    formattedTranscript = formatted,
                                    livePreview = pendingSuffix,
                                    controlsEnabled = true,
                                    errorMessage = null,
                                    stage = StreamingStage.STREAMING,
                                    currentChunk = chunk.number
                                )
                            }
                        } else {
                            lastSubmittedForAi = null
                            updateUiState {
                                it.copy(
                                    statusMessage = "Streaming…",
                                    formattedTranscript = rawText,
                                    errorMessage = "AI formatting unavailable",
                                    controlsEnabled = true,
                                    stage = StreamingStage.ERROR,
                                    livePreview = "",
                                    currentChunk = chunk.number
                                )
                            }
                        }
                    }
                    }
                    }
            } else {
                updateUiState {
                    it.copy(
                        statusMessage = "Chunk ${chunk.number} failed",
                        errorMessage = transcription ?: "Chunk transcription failed",
                        controlsEnabled = true,
                        stage = StreamingStage.ERROR,
                        currentChunk = chunk.number
                    )
                }
            }
        }
    }

    private fun updateUiState(transform: (StreamingUiState) -> StreamingUiState) {
        val current = _uiState.value
        _uiState.value = transform(current)
    }

    private fun appendToRawTranscript(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (rawTranscriptBuilder.isNotEmpty()) {
            val lastChar = rawTranscriptBuilder.last()
            if (!lastChar.isWhitespace()) {
                rawTranscriptBuilder.append(' ')
            }
        }
        rawTranscriptBuilder.append(trimmed)
    }

    private fun setRawTranscript(text: String) {
        rawTranscriptBuilder.setLength(0)
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            rawTranscriptBuilder.append(trimmed)
        }
    }

    companion object {
        private const val TAG = "StreamingSession"
        private const val ENABLE_STREAMING_RUNTIME = true
    }

    private data class QueuedChunk(
        val number: Int,
        val result: ChunkResult
    )

    data class ConversationContext(
        val selectedText: String,
        val screenContext: String,
        val appContext: String
    )

    private data class CommandDetection(
        val isCommand: Boolean,
        val body: String,
        val commandWord: String?,
        val original: String
    )

    private fun detectCommandChunk(text: String): CommandDetection {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return CommandDetection(false, "", null, trimmed)
        val parts = trimmed.split(whitespaceRegex, limit = 2)
        val firstRaw = parts.firstOrNull() ?: ""
        val normalized = firstRaw.lowercase().replace(punctuationTrimRegex, "")
        if (normalized.isNotEmpty() && commandWords.contains(normalized)) {
            val body = if (parts.size > 1) parts[1].trim() else ""
            return CommandDetection(true, body, normalized, trimmed)
        }
        val isImplicit = StreamingCommandExecutor.isRecognizedCommand(trimmed)
        return if (isImplicit) {
            CommandDetection(true, trimmed, null, trimmed)
        } else {
            CommandDetection(false, trimmed, null, trimmed)
        }
    }
    private suspend fun processFramesSoniox(
        frames: ReceiveChannel<AudioFrame>,
        pipeline: AudioCapturePipeline
    ) {
        try {
            for (frame in frames) {
                if (!isActive) break
                sonioxClient?.sendAudio(frame.samples)
                
                // Calculate and broadcast amplitude from audio samples for waveform visualization
                // Throttle updates to ~50ms to match MediaRecorder polling interval (smoother animation)
                val now = System.currentTimeMillis()
                if (now - lastBroadcastTime >= 50) {
                    val amplitude = calculateAmplitude(frame.samples)
                    // Average with previous frame for smoothing
                    val smoothedAmplitude = if (amplitudeHistory.isNotEmpty()) {
                        (amplitude + amplitudeHistory.last()) / 2
                    } else {
                        amplitude
                    }
                    amplitudeHistory.add(amplitude)
                    if (amplitudeHistory.size > 2) {
                        amplitudeHistory.removeAt(0)
                    }
                    updateUiState { it.copy(amplitude = smoothedAmplitude) }
                    lastBroadcastTime = now
                }
            }
            
            // Frames loop ended (recording stopped)
            // We exit immediately to allow instant pasting.
            // Cleanup happens in finally block.
        } catch (t: Throwable) {
            if (t !is CancellationException) {
                Log.e(TAG, "Error in Soniox frame loop", t)
                updateUiState {
                    it.copy(
                        statusMessage = "Streaming error",
                        errorMessage = t.message ?: "Streaming error",
                        controlsEnabled = false,
                        stage = StreamingStage.ERROR
                    )
                }
            }
            throw t
        } finally {
            try {
                pipeline.stop()
            } catch (_: Exception) {
            }
            sonioxClient?.close()
            amplitudeHistory.clear()
        }
    }

    private fun handleSonioxEvent(event: SonioxEvent) {
        if (!isActive) return
        
        when (event) {
            is SonioxEvent.Connected -> {
                updateUiState { it.copy(statusMessage = "Listening...", stage = StreamingStage.LISTENING) }
            }
            is SonioxEvent.Transcript -> {
                val finalTokens = event.tokens.filter { it.is_final }
                val nonFinalTokens = event.tokens.filter { !it.is_final }
                
                if (finalTokens.isNotEmpty()) {
                    val finalText = finalTokens.joinToString("") { it.text }
                    // Directly append text without extra spacing logic to preserve Soniox's natural tokenization
                    rawTranscriptBuilder.append(finalText)
                    latestFormattedTranscript = rawTranscriptBuilder.toString()
                }
                
                val nonFinalText = nonFinalTokens.joinToString("") { it.text }
                
                updateUiState {
                    it.copy(
                        statusMessage = "Streaming...",
                        // Merge finalized and non-final text into one field to prevent visual "rewriting" jumps
                        // and to satisfy the user's request to "focus on preview text".
                        formattedTranscript = latestFormattedTranscript + nonFinalText,
                        livePreview = "",
                        stage = StreamingStage.STREAMING
                    )
                }
            }
            is SonioxEvent.SegmentFinalized -> {
                Log.d(TAG, "Soniox V4 segment finalized, committed so far: ${rawTranscriptBuilder.length} chars")
                // When we're stopping, the <fin> after our finalize message means all
                // remaining tokens have been delivered — unblock stop() immediately.
                if (isStopping) {
                    sonioxCompletionDeferred?.complete(Unit)
                }
            }
            is SonioxEvent.Finished -> {
                Log.d(TAG, "Soniox stream finished")
                sonioxCompletionDeferred?.complete(Unit)
            }
            is SonioxEvent.Error -> {
                Log.e(TAG, "Soniox error: ${event.message}")
                lastSonioxError = event.message
                sonioxCompletionDeferred?.complete(Unit)
                updateUiState {
                    it.copy(
                        statusMessage = "Error",
                        errorMessage = event.message,
                        stage = StreamingStage.ERROR
                    )
                }
            }
        }
    }

    /**
     * Calculate peak amplitude from audio samples to match MediaRecorder.getMaxAmplitude() behavior.
     * Uses absolute maximum value across samples to detect peaks similar to how MediaRecorder works.
     */
    private fun calculateAmplitude(samples: ShortArray): Int {
        if (samples.isEmpty()) return 0
        
        // Find peak (maximum absolute value) like MediaRecorder does
        var maxAbs = 0
        for (sample in samples) {
            val abs = kotlin.math.abs(sample.toInt())
            if (abs > maxAbs) {
                maxAbs = abs
            }
        }
        
        // Apply gain boost for more visible waveform (1.5x multiplier)
        val boosted = (maxAbs * 1.5).toInt()
        
        // Scale to 16-bit range (MediaRecorder returns values 0-32767)
        return boosted.coerceIn(0, 32767)
    }
}

sealed class StreamingStartResult {
    object Started : StreamingStartResult()
    object Unsupported : StreamingStartResult()
    data class Failure(val message: String? = null) : StreamingStartResult()
}

enum class StopReason {
    COMPLETED,
    CANCELED,
    ERROR
}

sealed class StreamingResult {
    data class Completed(val text: String) : StreamingResult()
    object Canceled : StreamingResult()
    object Unsupported : StreamingResult()
    data class Failed(val message: String? = null) : StreamingResult()
}
