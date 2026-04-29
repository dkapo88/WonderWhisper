package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.slumdog88.dictationkeyboardai.ui.screens.ApiKeysScreen

class ApiKeysActivity : ComponentActivity() {
    
    private lateinit var secureApiKeyManager: SecureApiKeyManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize secure API key manager
        secureApiKeyManager = SecureApiKeyManager.getInstance(this)
        
        // Perform migration if needed
        if (secureApiKeyManager.isMigrationNeeded()) {
            Log.i("ApiKeysActivity", "Migrating API keys to encrypted storage")
            secureApiKeyManager.migrateFromPlainTextStorage()
            Toast.makeText(this, "API keys migrated to secure storage", Toast.LENGTH_SHORT).show()
        }

        // Load existing API keys from secure storage
        val initialKeys = mapOf(
            "openai_api_key" to (secureApiKeyManager.getApiKey("openai_api_key") ?: ""),
            "elevenlabs_api_key" to (secureApiKeyManager.getApiKey("elevenlabs_api_key") ?: ""),
            "groq_api_key" to (secureApiKeyManager.getApiKey("groq_api_key") ?: ""),
            "google_api_key" to (secureApiKeyManager.getApiKey("google_api_key") ?: ""),
            "deepgram_api_key" to (secureApiKeyManager.getApiKey("deepgram_api_key") ?: ""),
            "assemblyai_api_key" to (secureApiKeyManager.getApiKey("assemblyai_api_key") ?: ""),
            "anthropic_api_key" to (secureApiKeyManager.getApiKey("anthropic_api_key") ?: ""),
            "mistral_api_key" to (secureApiKeyManager.getApiKey("mistral_api_key") ?: ""),
            "openrouter_api_key" to (secureApiKeyManager.getApiKey("openrouter_api_key") ?: ""),
            "cerebras_api_key" to (secureApiKeyManager.getApiKey("cerebras_api_key") ?: ""),
            "soniox_api_key" to (secureApiKeyManager.getApiKey("soniox_api_key") ?: "")
        )
        
        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                            title = "API Keys",
                            onBack = {
                                HapticUtils.performHapticFeedback(this@ApiKeysActivity)
                                finish()
                            }
                        )
                    }
                ) { padding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(padding)
                    ) {
                        ApiKeysScreen(
                            onSave = { keys ->
                                HapticUtils.performHapticFeedback(this@ApiKeysActivity)
                                keys.forEach { (key, value) -> secureApiKeyManager.storeApiKey(key, value) }
                                SecureLogger.d("ApiKeysActivity", "API keys saved to encrypted storage")
                                Toast.makeText(this@ApiKeysActivity, "API keys saved securely", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onBack = {
                                HapticUtils.performHapticFeedback(this@ApiKeysActivity)
                                finish()
                            },
                            initialKeys = initialKeys
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeysTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFFFF7F00), // Orange accent for security/keys
        onPrimary = Color.Black,
        background = Color(0xFF1A1A1A), // nb_base
        onBackground = Color.White, // nb_white
        surface = Color(0xFF2B2B2B), // nb_charcoal
        onSurface = Color.White
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {
                content()
            }
        }
    )
}
