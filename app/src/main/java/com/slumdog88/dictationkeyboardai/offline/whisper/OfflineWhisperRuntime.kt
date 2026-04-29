package com.slumdog88.dictationkeyboardai.offline.whisper

import android.content.Context
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelDefinition
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OfflineWhisperRuntime {
    private val mutex = Mutex()
    private var activeModelId: String? = null
    private var activeFallbackId: String? = null
    private var activeSuppressNonSpeech: Boolean = true
    private var activeLanguages: Set<String> = emptySet()
    private var wrapper: WhisperModelWrapper? = null

    suspend fun <T> withModel(
        context: Context,
        model: OfflineWhisperModelDefinition,
        fallback: OfflineWhisperModelDefinition?,
        suppressNonSpeech: Boolean,
        languages: Set<String>,
        onStatusUpdate: (RunState) -> Unit = {},
        onPartialDecode: (String) -> Unit = {},
        block: suspend (WhisperModelWrapper) -> T
    ): T {
        return mutex.withLock {
            val requiresReload = wrapper == null ||
                activeModelId != model.id ||
                activeFallbackId != fallback?.id ||
                activeSuppressNonSpeech != suppressNonSpeech ||
                activeLanguages != languages

            if (requiresReload) {
                wrapper?.close()
                wrapper = WhisperModelWrapper(
                    context = context,
                    primaryModel = model,
                    fallbackModelId = fallback?.id,
                    suppressNonSpeech = suppressNonSpeech,
                    preferredLanguages = languages,
                    onStatusUpdate = onStatusUpdate,
                    onPartialDecode = onPartialDecode
                )
                activeModelId = model.id
                activeFallbackId = fallback?.id
                activeSuppressNonSpeech = suppressNonSpeech
                activeLanguages = languages
            } else {
                wrapper?.updateCallbacks(onStatusUpdate, onPartialDecode)
            }

            val activeWrapper = wrapper ?: throw IllegalStateException("Offline Whisper wrapper failed to initialize")
            block(activeWrapper)
        }
    }

    suspend fun clear() {
        mutex.withLock {
            wrapper?.close()
            wrapper = null
            activeModelId = null
            activeFallbackId = null
            activeLanguages = emptySet()
            activeSuppressNonSpeech = true
        }
    }
}
