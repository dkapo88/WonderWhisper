package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import com.slumdog88.dictationkeyboardai.ui.screens.AiPromptScreen
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme

class AiPromptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                            title = "AI Prompts & Settings",
                            onBack = { finish() }
                        )
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    ) {
                        AiPromptScreen(
                            onBackClick = { finish() }
                        )
                    }
                }
            }
        }
    }
}