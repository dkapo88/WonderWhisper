package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.content.Context
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate

data class VadConfig(
    val sampleRate: Int = 16_000,
    val amplitudeThreshold: Double = 0.5,
    val minSpeechDurationMs: Int = 250,
    val hangoverDurationMs: Int = 400,
    val mode: Mode = Mode.NORMAL
)

sealed class VadEvent {
    object SpeechStarted : VadEvent()
    object SpeechEnded : VadEvent()
}

class VoiceActivityDetector(
    context: Context,
    private val config: VadConfig = VadConfig()
) : AutoCloseable {
    private enum class State { Silence, Speech }

    private var state: State = State.Silence
    private var vad: VadSilero? = null

    init {
        // Silero VAD works best with 512 samples (32ms at 16kHz)
        vad = VadSilero(
            context = context,
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_512,
            mode = config.mode,
            silenceDurationMs = config.hangoverDurationMs,
            speechDurationMs = config.minSpeechDurationMs
        )
    }

    fun processFrame(frame: AudioFrame): List<VadEvent> {
        val events = mutableListOf<VadEvent>()
        val vadInstance = vad ?: return events

        // Directly use Silero VAD without manual RMS filtering
        val isSpeech = vadInstance.isSpeech(frame.samples)

        if (state == State.Silence && isSpeech) {
            state = State.Speech
            events.add(VadEvent.SpeechStarted)
        } else if (state == State.Speech && !isSpeech) {
            state = State.Silence
            events.add(VadEvent.SpeechEnded)
        }

        return events
    }

    fun reset() {
        state = State.Silence
        // VadSilero doesn't have a reset state method exposed publicly usually,
        // but the internal state machine should handle itself if we just continue.
        // However, if we want to force a reset (e.g. new session), we might need to recreate it.
        // But for now, just resetting our local state tracker is enough.
    }

    override fun close() {
        vad?.close()
        vad = null
    }

    private fun calculateRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            // Normalize 16-bit signed integer to -1.0..1.0
            val normalized = sample.toInt() / 32768.0
            sum += normalized * normalized
        }
        // Mean square
        val mean = sum / samples.size
        // Root mean square
        return kotlin.math.sqrt(mean)
    }
}
