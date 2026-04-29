package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.HapticUtils
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
    initialVocabulary: String = "",
    initialCustomSpelling: String = ""
) {
    val context = LocalContext.current
    
    // Neo-Brutalist lime accent for vocabulary/learning
    val limeAccent = Color(0xFF8AC926)
    val charcoalBackground = Color(0xFF2B2B2B)
    val baseBackground = Color(0xFF1A1A1A)
    val whiteText = Color(0xFFFFFFFF)
    val lightGrayText = Color(0xFFCCCCCC)
    
    var vocabularyText by remember { mutableStateOf(initialVocabulary) }
    var customSpellingText by remember { mutableStateOf(initialCustomSpelling) }
    
    // Set defaults if fields are empty
    LaunchedEffect(Unit) {
        if (vocabularyText.isBlank()) {
            vocabularyText = "WonderWhisper, ChatGPT, Groq, Anthropic, AssemblyAI"
        }
        if (customSpellingText.isBlank()) {
            customSpellingText = "wonder whisper = WonderWhisper\nopen a i = OpenAI"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBackground)
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Main Title
        Text(
            text = "CUSTOM VOCABULARY",
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = whiteText,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp,
                lineHeight = 30.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
        
        // Key Terms Section
        VocabularySection(
            title = "KEY TERMS",
            description = "Add custom words, names, or phrases (comma separated):",
            hint = "Enter custom vocabulary words\nComma separated: AssemblyAI, API, React Native, machine learning",
            text = vocabularyText,
            onTextChange = { vocabularyText = it },
            accentColor = limeAccent,
            charcoalBackground = charcoalBackground,
            whiteText = whiteText,
            lightGrayText = lightGrayText
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Custom Spelling Section  
        VocabularySection(
            title = "CUSTOM SPELLING",
            description = "Map spoken words to preferred spelling (format: spoken phrase = preferred spelling):",
            example = "Example: Body Fit Training = BFT, sequel = SQL",
            hint = "Enter custom spelling mappings\nOne per line: Body Fit Training = BFT\nsequel = SQL",
            text = customSpellingText,
            onTextChange = { customSpellingText = it },
            accentColor = limeAccent,
            charcoalBackground = charcoalBackground,
            whiteText = whiteText,
            lightGrayText = lightGrayText
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action Buttons
        VocabularyButton(
            text = "SAVE",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onSave(vocabularyText, customSpellingText)
            },
            accentColor = limeAccent,
            isPrimary = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        VocabularyButton(
            text = "BACK",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onBack()
            },
            accentColor = limeAccent,
            isPrimary = false
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun VocabularySection(
    title: String,
    description: String,
    hint: String,
    text: String,
    onTextChange: (String) -> Unit,
    accentColor: Color,
    charcoalBackground: Color,
    whiteText: Color,
    lightGrayText: Color,
    example: String? = null
) {
    Column {
        // Section Title
        Text(
            text = title,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = accentColor,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.3.sp,
                lineHeight = 20.sp
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Description
        Text(
            text = description,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = lightGrayText,
                fontFamily = FontFamily.SansSerif
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Example (if provided)
        example?.let {
            Text(
                text = it,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF00AAFF),
                    fontFamily = FontFamily.SansSerif
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Text Input Field with Neo-Brutalist styling
        VocabularyTextField(
            text = text,
            onTextChange = onTextChange,
            hint = hint,
            accentColor = accentColor,
            charcoalBackground = charcoalBackground,
            whiteText = whiteText,
            lightGrayText = lightGrayText
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocabularyTextField(
    text: String,
    onTextChange: (String) -> Unit,
    hint: String,
    accentColor: Color,
    charcoalBackground: Color,
    whiteText: Color,
    lightGrayText: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // Multi-layer shadow system
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main text field
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    text = hint,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = lightGrayText.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                )
            },
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = whiteText,
                fontFamily = FontFamily.Monospace
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = charcoalBackground,
                unfocusedContainerColor = charcoalBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = accentColor
            ),
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .background(charcoalBackground, RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun VocabularyButton(
    text: String,
    onClick: () -> Unit,
    accentColor: Color,
    isPrimary: Boolean
) {
    val backgroundColor = if (isPrimary) accentColor else Color(0xFF1F1F1F)
    val textColor = if (isPrimary) Color.Black else Color.White
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // Multi-layer shadow system
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main button
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.3.sp
                )
            )
        }
    }
}
