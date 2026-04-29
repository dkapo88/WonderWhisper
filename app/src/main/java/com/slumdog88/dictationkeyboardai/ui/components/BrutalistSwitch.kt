package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.HapticUtils

@Composable
fun BrutalistSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    
    // Animation for thumb position
    val thumbPosition by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumb_position"
    )
    
    // Animation for track color
    val trackColor by animateColorAsState(
        targetValue = if (checked) accentColor.copy(alpha = 0.3f) else Color(0xFF1F1F1F),
        animationSpec = tween(200),
        label = "track_color"
    )
    
    // Animation for thumb color
    val thumbColor by animateColorAsState(
        targetValue = if (checked) accentColor else Color(0xFF666666),
        animationSpec = tween(200),
        label = "thumb_color"
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (label.isNotEmpty()) Arrangement.SpaceBetween else Arrangement.Center
    ) {
        // Label text
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onBackground else Color(0xFF666666),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.2.sp
                ),
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        // Switch container with brutalist styling
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled
                ) {
                    HapticUtils.performHapticFeedback(context)
                    onCheckedChange(!checked)
                }
        ) {
            // Multi-layer shadow system
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .background(
                        if (checked) accentColor.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 2.dp, y = 2.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
            )
            
            // Main track
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color.White.copy(alpha = 0.2f),
                        spotColor = if (checked) accentColor.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.2f)
                    )
                    .background(trackColor, RoundedCornerShape(12.dp))
                    .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                // Thumb
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(
                            x = (24.dp * thumbPosition)
                        )
                        .align(Alignment.CenterStart)
                ) {
                    // Thumb shadow
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 2.dp, y = 2.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                    )
                    
                    // Main thumb
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(8.dp),
                                ambientColor = Color.White.copy(alpha = 0.3f),
                                spotColor = thumbColor.copy(alpha = 0.6f)
                            )
                            .background(thumbColor, RoundedCornerShape(8.dp))
                            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}
