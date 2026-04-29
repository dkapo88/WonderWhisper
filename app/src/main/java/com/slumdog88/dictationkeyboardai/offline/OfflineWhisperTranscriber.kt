package com.slumdog88.dictationkeyboardai.offline

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.slumdog88.dictationkeyboardai.offline.whisper.DecodingMode
import com.slumdog88.dictationkeyboardai.offline.whisper.OfflineWhisperRuntime
import com.slumdog88.dictationkeyboardai.offline.whisper.RunState
import com.slumdog88.dictationkeyboardai.offline.OfflineModelAvailability
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelRegistry
import java.io.File

object OfflineWhisperTranscriber {
    suspend fun transcribe(
        context: Context,
        modelId: String,
        audioFile: File,
        glossary: String,
        forceLanguage: String? = null,
        pcmSamples: FloatArray? = null,
        sampleRate: Int = 16_000,
        onPartialResult: (String) -> Unit = {},
        onStatusUpdate: (RunState) -> Unit = {}
    ): String {
        val overallStart = SystemClock.elapsedRealtime()
        val definition = OfflineWhisperModelRegistry.findDefinition(modelId)
        val availabilityStart = SystemClock.elapsedRealtime()
        val (availability, message) = OfflineWhisperModelManager.determineAvailability(context, definition)
        val availabilityDuration = SystemClock.elapsedRealtime() - availabilityStart
        if (availability != OfflineModelAvailability.READY) {
            val detail = message ?: "Model files missing."
            throw IllegalStateException("Offline model not ready: $detail")
        }

        val fallbackDefinition = definition.fallbackModelId?.let { OfflineWhisperModelRegistry.findDefinition(it) }
        val prepStart = SystemClock.elapsedRealtime()
        val rawSamples = when {
            pcmSamples != null && pcmSamples.isNotEmpty() -> {
                if (sampleRate != 16_000) {
                    val resampled = OfflineAudioUtils.resample(pcmSamples, sampleRate, 16_000)
                    resampled
                } else {
                    pcmSamples
                }
            }
            else -> OfflineAudioDecoder.decodeToMono16kFloatArray(audioFile)
        }
        val samples = OfflineAudioUtils.trimSilence(rawSamples, 16_000)
        val trimmedPct = if (rawSamples.isNotEmpty()) {
            (100.0 * (rawSamples.size - samples.size) / rawSamples.size).toInt()
        } else 0
        android.util.Log.d(
            "OfflineWhisperTranscriber",
            "audio prep: raw=${rawSamples.size} trimmed=${samples.size} (${trimmedPct}%)"
        )
        val prepDuration = SystemClock.elapsedRealtime() - prepStart
        require(samples.isNotEmpty()) { "Decoded audio contains no samples." }

        val inferenceStart = SystemClock.elapsedRealtime()
        val transcript = OfflineWhisperRuntime.withModel(
            context = context,
            model = definition,
            fallback = fallbackDefinition,
            suppressNonSpeech = definition.suppressNonSpeechTokens,
            languages = definition.supportedLanguages,
            onStatusUpdate = onStatusUpdate,
            onPartialDecode = onPartialResult
        ) { wrapper ->
            wrapper.run(
                samples = samples,
                glossary = glossary,
                forceLanguage = forceLanguage,
                decodingMode = DecodingMode.Greedy
            )
        }

        val inferenceDuration = SystemClock.elapsedRealtime() - inferenceStart
        val totalDuration = SystemClock.elapsedRealtime() - overallStart
        Log.d(
            "OfflineWhisperTranscriber",
            "timing model=${modelId}, availability=${availabilityDuration}ms, prep=${prepDuration}ms, inference=${inferenceDuration}ms, total=${totalDuration}ms, samples=${samples.size}"
        )

        return transcript.trim()
    }
}
