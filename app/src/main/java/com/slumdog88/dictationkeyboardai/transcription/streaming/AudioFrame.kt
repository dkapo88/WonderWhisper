package com.slumdog88.dictationkeyboardai.transcription.streaming

data class AudioFrame(
    val samples: ShortArray,
    val timestampMs: Long,
    val durationMs: Int
)
