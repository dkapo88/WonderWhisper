package com.slumdog88.dictationkeyboardai.transcription.streaming

/**
 * Represents the UI-facing state of a streaming dictation session.
 *
 * This will be consumed by overlay UI components to render the expanded streaming panel.
 */
enum class StreamingStage {
    IDLE,
    LISTENING,
    CAPTURING,
    TRANSCRIBING,
    FORMATTING,
    STREAMING,
    ERROR
}

data class StreamingUiState(
    val isActive: Boolean = false,
    val isRecording: Boolean = false,
    val statusMessage: String = "",
    val formattedTranscript: String = "",
    val livePreview: String = "",
    val errorMessage: String? = null,
    val controlsEnabled: Boolean = true,
    val stage: StreamingStage = StreamingStage.IDLE,
    val currentChunk: Int = 0,
    val amplitude: Int = 0
) {
    companion object {
        fun idle(): StreamingUiState = StreamingUiState(
            isActive = false,
            isRecording = false,
            statusMessage = "",
            formattedTranscript = "",
            livePreview = "",
            errorMessage = null,
            controlsEnabled = true,
            stage = StreamingStage.IDLE,
            currentChunk = 0,
            amplitude = 0
        )
    }

    val hasError: Boolean get() = !errorMessage.isNullOrBlank()
}
