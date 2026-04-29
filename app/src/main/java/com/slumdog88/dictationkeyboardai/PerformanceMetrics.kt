package com.slumdog88.dictationkeyboardai

import java.text.DecimalFormat

/**
 * Data class to hold performance timing metrics for dictation operations
 */
data class PerformanceMetrics(
    val transcriptionTimeMs: Long = 0L,
    val aiProcessingTimeMs: Long = 0L,
    val totalProcessingTimeMs: Long = 0L,
    val transcriptionCacheHit: Boolean = false,
    val aiProcessingCacheHit: Boolean = false,
    val transcriptionService: String = "",
    val aiModel: String = ""
) {
    
    companion object {
        private val decimalFormat = DecimalFormat("#.##")
        
        /**
         * Creates an empty metrics instance
         */
        fun empty(): PerformanceMetrics = PerformanceMetrics()
        
        /**
         * Parses performance metrics from a log entry string
         */
        fun fromLogEntry(rawLog: String): PerformanceMetrics? {
            try {
                val lines = rawLog.split("\n")
                var transcriptionTimeMs = 0L
                var aiProcessingTimeMs = 0L
                var totalProcessingTimeMs = 0L
                var transcriptionCacheHit = false
                var aiProcessingCacheHit = false
                var transcriptionService = ""
                var aiModel = ""
                
                for (line in lines) {
                    when {
                        line.startsWith("Performance - Transcription Time: ") -> {
                            val timeStr = line.substringAfter("Performance - Transcription Time: ").substringBefore("ms")
                            transcriptionTimeMs = timeStr.toLongOrNull() ?: 0L
                        }
                        line.startsWith("Performance - AI Processing Time: ") -> {
                            val timeStr = line.substringAfter("Performance - AI Processing Time: ").substringBefore("ms")
                            aiProcessingTimeMs = timeStr.toLongOrNull() ?: 0L
                        }
                        line.startsWith("Performance - Total Processing Time: ") -> {
                            val timeStr = line.substringAfter("Performance - Total Processing Time: ").substringBefore("ms")
                            totalProcessingTimeMs = timeStr.toLongOrNull() ?: 0L
                        }
                        line.startsWith("Performance - Transcription Cache: ") -> {
                            transcriptionCacheHit = line.substringAfter("Performance - Transcription Cache: ").toBoolean()
                        }
                        line.startsWith("Performance - AI Processing Cache: ") -> {
                            aiProcessingCacheHit = line.substringAfter("Performance - AI Processing Cache: ").toBoolean()
                        }
                        line.startsWith("Transcription Service: ") -> {
                            transcriptionService = line.substringAfter("Transcription Service: ")
                        }
                        line.startsWith("AI Model: ") -> {
                            aiModel = line.substringAfter("AI Model: ")
                        }
                    }
                }
                
                return PerformanceMetrics(
                    transcriptionTimeMs = transcriptionTimeMs,
                    aiProcessingTimeMs = aiProcessingTimeMs,
                    totalProcessingTimeMs = totalProcessingTimeMs,
                    transcriptionCacheHit = transcriptionCacheHit,
                    aiProcessingCacheHit = aiProcessingCacheHit,
                    transcriptionService = transcriptionService,
                    aiModel = aiModel
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
    
    /**
     * Converts milliseconds to seconds with 2 decimal places
     */
    private fun msToSeconds(ms: Long): String {
        return if (ms > 0) {
            decimalFormat.format(ms / 1000.0)
        } else {
            "0.00"
        }
    }
    
    /**
     * Returns a formatted string for display in the UI
     */
    fun getFormattedTimingString(): String {
        if (transcriptionTimeMs == 0L && aiProcessingTimeMs == 0L && totalProcessingTimeMs == 0L) {
            return "No timing data available"
        }
        
        val parts = mutableListOf<String>()
        
        if (transcriptionTimeMs > 0) {
            val cacheIndicator = if (transcriptionCacheHit) " (cached)" else ""
            parts.add("Transcription: ${msToSeconds(transcriptionTimeMs)}s$cacheIndicator")
        }
        
        if (aiProcessingTimeMs > 0) {
            val cacheIndicator = if (aiProcessingCacheHit) " (cached)" else ""
            parts.add("AI: ${msToSeconds(aiProcessingTimeMs)}s$cacheIndicator")
        }
        
        if (totalProcessingTimeMs > 0) {
            parts.add("Total: ${msToSeconds(totalProcessingTimeMs)}s")
        }
        
        return parts.joinToString(", ")
    }
    
    /**
     * Returns a detailed formatted string for expanded view
     */
    fun getDetailedTimingString(): String {
        if (transcriptionTimeMs == 0L && aiProcessingTimeMs == 0L && totalProcessingTimeMs == 0L) {
            return "No performance data available"
        }
        
        val sb = StringBuilder()
        
        if (transcriptionTimeMs > 0) {
            sb.append("Transcription: ${msToSeconds(transcriptionTimeMs)}s")
            if (transcriptionCacheHit) sb.append(" (cached)")
            if (transcriptionService.isNotEmpty()) sb.append(" via $transcriptionService")
            sb.append("\n")
        }
        
        if (aiProcessingTimeMs > 0) {
            sb.append("AI Processing: ${msToSeconds(aiProcessingTimeMs)}s")
            if (aiProcessingCacheHit) sb.append(" (cached)")
            if (aiModel.isNotEmpty()) sb.append(" via $aiModel")
            sb.append("\n")
        }
        
        if (totalProcessingTimeMs > 0) {
            sb.append("Total Processing: ${msToSeconds(totalProcessingTimeMs)}s")
        }
        
        return sb.toString().trim()
    }
    
    /**
     * Returns the log entry format for storing in recording history
     */
    fun toLogEntryFormat(): String {
        val sb = StringBuilder()
        
        if (transcriptionTimeMs > 0) {
            sb.append("Performance - Transcription Time: ${transcriptionTimeMs}ms\n")
            sb.append("Performance - Transcription Cache: $transcriptionCacheHit\n")
        }
        
        if (aiProcessingTimeMs > 0) {
            sb.append("Performance - AI Processing Time: ${aiProcessingTimeMs}ms\n")
            sb.append("Performance - AI Processing Cache: $aiProcessingCacheHit\n")
        }
        
        if (totalProcessingTimeMs > 0) {
            sb.append("Performance - Total Processing Time: ${totalProcessingTimeMs}ms\n")
        }
        
        return sb.toString()
    }
    
    /**
     * Checks if this metrics instance has any timing data
     */
    fun hasTimingData(): Boolean {
        return transcriptionTimeMs > 0 || aiProcessingTimeMs > 0 || totalProcessingTimeMs > 0
    }
}

/**
 * Builder class for constructing PerformanceMetrics with timing measurements
 */
class PerformanceMetricsBuilder {
    private var transcriptionStartTime: Long = 0L
    private var transcriptionEndTime: Long = 0L
    private var aiProcessingStartTime: Long = 0L
    private var aiProcessingEndTime: Long = 0L
    private var totalProcessingStartTime: Long = 0L
    private var totalProcessingEndTime: Long = 0L
    
    private var transcriptionCacheHit: Boolean = false
    private var aiProcessingCacheHit: Boolean = false
    private var transcriptionService: String = ""
    private var aiModel: String = ""
    
    fun startTotalProcessing(): PerformanceMetricsBuilder {
        totalProcessingStartTime = System.currentTimeMillis()
        return this
    }
    
    fun endTotalProcessing(): PerformanceMetricsBuilder {
        totalProcessingEndTime = System.currentTimeMillis()
        return this
    }
    
    fun startTranscription(): PerformanceMetricsBuilder {
        transcriptionStartTime = System.currentTimeMillis()
        return this
    }
    
    fun endTranscription(): PerformanceMetricsBuilder {
        transcriptionEndTime = System.currentTimeMillis()
        return this
    }
    
    fun startAIProcessing(): PerformanceMetricsBuilder {
        aiProcessingStartTime = System.currentTimeMillis()
        return this
    }
    
    fun endAIProcessing(): PerformanceMetricsBuilder {
        aiProcessingEndTime = System.currentTimeMillis()
        return this
    }
    
    fun setTranscriptionCacheHit(cacheHit: Boolean): PerformanceMetricsBuilder {
        transcriptionCacheHit = cacheHit
        return this
    }
    
    fun setAIProcessingCacheHit(cacheHit: Boolean): PerformanceMetricsBuilder {
        aiProcessingCacheHit = cacheHit
        return this
    }
    
    fun setTranscriptionService(service: String): PerformanceMetricsBuilder {
        transcriptionService = service
        return this
    }
    
    fun setAIModel(model: String): PerformanceMetricsBuilder {
        aiModel = model
        return this
    }
    
    fun build(): PerformanceMetrics {
        val transcriptionTime = if (transcriptionEndTime > transcriptionStartTime) {
            transcriptionEndTime - transcriptionStartTime
        } else 0L
        
        val aiProcessingTime = if (aiProcessingEndTime > aiProcessingStartTime) {
            aiProcessingEndTime - aiProcessingStartTime
        } else 0L
        
        val totalTime = if (totalProcessingEndTime > totalProcessingStartTime) {
            totalProcessingEndTime - totalProcessingStartTime
        } else 0L
        
        return PerformanceMetrics(
            transcriptionTimeMs = transcriptionTime,
            aiProcessingTimeMs = aiProcessingTime,
            totalProcessingTimeMs = totalTime,
            transcriptionCacheHit = transcriptionCacheHit,
            aiProcessingCacheHit = aiProcessingCacheHit,
            transcriptionService = transcriptionService,
            aiModel = aiModel
        )
    }
}
