package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun ApiKeysScreen(
    onSave: (Map<String, String>) -> Unit,
    onBack: () -> Unit,
    initialKeys: Map<String, String> = emptyMap(),
    isSimpleMode: Boolean = false
) {
    // State management for all API keys
    var openaiKey by remember { mutableStateOf(TextFieldValue(initialKeys["openai_api_key"] ?: "")) }
    var elevenlabsKey by remember { mutableStateOf(TextFieldValue(initialKeys["elevenlabs_api_key"] ?: "")) }
    var groqKey by remember { mutableStateOf(TextFieldValue(initialKeys["groq_api_key"] ?: "")) }
    var googleKey by remember { mutableStateOf(TextFieldValue(initialKeys["google_api_key"] ?: "")) }
    var deepgramKey by remember { mutableStateOf(TextFieldValue(initialKeys["deepgram_api_key"] ?: "")) }
    var assemblyaiKey by remember { mutableStateOf(TextFieldValue(initialKeys["assemblyai_api_key"] ?: "")) }
    var anthropicKey by remember { mutableStateOf(TextFieldValue(initialKeys["anthropic_api_key"] ?: "")) }
    var mistralKey by remember { mutableStateOf(TextFieldValue(initialKeys["mistral_api_key"] ?: "")) }
    var openrouterKey by remember { mutableStateOf(TextFieldValue(initialKeys["openrouter_api_key"] ?: "")) }
    var cerebrasKey by remember { mutableStateOf(TextFieldValue(initialKeys["cerebras_api_key"] ?: "")) }
    var sonioxKey by remember { mutableStateOf(TextFieldValue(initialKeys["soniox_api_key"] ?: "")) }

    val scrollState = rememberScrollState()
    val accentColor = Color(0xFFFF7F00) // Orange for security/keys context

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header (Material)
        Text(
            text = "API Keys",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSimpleMode) {
            SimpleModeGroqInstructions()

            ApiKeyInputSection(
                title = "Groq API Key",
                value = groqKey,
                onValueChange = { groqKey = it },
                placeholder = "Paste your Groq API key",
                accentColor = accentColor
            )
        } else {
            // OpenAI API Key
            ApiKeyInputSection(
                title = "OpenAI API Key",
                value = openaiKey,
                onValueChange = { openaiKey = it },
                placeholder = "Enter OpenAI API key",
                accentColor = accentColor
            )

            // ElevenLabs API Key
            ApiKeyInputSection(
                title = "ElevenLabs API Key",
                value = elevenlabsKey,
                onValueChange = { elevenlabsKey = it },
                placeholder = "Enter ElevenLabs API key",
                accentColor = accentColor
            )

            // Groq API Key (optional when hosted proxy is configured)
            ApiKeyInputSection(
                title = "Groq API Key (Optional)",
                value = groqKey,
                onValueChange = { groqKey = it },
                placeholder = "Enter Groq API key if you want to use your own account",
                accentColor = accentColor
            )

            // Google AI API Key
            ApiKeyInputSection(
                title = "Google AI API Key",
                value = googleKey,
                onValueChange = { googleKey = it },
                placeholder = "Enter Google AI API key",
                accentColor = accentColor
            )

            // Deepgram API Key
            ApiKeyInputSection(
                title = "Deepgram API Key",
                value = deepgramKey,
                onValueChange = { deepgramKey = it },
                placeholder = "Enter Deepgram API key",
                accentColor = accentColor
            )

            // AssemblyAI API Key
            ApiKeyInputSection(
                title = "AssemblyAI API Key",
                value = assemblyaiKey,
                onValueChange = { assemblyaiKey = it },
                placeholder = "Enter AssemblyAI API key",
                accentColor = accentColor
            )

            // Anthropic API Key
            ApiKeyInputSection(
                title = "Anthropic API Key",
                value = anthropicKey,
                onValueChange = { anthropicKey = it },
                placeholder = "Enter Anthropic API key",
                accentColor = accentColor
            )

            // Mistral API Key
            ApiKeyInputSection(
                title = "Mistral API Key",
                value = mistralKey,
                onValueChange = { mistralKey = it },
                placeholder = "Enter Mistral API key",
                accentColor = accentColor
            )

            // Cerebras API Key
            ApiKeyInputSection(
                title = "Cerebras API Key",
                value = cerebrasKey,
                onValueChange = { cerebrasKey = it },
                placeholder = "Enter Cerebras API key",
                accentColor = accentColor
            )

            // Soniox API Key
            ApiKeyInputSection(
                title = "Soniox API Key",
                value = sonioxKey,
                onValueChange = { sonioxKey = it },
                placeholder = "Enter Soniox API key",
                accentColor = accentColor
            )

            // OpenRouter API Key
            ApiKeyInputSection(
                title = "OpenRouter API Key",
                value = openrouterKey,
                onValueChange = { openrouterKey = it },
                placeholder = "Enter OpenRouter API key",
                accentColor = accentColor
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button (Material)
        androidx.compose.material3.Button(
            onClick = {
                val keys = if (isSimpleMode) {
                    mapOf("groq_api_key" to groqKey.text.trim())
                } else {
                    mapOf(
                        "openai_api_key" to openaiKey.text.trim(),
                        "elevenlabs_api_key" to elevenlabsKey.text.trim(),
                        "groq_api_key" to groqKey.text.trim(),
                        "google_api_key" to googleKey.text.trim(),
                        "deepgram_api_key" to deepgramKey.text.trim(),
                        "assemblyai_api_key" to assemblyaiKey.text.trim(),
                        "anthropic_api_key" to anthropicKey.text.trim(),
                        "mistral_api_key" to mistralKey.text.trim(),
                        "openrouter_api_key" to openrouterKey.text.trim(),
                        "cerebras_api_key" to cerebrasKey.text.trim(),
                        "soniox_api_key" to sonioxKey.text.trim()
                    )
                }
                onSave(keys)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Back Button (Material)
        androidx.compose.material3.OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SimpleModeGroqInstructions() {
    androidx.compose.material3.Card(
        shape = com.slumdog88.dictationkeyboardai.ui.theme.Radii.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Simple Mode uses Groq",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "WonderWhisper does not include a built-in Groq key. To use cloud transcription and AI cleanup in Simple Mode:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = """
1. Go to https://console.groq.com/keys
2. Sign in or create a Groq account.
3. Create a new API key.
4. Copy the key once and keep it private.
5. Paste it below and tap Save.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Do not paste your API key into chats, public issues, screenshots, or anywhere you do not trust.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiKeyInputSection(
    title: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    accentColor: Color
) {
    androidx.compose.material3.Card(
        shape = com.slumdog88.dictationkeyboardai.ui.theme.Radii.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Password input field (Material)
            M3PasswordField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder
            )
        }
    }
}

@Composable
private fun M3PasswordField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.material3.OutlinedTextField(
        value = value.text,
        onValueChange = { onValueChange(TextFieldValue(it)) },
        singleLine = true,
        label = { Text(placeholder) },
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        trailingIcon = {
            val iconLabel = if (visible) "Hide" else "Show"
            Text(
                iconLabel,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { visible = !visible }
            )
        },
        modifier = modifier.fillMaxWidth()
    )
}
