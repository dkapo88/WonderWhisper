package com.slumdog88.dictationkeyboardai

import android.util.Log

/**
 * Secure logging utility that prevents sensitive information exposure in release builds
 * 
 * Usage:
 * - SecureLogger.d() - Debug logs (only in debug builds)
 * - SecureLogger.i() - Info logs (only in debug builds)  
 * - SecureLogger.w() - Warning logs (always logged, content sanitized)
 * - SecureLogger.e() - Error logs (always logged, content sanitized)
 */
object SecureLogger {
    
    // Simple debug flag - will be true for debug builds, false for release
    private val isDebugBuild: Boolean by lazy {
        try {
            // Try to access BuildConfig.DEBUG, fallback to false if not available
            val buildConfigClass = Class.forName("com.slumdog88.dictationkeyboardai.BuildConfig")
            val debugField = buildConfigClass.getField("DEBUG")
            debugField.getBoolean(null)
        } catch (e: Exception) {
            false // Default to no logging if BuildConfig is not available
        }
    }
    
    /**
     * Debug logging - only enabled in debug builds
     */
    fun d(tag: String, message: String) {
        if (isDebugBuild) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Info logging - only enabled in debug builds
     */
    fun i(tag: String, message: String) {
        if (isDebugBuild) {
            Log.i(tag, message)
        }
    }
    
    /**
     * Warning logging - always enabled but content is sanitized
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitizedMessage = sanitizeMessage(message)
        if (throwable != null) {
            Log.w(tag, sanitizedMessage, throwable)
        } else {
            Log.w(tag, sanitizedMessage)
        }
    }
    
    /**
     * Error logging - always enabled but content is sanitized
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitizedMessage = sanitizeMessage(message)
        if (throwable != null) {
            Log.e(tag, sanitizedMessage, throwable)
        } else {
            Log.e(tag, sanitizedMessage)
        }
    }
    
    /**
     * Verbose logging - only enabled in debug builds
     */
    fun v(tag: String, message: String) {
        if (isDebugBuild) {
            Log.v(tag, message)
        }
    }
    
    /**
     * What The F logging - only enabled in debug builds
     */
    fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugBuild) {
            if (throwable != null) {
                Log.wtf(tag, message, throwable)
            } else {
                Log.wtf(tag, message)
            }
        }
    }
    
    /**
     * Sanitizes log messages to remove sensitive information
     */
    private fun sanitizeMessage(message: String): String {
        if (!isDebugBuild) {
            var sanitized = message
            
            // Remove API keys (look for patterns like "key:", "apikey:", "api_key:")
            sanitized = sanitized.replace(Regex("(api[_\\s]*key[:\\s=]+)[\\w\\-_]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            
            // Remove tokens
            sanitized = sanitized.replace(Regex("(token[:\\s=]+)[\\w\\-_]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            
            // Remove long transcription content (keep first 50 chars)
            if (sanitized.contains("transcription", ignoreCase = true) && sanitized.length > 100) {
                val prefix = sanitized.substring(0, minOf(50, sanitized.length))
                sanitized = "$prefix... [TRUNCATED]"
            }
            
            // Remove user input that might contain personal information
            if (sanitized.contains("user input", ignoreCase = true) && sanitized.length > 80) {
                val prefix = sanitized.substring(0, minOf(40, sanitized.length))
                sanitized = "$prefix... [PERSONAL_DATA_REDACTED]"
            }
            
            return sanitized
        }
        
        return message
    }
    
    /**
     * Legacy support - allows gradual migration from Log.d to SecureLogger.d
     */
    @Deprecated("Use SecureLogger.d() instead", ReplaceWith("SecureLogger.d(tag, message)"))
    fun debug(tag: String, message: String) = d(tag, message)
    
    @Deprecated("Use SecureLogger.e() instead", ReplaceWith("SecureLogger.e(tag, message, throwable)"))
    fun error(tag: String, message: String, throwable: Throwable? = null) = e(tag, message, throwable)
}