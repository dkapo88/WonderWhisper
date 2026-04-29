package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import com.slumdog88.dictationkeyboardai.ui.screens.HowToGuideScreen

class HowToGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                HowToGuideScreen()
            }
        }
    }
}


