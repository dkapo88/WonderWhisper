package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class DebugActivity : ComponentActivity() {
    
    private lateinit var secureApiKeyManager: SecureApiKeyManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize secure API key manager
        secureApiKeyManager = SecureApiKeyManager.getInstance(this)
        
        setContent {
            DebugScreen(
                onFinish = { finish() },
                onStartBubbleService = {
                    val intent = Intent(this, BubbleOverlayService::class.java)
                    startService(intent)
                },
                onClearPreferences = {
                    val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                },
                onResetDisclosure = {
                    val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("accessibility_disclosure_shown", false).apply()
                },
                onRequestAudioPermission = { requestAudioPermission() },
                onRequestOverlayPermission = { requestOverlayPermission() },
                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                onRequestBatteryOptimizationDisable = { requestBatteryOptimizationDisable() },
                onRefreshStatus = { updateDebugStatus(this, secureApiKeyManager) }
            )
        }
    }

    private fun requestAudioPermission() {
        androidx.core.app.ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            1001
        )
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please enable 'Display over other apps' for WonderWhisper", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable 'WonderWhisper' in accessibility settings", Toast.LENGTH_LONG).show()
    }

    private fun requestBatteryOptimizationDisable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, 
                    "Please select 'Allow' to disable battery optimization for WonderWhisper\n\nThis prevents Android from killing the accessibility service!", 
                    Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // Fallback to general battery optimization settings
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(fallbackIntent)
                    Toast.makeText(this, 
                        "Find 'WonderWhisper' in the list and disable battery optimization", 
                        Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Toast.makeText(this, "Please disable battery optimization in Settings > Battery", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Status will be refreshed automatically when Compose recomposes
        // The DebugScreen handles status updates via its state management
    }
}
