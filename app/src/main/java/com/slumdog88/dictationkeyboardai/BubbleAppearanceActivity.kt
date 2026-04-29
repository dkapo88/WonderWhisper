package com.slumdog88.dictationkeyboardai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.slumdog88.dictationkeyboardai.ui.screens.BubbleAppearanceScreen
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme

class BubbleAppearanceActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        settingsManager = SettingsManager(this)
        
        setContent {
            AppTheme {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                            title = "Bubble Appearance",
                            onBack = { finish() }
                        )
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    ) {
                        BubbleAppearanceScreen()
                    }
                }
            }
        }
    }

}
