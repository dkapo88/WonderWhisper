package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import java.io.File

/**
 * Storage helper for dictation logs. Uses a simple append-only text file instead of SharedPreferences
 * so we can avoid rewriting the entire history on each entry.
 */
class LogStorageManager private constructor(private val context: Context) {
    private val logFile: File by lazy { File(context.filesDir, "dictation_logs.txt") }
    private val lock = Any()

    fun readLogs(): String = synchronized(lock) {
        if (!logFile.exists()) return@synchronized ""
        logFile.readText()
    }

    fun writeLogs(rawLogs: String) = synchronized(lock) {
        if (rawLogs.isBlank()) {
            if (logFile.exists()) {
                logFile.writeText("")
            } else {
                logFile.createNewFile()
            }
        } else {
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
            }
            logFile.writeText(rawLogs)
        }
    }

    fun appendLog(entry: String) = synchronized(lock) {
        if (!logFile.exists()) {
            logFile.parentFile?.mkdirs()
            logFile.createNewFile()
        }
        logFile.appendText(entry)
    }

    fun clear() = synchronized(lock) {
        if (logFile.exists()) {
            logFile.writeText("")
        }
    }

    fun readEntries(): List<String> {
        val raw = readLogs()
        if (raw.isBlank()) return emptyList()
        return raw.split("---\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun writeEntries(entries: List<String>) {
        if (entries.isEmpty()) {
            clear()
            return
        }
        val builder = StringBuilder()
        entries.forEach { entry ->
            builder.append(entry.trim())
            if (!entry.trim().endsWith("---")) {
                builder.append('\n').append("---")
            }
            builder.append('\n')
        }
        writeLogs(builder.toString())
    }

    fun trimToLimit(limit: Int) = synchronized(lock) {
        if (limit <= 0) {
            clear()
            return@synchronized
        }
        val entries = readEntries()
        if (entries.size <= limit) return@synchronized
        val trimmed = entries.takeLast(limit)
        writeEntries(trimmed)
    }

    companion object {
        fun getInstance(context: Context): LogStorageManager {
            return LogStorageManager(context.applicationContext)
        }
    }
}
