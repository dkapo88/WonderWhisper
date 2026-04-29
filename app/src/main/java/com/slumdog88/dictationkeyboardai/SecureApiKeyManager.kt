package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure API key manager using encrypted storage
 * Replaces plain text SharedPreferences for API key storage
 */
class SecureApiKeyManager private constructor(private val context: Context) {
    
    companion object {
        private const val ENCRYPTED_PREFS_NAME = "secure_api_keys"
        private const val TAG = "SecureApiKeyManager"
        
        @Volatile
        private var INSTANCE: SecureApiKeyManager? = null
        
        fun getInstance(context: Context): SecureApiKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureApiKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedSharedPreferences()
    }
    
    private fun createEncryptedSharedPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to create encrypted preferences, falling back to regular SharedPreferences", e)
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences("secure_api_keys_fallback", Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Store an API key securely
     * Uses commit() instead of apply() to ensure the key is persisted
     * before returning - prevents race conditions with immediate usage.
     */
    fun storeApiKey(keyName: String, keyValue: String) {
        try {
            val success = encryptedPrefs.edit()
                .putString(keyName, keyValue)
                .commit()  // Synchronous write - ensures key is available immediately
            if (success) {
                SecureLogger.d(TAG, "Successfully stored API key: $keyName")
            } else {
                SecureLogger.w(TAG, "API key commit returned false: $keyName")
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to store API key: $keyName", e)
        }
    }
    
    /**
     * Retrieve an API key securely
     */
    fun getApiKey(keyName: String): String? {
        return try {
            val key = encryptedPrefs.getString(keyName, null)
            if (key != null) {
                SecureLogger.d(TAG, "Successfully retrieved API key: $keyName")
            }
            key
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to retrieve API key: $keyName", e)
            null
        }
    }
    
    /**
     * Check if an API key exists and is not empty
     */
    fun hasApiKey(keyName: String): Boolean {
        val key = getApiKey(keyName)
        return !key.isNullOrBlank()
    }
    
    /**
     * Remove an API key
     */
    fun removeApiKey(keyName: String) {
        try {
            encryptedPrefs.edit()
                .remove(keyName)
                .apply()
            SecureLogger.d(TAG, "Successfully removed API key: $keyName")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to remove API key: $keyName", e)
        }
    }
    
    /**
     * Clear all API keys
     */
    fun clearAllApiKeys() {
        try {
            encryptedPrefs.edit()
                .clear()
                .apply()
            SecureLogger.d(TAG, "Successfully cleared all API keys")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to clear API keys", e)
        }
    }
    
    /**
     * Migrate existing plain text API keys to encrypted storage
     * Should be called once during app upgrade
     */
    fun migrateFromPlainTextStorage() {
        try {
            val regularPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val apiKeys = mapOf(
                "openai_api_key" to regularPrefs.getString("openai_api_key", ""),
                "elevenlabs_api_key" to regularPrefs.getString("elevenlabs_api_key", ""),
                "groq_api_key" to regularPrefs.getString("groq_api_key", ""),
                "google_api_key" to regularPrefs.getString("google_api_key", ""),
                "assemblyai_api_key" to regularPrefs.getString("assemblyai_api_key", ""),
                "anthropic_api_key" to regularPrefs.getString("anthropic_api_key", ""),
                "mistral_api_key" to regularPrefs.getString("mistral_api_key", ""),
                "openrouter_api_key" to regularPrefs.getString("openrouter_api_key", ""),
                "cerebras_api_key" to regularPrefs.getString("cerebras_api_key", ""),
                "soniox_api_key" to regularPrefs.getString("soniox_api_key", "")
            )
            
            SecureLogger.d(TAG, "Starting migration. Found keys: ${apiKeys.filter { !it.value.isNullOrBlank() }.keys}")
            
            var migratedCount = 0
            apiKeys.forEach { (keyName, keyValue) ->
                if (!keyValue.isNullOrBlank()) {
                    SecureLogger.d(TAG, "Migrating key: $keyName (length: ${keyValue.length})")
                    storeApiKey(keyName, keyValue)
                    migratedCount++
                } else {
                    SecureLogger.d(TAG, "Skipping empty key: $keyName")
                }
            }
            
            if (migratedCount > 0) {
                // Clear the old plain text keys after successful migration
                val editor = regularPrefs.edit()
                apiKeys.keys.forEach { keyName ->
                    editor.remove(keyName)
                }
                editor.apply()
                
                SecureLogger.i(TAG, "Successfully migrated $migratedCount API keys to encrypted storage")
            }
            
            // Mark migration as completed
            regularPrefs.edit()
                .putBoolean("api_keys_migration_completed", true)
                .apply()
                
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to migrate API keys from plain text storage", e)
        }
    }
    
    /**
     * Check if migration from plain text storage is needed
     */
    fun isMigrationNeeded(): Boolean {
        val regularPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val migrationCompleted = regularPrefs.getBoolean("api_keys_migration_completed", false)
        SecureLogger.d(TAG, "Migration needed check: migration_completed=$migrationCompleted")
        return !migrationCompleted
    }
    
    /**
     * Debug method to get current API key storage status
     */
    fun getDebugStatus(): String {
        val sb = StringBuilder()
        sb.append("=== SecureApiKeyManager Debug ===\n")
        
        val regularPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val migrationCompleted = regularPrefs.getBoolean("api_keys_migration_completed", false)
        sb.append("Migration completed: $migrationCompleted\n")
        
        val apiKeyNames = listOf(
            "openai_api_key",
            "elevenlabs_api_key",
            "groq_api_key",
            "google_api_key",
            "assemblyai_api_key",
            "anthropic_api_key",
            "mistral_api_key",
            "openrouter_api_key",
            "cerebras_api_key",
            "soniox_api_key"
        )
        
        sb.append("\nPlain text storage:\n")
        apiKeyNames.forEach { keyName ->
            val plainTextKey = regularPrefs.getString(keyName, "")
            sb.append("- $keyName: ${if (plainTextKey.isNullOrBlank()) "EMPTY" else "SET (${plainTextKey.length} chars)"}\n")
        }
        
        sb.append("\nEncrypted storage:\n")
        apiKeyNames.forEach { keyName ->
            val encryptedKey = getApiKey(keyName)
            sb.append("- $keyName: ${if (encryptedKey.isNullOrBlank()) "EMPTY" else "SET (${encryptedKey.length} chars)"}\n")
        }
        
        return sb.toString()
    }
}