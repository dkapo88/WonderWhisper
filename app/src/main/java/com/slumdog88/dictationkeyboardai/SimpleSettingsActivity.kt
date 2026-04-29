package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import kotlin.math.max
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import kotlin.math.sin
import com.slumdog88.dictationkeyboardai.ui.theme.ThemeManager
import com.slumdog88.dictationkeyboardai.ui.theme.AppThemeMode
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardLayoutRepository

class SimpleSettingsActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Use the same SharedPreferences file as Pro mode for synchronization
        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                com.slumdog88.dictationkeyboardai.ui.screens.SimpleSettingsScreenDM(
                    sharedPreferences = sharedPreferences,
                    onNavigateToNotepad = {
                        HapticUtils.performHapticFeedback(this)
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra("navigate_to", "notepad")
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SimpleSettingsActivity::class.java)
            context.startActivity(intent)
        }
    }
}


