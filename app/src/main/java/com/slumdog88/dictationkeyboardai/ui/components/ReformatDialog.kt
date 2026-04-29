package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.ReformatPrompt

@Composable
fun ReformatDialog(
    onDismiss: () -> Unit,
    onPromptSelected: (ReformatPrompt) -> Unit
) {
    val defaultPrompt = ReformatPrompt.getDefaultPrompts().firstOrNull { it.isDefault } ?: 
        ReformatPrompt.getDefaultPrompts().firstOrNull() ?: 
        ReformatPrompt(
            name = "Default",
            description = "Default reformatting",
            promptText = "Please reformat the following content to be clear and well-structured."
        )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reformat Note") },
        text = { Text("Select a reformatting prompt:") },
        confirmButton = {
            TextButton(onClick = {
                onPromptSelected(defaultPrompt)
                onDismiss()
            }) {
                Text("Default")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
