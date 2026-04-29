package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.R

// Performance fix: Pre-computed shape and color to avoid repeated allocations
private val SettingsCardShape = RoundedCornerShape(16.dp)
private val SurfaceColor = Color(0xFF1F1F1F) // nb_charcoal

@Composable
fun BrutalistSettingsCard(
    accentColor: Color = colorResource(id = R.color.nb_cyan),
    content: @Composable () -> Unit
) {
    // Performance fix: Pre-compute shadow color
    val shadowColor = remember(accentColor) { accentColor.copy(alpha = 0.5f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Performance fix: Simplified to single shadow layer (reduced from 2 layers + shadow modifier)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(shadowColor, SettingsCardShape)
        )

        // Main card (removed GPU-intensive shadow() modifier)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceColor, SettingsCardShape)
                .border(2.dp, Color.Black, SettingsCardShape)
                .padding(20.dp)
        ) {
            content()
        }
    }
}