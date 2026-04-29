package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manager class for handling audio file operations including directory management,
 * file migration, and audio file utilities.
 */
class AudioFileManager(private val context: Context) {
    
    companion object {
        private const val WUNDER_WHISPER_DIR = "WonderWhisper"
        private const val MIGRATION_COMPLETED_KEY = "audio_migration_completed"
    }
    
    /**
     * Gets the public WunderWhisper directory for storing audio files
     * Creates the directory if it doesn't exist
     */
    fun getPublicAudioDirectory(): File {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val wonderWhisperDir = File(downloadsDir, WUNDER_WHISPER_DIR)
        
        if (!wonderWhisperDir.exists()) {
            wonderWhisperDir.mkdirs()
            Log.d("AudioFileManager", "Created WonderWhisper directory: ${wonderWhisperDir.absolutePath}")
        }
        
        return wonderWhisperDir
    }
    
    /**
     * Creates a temporary audio file for recording
     */
    fun createTempAudioFile(): File {
        val audioFile = File(context.filesDir, "temp_audio_${System.currentTimeMillis()}.m4a")
        audioFile.delete() // Clean up any existing file
        return audioFile
    }

    fun createTempPcmAudioFile(): File {
        val audioFile = File(context.filesDir, "temp_audio_${System.currentTimeMillis()}.wav")
        audioFile.delete()
        return audioFile
    }
    
    /**
     * Migrates existing audio files from the old private directory to the new public WunderWhisper directory
     * This runs once when the service is created to ensure users don't lose existing recordings
     */
    fun migrateExistingAudioFiles() {
        try {
            // Check if migration has already been done
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val migrationDone = prefs.getBoolean(MIGRATION_COMPLETED_KEY, false)
            
            if (migrationDone) {
                Log.d("AudioFileManager", "Audio migration already completed, skipping")
                return
            }
            
            // Old private directory
            val oldOutputDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: context.filesDir
            
            // New public directory
            val newOutputDir = getPublicAudioDirectory()
            
            Log.d("AudioFileManager", "Starting audio file migration to WonderWhisper folder")
            Log.d("AudioFileManager", "From: ${oldOutputDir.absolutePath}")
            Log.d("AudioFileManager", "To: ${newOutputDir.absolutePath}")
            
            if (!oldOutputDir.exists()) {
                Log.d("AudioFileManager", "Old directory doesn't exist, marking migration as complete")
                prefs.edit().putBoolean(MIGRATION_COMPLETED_KEY, true).apply()
                return
            }
            
            // Find all audio files in old directory
            val audioFiles = oldOutputDir.listFiles { file -> 
                file.name.endsWith(".m4a") || file.name.endsWith(".wav") || file.name.endsWith(".mp3")
            }
            
            if (audioFiles.isNullOrEmpty()) {
                Log.d("AudioFileManager", "No audio files to migrate")
                prefs.edit().putBoolean(MIGRATION_COMPLETED_KEY, true).apply()
                return
            }
            
            Log.d("AudioFileManager", "Found ${audioFiles.size} audio files to migrate")
            
            var migratedCount = 0
            for (audioFile in audioFiles) {
                try {
                    val newFile = File(newOutputDir, audioFile.name)
                    
                    // Check if file already exists in new location
                    if (newFile.exists()) {
                        Log.d("AudioFileManager", "File already exists in new location: ${audioFile.name}")
                        continue
                    }
                    
                    // Copy file to new location
                    audioFile.copyTo(newFile, overwrite = false)
                    
                    // Verify copy was successful
                    if (newFile.exists() && newFile.length() == audioFile.length()) {
                        // Delete old file after successful copy
                        audioFile.delete()
                        migratedCount++
                        Log.d("AudioFileManager", "Migrated: ${audioFile.name}")
                    } else {
                        Log.e("AudioFileManager", "Migration failed for: ${audioFile.name}")
                    }
                    
                } catch (e: Exception) {
                    Log.e("AudioFileManager", "Error migrating file: ${audioFile.name}", e)
                }
            }
            
            Log.d("AudioFileManager", "Migration completed: $migratedCount/${audioFiles.size} files migrated")
            
            // Mark migration as complete
            prefs.edit().putBoolean(MIGRATION_COMPLETED_KEY, true).apply()
            
        } catch (e: Exception) {
            Log.e("AudioFileManager", "Error during audio file migration", e)
        }
    }
    
    /**
     * Generate a hash for an audio file for caching purposes
     * Uses file size and last modified time for fast hashing
     */
    fun getAudioFileHash(audioFile: File): String {
        // Simple hash based on file size and modification time
        // This is fast and good enough for detecting identical recordings
        return "${audioFile.length()}_${audioFile.lastModified()}".hashCode().toString()
    }
    
    /**
     * Save an audio file to the public directory with a specific name
     */
    fun saveAudioFileToPublicDirectory(sourceFile: File, fileName: String): File? {
        return try {
            val publicDir = getPublicAudioDirectory()
            val targetFile = File(publicDir, fileName)
            
            if (sourceFile.exists()) {
                sourceFile.copyTo(targetFile, overwrite = true)
                Log.d("AudioFileManager", "Audio file saved to public directory: $fileName")
                targetFile
            } else {
                Log.e("AudioFileManager", "Source file does not exist: ${sourceFile.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e("AudioFileManager", "Error saving audio file to public directory", e)
            null
        }
    }
    
    /**
     * Delete an audio file from the public directory
     */
    fun deleteAudioFileFromPublicDirectory(fileName: String): Boolean {
        return try {
            val publicDir = getPublicAudioDirectory()
            val fileToDelete = File(publicDir, fileName)
            
            if (fileToDelete.exists()) {
                val deleted = fileToDelete.delete()
                if (deleted) {
                    Log.d("AudioFileManager", "Deleted audio file: $fileName")
                } else {
                    Log.w("AudioFileManager", "Failed to delete audio file: $fileName")
                }
                deleted
            } else {
                Log.w("AudioFileManager", "Audio file not found for deletion: $fileName")
                false
            }
        } catch (e: Exception) {
            Log.e("AudioFileManager", "Error deleting audio file", e)
            false
        }
    }
    
    /**
     * Get all audio files in the public directory
     */
    fun getAudioFilesInPublicDirectory(): List<File> {
        return try {
            val publicDir = getPublicAudioDirectory()
            val audioFiles = publicDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".m4a") || file.name.endsWith(".wav") || file.name.endsWith(".mp3"))
            }
            audioFiles?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e("AudioFileManager", "Error getting audio files from public directory", e)
            emptyList()
        }
    }
    
    /**
     * Clean up temporary audio files
     */
    fun cleanupTempAudioFiles() {
        try {
            val tempFiles = context.filesDir.listFiles { file ->
                file.name.startsWith("temp_audio_") && file.name.endsWith(".m4a")
            }
            
            tempFiles?.forEach { file ->
                try {
                    if (file.delete()) {
                        Log.d("AudioFileManager", "Cleaned up temp file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.w("AudioFileManager", "Failed to delete temp file: ${file.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioFileManager", "Error during temp file cleanup", e)
        }
    }
    
    /**
     * Get the size of all audio files in the public directory
     */
    fun getTotalAudioFilesSize(): Long {
        return try {
            getAudioFilesInPublicDirectory().sumOf { it.length() }
        } catch (e: Exception) {
            Log.e("AudioFileManager", "Error calculating total audio files size", e)
            0L
        }
    }
    
    /**
     * Check if an audio file exists in the public directory
     */
    fun audioFileExists(fileName: String): Boolean {
        return try {
            val publicDir = getPublicAudioDirectory()
            val file = File(publicDir, fileName)
            file.exists()
        } catch (e: Exception) {
            Log.e("AudioFileManager", "Error checking if audio file exists", e)
            false
        }
    }
}
