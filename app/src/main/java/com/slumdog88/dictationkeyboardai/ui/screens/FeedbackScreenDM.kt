package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.width
import com.slumdog88.dictationkeyboardai.ui.theme.Radii

@Composable
fun FeedbackScreenDM(
    onSendFeedback: (String, String, String, String, String, String) -> Unit,
    onBackPressed: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    // Form state - using rememberSaveable to preserve state through configuration changes (e.g., rotation)
    var feedbackType by rememberSaveable { mutableStateOf("Bug Report") }
    var priority by rememberSaveable { mutableStateOf("Medium") }
    var category by rememberSaveable { mutableStateOf("UI/UX") }
    var subject by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var description by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var steps by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    val showSteps = feedbackType == "Bug Report" || feedbackType == "Performance Issue"

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
                text = "FEEDBACK\n& BUGS",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onBackground
            )

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Feedback Type") {
                DropdownField(
                    value = feedbackType,
                    onValueChange = { feedbackType = it },
                    options = listOf("Bug Report", "Feature Request", "General Feedback", "Performance Issue")
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Priority") {
                DropdownField(
                    value = priority,
                    onValueChange = { priority = it },
                    options = listOf("Low", "Medium", "High", "Critical")
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Category") {
                DropdownField(
                    value = category,
                    onValueChange = { category = it },
                    options = listOf("UI/UX", "Audio Processing", "AI Integration", "Performance", "Crash/Stability", "Settings", "Other")
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Subject") {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    placeholder = { Text("Brief description of the issue or request") },
                    shape = Radii.medium,
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
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Description") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Detailed description. Please be as specific as possible.") },
                    shape = Radii.medium,
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
                        .height(140.dp),
                    maxLines = Int.MAX_VALUE
                )
            }

            if (showSteps) {
                Spacer(Modifier.height(12.dp))
                SectionCard(title = "Steps to Reproduce") {
                    OutlinedTextField(
                        value = steps,
                        onValueChange = { steps = it },
                        placeholder = { Text("1. First step\n2. Second step\n3. Third step...") },
                        shape = Radii.medium,
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
                            .height(120.dp),
                        maxLines = Int.MAX_VALUE
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "GitHub Issue") {
                Text(
                    text = "This will open a pre-filled issue on GitHub. Attach screenshots or sanitized logs on GitHub only if you want to share them publicly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSendFeedback(feedbackType, priority, category, subject.text, description.text, steps.text) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) { Text("Open GitHub Issue", color = Color.Black) }

                Button(
                    onClick = onBackPressed,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                ) { Text("Back", color = colors.onSurface) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = null,
            modifier = Modifier.fillMaxWidth(),
            shape = Radii.medium,
            colors = TextFieldDefaults.colors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledIndicatorColor = Color.Transparent,
                disabledTextColor = MaterialTheme.colorScheme.onSurface
            ),
            trailingIcon = { Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        
        // Clickable overlay that captures all touch events
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    expanded = true
                }
        )
    }

    // Modal dialog for selecting options
    if (expanded) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                expanded = false
            },
            confirmButton = {},
            title = {
                Text(
                    text = "Select",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    options.forEachIndexed { idx, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(option)
                                    expanded = false
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (idx != options.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shape = Radii.large
        )
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
