package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM
import com.slumdog88.dictationkeyboardai.ui.screens.StreamingSettingsScreenDM
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import com.slumdog88.dictationkeyboardai.utils.SettingsManager

class StreamingSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsManager = SettingsManager(this)

        setContent {
            AppTheme {
                StreamingSettingsScaffold(
                    settingsManager = settingsManager,
                    onBack = {
                        HapticUtils.performHapticFeedback(this)
                        finish()
                    }
                )
            }
        }
    }

    @Composable
    private fun StreamingSettingsScaffold(
        settingsManager: SettingsManager,
        onBack: () -> Unit
    ) {
        Scaffold(
            topBar = {
                AppTopBarDM(
                    title = "Streaming Detection",
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                StreamingSettingsScreenDM(settingsManager)
            }
        }
    }
}
