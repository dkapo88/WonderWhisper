package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.ai.AIProcessingManager

/**
 * Manager class for handling note storage and retrieval with in-memory caching
 */
class NotePadManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    // In-memory cache to avoid repeated JSON parsing
    private var cachedNotes: MutableList<Note>? = null
    private var cacheLastModified: Long = 0

    companion object {
        private const val NOTES_KEY = "notepad_entries"
        private const val NOTE_AUDIO_PREFIX = "note_"
        private const val NOTES_MODIFIED_KEY = "notepad_modified"
    }

    /**
     * Loads notes from storage into cache if needed
     */
    private fun ensureCacheLoaded() {
        val lastModified = prefs.getLong(NOTES_MODIFIED_KEY, 0)

        // Load cache if it's null or if data has been modified externally
        if (cachedNotes == null || lastModified > cacheLastModified) {
            val notesJson = prefs.getString(NOTES_KEY, "[]") ?: "[]"
            cachedNotes = try {
                val type = object : TypeToken<MutableList<Note>>() {}.type
                gson.fromJson(notesJson, type) ?: mutableListOf()
            } catch (e: Exception) {
                Log.e("NotePadManager", "Error parsing notes from storage", e)
                mutableListOf()
            }
            cacheLastModified = lastModified
            Log.d("NotePadManager", "Cache loaded with ${cachedNotes?.size ?: 0} notes")
        }
    }

    /**
     * Saves the current cache to persistent storage
     */
    private fun saveCache() {
        cachedNotes?.let { notes ->
            val notesJson = gson.toJson(notes)
            val currentTime = System.currentTimeMillis()

            prefs.edit()
                .putString(NOTES_KEY, notesJson)
                .putLong(NOTES_MODIFIED_KEY, currentTime)
                .apply()

            cacheLastModified = currentTime
            Log.d("NotePadManager", "Cache saved with ${notes.size} notes")
        }
    }

    /**
     * Gets all notes as a flow
     */
    fun getAllNotesFlow(): Flow<List<Note>> = flow {
        emit(getAllNotes())
    }

    /**
     * Gets a specific note by ID as a flow
     */
    fun getNoteByIdFlow(noteId: String): Flow<Note?> = flow {
        emit(getNoteById(noteId))
    }

    /**
     * Saves a note to storage
     */
    fun saveNote(note: Note) {
        ensureCacheLoaded()

        // Remove existing note with same ID if present, unless it's a temporary note
        if (note.title != "Processing Note...") {
            cachedNotes?.removeAll { it.id == note.id }
        }

        // Add the new/updated note
        cachedNotes?.add(0, note) // Add to beginning for newest first

        // Save to persistent storage
        saveCache()

        Log.d("NotePadManager", "Note saved: ${note.title}")
    }
    
    /**
     * Gets all notes from storage
     */
    fun getAllNotes(): List<Note> {
        ensureCacheLoaded()
        return cachedNotes?.toList() ?: emptyList()
    }
    
    /**
     * Gets a specific note by ID
     */
    fun getNoteById(id: String): Note? {
        ensureCacheLoaded()
        return cachedNotes?.find { it.id == id }
    }
    
    /**
     * Deletes a note and its audio file
     */
    fun deleteNote(noteId: String) {
        ensureCacheLoaded()
        val noteToDelete = cachedNotes?.find { it.id == noteId }

        if (noteToDelete != null) {
            // Delete the audio file
            deleteAudioFile(noteToDelete.audioFileName)

            // Remove from cache
            cachedNotes?.removeAll { it.id == noteId }

            // Save updated cache
            saveCache()

            Log.d("NotePadManager", "Note deleted: ${noteToDelete.title}")
        }
    }
    
    /**
     * Updates the content of an existing note
     */
    fun updateNoteContent(noteId: String, newContent: String) {
        ensureCacheLoaded()
        val noteIndex = cachedNotes?.indexOfFirst { it.id == noteId } ?: -1

        if (noteIndex != -1 && cachedNotes != null) {
            val updatedNote = cachedNotes!![noteIndex].copy(content = newContent)
            cachedNotes!![noteIndex] = updatedNote

            // Save updated cache
            saveCache()

            Log.d("NotePadManager", "Note content updated: ${updatedNote.title}")
        }
    }
    
    /**
     * Updates the original transcript of an existing note
     */
    fun updateNoteOriginalTranscript(noteId: String, originalTranscript: String) {
        ensureCacheLoaded()
        val noteIndex = cachedNotes?.indexOfFirst { it.id == noteId } ?: -1

        if (noteIndex != -1 && cachedNotes != null) {
            val updatedNote = cachedNotes!![noteIndex].copy(originalTranscript = originalTranscript)
            cachedNotes!![noteIndex] = updatedNote

            // Save updated cache
            saveCache()

            Log.d("NotePadManager", "Note original transcript updated: ${updatedNote.title}")
        }
    }
    
    /**
     * Updates the AI-processed version of an existing note
     */
    fun updateNoteAiProcessed(noteId: String, aiProcessed: String) {
        ensureCacheLoaded()
        val noteIndex = cachedNotes?.indexOfFirst { it.id == noteId } ?: -1

        if (noteIndex != -1 && cachedNotes != null) {
            val updatedNote = cachedNotes!![noteIndex].copy(aiProcessed = aiProcessed)
            cachedNotes!![noteIndex] = updatedNote

            // Save updated cache
            saveCache()

            Log.d("NotePadManager", "Note AI-processed version updated: ${updatedNote.title}")
        }
    }

    /**
     * Updates the AI-generated title of an existing note
     */
    fun updateNoteAiGeneratedTitle(noteId: String, aiGeneratedTitle: String) {
        ensureCacheLoaded()
        val noteIndex = cachedNotes?.indexOfFirst { it.id == noteId } ?: -1

        if (noteIndex != -1 && cachedNotes != null) {
            val updatedNote = cachedNotes!![noteIndex].copy(aiGeneratedTitle = aiGeneratedTitle)
            cachedNotes!![noteIndex] = updatedNote

            // Save updated cache
            saveCache()

            Log.d("NotePadManager", "Note AI-generated title updated: ${updatedNote.getDisplayTitle()}")
        }
    }
    
    /**
     * Generates a unique audio filename for a new note
     */
    fun generateNoteAudioFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "${NOTE_AUDIO_PREFIX}${timestamp}.m4a"
    }
    
    /**
     * Gets the public WonderWhisper directory for storing note audio files
     */
    fun getNotesAudioDirectory(): File {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val wonderWhisperDir = File(downloadsDir, "WonderWhisper")
        
        if (!wonderWhisperDir.exists()) {
            wonderWhisperDir.mkdirs()
        }
        
        return wonderWhisperDir
    }
    
    /**
     * Deletes an audio file from the notes directory
     */
    private fun deleteAudioFile(audioFileName: String) {
        try {
            val audioFile = File(getNotesAudioDirectory(), audioFileName)
            if (audioFile.exists()) {
                audioFile.delete()
                Log.d("NotePadManager", "Audio file deleted: $audioFileName")
            }
        } catch (e: Exception) {
            Log.e("NotePadManager", "Error deleting audio file: $audioFileName", e)
        }
    }
    
    /**
     * Cleans up old notes and audio files (optional maintenance)
     */
    fun cleanupOldNotes(maxNotes: Int = 100) {
        ensureCacheLoaded()

        if ((cachedNotes?.size ?: 0) > maxNotes) {
            // Delete old notes and their audio files
            val notesToDelete = cachedNotes?.drop(maxNotes) ?: emptyList()
            notesToDelete.forEach { note ->
                deleteAudioFile(note.audioFileName)
            }

            // Keep only the most recent notes in cache
            cachedNotes = cachedNotes?.take(maxNotes)?.toMutableList()

            // Save updated cache
            saveCache()

            Log.d("NotePadManager", "Cleaned up ${notesToDelete.size} old notes")
        }
    }

    /**
     * Regenerates AI titles for all notes that don't have them
     */
    suspend fun regenerateAiTitlesForExistingNotes(context: Context) {
        ensureCacheLoaded()
        val notesNeedingTitles = cachedNotes?.filter { it.aiGeneratedTitle.isNullOrBlank() } ?: emptyList()

        if (notesNeedingTitles.isEmpty()) {
            Log.d("NotePadManager", "No notes need title regeneration")
            return
        }

        Log.d("NotePadManager", "Regenerating AI titles for ${notesNeedingTitles.size} notes")

        for (note in notesNeedingTitles) {
            try {
                val contentForTitle = note.aiProcessed ?: note.originalTranscript ?: note.content

                if (contentForTitle.isNotBlank()) {
                    // Use the same title generation logic from BubbleOverlayService
                    val aiGeneratedTitle = generateNoteTitleForContent(contentForTitle, context)

                    if (aiGeneratedTitle.isNotBlank()) {
                        updateNoteAiGeneratedTitle(note.id, aiGeneratedTitle)
                        Log.d("NotePadManager", "Regenerated title for note ${note.id}: $aiGeneratedTitle")
                    }
                }
            } catch (e: Exception) {
                Log.e("NotePadManager", "Error regenerating title for note ${note.id}", e)
            }
        }

        Log.d("NotePadManager", "Completed AI title regeneration for existing notes")
    }

    private suspend fun generateNoteTitleForContent(content: String, context: Context): String {
        return try {
            val settingsManager = SettingsManager(context)
            val aiModel = settingsManager.getStringSetting("ai_model", "mistral-saba-24b")

            // Clean and prepare content for title generation
            val cleanContent = content.trim()
                .replace(Regex("\\s+"), " ") // Normalize whitespace
                .take(1000) // Limit content length for processing

            if (cleanContent.isBlank()) {
                // Fallback to timestamp-based title for empty content
                val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                return "Empty Note $timestamp"
            }

            // Use a more specific prompt to generate a better title
            val prompt = """Analyze the following note content and generate a short, descriptive title (maximum 6 words) that captures the main topic or subject.

Content: "$cleanContent"

Requirements:
- Title should be 3-6 words maximum
- Focus on the core topic or subject matter
- Use action verbs or descriptive nouns
- Avoid generic words like "Note" or "Meeting"
- Make it specific and meaningful

Examples:
- "Quarterly Sales Strategy Discussion" (for sales meeting notes)
- "Recipe for Chocolate Chip Cookies" (for cooking instructions)
- "Project Timeline and Milestones" (for project planning)
- "Customer Feedback Analysis Report" (for feedback analysis)

Generate only the title, nothing else:"""

            // Use the AI processing manager to generate the title
            val networkManager = com.slumdog88.dictationkeyboardai.network.NetworkManager()
            val secureApiKeyManager = com.slumdog88.dictationkeyboardai.SecureApiKeyManager.getInstance(context)
            val aiProcessingManager = AIProcessingManager(context, networkManager, settingsManager, secureApiKeyManager)
            val titleResult = aiProcessingManager.processWithAI(prompt, "", "", "", false) ?: ""

            // Clean up the title and ensure it's not too long
            val cleanTitle = titleResult.trim()
                .replace("\"", "")
                .replace("Title:", "")
                .replace("title:", "")
                .replace(Regex("^[-\\s]*"), "") // Remove leading dashes or spaces
                .replace(Regex("[-\\s]*$"), "") // Remove trailing dashes or spaces
                .trim()

            // Validate the generated title
            if (cleanTitle.isNotEmpty() && cleanTitle.length <= 60 && cleanTitle.split("\\s+".toRegex()).size <= 8) {
                Log.d("NotePadManager", "AI generated title: '$cleanTitle'")
                cleanTitle
            } else {
                // Create a fallback title based on content analysis
                createFallbackTitle(cleanContent)
            }

        } catch (e: Exception) {
            Log.e("NotePadManager", "Error generating note title", e)
            // Create a fallback title based on content analysis
            createFallbackTitle(content)
        }
    }

    private fun createFallbackTitle(content: String): String {
        return try {
            val cleanContent = content.trim()

            // Extract first meaningful sentence or phrase
            val sentences = cleanContent.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
            val firstSentence = sentences.firstOrNull()?.trim()

            if (firstSentence != null && firstSentence.length > 10) {
                // Take first 4-5 words from the first sentence
                val words = firstSentence.split("\\s+".toRegex())
                    .filter { it.isNotBlank() }
                    .take(5)

                if (words.size >= 2) {
                    // Capitalize first letter of each word
                    val title = words.joinToString(" ") { word ->
                        word.lowercase().replaceFirstChar { it.uppercase() }
                    }

                    if (title.length <= 50) {
                        return title
                    }
                }
            }

            // Fallback to timestamp-based title
            val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            "Note $timestamp"

        } catch (e: Exception) {
            // Final fallback
            val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            "Note $timestamp"
        }
    }
}