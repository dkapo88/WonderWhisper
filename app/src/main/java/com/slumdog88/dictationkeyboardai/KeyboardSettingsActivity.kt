package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.slumdog88.dictationkeyboardai.ui.screens.KeyboardSettingsScreenDM

class KeyboardSettingsActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                KeyboardSettingsScreenDM(
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, KeyboardSettingsActivity::class.java)
            context.startActivity(intent)
        }
    }
}

