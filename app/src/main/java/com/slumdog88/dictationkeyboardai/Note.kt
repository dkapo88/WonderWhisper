package com.slumdog88.dictationkeyboardai

import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing a voice note entry
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String, // For backward compatibility - contains the final processed content
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val audioFileName: String,
    val isRecording: Boolean = false,
    val originalTranscript: String? = null, // Raw transcript from speech recognition
    val aiProcessed: String? = null, // AI-enhanced/processed version
    val aiGeneratedTitle: String? = null // AI-generated title from content analysis
) {
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
        return content.split("\n").firstOrNull()?.take(100) ?: ""
    }
    
    /**
     * Gets the original transcript, falling back to content for backward compatibility
     */
    fun getOriginalTranscriptContent(): String {
        return originalTranscript ?: content
    }
    
    /**
     * Gets the AI-processed version, falling back to content for backward compatibility
     */
    fun getAiProcessedContent(): String {
        return aiProcessed ?: content
    }
    
    /**
     * Returns true if this note has separate original and AI-processed versions
     */
    fun hasDistinctVersions(): Boolean {
        return originalTranscript != null && aiProcessed != null
    }

    /**
     * Gets the display title, preferring AI-generated title if available
     */
    fun getDisplayTitle(): String {
        return aiGeneratedTitle ?: title
    }
}