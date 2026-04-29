package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.slumdog88.dictationkeyboardai.BubbleOverlayService
import com.slumdog88.dictationkeyboardai.LogEntry
import java.io.File

object ServiceManager {

    fun reprocessAudio(context: Context, entry: LogEntry, callback: () -> Unit) {
        if (entry.audioFileName == null) {
            android.util.Log.w("ServiceManager", "No audio file available for reprocess")
            callback()
            return
        }

        val outputDir = AudioManager.getPublicAudioDirectory(context)
        val audioFile = File(outputDir, entry.audioFileName)
        
        if (!audioFile.exists()) {
            android.util.Log.w("ServiceManager", "Audio file not found: ${entry.audioFileName}")
            callback()
            return
        }

        android.util.Log.d("ServiceManager", "Reprocessing audio...")
        
        // Start BubbleOverlayService for reprocessing
        val serviceIntent = Intent(context, BubbleOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Build bundled context from the entry raw log
        val contextLines = mutableListOf<String>()
        entry.rawText.split("\n").forEach { line ->
            when {
                line.startsWith("App: ") -> contextLines.add(line)
                line.startsWith("Selected Text: ") && !line.endsWith("Selected Text: ") -> contextLines.add(line)
                line.startsWith("Screen: ") && !line.endsWith("Screen: ") -> contextLines.add(line)
                line.startsWith("Clipboard: ") && !line.endsWith("Clipboard: ") -> contextLines.add(line)
            }
        }
        val bundledContext = if (contextLines.isNotEmpty()) contextLines.joinToString("\n") else null

        // Send broadcast and direct action for reliability
        val reprocessIntent = Intent(BubbleOverlayService.ACTION_REPROCESS_AUDIO).apply {
            setClass(context, BubbleOverlayService::class.java)
            putExtra("audio_file_path", audioFile.absolutePath)
            putExtra("audio_file_name", entry.audioFileName)
            if (bundledContext != null) putExtra("context", bundledContext)
        }
        context.sendBroadcast(reprocessIntent)

        val directIntent = Intent(BubbleOverlayService.ACTION_REPROCESS_AUDIO_DIRECT).apply {
            setClass(context, BubbleOverlayService::class.java)
            putExtra("audio_file_path", audioFile.absolutePath)
            putExtra("audio_file_name", entry.audioFileName)
            if (bundledContext != null) putExtra("context", bundledContext)
        }
        context.startService(directIntent)
        
        callback()
    }

    fun startNoteRecording(context: Context) {
        // Start/bring up the service and request note recording
        val serviceIntent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_START_NOTE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopNoteRecording(context: Context) {
        // Tell the running service to stop and transcribe
        val serviceIntent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_STOP_NOTE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    // IME keyboard dictation (insert into focused field + history)
    fun startImeDictation(context: Context) {
        val serviceIntent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_START_DICTATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopImeDictation(context: Context) {
        val serviceIntent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_STOP_DICTATION
        }
        // Only send a stop command if the service is already running. Starting a new
        // foreground service just to stop immediately can trigger a 5s ANR on Samsung.
        if (BubbleOverlayService.isRunning()) {
            // Regular startService is sufficient to deliver the stop action
            context.startService(serviceIntent)
        } else {
            // Service isn't running; nothing to stop.
            // Optionally, we could no-op or show a lightweight toast/log.
        }
    }
}
