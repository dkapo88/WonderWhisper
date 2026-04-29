package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.HapticUtils
import kotlin.math.roundToInt

@Composable
fun BrutalistSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    label: String = "",
    valueFormatter: (Float) -> String = { "${it.roundToInt()}" },
    minimumValue: Float? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // Animated progress for smooth transitions
    val animatedProgress by animateFloatAsState(
        targetValue = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start),
        animationSpec = tween(150),
        label = "seekbar_progress"
    )
    
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Label and value display
            if (label.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label.uppercase(),
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.3.sp
                        )
                    )
                    
                    Text(
                        text = valueFormatter(value),
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.2.sp
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // SeekBar container with brutalist styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                // Multi-layer shadow system
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 8.dp, y = 8.dp)
                        .background(accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 4.dp, y = 4.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                )
                
                // Main track
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color.White.copy(alpha = 0.2f),
                            spotColor = accentColor.copy(alpha = 0.4f)
                        )
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(12.dp))
                        .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    HapticUtils.performHapticFeedback(context)
                                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                    var newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                                    
                                    // Apply minimum value constraint if specified
                                    minimumValue?.let { min ->
                                        newValue = newValue.coerceAtLeast(min)
                                    }
                                    
                                    onValueChange(newValue)
                                },
                                onDrag = { change, _ ->
                                    val newProgress = ((change.position.x) / size.width).coerceIn(0f, 1f)
                                    var newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                                    
                                    // Apply minimum value constraint if specified
                                    minimumValue?.let { min ->
                                        newValue = newValue.coerceAtLeast(min)
                                    }
                                    
                                    onValueChange(newValue)
                                }
                            )
                        }
                ) {
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                accentColor.copy(alpha = 0.8f),
                                RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                            )
                    )
                    
                    // Thumb (handle)
                    BoxWithConstraints {
                        val trackWidth = maxWidth - 32.dp
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .offset(
                                    x = (trackWidth * animatedProgress)
                                )
                                .align(Alignment.CenterStart)
                        ) {
                            // Thumb shadow
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .offset(x = 3.dp, y = 3.dp)
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                            )
                            
                            // Main thumb
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(8.dp),
                                        ambientColor = Color.White.copy(alpha = 0.3f),
                                        spotColor = accentColor
                                    )
                                    .background(accentColor, RoundedCornerShape(8.dp))
                                    .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
