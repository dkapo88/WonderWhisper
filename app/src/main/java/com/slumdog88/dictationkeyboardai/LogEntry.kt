package com.slumdog88.dictationkeyboardai

import java.text.SimpleDateFormat
import java.util.*

/**
 * Enum representing the point at which a failure occurred
 */
enum class FailurePoint {
    TRANSCRIPTION,    // Voice model failed
    AI_PROCESSING     // Post-processing failed
}

/**
 * Data class representing error state for failed transcriptions
 */
data class ErrorState(
    val failurePoint: FailurePoint,
    val serviceName: String,
    val errorMessage: String
)

/**
 * Data class representing a log entry for recording history
 */
data class LogEntry(
    val timestamp: String,
    val audioFileName: String?,
    val context: String?,
    val userMessage: String?,
    val aiProcessed: String?,
    val rawText: String,
    val isReprocessed: Boolean = false,
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics.empty(),
    val errorState: ErrorState? = null
) {

    /**
     * Returns true if this entry represents a failed transcription
     */
    fun isFailed(): Boolean = errorState != null
    
    /**
     * Gets a formatted display timestamp
     */
    fun getFormattedTimestamp(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp
        }
    }
    
    /**
     * Gets the first line of content for preview
     */
    fun getPreviewContent(): String {
        if (errorState != null) {
            return "Transcription failed"
        }
        return (userMessage ?: rawText).split("\n").firstOrNull()?.take(100) ?: ""
    }
    
    /**
     * Gets the raw transcription, falling back to userMessage for backward compatibility
     */
    fun getRawTranscriptionContent(): String {
        // Return only the voice model output (the parsed transcription),
        // never the entire raw log blob.
        return userMessage ?: ""
    }
    
    /**
     * Gets the AI-processed version, falling back to userMessage for backward compatibility
     */
    fun getAiProcessedContent(): String {
        return aiProcessed ?: userMessage ?: ""
    }
    
    /**
     * Returns true if this log entry has separate raw and AI-processed versions
     */
    fun hasDistinctVersions(): Boolean {
        return !userMessage.isNullOrBlank() && !aiProcessed.isNullOrBlank() && userMessage != aiProcessed
    }
}
