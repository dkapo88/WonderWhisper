package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.ui.theme.Radii

@Composable
fun VocabularyScreenDM(
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
    initialVocabulary: String = "",
    initialCustomSpelling: String = ""
) {
    val colors = MaterialTheme.colorScheme
    var vocabularyText by remember { mutableStateOf(initialVocabulary) }
    var customSpellingText by remember { mutableStateOf(initialCustomSpelling) }

    // Sensible defaults for first-time users
    LaunchedEffect(Unit) {
        if (vocabularyText.isBlank()) {
            vocabularyText = "WonderWhisper, ChatGPT, Groq, Anthropic, AssemblyAI"
        }
        if (customSpellingText.isBlank()) {
            customSpellingText = "wonder whisper = WonderWhisper\nopen a i = OpenAI"
        }
    }

    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.background,
                        colors.surfaceVariant,
                        colors.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Header
            Text(
                text = "CUSTOM\nVOCABULARY",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onBackground
            )

            Spacer(Modifier.height(16.dp))

            // Key Terms
            SectionCard(title = "Key Terms") {
                Text(
                    "Add custom words, names, or phrases (comma separated):",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vocabularyText,
                    onValueChange = { vocabularyText = it },
                    placeholder = { Text("AssemblyAI, API, React Native, machine learning") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceVariant,
                        unfocusedContainerColor = colors.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = Radii.medium
                )
            }

            Spacer(Modifier.height(12.dp))

            // Custom Spelling
            SectionCard(title = "Custom Spelling") {
                Text(
                    "Map spoken words to preferred spelling (format: spoken = preferred):",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Example: Body Fit Training = BFT, sequel = SQL",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tertiary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customSpellingText,
                    onValueChange = { customSpellingText = it },
                    placeholder = { Text("Body Fit Training = BFT\nsequel = SQL") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceVariant,
                        unfocusedContainerColor = colors.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = Radii.medium
                )
            }

            Spacer(Modifier.height(16.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSave(vocabularyText, customSpellingText) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) { Text("Save", color = Color.Black) }

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant),
                ) { Text("Back", color = colors.onSurface) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}