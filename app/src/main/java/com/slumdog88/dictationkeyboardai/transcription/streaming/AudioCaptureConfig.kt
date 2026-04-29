package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.media.AudioFormat
import kotlin.math.max

data class AudioCaptureConfig(
    val sampleRate: Int = 16_000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
    val frameSizeMs: Int = 32,
    val bufferSizeMs: Int = 400
) {
    val frameSizeSamples: Int = (sampleRate * frameSizeMs) / 1000
    val frameDurationMs: Int = frameSizeMs

    val bufferSizeSamples: Int = max(
        frameSizeSamples * 2,
        (sampleRate * bufferSizeMs) / 1000
    )

    private val bytesPerSample: Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    val bufferSizeBytes: Int = bufferSizeSamples * bytesPerSample
}
