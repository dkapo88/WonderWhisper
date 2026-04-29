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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.R

// Performance fix: Pre-computed shape to avoid repeated allocations
private val ButtonShape = RoundedCornerShape(16.dp)

@Composable
fun BrutalistActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = colorResource(id = R.color.nb_pink)
) {
    // Performance fix: Pre-compute Color.copy() to avoid allocations on recomposition
    val shadowColor = remember(accentColor) { accentColor.copy(alpha = 0.5f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Performance fix: Simplified to single shadow layer (reduced from 3 layers + shadow modifier)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(shadowColor, ButtonShape)
        )

        // Main button (removed GPU-intensive shadow() modifier)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(accentColor, ButtonShape)
                .border(2.dp, Color.Black, ButtonShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}