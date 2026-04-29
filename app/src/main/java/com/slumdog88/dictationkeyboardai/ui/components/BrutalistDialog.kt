package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun BrutalistDialog(
    title: String,
    message: String? = null,
    inputField: @Composable (() -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "CONFIRM",
    dismissText: String = "CANCEL",
    accentColor: Color,
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false
) {
    // Neo-Brutalist colors
    val nb_base = Color(0xFF1A1A1A)
    val nb_white = Color(0xFFFFFFFF)
    val nb_charcoal = Color(0xFF2B2B2B)
    
    val confirmColor = if (isDestructive) Color(0xFFFF4444) else accentColor
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Colored shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 12.dp, y = 12.dp)
                    .background(
                        accentColor.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
            )
            
            // Black shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 6.dp, y = 6.dp)
                    .background(
                        Color.Black,
                        RoundedCornerShape(16.dp)
                    )
            )
            
            // Main dialog content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = nb_white.copy(alpha = 0.2f),
                        spotColor = accentColor.copy(alpha = 0.4f)
                    )
                    .border(
                        width = 3.dp,
                        color = nb_white.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = nb_charcoal
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = title.uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Message (optional)
                    message?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = nb_white,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }
                    
                    // Input field (optional)
                    inputField?.let { input ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        ) {
                            input()
                        }
                    }
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        BrutalistDialogButton(
                            text = dismissText,
                            onClick = onDismiss,
                            accentColor = nb_white.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Confirm button
                        BrutalistDialogButton(
                            text = confirmText,
                            onClick = onConfirm,
                            accentColor = confirmColor,
                            enabled = confirmEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrutalistDialogButton(
    text: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val nb_charcoal = Color(0xFF2B2B2B)
    val nb_white = Color(0xFFFFFFFF)
    
    Box(modifier = modifier) {
        // Shadow layers
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(
                    accentColor.copy(alpha = if (enabled) 0.3f else 0.1f),
                    RoundedCornerShape(8.dp)
                )
        )
        
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .background(
                    Color.Black.copy(alpha = if (enabled) 1f else 0.3f),
                    RoundedCornerShape(8.dp)
                )
        )
        
        // Main button
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(8.dp),
                    ambientColor = nb_white.copy(alpha = 0.1f),
                    spotColor = accentColor.copy(alpha = 0.2f)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = nb_charcoal,
                contentColor = if (enabled) accentColor else accentColor.copy(alpha = 0.5f),
                disabledContainerColor = nb_charcoal.copy(alpha = 0.5f),
                disabledContentColor = accentColor.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.6.sp
            )
        }
    }
}