package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.util.Log
import com.slumdog88.dictationkeyboardai.ai.AIProcessingManager
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.selects.select

class StreamingConversationSession(
    private val scope: CoroutineScope,
    private val aiProcessingManager: AIProcessingManager,
    private val settingsManager: SettingsManager
) {

    // Represents one queued LLM request (command or format)
    private data class PendingRequest(
        val id: Long,
        val payload: ConversationPayload,
        val transcriptProvider: () -> String,
        val onResult: (String?, String) -> Unit
    )

    private val commandChannel = Channel<PendingRequest>(Channel.BUFFERED)
    private val formatSignal = Channel<Unit>(Channel.CONFLATED)
    private val latestFormattingRequest = AtomicReference<PendingRequest?>(null)
    private val nextRequestId = AtomicLong(1L)

    // The currently running LLM job (if any), used for awaitIdle/cancel semantics.
    private var currentProcessingJob: Job? = null
    private var actorJob: Job? = null
    @Volatile private var isClosed = false

    private var latestFormatted: String = ""
    private val history: MutableList<ChatMessage> = mutableListOf()

    init {
        // Single actor loop: processes all requests strictly sequentially.
        actorJob = scope.launch {
            while (true) {
                val request = nextRequest() ?: break
                handleRequestSequentially(request)
            }
        }
    }

    private suspend fun nextRequest(): PendingRequest? {
        while (true) {
            commandChannel.tryReceive().getOrNull()?.let { return it }
            latestFormattingRequest.getAndSet(null)?.let { return it }
            if (isClosed) {
                return null
            }

            val request = select<PendingRequest?> {
                commandChannel.onReceiveCatching { result ->
                    result.getOrNull()
                }
                formatSignal.onReceiveCatching {
                    latestFormattingRequest.getAndSet(null)
                }
            }

            if (request != null) {
                return request
            }
        }
    }

    /**
     * Public entrypoint: enqueue a new request.
     *
     * - Commands are always queued and executed in order.
     * - Formatting requests are also queued sequentially (no skipping).
     */
    fun submit(
        payload: ConversationPayload,
        transcriptProvider: () -> String,
        onResult: (String?, String) -> Unit
    ) {
        val id = nextRequestId.getAndIncrement()

        val request = PendingRequest(
            id = id,
            payload = payload,
            transcriptProvider = transcriptProvider,
            onResult = onResult
        )

        if (isClosed) {
            return
        }

        if (payload.isCommandMode) {
            if (!commandChannel.trySend(request).isSuccess) {
                scope.launch {
                    commandChannel.send(request)
                }
            }
        } else {
            latestFormattingRequest.set(request)
            formatSignal.trySend(Unit)
        }
    }

    /**
     * Runs inside the actor loop; guarantees strict sequential processing.
     */
    private suspend fun handleRequestSequentially(request: PendingRequest) {
        // Snapshot the *current* transcript at processing time, not enqueue time.
        val freshTranscript = request.transcriptProvider()
        val effectivePayload = request.payload.copy(transcript = freshTranscript)
        val isCommand = effectivePayload.isCommandMode

        // Run the LLM work in a child job and wait for it.
        val job = scope.launch {
            try {
                val commandWords = settingsManager.getCommandWords()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val customInstructions = settingsManager.getStreamingCustomInstructions()
                val systemPrompt = StreamingPromptBuilder.buildSystemPrompt(commandWords, customInstructions)
                val languageConfig = settingsManager.getCustomLanguageConfig()
                val vocabulary = buildList {
                    addAll(languageConfig.vocabularyItems)
                    languageConfig.spellingPairs.forEach { pair ->
                        add(pair.first)
                        add(pair.second)
                    }
                }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                val userMessage = StreamingPromptBuilder.buildConversationUserMessage(
                    request.payload,
                    vocabulary
                )
                val userEntry = ChatMessage(role = "user", content = userMessage)

                // STATELESS MODE:
                // We do not accumulate history to prevent "format regression"
                // (old instructions confusing the model). The document state
                // <TRANSCRIPT_SNIPPET> is the single source of truth.
                val statelessHistory = listOf(userEntry.role to userEntry.content)

                val completion = aiProcessingManager.processStreamingConversation(
                    systemMessage = systemPrompt,
                    history = statelessHistory,
                    overrideModel = settingsManager.getStreamingAiModel().takeIf { it.isNotBlank() }
                )

                if (completion == null || completion.assistantContent.isNullOrBlank()) {
                    request.onResult(null, freshTranscript)
                    return@launch
                }

                val formatted = completion.formattedText
                if (!formatted.isNullOrBlank()) {
                    latestFormatted = formatted
                }

                // Deliver result to caller
                request.onResult(formatted?.takeIf { it.isNotBlank() }, freshTranscript)

                // If this was a command that completed, record its completion time.
                // (No longer tracking lastCommandCompletionTime as stale logic is removed)
            } catch (c: CancellationException) {
                // Propagate cancellation so the coroutine ends quickly
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "AI formatting failed", t)
                request.onResult(null, freshTranscript)
            }
        }

        currentProcessingJob = job
        try {
            job.join()
        } finally {
            if (currentProcessingJob === job) {
                currentProcessingJob = null
            }
        }
    }

    fun reset() {
        cancel()
        latestFormatted = ""
        history.clear()
    }

    fun cancel() {
        // Cancel the in-flight LLM call (if any)
        currentProcessingJob?.cancel()
        currentProcessingJob = null

        latestFormattingRequest.set(null)

        // Best-effort drain of any queued-but-unprocessed work
        while (true) {
            commandChannel.tryReceive().getOrNull() ?: break
        }
        while (true) {
            formatSignal.tryReceive().getOrNull() ?: break
        }

        // Keep the actor alive so future submit() calls still work
    }

    suspend fun close() {
        if (isClosed) return
        isClosed = true
        cancel()
        commandChannel.close()
        formatSignal.close()
        actorJob?.cancelAndJoin()
        actorJob = null
    }

    suspend fun awaitIdle() {
        // Same semantics as before: wait for the in-flight job, if any.
        currentProcessingJob?.join()
    }

    fun currentFormatted(): String = latestFormatted

    @Suppress("unused")
    private fun pruneHistory(maxPairs: Int = 24) {
        val maxMessages = maxPairs * 2
        while (history.size > maxMessages) {
            history.removeAt(0)
        }
    }

    @Suppress("unused")
    private fun <T> MutableList<T>.removeLastOrNull(): T? {
        if (isEmpty()) return null
        return removeAt(size - 1)
    }

    companion object {
        private const val TAG = "StreamingConversation"
    }
}

data class ConversationPayload(
    val transcript: String,
    val selectedText: String,
    val screenContext: String,
    val appContext: String,
    val isCommandMode: Boolean,
    val rawChunk: String,
    // Creation timestamp used for stale-protection logic.
    // Callers don't need to pass this; default is "now".
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val role: String,
    val content: String
)
