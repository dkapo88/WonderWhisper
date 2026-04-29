package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 10,
    placeholder: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences
    )
) {
    // Neo-Brutalist colors
    val nb_base = Color(0xFF1A1A1A)
    val nb_white = Color(0xFFFFFFFF)
    val nb_charcoal = Color(0xFF2B2B2B)
    
    var isFocused by remember { mutableStateOf(false) }
    
    // Animation for focus state
    val animatedBorderWidth by animateFloatAsState(
        targetValue = if (isFocused) 4f else 2f,
        animationSpec = tween(200),
        label = "border width"
    )
    
    val animatedShadowOffset by animateDpAsState(
        targetValue = if (isFocused) 8.dp else 4.dp,
        animationSpec = tween(200),
        label = "shadow offset"
    )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Label
        if (label.isNotEmpty()) {
            Text(
                text = label.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = accentColor,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        
        // Text Field with Neo-Brutalist styling
        Box {
            // Colored shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = animatedShadowOffset, y = animatedShadowOffset)
                    .background(
                        accentColor.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    )
            )
            
            // Black shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = (animatedShadowOffset / 2), y = (animatedShadowOffset / 2))
                    .background(
                        Color.Black,
                        RoundedCornerShape(12.dp)
                    )
            )
            
            // Main text field
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = nb_white.copy(alpha = 0.1f),
                        spotColor = accentColor.copy(alpha = 0.3f)
                    ),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = nb_white,
                    fontFamily = FontFamily.SansSerif
                ),
                placeholder = placeholder?.let { placeholderText ->
                    {
                        Text(
                            text = placeholderText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                singleLine = singleLine,
                maxLines = maxLines,
                enabled = enabled,
                keyboardOptions = keyboardOptions,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = nb_charcoal,
                    unfocusedContainerColor = nb_charcoal,
                    disabledContainerColor = nb_charcoal.copy(alpha = 0.5f),
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = nb_white.copy(alpha = 0.3f),
                    disabledBorderColor = nb_white.copy(alpha = 0.1f),
                    cursorColor = accentColor,
                    focusedTextColor = nb_white,
                    unfocusedTextColor = nb_white,
                    disabledTextColor = nb_white.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}