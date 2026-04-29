package com.slumdog88.dictationkeyboardai.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Context

object KeyboardSizing {
    
    // Standard Android keyboard is roughly 35-40% of screen height
    // Or about 250dp - 300dp on typical phones.
    
    @Composable
    fun calculateKeyHeight(): Dp {
        val configuration = LocalConfiguration.current
        val context = LocalContext.current
        val screenHeight = configuration.screenHeightDp.dp
        
        // Read scale factor from preferences (default 1.0)
        val prefs = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
        val scaleFactor = prefs.getFloat("keyboard_height_scale", 1.0f)
        
        // Let's aim for a target total keyboard height of ~40% of screen in portrait
        // This includes the dictation bar (64dp) and 4 rows of keys
        
        val totalTargetHeight = screenHeight * 0.40f * scaleFactor
        val keyRowsHeight = totalTargetHeight - 64.dp // Minus dictation bar
        
        // 4 rows standard
        val rawRowHeight = keyRowsHeight / 4
        
        // Clamp values to reasonable minimums and maximums
        // 35dp is very compact, 85dp is very tall
        return rawRowHeight.coerceIn(35.dp, 85.dp)
    }
}
