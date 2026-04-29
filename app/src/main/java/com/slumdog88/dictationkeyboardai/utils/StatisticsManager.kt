package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import android.content.SharedPreferences

class StatisticsManager(private val context: Context) {

    private val statsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("transcription_stats", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_TOTAL_WORDS = "total_words"
        private const val KEY_TOTAL_TRANSCRIPTION_TIME = "total_transcription_time"
        private const val KEY_SESSION_COUNT = "session_count"
        private const val KEY_LAST_UPDATED = "last_updated"
    }

    /**
     * Add statistics from a completed transcription session
     * @param wordCount Number of words in the transcription
     * @param transcriptionTimeSeconds Time spent on this transcription in seconds
     */
    fun addTranscriptionStats(wordCount: Int, transcriptionTimeSeconds: Int) {
        val currentWords = getTotalWords()
        val currentTime = getTotalTranscriptionTime()
        val currentSessions = getSessionCount()

        statsPrefs.edit()
            .putInt(KEY_TOTAL_WORDS, currentWords + wordCount)
            .putInt(KEY_TOTAL_TRANSCRIPTION_TIME, currentTime + transcriptionTimeSeconds)
            .putInt(KEY_SESSION_COUNT, currentSessions + 1)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    /**
     * Get total words transcribed across all sessions
     */
    fun getTotalWords(): Int {
        return statsPrefs.getInt(KEY_TOTAL_WORDS, 0)
    }

    /**
     * Get total time spent transcribing in seconds
     */
    fun getTotalTranscriptionTime(): Int {
        return statsPrefs.getInt(KEY_TOTAL_TRANSCRIPTION_TIME, 0)
    }

    /**
     * Get total number of transcription sessions
     */
    fun getSessionCount(): Int {
        return statsPrefs.getInt(KEY_SESSION_COUNT, 0)
    }

    /**
     * Calculate user's words per minute based on cumulative stats
     * Returns 0 if no data available
     */
    fun getUserWpm(): Int {
        val totalTime = getTotalTranscriptionTime()
        val totalWords = getTotalWords()

        if (totalTime <= 0 || totalWords <= 0) {
            return 0
        }

        return (totalWords.toDouble() / (totalTime.toDouble() / 60.0)).toInt()
    }

    /**
     * Calculate time saved compared to phone typing
     * Uses realistic phone typing speed of 35 WPM
     * @return Time saved in seconds
     */
    fun getTimeSaved(): Int {
        val totalWords = getTotalWords()
        val transcriptionTime = getTotalTranscriptionTime()
        val phoneTypingWpm = 35

        // Calculate how long it would take to type on phone
        val typingTimeSeconds = (totalWords.toDouble() / phoneTypingWpm * 60).toInt()

        // Time saved is typing time minus actual transcription time
        return maxOf(0, typingTimeSeconds - transcriptionTime)
    }

    /**
     * Get the speed multiplier compared to phone typing
     * @return Speed multiplier (e.g., 2.5x means 2.5 times faster than typing)
     */
    fun getSpeedMultiplier(): Double {
        val userWpm = getUserWpm()
        val phoneTypingWpm = 35

        if (userWpm <= 0 || phoneTypingWpm <= 0) {
            return 1.0
        }

        return userWpm.toDouble() / phoneTypingWpm.toDouble()
    }

    /**
     * Reset all statistics to zero
     */
    fun resetStatistics() {
        statsPrefs.edit()
            .remove(KEY_TOTAL_WORDS)
            .remove(KEY_TOTAL_TRANSCRIPTION_TIME)
            .remove(KEY_SESSION_COUNT)
            .remove(KEY_LAST_UPDATED)
            .apply()
    }

    /**
     * Get statistics summary as a data class
     */
    fun getStatisticsSummary(): StatisticsSummary {
        return StatisticsSummary(
            totalWords = getTotalWords(),
            totalTranscriptionTime = getTotalTranscriptionTime(),
            sessionCount = getSessionCount(),
            userWpm = getUserWpm(),
            timeSaved = getTimeSaved(),
            speedMultiplier = getSpeedMultiplier()
        )
    }

    /**
     * Data class to hold all statistics
     */
    data class StatisticsSummary(
        val totalWords: Int,
        val totalTranscriptionTime: Int,
        val sessionCount: Int,
        val userWpm: Int,
        val timeSaved: Int,
        val speedMultiplier: Double
    )
}