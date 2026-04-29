package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import com.slumdog88.dictationkeyboardai.ErrorState
import com.slumdog88.dictationkeyboardai.FailurePoint
import com.slumdog88.dictationkeyboardai.LogEntry
import com.slumdog88.dictationkeyboardai.PerformanceMetrics

object HistoryManager {

    fun parseLogEntry(rawLog: String): LogEntry? {
        val lines = rawLog.split("\n")
        if (lines.isEmpty()) {
            return null
        }
        
        var timestamp = ""
        var audioFileName: String? = null
        var appName: String? = null
        var selectedText: String? = null
        var clipboardText: String? = null
        var transcription: String? = null
        var aiProcessed: String? = null
        var isReprocessed = false
        var errorType: String? = null
        var errorService: String? = null
        var errorMessage: String? = null
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("[") && (line.endsWith("]") || line.contains("] (REPROCESSED)")) -> {
                    if (line.contains("] (REPROCESSED)")) {
                        val endIndex = line.indexOf("] (REPROCESSED)")
                        timestamp = line.substring(1, endIndex)
                        isReprocessed = true
                    } else {
                        timestamp = line.substring(1, line.length - 1)
                        isReprocessed = false
                    }
                    i++
                }
                line.startsWith("Audio: ") -> {
                    audioFileName = line.substring(7)
                    i++
                }
                line.startsWith("App: ") -> {
                    appName = line.substring(5)
                    i++
                }
                line.startsWith("Selected Text: ") -> {
                    selectedText = line.substring(15)
                    i++
                }
                line.startsWith("Clipboard: ") -> {
                    clipboardText = line.substring(11)
                    i++
                }
                line.startsWith("Transcription: ") -> {
                    transcription = line.substring(15)
                    i++
                }
                line.startsWith("AI Processed: ") -> {
                    aiProcessed = line.substring(14)
                    i++
                }
                line.startsWith("Error: ") -> {
                    errorType = line.substring(7)
                    i++
                }
                line.startsWith("Error Service: ") -> {
                    errorService = line.substring(15)
                    i++
                }
                line.startsWith("Error Message: ") -> {
                    errorMessage = line.substring(15)
                    i++
                }
                else -> i++
            }
        }

        // Construct ErrorState if error fields are present
        val errorState = if (errorType != null) {
            ErrorState(
                failurePoint = try {
                    FailurePoint.valueOf(errorType)
                } catch (e: Exception) {
                    FailurePoint.TRANSCRIPTION
                },
                serviceName = errorService ?: "Unknown",
                errorMessage = errorMessage ?: "Unknown error"
            )
        } else null

        return LogEntry(
            timestamp = timestamp,
            audioFileName = audioFileName,
            context = selectedText,
            userMessage = transcription ?: "",
            aiProcessed = aiProcessed,
            rawText = transcription ?: "",
            isReprocessed = isReprocessed,
            performanceMetrics = PerformanceMetrics(),
            errorState = errorState
        )
    }

    fun parseRecordingHistory(context: Context, limit: Int = 30): List<LogEntry> {
        if (limit <= 0) return emptyList()

        val preferencesManager = PreferencesManager(context)
        val logs = preferencesManager.getDictationLogs()

        if (logs.isBlank()) {
            return emptyList()
        }

        return try {
            val segments = logs.split("---\n")
                .filter { it.isNotBlank() }

            if (segments.isEmpty()) {
                emptyList()
            } else {
                val safeLimit = limit.coerceAtLeast(1)
                val limitedSegments = if (segments.size <= safeLimit) segments else segments.takeLast(safeLimit)
                limitedSegments
                    .asReversed()
                    .mapNotNull { parseLogEntry(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("HistoryManager", "Error parsing recording history", e)
            emptyList()
        }
    }
}
