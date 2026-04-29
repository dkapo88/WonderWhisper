package com.slumdog88.dictationkeyboardai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import com.slumdog88.dictationkeyboardai.ui.screens.AccessibilityDisclosureScreenDM

class AccessibilityDisclosureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                            title = "Accessibility",
                            onBack = { finish() }
                        )
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    ) {
                        AccessibilityDisclosureScreenDM(
                            onBack = {
                                finish()
                            },
                            onPrivacyPolicy = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://your-website.com/privacy-policy"))
                                startActivity(intent)
                            },
                            onUnderstandAndAgree = {
                                val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                                prefs.edit().putBoolean("accessibility_disclosure_shown", true).apply()
                                
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
} 