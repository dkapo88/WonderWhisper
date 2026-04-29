package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.padding
import com.slumdog88.dictationkeyboardai.ui.screens.VocabularyScreenDM

class VocabularyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        
        // Load existing vocabulary and custom spelling, with helpful defaults
        val defaultVocabulary = "WonderWhisper, ChatGPT, Groq, Anthropic, AssemblyAI"
        val defaultCustomSpelling = "wonder whisper = WonderWhisper\nopen a i = OpenAI"
        
        val currentVocabulary = prefs.getString("custom_vocabulary", "")
        val currentCustomSpelling = prefs.getString("custom_spelling", "")
        
        val initialVocabulary = if (currentVocabulary.isNullOrBlank()) defaultVocabulary else currentVocabulary
        val initialCustomSpelling = if (currentCustomSpelling.isNullOrBlank()) defaultCustomSpelling else currentCustomSpelling
        
        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                            title = "Custom Vocabulary",
                            onBack = { finish() }
                        )
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    ) {
                        VocabularyScreenDM(
                            onSave = { vocabularyText, customSpellingText ->
                                prefs.edit()
                                    .putString("custom_vocabulary", vocabularyText)
                                    .putString("custom_spelling", customSpellingText)
                                    .apply()
                                Log.d("VocabularyActivity", "Custom vocabulary and spelling saved")
                                Log.d("VocabularyActivity", "Vocabulary: '$vocabularyText'")
                                Log.d("VocabularyActivity", "Custom spelling: '$customSpellingText'")
                                finish()
                            },
                            onBack = { finish() },
                            initialVocabulary = initialVocabulary,
                            initialCustomSpelling = initialCustomSpelling
                        )
                    }
                }
            }
        }
    }
} 