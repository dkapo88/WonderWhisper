package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF1BE7FF), // Electric Cyan (Accent/Info)
    content: @Composable () -> Unit
) {
    val backgroundColor = Color(0xFF1E1E1E) // Dark gray background
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.White.copy(alpha = 0.2f),
                spotColor = accentColor
            )
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(3.dp, accentColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun BrutalCardTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1BE7FF) // Electric Cyan (Accent/Info)
        ),
        modifier = modifier.padding(bottom = 12.dp)
    )
}