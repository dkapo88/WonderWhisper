package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalistSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    label: String = "",
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    
    // Animation for background color when focused
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF2B2B2B) else Color(0xFF1F1F1F),
        animationSpec = tween(200),
        label = "background_color"
    )
    
    // Animation for border color when focused
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) accentColor else Color.Black,
        animationSpec = tween(200),
        label = "border_color"
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
        
        // Search field with Neo-Brutalist styling
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Colored shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 6.dp, y = 6.dp)
                    .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            )
            
            // Black shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
            )
            
            // Main search field container
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
                    .border(3.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Search icon
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isFocused) accentColor else Color(0xFF666666),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Text field
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            enabled = enabled,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (enabled) Color.White else Color(0xFF666666),
                                fontFamily = FontFamily.SansSerif
                            ),
                            cursorBrush = SolidColor(accentColor),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    onSearch?.invoke()
                                    keyboardController?.hide()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                }
                        )
                        
                        // Placeholder text
                        if (value.text.isEmpty() && !isFocused) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF666666),
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