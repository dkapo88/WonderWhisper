package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager class for handling dictation prompts storage and retrieval
 */
class DictationPromptManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val USER_PROMPTS_KEY = "user_dictation_prompts"
        private const val MIGRATION_V2_COMPLETE_KEY = "dictation_prompts_migration_v2_complete"
    }
    
    /**
     * Gets all available dictation prompts (default + user-created)
     */
    fun getAllPrompts(): List<DictationPrompt> {
        // Run migration if needed
        migrateOldDefaultPromptsIfNeeded()
        
        val defaultPrompts = DictationPrompt.getDefaultPrompts()
        val userPrompts = getUserPrompts()
        
        return defaultPrompts + userPrompts
    }
    
    /**
     * Gets only the default dictation prompts
     */
    fun getDefaultPrompts(): List<DictationPrompt> {
        return DictationPrompt.getDefaultPrompts()
    }
    
    /**
     * Gets user-created dictation prompts
     */
    fun getUserPrompts(): List<DictationPrompt> {
        val promptsJson = prefs.getString(USER_PROMPTS_KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<DictationPrompt>>() {}.type
            gson.fromJson(promptsJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("DictationPromptManager", "Error parsing user prompts from storage", e)
            emptyList()
        }
    }
    
    /**
     * Saves a user-created dictation prompt
     */
    fun saveUserPrompt(prompt: DictationPrompt) {
        val userPrompts = getUserPrompts().toMutableList()
        
        // Remove existing prompt with same ID if present
        userPrompts.removeAll { it.id == prompt.id }
        
        // Add the new prompt
        userPrompts.add(prompt)
        
        // Save to preferences
        val promptsJson = gson.toJson(userPrompts)
        prefs.edit().putString(USER_PROMPTS_KEY, promptsJson).apply()
        
        Log.d("DictationPromptManager", "User prompt saved: ${prompt.name}")
    }
    
    /**
     * Deletes a user-created dictation prompt
     */
    fun deleteUserPrompt(promptId: String) {
        val userPrompts = getUserPrompts().toMutableList()
        val removedPrompt = userPrompts.find { it.id == promptId }
        
        if (removedPrompt != null) {
            userPrompts.removeAll { it.id == promptId }
            
            // Save updated list
            val promptsJson = gson.toJson(userPrompts)
            prefs.edit().putString(USER_PROMPTS_KEY, promptsJson).apply()
            
            Log.d("DictationPromptManager", "User prompt deleted: ${removedPrompt.name}")
        }
    }
    
    /**
     * Gets a specific prompt by ID
     */
    fun getPromptById(promptId: String): DictationPrompt? {
        return getAllPrompts().find { it.id == promptId }
    }
    
    /**
     * Checks if a prompt name already exists (case-insensitive)
     */
    fun isPromptNameExists(name: String): Boolean {
        return getAllPrompts().any { it.name.lowercase() == name.lowercase() }
    }
    
    /**
     * Validates a prompt name for user prompts
     */
    fun validatePromptName(name: String): String? {
        return when {
            name.isBlank() -> "Prompt name cannot be empty"
            name.length < 2 -> "Prompt name must be at least 2 characters"
            name.length > 50 -> "Prompt name must be less than 50 characters"
            isPromptNameExists(name) -> "A prompt with this name already exists"
            else -> null // Valid
        }
    }
    
    /**
     * Validates a prompt text for user prompts
     */
    fun validatePromptText(promptText: String): String? {
        return when {
            promptText.isBlank() -> "Prompt text cannot be empty"
            promptText.length < 10 -> "Prompt text must be at least 10 characters"
            else -> null // Valid
        }
    }
    
    /**
     * Renames a user-created dictation prompt
     */
    fun renameUserPrompt(promptId: String, newName: String): Boolean {
        // Validate new name
        val nameValidationError = validatePromptName(newName)
        if (nameValidationError != null) {
            Log.w("DictationPromptManager", "Cannot rename prompt: $nameValidationError")
            return false
        }
        
        val userPrompts = getUserPrompts().toMutableList()
        val promptIndex = userPrompts.indexOfFirst { it.id == promptId }
        
        if (promptIndex >= 0) {
            val oldPrompt = userPrompts[promptIndex]
            val updatedPrompt = oldPrompt.copy(name = newName)
            userPrompts[promptIndex] = updatedPrompt
            
            // Save updated list
            val promptsJson = gson.toJson(userPrompts)
            prefs.edit().putString(USER_PROMPTS_KEY, promptsJson).apply()
            
            Log.d("DictationPromptManager", "User prompt renamed from '${oldPrompt.name}' to '$newName'")
            return true
        }
        
        return false
    }
    
    /**
     * Updates the content of a user-created dictation prompt
     */
    fun updateUserPromptContent(promptId: String, newContent: String): Boolean {
        // Validate new content
        val textValidationError = validatePromptText(newContent)
        if (textValidationError != null) {
            Log.w("DictationPromptManager", "Cannot update prompt content: $textValidationError")
            return false
        }
        
        val userPrompts = getUserPrompts().toMutableList()
        val promptIndex = userPrompts.indexOfFirst { it.id == promptId }
        
        if (promptIndex >= 0) {
            val oldPrompt = userPrompts[promptIndex]
            val updatedPrompt = oldPrompt.copy(promptText = newContent)
            userPrompts[promptIndex] = updatedPrompt
            
            // Save updated list
            val promptsJson = gson.toJson(userPrompts)
            prefs.edit().putString(USER_PROMPTS_KEY, promptsJson).apply()
            
            Log.d("DictationPromptManager", "User prompt content updated: ${oldPrompt.name}")
            return true
        }
        
        return false
    }
    
    /**
     * Checks if a prompt can be deleted or renamed (i.e., it's not a default prompt)
     */
    fun canModifyPrompt(promptId: String): Boolean {
        val prompt = getPromptById(promptId)
        return prompt != null && !prompt.isDefault
    }
    
    /**
     * Creates a legacy prompt from existing stored text for migration
     */
    fun createLegacyPrompt(promptText: String): DictationPrompt {
        return DictationPrompt(
            id = "legacy_dictation_${System.currentTimeMillis()}",
            name = "Legacy Dictation",
            description = "Migrated from previous app version",
            promptText = promptText,
            isDefault = false
        )
    }
    
    /**
     * Migrates old default prompts to user prompts (example prompts that can be deleted)
     */
    private fun migrateOldDefaultPromptsIfNeeded() {
        // Check if migration has already been completed
        if (prefs.getBoolean(MIGRATION_V2_COMPLETE_KEY, false)) {
            return
        }
        
        Log.d("DictationPromptManager", "Running migration: converting old default prompts to user examples")
        
        // Get existing user prompts to avoid duplicates
        val existingUserPrompts = getUserPrompts()
        val existingIds = existingUserPrompts.map { it.id }.toSet()
        
        // Get old default prompts that should become user examples
        val oldDefaultPrompts = DictationPrompt.getOldDefaultPromptsForMigration()
        
        // Add any that don't already exist
        val promptsToAdd = oldDefaultPrompts.filter { it.id !in existingIds }
        
        if (promptsToAdd.isNotEmpty()) {
            val updatedUserPrompts = existingUserPrompts.toMutableList()
            updatedUserPrompts.addAll(promptsToAdd)
            
            // Save updated user prompts
            val promptsJson = gson.toJson(updatedUserPrompts)
            prefs.edit().putString(USER_PROMPTS_KEY, promptsJson).apply()
            
            Log.d("DictationPromptManager", "Migrated ${promptsToAdd.size} old default prompts to user examples")
        }
        
        // Mark migration as complete
        prefs.edit().putBoolean(MIGRATION_V2_COMPLETE_KEY, true).apply()
        Log.d("DictationPromptManager", "Migration v2 completed")
    }
}