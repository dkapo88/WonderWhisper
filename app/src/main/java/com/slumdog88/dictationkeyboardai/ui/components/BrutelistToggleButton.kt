package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Performance fix: Pre-computed shape to avoid repeated allocations
private val ToggleButtonShape = RoundedCornerShape(12.dp)

@Composable
fun BrutelistToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground

    // Performance fix: Pre-compute shadow color with remember
    val shadowColor = remember(isSelected) {
        if (isSelected) Color(0xFFFF7F00).copy(alpha = 0.4f)
        else Color(0xFF00F5FF).copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .height(40.dp)
            .width(80.dp)
            .clickable { onClick() }
    ) {
        // Performance fix: Simplified to single shadow layer (reduced from 2 layers + shadow modifier)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(shadowColor, ToggleButtonShape)
        )

        // Main button layer (removed GPU-intensive shadow() modifier)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor, ToggleButtonShape)
                .border(2.dp, Color.Black, ToggleButtonShape)
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.8.sp,
                    lineHeight = 14.sp
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}