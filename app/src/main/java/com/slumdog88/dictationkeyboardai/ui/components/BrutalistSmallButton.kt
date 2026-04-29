package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Performance fix: Pre-computed shape to avoid repeated allocations
private val SmallButtonShape = RoundedCornerShape(8.dp)

@Composable
fun BrutalistSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val accentColor = Color(0xFF1BE7FF) // Brand: Electric Cyan (Accent/Info)
    val backgroundColor = if (isSelected) Color.Black else accentColor
    val textColor = if (isSelected) accentColor else Color.Black

    Box(
        modifier = modifier
            .height(48.dp)
    ) {
        // Performance fix: Single shadow layer (removed GPU-intensive shadow() modifier)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, SmallButtonShape)
        )

        // Main button
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor, SmallButtonShape)
                .border(2.dp, accentColor, SmallButtonShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.3.sp
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
