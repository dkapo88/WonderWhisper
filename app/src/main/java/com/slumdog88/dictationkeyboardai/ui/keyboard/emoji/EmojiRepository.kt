package com.slumdog88.dictationkeyboardai.ui.keyboard.emoji

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object EmojiRepository {
    private var allEmojis: List<EmojiItem> = emptyList()
    private var categories: List<String> = emptyList()
    private var emojisByCategory: Map<String, List<EmojiItem>> = emptyMap()
    
    private var recentEmojis: MutableList<String> = mutableListOf()
    private const val PREFS_NAME = "emoji_prefs"
    private const val KEY_RECENTS = "recent_emojis"
    private const val MAX_RECENTS = 40

    suspend fun load(context: Context) {
        if (allEmojis.isNotEmpty()) return
        
        withContext(Dispatchers.IO) {
            try {
                // Load Recents
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedRecents = prefs.getString(KEY_RECENTS, "") ?: ""
                if (savedRecents.isNotEmpty()) {
                    recentEmojis.addAll(savedRecents.split(","))
                }

                val inputStream = context.assets.open("gemoji.json")
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                reader.close()
                
                val json = Json { ignoreUnknownKeys = true }
                allEmojis = json.decodeFromString<List<EmojiItem>>(jsonString)
                
                // Filter out emojis that might not be supported or empty categories if any
                // For now, take all.
                
                // Order categories? distinct() preserves order of appearance in the list.
                // usually: Smileys, People, etc.
                categories = allEmojis.map { it.category }.distinct()
                emojisByCategory = allEmojis.groupBy { it.category }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun addRecent(emoji: String, context: Context) {
        // Remove if exists (to move to front)
        recentEmojis.remove(emoji)
        // Add to front
        recentEmojis.add(0, emoji)
        // Limit size
        if (recentEmojis.size > MAX_RECENTS) {
            recentEmojis.removeAt(recentEmojis.lastIndex)
        }
        // Save
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECENTS, recentEmojis.joinToString(",")).apply()
    }
    
    fun getRecentEmojis(): List<EmojiItem> {
        // Map strings back to items
        // Optimization: Create a map of emoji char -> item for faster lookup if needed
        // For now, simple filter/find
        return recentEmojis.mapNotNull { char ->
            allEmojis.find { it.emoji == char }
        }
    }

    fun getCategories(): List<String> = categories

    fun getEmojisByCategory(category: String): List<EmojiItem> {
        return emojisByCategory[category] ?: emptyList()
    }
    
    fun getAllEmojis(): List<EmojiItem> = allEmojis

    fun search(query: String): List<EmojiItem> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return allEmojis.filter { emoji ->
            emoji.description.contains(lowerQuery, ignoreCase = true) ||
            emoji.aliases.any { it.contains(lowerQuery, ignoreCase = true) } ||
            emoji.tags.any { it.contains(lowerQuery, ignoreCase = true) }
        }
    }
}
