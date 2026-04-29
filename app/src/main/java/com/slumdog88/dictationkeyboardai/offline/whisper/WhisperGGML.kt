package com.slumdog88.dictationkeyboardai.offline.whisper

import androidx.annotation.Keep
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.nio.Buffer

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
val whisperInferenceContext = newSingleThreadContext("whisper-ggml-inference")

enum class DecodingMode(val value: Int) {
    Greedy(0),
    BeamSearch5(5)
}

class BailLanguageException(val language: String) : Exception()

class WhisperGGML(
    modelBuffer: Buffer,
    private val partialResultCallback: (String) -> Unit
) {
    companion object {
        init {
            System.loadLibrary("voiceinput")
        }
    }

    private var handle: Long = 0L

    init {
        handle = openFromBufferNative(modelBuffer)
        if (handle == 0L) {
            throw IllegalArgumentException("The Whisper model could not be loaded from the provided buffer")
        }
    }

    @Keep
    private fun invokePartialResult(text: String) {
        partialResultCallback(text.trim())
    }

    @Throws(BailLanguageException::class)
    suspend fun infer(
        samples: FloatArray,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        decodingMode: DecodingMode,
        suppressNonSpeechTokens: Boolean
    ): String = withContext(whisperInferenceContext) {
        if (handle == 0L) {
            throw IllegalStateException("WhisperGGML has already been closed, cannot infer")
        }

        val result = inferNative(
            handle,
            samples,
            prompt,
            languages,
            bailLanguages,
            decodingMode.value,
            suppressNonSpeechTokens
        ).trim()

        if (result.contains("<>CANCELLED<>")) {
            val language = result.split("lang=").getOrNull(1) ?: "unknown"
            throw BailLanguageException(language)
        } else {
            result
        }
    }

    suspend fun close() = withContext(whisperInferenceContext) {
        if (handle != 0L) {
            closeNative(handle)
        }
        handle = 0L
    }

    private external fun openFromBufferNative(buffer: Buffer): Long
    private external fun inferNative(
        handle: Long,
        samples: FloatArray,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        decodingMode: Int,
        suppressNonSpeechTokens: Boolean
    ): String

    private external fun closeNative(handle: Long)
}
