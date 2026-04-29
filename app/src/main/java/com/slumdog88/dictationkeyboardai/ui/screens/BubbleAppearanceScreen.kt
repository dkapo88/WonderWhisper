package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.BubbleOverlayService
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.ui.components.*
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BubbleAppearanceScreen() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // State management
    var opacity by remember { mutableFloatStateOf(settingsManager.getBubbleOpacity().toFloat()) }
    var size by remember { mutableFloatStateOf(settingsManager.getBubbleSize().toFloat()) }
    var keyboardAwareBubble by remember { mutableStateOf(settingsManager.isKeyboardAwareBubbleEnabled()) }
    var bubbleOverlayEnabled by remember { mutableStateOf(settingsManager.isBubbleOverlayEnabled()) }
    var isApplying by remember { mutableStateOf(false) }
    var showAppliedFeedback by remember { mutableStateOf(false) }
    
    val accentColor = MaterialTheme.colorScheme.primary
    
    // Animated background based on scroll
    val scrollOffset by remember {
        derivedStateOf { scrollState.value.toFloat() }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header (Material)
            Text(
                text = "Bubble Appearance",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Opacity Section
            TokenCard {
                Column {
                    Text(
                        text = "BUBBLE OPACITY",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.3.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // M3 Slider with token colors
                    androidx.compose.material3.Slider(
                        value = opacity.coerceAtLeast(10f),
                        onValueChange = { newValue ->
                            val v = newValue.coerceAtLeast(10f) // Minimum 10% for visibility
                            opacity = v
                            settingsManager.saveBubbleOpacity(v.toInt())
                        },
                        valueRange = 0f..100f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${opacity.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Adjust how transparent the bubble appears on screen",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Size Section
            TokenCard {
                Column {
                    Text(
                        text = "BUBBLE SIZE",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.3.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // M3 Slider with token colors
                    androidx.compose.material3.Slider(
                        value = size,
                        onValueChange = { newValue ->
                            size = newValue
                            settingsManager.saveBubbleSize(newValue.toInt())
                        },
                        valueRange = 50f..150f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${size.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Adjust the size of the bubble (50% - 150%)",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Apply Changes Button
            BrutalistActionButton(
                text = if (showAppliedFeedback) "✓ APPLIED!" else if (isApplying) "APPLYING..." else "APPLY CHANGES",
                onClick = {
                    if (!isApplying) {
                        HapticUtils.performHapticFeedback(context)
                        isApplying = true
                        
                        val success = BubbleOverlayService.updateBubbleAppearanceDirect(
                            opacity.toInt(),
                            size.toInt()
                        )
                        
                        if (success) {
                            Toast.makeText(context, "Bubble appearance updated!", Toast.LENGTH_SHORT).show()
                            showAppliedFeedback = true
                            
                            // Reset feedback after delay
                            coroutineScope.launch {
                                delay(1500)
                                showAppliedFeedback = false
                            }
                        } else {
                            // Fallback to broadcast method (explicit within app per lint guidance)
                            val intent = Intent("com.slumdog88.dictationkeyboardai.UPDATE_BUBBLE_APPEARANCE")
                            intent.setPackage(context.packageName)
                            intent.putExtra("opacity", opacity.toInt())
                            intent.putExtra("size", size.toInt())
                            context.sendBroadcast(intent)
                            Toast.makeText(context, "Changes applied (restart bubble if needed)", Toast.LENGTH_LONG).show()
                        }
                        
                        isApplying = false
                    }
                },
                accentColor = accentColor
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Behavior Section
            TokenCard {
                Column {
                    Text(
                        text = "⚙️ BEHAVIOR",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.3.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Master Bubble Overlay toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bubble Overlay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Switch(
                            checked = bubbleOverlayEnabled,
                            onCheckedChange = { enabled ->
                                bubbleOverlayEnabled = enabled
                                val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("bubble_overlay_enabled", enabled).apply()
                                // Notify service to apply immediately
                                val action = if (enabled) BubbleOverlayService.ACTION_ENABLE_BUBBLE else BubbleOverlayService.ACTION_DISABLE_BUBBLE
                                context.startService(Intent(context, BubbleOverlayService::class.java).apply { this.action = action })
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Keyboard-Aware Bubble",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Switch(
                            checked = keyboardAwareBubble,
                            onCheckedChange = { enabled ->
                                keyboardAwareBubble = enabled
                                val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("keyboard_aware_bubble", enabled).apply()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Turn Bubble Overlay on/off to globally enable the floating mic. When enabled, 'Keyboard‑Aware Bubble' auto-shows the bubble when the keyboard opens. You can also toggle it from the persistent notification.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tips Section
            TokenCard {
                Column {
                    Text(
                        text = "💡 TIPS",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.3.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "• Tap 'APPLY CHANGES' to update the bubble\n• Lower opacity makes the bubble less intrusive\n• Larger size makes the bubble easier to tap\n• Settings are saved automatically",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color(0xFFCCCCCC),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Material-styled surface card for sections
@Composable
private fun TokenCard(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        shape = com.slumdog88.dictationkeyboardai.ui.theme.Radii.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}
