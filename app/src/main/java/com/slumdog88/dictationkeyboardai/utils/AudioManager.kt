package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.core.content.FileProvider
import com.slumdog88.dictationkeyboardai.LogEntry
import java.io.File

object AudioManager {
    
    fun getPublicAudioDirectory(context: Context): File {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val wonderWhisperDir = File(downloadsDir, "WonderWhisper")
        
        if (!wonderWhisperDir.exists()) {
            wonderWhisperDir.mkdirs()
        }
        
        return wonderWhisperDir
    }

    fun playAudioFile(context: Context, audioFileName: String): MediaPlayer? {
        val mediaPlayer = MediaPlayer()
        try {
            val audioDir = getPublicAudioDirectory(context)
            val audioFile = File(audioDir, audioFileName)
            if (audioFile.exists()) {
                mediaPlayer.setDataSource(audioFile.absolutePath)
                mediaPlayer.prepare()
                mediaPlayer.start()
                Toast.makeText(context, "Playing: $audioFileName", Toast.LENGTH_SHORT).show()
                
                mediaPlayer.setOnCompletionListener {
                    it.release()
                }
                
                return mediaPlayer
            } else {
                Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
                mediaPlayer.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioManager", "Error playing audio", e)
            Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show()
            mediaPlayer.release()
        }
        return null
    }

    fun shareAudioFile(context: Context, audioFileName: String) {
        try {
            val audioDir = getPublicAudioDirectory(context)
            val audioFile = File(audioDir, audioFileName)
            if (audioFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    audioFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "WonderWhisper Recording")
                    putExtra(Intent.EXTRA_TEXT, "WonderWhisper audio recording: ${audioFile.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share audio file"))
            } else {
                Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioManager", "Error sharing audio file", e)
            Toast.makeText(context, "Error sharing audio file", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteAllAudioFiles(context: Context): Int {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val audioFiles = prefs.getStringSet("audio_files", LinkedHashSet())?.toSet() ?: emptySet()
        val outputDir = getPublicAudioDirectory(context)
        
        var deletedCount = 0
        for (audioFileEntry in audioFiles) {
            val parts = audioFileEntry.split(":")
            if (parts.isNotEmpty()) {
                val fileName = parts[0]
                val file = File(outputDir, fileName)
                try {
                    if (file.exists()) {
                        file.delete()
                        deletedCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioManager", "Error deleting audio file: $fileName", e)
                }
            }
        }
        
        // Clear audio metadata
        prefs.edit()
            .remove("audio_files")
            .apply()
            
        return deletedCount
    }
}