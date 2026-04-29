package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.ui.theme.Radii

@Composable
fun AccessibilityDisclosureScreenDM(
    onBack: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onUnderstandAndAgree: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
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
                text = "ACCESSIBILITY\nDISCLOSURE",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onBackground
            )

            Spacer(Modifier.height(16.dp))

            // Sections
            SectionCard(
                title = "Important Warning",
                accent = colors.tertiary
            ) {
                BodyText("WonderWhisper requires Accessibility Service permission to function properly.")
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(
                title = "Why we need this permission",
                accent = colors.primary
            ) {
                BodyText(
                    """
WonderWhisper uses the AccessibilityServices API exclusively to:
• Insert dictated text into any app (Messages, Email, Notes, etc.)
• Read selected text to provide AI command context
• Detect active text fields to show the dictation bubble
                    """.trimIndent()
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(
                title = "What we access",
                accent = colors.secondary
            ) {
                BodyText(
                    """
✓ Text fields and input areas (to insert dictated text)
✓ Selected text content (for AI command context)
✓ Current app information (for app‑specific features)

✗ We do not access passwords, payment info, or sensitive data
✗ We do not monitor your browsing or app usage
✗ We do not collect data for advertising
                    """.trimIndent()
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(
                title = "How your data is used",
                accent = colors.primary
            ) {
                BodyText(
                    """
• Voice recordings: Temporarily processed for transcription, then deleted
• Selected text: Used only for AI command context, not stored permanently
• App context: Used to improve dictation accuracy, not tracked or stored
• Transcription logs: Stored locally on your device only, you control deletion

No data is sent to our servers. Processing uses your own API keys with services you configure.
                    """.trimIndent()
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(
                title = "Security & privacy",
                accent = colors.secondary
            ) {
                BodyText(
                    """
• All data processing is local or through your API keys
• No data is sent to WonderWhisper servers
• You can disable the service anytime in Android settings
• You can view/delete all logs in the app
                    """.trimIndent()
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(
                title = "Compliance",
                accent = colors.tertiary
            ) {
                BodyText(
                    "This disclosure complies with Google Play's AccessibilityServices policy. WonderWhisper uses accessibility solely for assistive purposes—enabling voice dictation across apps."
                )
            }

            Spacer(Modifier.height(16.dp))

            // Actions
            Button(
                onClick = onPrivacyPolicy,
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
            ) {
                Text("View full privacy policy", color = colors.onSurface)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onUnderstandAndAgree,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("I understand & agree - Enable accessibility", color = Color.Black)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
            ) {
                Text("Back to main menu", color = colors.onSurface)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    accent: Color,
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
                color = accent
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}