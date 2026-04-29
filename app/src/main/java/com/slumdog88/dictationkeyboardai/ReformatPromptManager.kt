package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager class for handling reformat prompts storage and retrieval
 */
class ReformatPromptManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val USER_PROMPTS_KEY = "user_reformat_prompts"
    }
    
    /**
     * Gets all available reformat prompts (default + user-created)
     * User-created prompts are prioritized at the top for easier access
     */
    fun getAllPrompts(): List<ReformatPrompt> {
        val defaultPrompts = ReformatPrompt.getDefaultPrompts()
        val userPrompts = getUserPrompts()

        // Sort so user prompts appear first (isDefault = false comes before isDefault = true)
        return (defaultPrompts + userPrompts).sortedBy { it.isDefault }
    }
    
    /**
     * Gets only the default reformat prompts
     */
    fun getDefaultPrompts(): List<ReformatPrompt> {
        return ReformatPrompt.getDefaultPrompts()
    }
    
    /**
     * Gets user-created reformat prompts
     */
    fun getUserPrompts(): List<ReformatPrompt> {
        val promptsJson = prefs.getString(USER_PROMPTS_KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<ReformatPrompt>>() {}.type
            gson.fromJson(promptsJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ReformatPromptManager", "Error parsing user prompts from storage", e)
            emptyList()
        }
    }
    
    /**
     * Saves a user-created reformat prompt
     */
    fun saveUserPrompt(prompt: ReformatPrompt) {
        val userPrompts = getUserPrompts().toMutableList()
        
        // Remove existing prompt with same ID if present
        userPrompts.removeAll { it.id == prompt.id }
        
        // Add the new prompt
        userPrompts.add(prompt)
        
        // Save to preferences
        val promptsJson = gson.toJson(userPrompts)
        prefs.edit().putString(USER_PROMPTS_KEY, promptsJson).apply()
        
        Log.d("ReformatPromptManager", "User prompt saved: ${prompt.name}")
    }
    
    /**
     * Deletes a user-created reformat prompt
     */
    fun deleteUserPrompt(promptId: String) {
        val userPrompts = getUserPrompts().toMutableList()
        val removedPrompt = userPrompts.find { it.id == promptId }
        
        if (removedPrompt != null) {
            userPrompts.removeAll { it.id == promptId }
            
            // Save updated list
            val promptsJson = gson.toJson(userPrompts)
            prefs.edit().putString(USER_PROMPTS_KEY, promptsJson).apply()
            
            Log.d("ReformatPromptManager", "User prompt deleted: ${removedPrompt.name}")
        }
    }
    
    /**
     * Gets a specific prompt by ID
     */
    fun getPromptById(promptId: String): ReformatPrompt? {
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
            promptText.length > 2000 -> "Prompt text must be less than 2000 characters"
            else -> null // Valid
        }
    }
}