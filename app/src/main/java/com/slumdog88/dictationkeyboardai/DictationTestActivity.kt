package com.slumdog88.dictationkeyboardai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import com.slumdog88.dictationkeyboardai.ui.theme.Bg
import com.slumdog88.dictationkeyboardai.ui.theme.PastelBlue
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPurple
import com.slumdog88.dictationkeyboardai.ui.theme.Surface1
import com.slumdog88.dictationkeyboardai.ui.theme.Surface2

class DictationTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                DictationTestScreenDM(
                    onBack = {
                        HapticUtils.performHapticFeedback(this@DictationTestActivity)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun DictationTestScreen(onBack: () -> Unit) {
    // Keep legacy signature; delegate to themed screen
    DictationTestScreenDM(onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationTestScreenDM(onBack: () -> Unit) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val bg = MaterialTheme.colorScheme.background
    val hi = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                title = "Dictation Test",
                onBack = onBack
            )
        },
        containerColor = bg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Surface1,
                    contentColor = hi
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Use this area to test dictation functionality.\n\n" +
                           "1. Ensure accessibility service is enabled\n" +
                           "2. Tap in the text field below\n" +
                           "3. Use the floating bubble to start dictation\n" +
                           "4. Speak your text and tap stop when finished",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = dim
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Text area
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Surface2,
                    contentColor = hi
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                OutlinedTextField(
                    value = textFieldValue.text,
                    onValueChange = { textFieldValue = TextFieldValue(it) },
                    label = { Text("Dictation text") },
                    placeholder = { Text("Tap here and use the floating bubble to test dictation") },
                    colors = OutlinedTextFieldDefaults.colors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    minLines = 8
                )
            }

            // Bottom actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { textFieldValue = TextFieldValue("") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelBlue,
                        contentColor = Bg
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelPurple,
                        contentColor = Bg
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
fun DictationTestCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00F5FF),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Multi-layer shadow - colored shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 10.dp, y = 10.dp)
                .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        )
        // Black shadow for definition
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main content with proper Neo-Brutalist charcoal background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp)) // nb_charcoal
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            content()
        }
    }
}

@Composable
fun BrutalistTextFieldCard(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00F5FF)
) {
    Box(
        modifier = modifier
            .padding(vertical = 6.dp)
    ) {
        // Multi-layer shadow - colored shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        )
        // Black shadow for definition
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main text field
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.2f),
                    spotColor = accentColor.copy(alpha = 0.4f)
                )
                .background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                textStyle = TextStyle(
                    color = Color(0xFFFFFFFF),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(accentColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = "Tap here and use the floating bubble to test dictation",
                                style = TextStyle(
                                    color = Color(0xFF888888),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
fun BrutalistButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00F5FF)
) {
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        // Multi-layer shadow - colored shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )
        // Black shadow for definition
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
        )
        
        // Main button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.White.copy(alpha = 0.2f),
                    spotColor = accentColor.copy(alpha = 0.4f)
                )
                .background(Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFFFFF),
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.3.sp
                )
            )
        }
    }
}

