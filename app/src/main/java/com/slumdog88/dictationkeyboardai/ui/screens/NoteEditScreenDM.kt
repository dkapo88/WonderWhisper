package com.slumdog88.dictationkeyboardai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.Color
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import com.slumdog88.dictationkeyboardai.network.GroqProxyConfig
import com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.MarkdownHtmlConverter
import com.slumdog88.dictationkeyboardai.Note
import com.slumdog88.dictationkeyboardai.NotePadManager
import com.slumdog88.dictationkeyboardai.ReformatPrompt
import com.slumdog88.dictationkeyboardai.ReformatPromptManager
import com.slumdog88.dictationkeyboardai.SecureApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreenDM(
    noteId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val notePadManager = remember { NotePadManager(context) }
    val promptManager = remember { ReformatPromptManager(context) }
    val scope = rememberCoroutineScope()

    var note by remember { mutableStateOf<Note?>(null) }
    var tabIndex by remember { mutableIntStateOf(0) } // 0=Transcript, 1=AI, 2=Preview
    var transcript by remember { mutableStateOf("") }
    var aiEnhanced by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Reprocess dialog state
    var showPromptDialog by remember { mutableStateOf(false) }
    var showAddEditPromptDialog by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var customPromptText by remember { mutableStateOf("") }

    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }

    // Add/Edit prompt dialog state
    var editingPrompt by remember { mutableStateOf<ReformatPrompt?>(null) }
    var promptName by remember { mutableStateOf("") }
    var promptDescription by remember { mutableStateOf("") }
    var promptText by remember { mutableStateOf("") }

    fun load() {
        scope.launch(Dispatchers.IO) {
            val n = notePadManager.getNoteById(noteId)
            launch(Dispatchers.Main) {
                note = n
                transcript = n?.getOriginalTranscriptContent() ?: n?.content ?: ""
                aiEnhanced = if (n?.hasDistinctVersions() == true) n.getAiProcessedContent() else n?.content ?: ""
                isLoading = false
            }
        }
    }

    LaunchedEffect(noteId) { load() }

    fun shareAudio(context: Context, note: Note?) {
        if (note == null) {
            Toast.makeText(context, "No note loaded", Toast.LENGTH_SHORT).show()
            return
        }

        if (note.audioFileName.isEmpty()) {
            Toast.makeText(context, "No audio file available for this note", Toast.LENGTH_SHORT).show()
            return
        }

        // Get the full path to the audio file
        val audioDirectory = NotePadManager(context).getNotesAudioDirectory()
        val audioFile = File(audioDirectory, note.audioFileName)

        if (!audioFile.exists()) {
            Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Create share intent for the audio file
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "audio/*"

            // Use FileProvider to create content URI
            val audioUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                audioFile
            )

            shareIntent.putExtra(Intent.EXTRA_STREAM, audioUri)
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Shared Audio Note: ${note.getDisplayTitle()}")
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Audio note: ${note.getDisplayTitle()}")

            // Grant read permission to receiving apps
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(Intent.createChooser(shareIntent, "Share Audio File"))

        } catch (e: Exception) {
            android.util.Log.e("NoteEditScreenDM", "Error sharing audio file", e)
            Toast.makeText(context, "Failed to share audio file", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
        topBar = {
            AppTopBarDM(
                title = note?.title ?: "Note",
                onBack = onBack,
                centered = false,
                actions = {
                    // Reprocess
                    IconButton(
                        onClick = {
                            // Full end-to-end reprocess of this note using current model settings
                            val nf = note
                            if (nf == null) {
                                Toast.makeText(context, "No note loaded", Toast.LENGTH_SHORT).show()
                            } else if (nf.audioFileName.isBlank()) {
                                Toast.makeText(context, "No audio file on this note", Toast.LENGTH_SHORT).show()
                            } else {
                                processing = true
                                // Ask service to reprocess and update this same note
                                val svc = Intent(context, com.slumdog88.dictationkeyboardai.BubbleOverlayService::class.java).apply {
                                    action = com.slumdog88.dictationkeyboardai.BubbleOverlayService.ACTION_REPROCESS_NOTE
                                    putExtra("audio_file_name", nf.audioFileName)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc) else context.startService(svc)

                                // Optimistic UI: show toast; list will refresh via broadcast
                                Toast.makeText(context, "Reprocessing note...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !processing
                    ) { Icon(Icons.Filled.Refresh, contentDescription = "Reprocess") }

                    // Share options
                    IconButton(
                        onClick = {
                            showShareDialog = true
                            HapticUtils.performHapticFeedback(context)
                        }
                    ) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading...", color = MaterialTheme.colorScheme.onBackground)
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Tabs
                val tabs = listOf("Transcript", "AI Enhanced", "Preview")
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                HorizontalDivider()

                when (tabIndex) {
                    0 -> EditorTab(
                        value = transcript,
                        onChange = {
                            transcript = it
                            // Persist to transcript field
                            scope.launch(Dispatchers.IO) {
                                notePadManager.updateNoteOriginalTranscript(noteId, it)
                                // Refresh the backing object
                                note = notePadManager.getNoteById(noteId)
                            }
                        },
                        label = "Raw transcript",
                        onReprocess = { showPromptDialog = true }
                    )

                    1 -> EditorTab(
                        value = aiEnhanced,
                        onChange = {
                            aiEnhanced = it
                            scope.launch(Dispatchers.IO) {
                                notePadManager.updateNoteAiProcessed(noteId, it)
                                note = notePadManager.getNoteById(noteId)
                            }
                        },
                        label = "AI‑enhanced",
                        onReprocess = { showPromptDialog = true }
                    )

                    2 -> PreviewTab(
                        markdown = if ((note?.hasDistinctVersions() == true && aiEnhanced.isNotBlank())) aiEnhanced else transcript
                    )
                }
            }
        }
    }

    // Share Options Dialog
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share Options") },
            text = { Text("Choose what to share:") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Share Content (primary)
                    Button(
                        onClick = {
                            showShareDialog = false
                            val content = if (tabIndex == 2) {
                                val md = if ((note?.hasDistinctVersions() == true && aiEnhanced.isNotBlank())) aiEnhanced else transcript
                                shareFormatted(context, note?.title ?: "Note", md)
                            } else {
                                val body = if (tabIndex == 1) aiEnhanced else transcript
                                sharePlain(context, note?.title ?: "Note", body)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Share Content", color = MaterialTheme.colorScheme.onPrimary) }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Share Audio (outlined)
                    OutlinedButton(
                        onClick = {
                            showShareDialog = false
                            shareAudio(context, note)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Share Audio") }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cancel below the options
                    TextButton(
                        onClick = { showShareDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Cancel") }
                }
            },
            dismissButton = {}
        )
    }

    // Add/Edit Prompt Dialog
    if (showAddEditPromptDialog) {
        AddEditPromptDialog(
            onDismiss = { showAddEditPromptDialog = false },
            editingPrompt = editingPrompt,
            promptName = promptName,
            onPromptNameChange = { promptName = it },
            promptDescription = promptDescription,
            onPromptDescriptionChange = { promptDescription = it },
            promptText = promptText,
            onPromptTextChange = { promptText = it },
            onSave = {
                val nameError = promptManager.validatePromptName(promptName)
                val textError = promptManager.validatePromptText(promptText)

                if (nameError != null) {
                    Toast.makeText(context, nameError, Toast.LENGTH_LONG).show()
                    return@AddEditPromptDialog
                }

                if (textError != null) {
                    Toast.makeText(context, textError, Toast.LENGTH_LONG).show()
                    return@AddEditPromptDialog
                }

                val prompt = if (editingPrompt != null) {
                    // Edit existing prompt
                    editingPrompt!!.copy(
                        name = promptName,
                        description = promptDescription,
                        promptText = promptText
                    )
                } else {
                    // Create new prompt
                    ReformatPrompt.createUserPrompt(
                        name = promptName,
                        description = promptDescription,
                        promptText = promptText
                    )
                }

                promptManager.saveUserPrompt(prompt)
                showAddEditPromptDialog = false
                HapticUtils.performHapticFeedback(context)
                Toast.makeText(context, "Prompt saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showPromptDialog) {
        ReprocessDialog(
            onDismiss = { showPromptDialog = false },
            prompts = promptManager.getAllPrompts(),
            customPromptText = customPromptText,
            onCustomPromptChange = { customPromptText = it },
            onAddNewPrompt = {
                editingPrompt = null
                promptName = ""
                promptDescription = ""
                promptText = ""
                showAddEditPromptDialog = true
            },
            onEditPrompt = { prompt ->
                editingPrompt = prompt
                promptName = prompt.name
                promptDescription = prompt.description
                promptText = prompt.promptText
                showAddEditPromptDialog = true
            },
            onDeletePrompt = { prompt ->
                promptManager.deleteUserPrompt(prompt.id)
                // The dialog will automatically refresh when state changes
            },
            onRun = { selectedPrompt ->
                showPromptDialog = false
                HapticUtils.performHapticFeedback(context)
                val baseText = transcript.ifBlank { aiEnhanced }
                if (baseText.isBlank()) {
                    Toast.makeText(context, "No content to process", Toast.LENGTH_SHORT).show()
                    return@ReprocessDialog
                }
                // Get AI model selection (with fallback to openai/gpt-oss-120b if no keys)
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                var aiModel = prefs.getString("notepad_ai_model",
                    prefs.getString("ai_model", "openai/gpt-oss-120b")) ?: "openai/gpt-oss-120b"

                // Check if we need to fallback due to missing API keys
                val validationError = validateModelKeys(context, aiModel)
                if (validationError != null) {
                    android.util.Log.w("NoteEditScreenDM", "API key validation failed for '$aiModel': $validationError")
                    android.util.Log.d("NoteEditScreenDM", "Attempting fallback to openai/gpt-oss-120b")
                    // Always try fallback to openai/gpt-oss-120b for Pro mode alternative models
                    aiModel = "openai/gpt-oss-120b"
                    android.util.Log.d("NoteEditScreenDM", "Using fallback model: openai/gpt-oss-120b")
                    // Show fallback notification for Pro mode alternative models
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Using fallback model", Toast.LENGTH_SHORT).show()
                    }
                }

                processing = true
                scope.launch(Dispatchers.IO) {
                    val promptText = when {
                        selectedPrompt != null -> selectedPrompt.promptText
                        customPromptText.isNotBlank() -> customPromptText
                        else -> "Rewrite and improve clarity, preserve key points."
                    }
                    android.util.Log.d("NoteEditScreenDM", "Reformat RUN clicked: selectedPrompt='${selectedPrompt?.name ?: "none"}', promptLen=${promptText.length}, baseLen=${baseText.length}")
                    val prefsDbg = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val dbgModel = prefsDbg.getString("notepad_ai_model", prefsDbg.getString("ai_model", "OpenAI")) ?: "OpenAI"
                    android.util.Log.d("NoteEditScreenDM", "Reformat using modelLabel='$dbgModel', notepad_openrouter_model_id='${prefsDbg.getString("notepad_openrouter_model_id", "")}'")
                    android.util.Log.d("NoteEditScreenDM", "About to call tryAIReformat with model: $aiModel")
                    android.util.Log.d("NoteEditScreenDM", "Full prompt: ${promptText + "\n\n" + baseText}")

                    val result = tryAIReformat(context, promptText + "\n\n" + baseText, aiModel)
                    android.util.Log.d("NoteEditScreenDM", "tryAIReformat returned: ${result?.take(100) ?: "null"}")

                    launch(Dispatchers.Main) {
                        processing = false
                        if (!result.isNullOrBlank()) {
                            aiEnhanced = result
                            // Save to AI-enhanced slot
                            scope.launch(Dispatchers.IO) {
                                notePadManager.updateNoteAiProcessed(noteId, result)
                                note = notePadManager.getNoteById(noteId)
                            }
                            tabIndex = 1
                            Toast.makeText(context, "AI enhancement complete", Toast.LENGTH_SHORT).show()
                        } else {
                            android.util.Log.e("NoteEditScreenDM", "tryAIReformat returned null or blank")
                            Toast.makeText(context, "Failed to enhance text", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun EditorTab(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    onReprocess: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding(), // Add IME padding to ensure text field is visible above keyboard
            minLines = 10,
            maxLines = Int.MAX_VALUE,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ctx = LocalContext.current
            Button(
                onClick = {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Note", value))
                    Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    HapticUtils.performHapticFeedback(ctx)
                }
            ) { Text("Copy") }
            OutlinedButton(
                onClick = {
                    sharePlain(ctx, "Note", value)
                    HapticUtils.performHapticFeedback(ctx)
                }
            ) { Text("Share") }
            // Explicit reprocess entry point below the editor
            Button(
                onClick = onReprocess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Reformat it") }
        }
    }
}

@Composable
private fun PreviewTab(markdown: String) {
    val ctx = LocalContext.current
    val baseHtml = remember(markdown) { MarkdownHtmlConverter.markdownToHtml(markdown) }

    // Inject Material color tokens into the HTML so text is readable on dark background
    val onBgHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onBackground.toArgb())
    val primaryHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val surfaceVarHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surfaceVariant.toArgb())

    val themedHtml = remember(markdown, onBgHex, primaryHex, surfaceVarHex) {
        // Append an extra style block before </head> to override defaults
        val overrideCss = """
            <style>
              body { color: ${onBgHex}; background-color: transparent !important; }
              a { color: ${primaryHex}; }
              code { background: ${surfaceVarHex}; }
              pre { background: ${surfaceVarHex}; }
            </style>
        """.trimIndent()
        if (baseHtml.contains("</head>", ignoreCase = true)) {
            baseHtml.replace("</head>", "$overrideCss</head>", ignoreCase = true)
        } else baseHtml + overrideCss
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        AndroidView(
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    setBackgroundColor(Color.TRANSPARENT)
                }
            },
            update = { webView ->
                webView.setBackgroundColor(Color.TRANSPARENT)
                webView.loadDataWithBaseURL(
                    null,
                    themedHtml,
                    "text/html",
                    "utf-8",
                    null
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                val clip = ClipData.newHtmlText("Formatted Note", markdown, themedHtml)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(ctx, "Formatted note copied", Toast.LENGTH_SHORT).show()
                HapticUtils.performHapticFeedback(ctx)
            }) { Text("Copy Formatted") }

            OutlinedButton(onClick = {
                shareFormatted(ctx, "Note", markdown)
                HapticUtils.performHapticFeedback(ctx)
            }) { Text("Share Formatted") }
        }
    }


}

@Composable
private fun ReprocessDialog(
    onDismiss: () -> Unit,
    prompts: List<ReformatPrompt>,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    onAddNewPrompt: () -> Unit,
    onEditPrompt: (ReformatPrompt) -> Unit,
    onDeletePrompt: (ReformatPrompt) -> Unit,
    onRun: (ReformatPrompt?) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onRun(if (selectedIndex in prompts.indices) prompts[selectedIndex] else null) }) {
                    Text("Run")
                }
            }
        },
        title = { Text("AI Enhancement") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Add New Prompt button at the top
                Button(
                    onClick = onAddNewPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add New Prompt")
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text("Saved prompts", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))

                prompts.forEachIndexed { idx, p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Prompt info (clickable to select)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedIndex = idx }
                        ) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            if (p.description.isNotBlank()) {
                                Text(
                                    p.description,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                )
                            }
                        }

                        // Action buttons for user prompts
                        if (!p.isDefault) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Edit button
                                IconButton(
                                    onClick = { onEditPrompt(p) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit prompt")
                                }

                                // Delete button
                                IconButton(
                                    onClick = { onDeletePrompt(p) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete prompt")
                                }
                            }
                        }

                        // Select button
                        OutlinedButton(
                            onClick = { selectedIndex = idx },
                            colors = if (selectedIndex == idx)
                                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            else ButtonDefaults.outlinedButtonColors()
                        ) { Text(if (selectedIndex == idx) "Selected" else "Select") }
                    }
                }

                if (prompts.isEmpty()) {
                    Text(
                        "No saved prompts yet. Tap 'Add New Prompt' to create one.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text("Or use a quick custom prompt", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = customPromptText,
                    onValueChange = onCustomPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("e.g., Summarize action items and decisions in bullet points") }
                )
            }
        }
    )
}

@Composable
private fun AddEditPromptDialog(
    onDismiss: () -> Unit,
    editingPrompt: ReformatPrompt?,
    promptName: String,
    onPromptNameChange: (String) -> Unit,
    promptDescription: String,
    onPromptDescriptionChange: (String) -> Unit,
    promptText: String,
    onPromptTextChange: (String) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSave) {
                    Text(if (editingPrompt != null) "Update" else "Save")
                }
            }
        },
        title = { Text(if (editingPrompt != null) "Edit Prompt" else "Add New Prompt") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))

                // Prompt Name
                Text("Prompt Name", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = promptName,
                    onValueChange = onPromptNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., Meeting Summary") },
                    maxLines = 1
                )

                Spacer(Modifier.height(12.dp))

                // Prompt Description (optional)
                Text("Description (optional)", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = promptDescription,
                    onValueChange = onPromptDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Brief description of what this prompt does") },
                    minLines = 1,
                    maxLines = 2
                )

                Spacer(Modifier.height(12.dp))

                // Prompt Text
                Text("Prompt Text", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = promptText,
                    onValueChange = onPromptTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("Enter your AI prompt here. Be specific about what you want the AI to do with the text.") },
                    minLines = 4,
                    maxLines = 10
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Tip: Be specific about the format and style you want. Include examples if helpful.",
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    )
}

/* ---------- Helpers ---------- */


private fun sharePlain(context: Context, title: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share Note"))
}

private fun shareFormatted(context: Context, title: String, markdown: String) {
    val html = MarkdownHtmlConverter.markdownToHtml(markdown)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, html)
    }
    context.startActivity(Intent.createChooser(intent, "Share Formatted Note"))
}

/* ---------- AI Processing (inline minimal copy of prior logic) ---------- */

/**
 * Validate API keys for the chosen Notepad reformat model/provider.
 * Mirrors AIProcessingManager and supports explicit prefixes:
 * - "openrouter/<vendor/model>" -> OpenRouter key
 * - "groq/<vendor/model>"       -> Groq key
 * - "openai/<model>" or contains "gpt"/"openai" -> OpenAI key
 * - "gemini/<model>" or contains "gemini"/"google"/"2.0"/"2.5" -> Google key
 * - "anthropic/<model>" or contains "claude"/"anthropic" -> Anthropic key
 * - otherwise -> Groq key
 */
private fun validateModelKeys(context: Context, aiModel: String): String? {
    val keys = SecureApiKeyManager.getInstance(context)
    val lower = aiModel.lowercase().trim()
    val hasGroqAccess = !keys.getApiKey("groq_api_key").isNullOrBlank() || GroqProxyConfig.isConfigured()
    android.util.Log.d("NoteEditScreenDM", "Validating keys for model: '$aiModel'")

    // Explicit prefix checks first
    return when {
        lower.startsWith("openrouter/") ->
            if (keys.getApiKey("openrouter_api_key").isNullOrBlank()) "OpenRouter API key missing" else null
        lower.startsWith("groq/") ->
            if (!hasGroqAccess) "Groq API key missing" else null
        lower.startsWith("openai/") || lower.contains("gpt") || lower.contains("openai") ->
            if (keys.getApiKey("openai_api_key").isNullOrBlank()) "OpenAI API key missing" else null
        lower.startsWith("gemini/") || lower.startsWith("google/") ||
            lower.contains("gemini") || lower.contains("google") || lower.contains("2.0") || lower.contains("2.5") ->
            if (keys.getApiKey("google_api_key").isNullOrBlank()) "Google API key missing" else null
        lower.startsWith("anthropic/") || lower.startsWith("claude/") ||
            lower.contains("claude") || lower.contains("anthropic") ->
            if (keys.getApiKey("anthropic_api_key").isNullOrBlank()) "Anthropic API key missing" else null
        lower.startsWith("cerebras/") ->
            if (keys.getApiKey("cerebras_api_key").isNullOrBlank()) "Cerebras API key missing" else null
        lower == "mistral-saba-24b" ->
            if (!hasGroqAccess) "Groq API key missing" else null
        lower.startsWith("mistral/") || lower.contains("voxtral") ->
            if (keys.getApiKey("mistral_api_key").isNullOrBlank()) "Mistral API key missing" else null
        // Explicit OpenRouter selection without inline model id
        lower == "openrouter" ->
            if (keys.getApiKey("openrouter_api_key").isNullOrBlank()) "OpenRouter API key missing" else null
        // Default -> Groq
        else ->
            if (!hasGroqAccess) "Groq API key missing" else null
    }
}

private suspend fun tryAIReformat(context: Context, fullPrompt: String, overrideModel: String? = null): String? {
    android.util.Log.d("NoteEditScreenDM", "=== tryAIReformat STARTED ===")
    android.util.Log.d("NoteEditScreenDM", "Full prompt length: ${fullPrompt.length}")
    android.util.Log.d("NoteEditScreenDM", "Override model: $overrideModel")

    return try {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val keyManager = SecureApiKeyManager.getInstance(context)

        // Ensure API key migration is completed
        if (keyManager.isMigrationNeeded()) {
            android.util.Log.d("NoteEditScreenDM", "Performing API key migration")
            keyManager.migrateFromPlainTextStorage()
        }

        // Use override model if provided (for fallback), otherwise read from preferences
        val selectedLabel = overrideModel ?: prefs.getString("notepad_ai_model",
            prefs.getString("ai_model", "OpenAI")) ?: "OpenAI"
        val rawLabel = normalizeDeprecatedGroqModel(selectedLabel)
        android.util.Log.d("NoteEditScreenDM", "Using model: $rawLabel")

        // Configured OpenRouter model id (Notepad override first)
        val configuredOpenRouterModelId = prefs.getString(
            "notepad_openrouter_model_id",
            prefs.getString("openrouter_model_id", null)
        )?.trim()

        // Determine provider and model using explicit prefix first, then keyword families
        val lower = rawLabel.lowercase().trim()
        var effectiveProvider = ""
        var modelForVendor: String? = null
        var openRouterIdToUse: String? = null

        when {
            // Explicit prefixes allow disambiguation like "groq/openai/gpt-oss-20b"
            lower.startsWith("groq/") -> {
                effectiveProvider = "Groq"
                modelForVendor = rawLabel.substringAfter("groq/")
            }
            lower.startsWith("openrouter/") -> {
                effectiveProvider = "OpenRouter"
                openRouterIdToUse = rawLabel.substringAfter("openrouter/").ifBlank { null }
            }
            // Handle Groq models with different prefixes (like openai/gpt-oss-120b)
            lower == "openai/gpt-oss-120b" -> {
                effectiveProvider = "Groq"
                modelForVendor = "openai/gpt-oss-120b"
            }
            lower.startsWith("openai/") -> {
                effectiveProvider = "OpenAI"
                modelForVendor = rawLabel.substringAfter("openai/")
            }
            lower.startsWith("gemini/") || lower.startsWith("google/") -> {
                effectiveProvider = "Gemini"
                modelForVendor = rawLabel.substringAfter("/")
            }
            lower.startsWith("anthropic/") || lower.startsWith("claude/") -> {
                effectiveProvider = "Claude"
                modelForVendor = rawLabel.substringAfter("/")
            }
            // Keyword families (mirror AIProcessingManager)
            rawLabel.equals("OpenRouter", ignoreCase = true) -> {
                effectiveProvider = "OpenRouter"
                openRouterIdToUse = configuredOpenRouterModelId
            }
            rawLabel.startsWith("cerebras/", true) -> {
                effectiveProvider = "Cerebras"
                modelForVendor = rawLabel
            }
            rawLabel.contains("gpt", true) || rawLabel.contains("openai", true) -> {
                effectiveProvider = "OpenAI"
                modelForVendor = rawLabel
            }
            rawLabel.contains("claude", true) || rawLabel.contains("anthropic", true) -> {
                effectiveProvider = "Claude"
                modelForVendor = rawLabel
            }
            rawLabel.contains("gemini", true) || rawLabel.contains("google", true) ||
                rawLabel.contains("2.0", true) || rawLabel.contains("2.5", true) -> {
                effectiveProvider = "Gemini"
                modelForVendor = rawLabel
            }
            else -> {
                effectiveProvider = "Groq"
                modelForVendor = rawLabel
            }
        }

        android.util.Log.d(
            "NoteEditScreenDM",
            "Routing providerRaw='$rawLabel' -> effective='$effectiveProvider', vendorModel='${modelForVendor ?: ""}', orId='${openRouterIdToUse ?: ""}'"
        )

        // Debug API key status
        val debugStatus = when (effectiveProvider) {
            "OpenRouter" -> "OpenRouter key: ${if (keyManager.hasApiKey("openrouter_api_key")) "SET" else "MISSING"}"
            "OpenAI" -> "OpenAI key: ${if (keyManager.hasApiKey("openai_api_key")) "SET" else "MISSING"}"
            "Gemini" -> "Google key: ${if (keyManager.hasApiKey("google_api_key")) "SET" else "MISSING"}"
            "Claude" -> "Anthropic key: ${if (keyManager.hasApiKey("anthropic_api_key")) "SET" else "MISSING"}"
            "Cerebras" -> "Cerebras key: ${if (keyManager.hasApiKey("cerebras_api_key")) "SET" else "MISSING"}"
            else -> "Groq access: ${if (keyManager.hasApiKey("groq_api_key") || GroqProxyConfig.isConfigured()) "SET" else "MISSING"}"
        }
        android.util.Log.d("NoteEditScreenDM", "API Key Status: $debugStatus")

        // Safety override: if label looks like vendor-id and OpenRouter key is missing but Groq key is present, prefer Groq
        if (effectiveProvider != "OpenRouter"
            && rawLabel.contains("/")
            && !keyManager.hasApiKey("openrouter_api_key")
            && (keyManager.hasApiKey("groq_api_key") || GroqProxyConfig.isConfigured())) {
            val model = modelForVendor ?: rawLabel
            android.util.Log.w("NoteEditScreenDM", "Safety override -> using Groq with model='$model'")
            return processWithGroq(context, fullPrompt, model)
        }


        // Route by effective provider; pass explicit model when available
        val result = when (effectiveProvider) {
            "OpenRouter" -> {
                val id = (openRouterIdToUse ?: modelForVendor).orEmpty()
                if (id.isEmpty()) {
                    android.util.Log.e("NoteEditScreenDM", "OpenRouter selected but model id is empty")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Select an OpenRouter model in AI Models > Notepad Reformat", Toast.LENGTH_LONG).show()
                    }
                    null
                } else {
                    processWithOpenRouter(context, fullPrompt, id)
                }
            }
            "OpenAI" -> {
                processWithOpenAI(context, fullPrompt, (modelForVendor ?: rawLabel))
            }
            "Gemini" -> {
                processWithGemini(context, fullPrompt, (modelForVendor ?: rawLabel))
            }
            "Claude" -> {
                processWithClaude(context, fullPrompt, (modelForVendor ?: rawLabel))
            }
            "Cerebras" -> {
                processWithCerebras(context, fullPrompt, (modelForVendor ?: rawLabel))
            }
            else -> {
                processWithGroq(context, fullPrompt, (modelForVendor ?: rawLabel))
            }
        }

        android.util.Log.d("NoteEditScreenDM", "Provider result: ${if (result.isNullOrBlank()) "NULL/EMPTY" else "SUCCESS (${result.length} chars)"}")

        if (result.isNullOrBlank()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "AI provider returned empty response. Check API keys and network connection.", Toast.LENGTH_LONG).show()
            }
        }

        android.util.Log.d("NoteEditScreenDM", "=== tryAIReformat ENDING with result: ${result?.take(50) ?: "null"} ===")
        return result
    } catch (e: Exception) {
        android.util.Log.e("NoteEditScreenDM", "tryAIReformat exception", e)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "AI error: ${e.message ?: "unknown"}", Toast.LENGTH_LONG).show()
        }
        null
    }
}

private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.MINUTES)
    .writeTimeout(2, TimeUnit.MINUTES)
    .readTimeout(3, TimeUnit.MINUTES)
    .build()

/**
 * Map a provider label/string to a concrete model id expected by each vendor API.
 * Note: This is ONLY used when the provider is a vendor (OpenAI/Gemini/Claude/Groq).
 * For OpenRouter, we always pass through the explicit vendor/model id.
 */
private fun resolveModelForProvider(selectedAiModel: String): String {
    val m = selectedAiModel.lowercase()
    return when {
        // OpenAI chat completions
        m.contains("openai") || m.contains("gpt") -> "gpt-4o-mini"
        // Google Gemini
        m.contains("gemini") || m.contains("google") || m.contains("2.0") || m.contains("2.5") -> "gemini-2.0-flash-exp"
        // Anthropic Claude
        m.contains("claude") || m.contains("anthropic") || m.contains("sonnet") || m.contains("opus") || m.contains("haiku") -> "claude-3-5-sonnet-20240620"
        // Groq — pick a currently supported model (avoid decommissioned)
        else -> "llama-3.1-70b-versatile"
    }
}

/**
 * Infer a provider label from a vendor/model id string (e.g., "groq/openai/gpt-oss-20b").
 * This is a best‑effort mapping used only when the Notepad preference accidentally stores a model id
 * instead of a provider label. It avoids incorrectly routing via OpenRouter.
 */
private fun inferProviderFromVendor(vendorModelId: String): String {
    val id = vendorModelId.lowercase()
    return when {
        // OpenAI
        id.startsWith("openai/") || id.contains("/gpt") -> "OpenAI"

        // Google Gemini
        id.startsWith("google/") || id.contains("/gemini") || id.contains("google") -> "Gemini"

        // Anthropic Claude
        id.startsWith("anthropic/") || id.contains("/claude") || id.contains("anthropic") -> "Claude"

        // Cerebras (OpenAI-compatible)
        id.startsWith("cerebras/") || id.contains("cerebras") -> "Cerebras"

        // Groq/Mistral ecosystem often exposed via Groq
        id.startsWith("groq/") ||
        id.contains("mistral") ||
        id.contains("mixtral") ||
        id.contains("llama") ||
        id.contains("gemma") -> "Groq"

        else -> "OpenAI"
    }
}

private fun normalizeDeprecatedGroqModel(model: String): String {
    return when (model.lowercase().trim()) {
        "meta-llama/llama-4-scout-17b-16e-instruct",
        "meta-llama/llama-4-maverick-17b-128e-instruct",
        "moonshotai/kimi-k2-instruct-0905" -> "openai/gpt-oss-120b"
        else -> model
    }
}

private fun getOpenAIReasoningEffort(context: Context): String {
    val value = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .getString("openai_reasoning_effort", "none")
        ?.lowercase()
        ?.trim()
        ?: "none"
    return when (value) {
        "minimal", "low", "medium", "high", "xhigh" -> value
        else -> "none"
    }
}

private fun supportsOpenAIReasoningNone(model: String): Boolean {
    val normalized = model.lowercase().removePrefix("openai/")
    return normalized.startsWith("gpt-5.4") || normalized.startsWith("gpt-5.1")
}

private suspend fun processWithOpenAI(context: Context, prompt: String, model: String): String? {
    return try {
        val key = SecureApiKeyManager.getInstance(context).getApiKey("openai_api_key")
        if (key.isNullOrBlank()) {
            android.util.Log.e("NoteEditScreenDM", "OpenAI API key is missing")
            return null
        }

        android.util.Log.d("NoteEditScreenDM", "Processing with OpenAI model: $model")

        val isGpt5Responses = model.lowercase().startsWith("gpt-5") && !model.equals("gpt-5-chat-latest", ignoreCase = true)
        val requestBody = if (isGpt5Responses) {
            JSONObject().apply {
                put("model", model)
                put("input", prompt)
                val reasoningEffort = getOpenAIReasoningEffort(context)
                if (reasoningEffort != "none" || supportsOpenAIReasoningNone(model)) {
                    put("reasoning", JSONObject().put("effort", reasoningEffort))
                }
            }
        } else {
            JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", 4000)
                put("temperature", 0.7)
            }
        }
        val req = Request.Builder()
            .url(if (isGpt5Responses) "https://api.openai.com/v1/responses" else "https://api.openai.com/v1/chat/completions")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $key")
            .build()
        val resp = httpClient().newCall(req).execute()
        if (resp.isSuccessful) {
            val json = JSONObject(resp.body?.string() ?: "")
            val content = if (isGpt5Responses) {
                json.optString("output_text").ifBlank {
                    val output = json.optJSONArray("output")
                    var textValue = ""
                    if (output != null) {
                        for (index in 0 until output.length()) {
                            val item = output.optJSONObject(index) ?: continue
                            if (item.optString("type") == "output_text") {
                                textValue = item.optString("text")
                                if (textValue.isNotBlank()) break
                            }
                        }
                    }
                    textValue
                }
            } else {
                val choices = json.getJSONArray("choices")
                if (choices.length() > 0) {
                    choices.getJSONObject(0).getJSONObject("message").getString("content")
                } else {
                    ""
                }
            }
            if (content.isNotBlank()) {
                val extracted = com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                val result = if (extracted.isNotBlank()) extracted else content
                android.util.Log.d("NoteEditScreenDM", "OpenAI success: ${result.length} chars (post-extract)")
                return result
            } else {
                android.util.Log.e("NoteEditScreenDM", "OpenAI returned no content")
            }

        } else {
            val errorBody = resp.body?.string()
            android.util.Log.e("NoteEditScreenDM", "OpenAI failed: ${resp.code} - $errorBody")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "OpenAI error: HTTP ${resp.code}", Toast.LENGTH_LONG).show()
            }
        }
        null
    } catch (e: Exception) {
        android.util.Log.e("NoteEditScreenDM", "OpenAI exception", e)
        null
    }
}

private suspend fun processWithGemini
(context: Context, prompt: String, model: String): String? {
    return try {
        val key = SecureApiKeyManager.getInstance(context).getApiKey("google_api_key")
        if (key.isNullOrBlank()) {
            android.util.Log.e("NoteEditScreenDM", "Google API key is missing")
            return null
        }

        android.util.Log.d("NoteEditScreenDM", "Processing with Gemini model: $model")

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 4000)
            })
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = httpClient().newCall(req).execute()
        if (resp.isSuccessful) {
            val json = JSONObject(resp.body?.string() ?: "")
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                if (parts.length() > 0) {
                    val content = parts.getJSONObject(0).getString("text")
                    val extracted = com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                    val result = if (extracted.isNotBlank()) extracted else content
                    android.util.Log.d("NoteEditScreenDM", "Gemini success: ${result.length} chars (post-extract)")
                    return result
                } else {
                    android.util.Log.e("NoteEditScreenDM", "Gemini returned no content parts")
                }
            } else {
                android.util.Log.e("NoteEditScreenDM", "Gemini returned no candidates")
            }
        } else {
            val errorBody = resp.body?.string()
            android.util.Log.e("NoteEditScreenDM", "Gemini failed: ${resp.code} - $errorBody")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Gemini error: HTTP ${resp.code}", Toast.LENGTH_LONG).show()
            }
        }
        null
    } catch (e: Exception) {
        android.util.Log.e("NoteEditScreenDM", "Gemini exception", e)
        null
    }
}


private suspend fun processWithCerebras(context: Context, prompt: String, model: String): String? {
    return try {
        val key = SecureApiKeyManager.getInstance(context).getApiKey("cerebras_api_key")
        if (key.isNullOrBlank()) {
            android.util.Log.e("NoteEditScreenDM", "Cerebras API key is missing")
            return null
        }

        val resolvedModel = model.removePrefix("cerebras/")
        android.util.Log.d("NoteEditScreenDM", "Processing with Cerebras model: $resolvedModel")

        val requestBody = JSONObject().apply {
            put("model", resolvedModel)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_completion_tokens", 1000)
            put("temperature", 0.7)
        }
        val req = Request.Builder()
            .url("https://api.cerebras.ai/v1/chat/completions")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $key")
            .build()
        val resp = httpClient().newCall(req).execute()
        if (resp.isSuccessful) {
            val json = JSONObject(resp.body?.string() ?: "")
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                val extracted = com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                val result = if (!extracted.isNullOrBlank()) extracted else content
                android.util.Log.d("NoteEditScreenDM", "Cerebras success: ${result.length} chars (post-extract)")
                return result
            } else {
                android.util.Log.e("NoteEditScreenDM", "Cerebras returned no choices")
            }
        } else {
            val errorBody = resp.body?.string()
            android.util.Log.e("NoteEditScreenDM", "Cerebras failed: ${resp.code} - $errorBody")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Cerebras error: HTTP ${resp.code}", Toast.LENGTH_LONG).show()
            }
        }
        null
    } catch (e: Exception) {
        android.util.Log.e("NoteEditScreenDM", "Cerebras exception", e)
        null
    }
}

private suspend fun processWithClaude(context: Context, prompt: String, model: String): String? {
    return try {
        val key = SecureApiKeyManager.getInstance(context).getApiKey("anthropic_api_key")
        if (key.isNullOrBlank()) {
            android.util.Log.e("NoteEditScreenDM", "Anthropic API key is missing")
            return null
        }

        android.util.Log.d("NoteEditScreenDM", "Processing with Claude model: $model")

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4000)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .build()
        val resp = httpClient().newCall(req).execute()
        if (resp.isSuccessful) {
            val json = JSONObject(resp.body?.string() ?: "")
            val content = json.getJSONArray("content")
            if (content.length() > 0) {
                val text = content.getJSONObject(0).getString("text")
                val extracted = com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils.extractXmlTagContent(text, "FORMATTED_TEXT")
                val result = if (extracted.isNotBlank()) extracted else text
                android.util.Log.d("NoteEditScreenDM", "Claude success: ${result.length} chars (post-extract)")
                return result
            } else {
                android.util.Log.e("NoteEditScreenDM", "Claude returned no content")
            }
        } else {
            val errorBody = resp.body?.string()
            android.util.Log.e("NoteEditScreenDM", "Claude failed: ${resp.code} - $errorBody")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Claude error: HTTP ${resp.code}", Toast.LENGTH_LONG).show()
            }
        }
        null
    } catch (e: Exception) {
        android.util.Log.e("NoteEditScreenDM", "Claude exception", e)
        null
    }
}

private suspend fun processWithGroq(context: Context, prompt: String, model: String): String? {
    return try {
        val resolvedModel = normalizeDeprecatedGroqModel(model)
        val userKey = SecureApiKeyManager.getInstance(context).getApiKey("groq_api_key")
        val useProxy = GroqProxyConfig.shouldUseProxy(userKey)
        val key = if (!userKey.isNullOrBlank()) {
            android.util.Log.d("NoteEditScreenDM", "Using user's Groq API key for model: $resolvedModel")
            userKey
        } else if (useProxy) {
            android.util.Log.d("NoteEditScreenDM", "Using hosted Groq proxy for model: $resolvedModel")
            ""
        } else {
            android.util.Log.e("NoteEditScreenDM", "Groq API key is missing")
            return null
        }

        android.util.Log.d("NoteEditScreenDM", "Processing with Groq model: $resolvedModel, directKeyPresent=${key.isNotBlank()}")

        val body = JSONObject().apply {
            put("model", resolvedModel)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 4000)
            put("temperature", 0.7)
        }
        val requestBuilder = Request.Builder()
            .url(GroqProxyConfig.endpoint("/openai/v1/chat/completions", useProxy))
            .post(body.toString().toRequestBody("application/json".toMediaType()))

        GroqProxyConfig.applyHeaders(requestBuilder, key, useProxy)

        val req = requestBuilder.build()
        val resp = httpClient().newCall(req).execute()
        android.util.Log.d("NoteEditScreenDM", "Groq response code: ${resp.code}")

        if (resp.isSuccessful) {
            val responseBody = resp.body?.string()
            android.util.Log.d("NoteEditScreenDM", "Groq response body: $responseBody")

            if (responseBody.isNullOrBlank()) {
                android.util.Log.e("NoteEditScreenDM", "Groq returned empty response body")
                return null
            }

            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            android.util.Log.d("NoteEditScreenDM", "Groq choices length: ${choices.length()}")

            if (choices.length() > 0) {
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                val extracted = com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                val result = if (extracted.isNotBlank()) extracted else content
                android.util.Log.d("NoteEditScreenDM", "Groq success: ${result.length} chars (post-extract)")
                return result
            } else {
                android.util.Log.e("NoteEditScreenDM", "Groq returned no choices")
            }
        } else {
            val errorBody = resp.body?.string()
            android.util.Log.e("NoteEditScreenDM", "Groq failed: ${resp.code} - $errorBody")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Groq error: HTTP ${resp.code}", Toast.LENGTH_LONG).show()
            }
        }
        null
    } catch (e: Exception) {
        android.util.Log.e("NoteEditScreenDM", "Groq exception", e)
        null
    }
}

// OpenRouter provider (unified API) — single implementation
private suspend fun processWithOpenRouter(context: Context, prompt: String, modelId: String): String? {
    return try {
        val key = SecureApiKeyManager.getInstance(context).getApiKey("openrouter_api_key") ?: return null
        android.util.Log.d("NoteEditScreenDM", "Reformat via OpenRouter model=$modelId")

        val body = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 4000)
            put("temperature", 0.7)
        }

        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $key")
            .build()

        val resp = httpClient().newCall(req).execute()
        if (resp.isSuccessful) {
            val json = JSONObject(resp.body?.string() ?: "")
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = message?.optString("content")
                if (!content.isNullOrBlank()) {
                    val extracted = com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils.extractXmlTagContent(content, "FORMATTED_TEXT")
                    val result = if (extracted.isNotBlank()) extracted else content
                    return result
                }
            }
        } else {
            val bodyStr = resp.body?.string()
            android.util.Log.e("NoteEditScreenDM", "OpenRouter failed: ${resp.code} ${bodyStr ?: ""}")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "AI error (OpenRouter): HTTP ${resp.code}", Toast.LENGTH_LONG).show()
            }
        }
        null
    } catch (e: Exception) {
        android.util.Log.e("NoteEditScreenDM", "OpenRouter exception", e)
        null
    }
}
