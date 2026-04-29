package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.HapticUtils

@Composable
fun BrutalistDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    // Animation for dropdown arrow rotation
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrow_rotation"
    )
    
    // Animation for background color on interaction
    val backgroundColor by animateColorAsState(
        targetValue = if (expanded) Color(0xFF2B2B2B) else Color(0xFF1F1F1F),
        animationSpec = tween(200),
        label = "background_color"
    )
    
    Column(modifier = modifier) {
        // Label
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onBackground else Color(0xFF666666),
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.2.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Main dropdown button with Neo-Brutalist styling
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled
                ) {
                    HapticUtils.performHapticFeedback(context)
                    expanded = !expanded
                }
        ) {
            // Colored shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 6.dp, y = 6.dp)
                    .background(accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            )
            
            // Black shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
            )
            
            // Main dropdown container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color.White.copy(alpha = 0.2f),
                        spotColor = accentColor.copy(alpha = 0.3f)
                    )
                    .background(backgroundColor, RoundedCornerShape(12.dp))
                    .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (options.isNotEmpty() && selectedIndex in 0 until options.size) {
                            options[selectedIndex]
                        } else {
                            "Select option"
                        },
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) Color.White else Color(0xFF666666),
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = if (enabled) accentColor else Color(0xFF666666),
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(arrowRotation)
                    )
                }
            }
        }
        
        // Animated dropdown menu that pushes content below
        AnimatedVisibility(
            visible = expanded && options.isNotEmpty(),
            enter = expandVertically(
                animationSpec = tween(300),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(300),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(300))
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                // Colored shadow layer for dropdown menu
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 6.dp, y = 6.dp)
                        .background(accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                )
                
                // Black shadow layer for dropdown menu
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 3.dp, y = 3.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                )
                
                // Dropdown content container
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color.White.copy(alpha = 0.2f),
                            spotColor = accentColor.copy(alpha = 0.4f)
                        )
                        .background(Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
                        .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
                ) {
                    itemsIndexed(options) { index, option ->
                        val isSelected = index == selectedIndex
                        val itemBackgroundColor by animateColorAsState(
                            targetValue = if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                            animationSpec = tween(150),
                            label = "item_background"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(itemBackgroundColor)
                                .clickable {
                                    HapticUtils.performHapticFeedback(context)
                                    onSelectionChange(index)
                                    expanded = false
                                }
                                .padding(16.dp)
                        ) {
                            Text(
                                text = option,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else Color.White,
                                    fontFamily = FontFamily.SansSerif
                                )
                            )
                        }
                    }
                }
                }
            }
        }
    }
}