package com.slumdog88.dictationkeyboardai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.ai.AIProcessingManager
import com.slumdog88.dictationkeyboardai.Note
import com.slumdog88.dictationkeyboardai.NotePadManager
import com.slumdog88.dictationkeyboardai.enums.ViewMode
import com.slumdog88.dictationkeyboardai.network.NetworkManager
import com.slumdog88.dictationkeyboardai.SecureApiKeyManager
import com.slumdog88.dictationkeyboardai.ui.components.BrutalAnimatedBackground
import com.slumdog88.dictationkeyboardai.ui.components.BrutalistSmallButton
import com.slumdog88.dictationkeyboardai.ui.components.ReformatDialog
import com.slumdog88.dictationkeyboardai.util.copy
import com.slumdog88.dictationkeyboardai.util.share
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import io.noties.markwon.Markwon
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NoteEditScreen(
    noteId: String,
    navController: androidx.navigation.NavController,
    context: android.content.Context,
    viewModel: NoteViewModel
) {
    val note by viewModel.getNoteById(noteId).collectAsState(initial = null)
    
    var viewMode by remember { mutableStateOf(ViewMode.TRANSCRIPT) }
    
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // Calculate available screen height minus keyboard space and UI elements
    val screenHeightDp = configuration.screenHeightDp.dp
    val statusBarHeight = WindowInsets.systemBars.getTop(density).dp
    val keyboardHeight = WindowInsets.ime.getBottom(density).dp
    val estimatedKeyboardHeight = if (keyboardHeight > 0.dp) keyboardHeight else 300.dp // Estimated keyboard height
    
    // Calculate dynamic text field height
    val availableHeight = screenHeightDp - statusBarHeight - estimatedKeyboardHeight - 200.dp // Reserve space for header and buttons
    val textFieldHeight = remember(availableHeight) { 
        if (availableHeight < 200.dp) 200.dp else availableHeight 
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding() // Add padding for keyboard
    ) {
        BrutalAnimatedBackground(scrollOffset = scrollState.value.toFloat())
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Text(
                text = note?.title?.uppercase() ?: "EDIT NOTE",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = colorResource(id = R.color.nb_pink),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.5.sp,
                    lineHeight = 30.sp
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Created: ${note?.getFormattedTimestamp()}",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content Toggle - Only show if note has distinct versions
            if (note?.hasDistinctVersions() == true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistSmallButton(
                        text = "TRANSCRIPT",
                        isSelected = viewMode == ViewMode.TRANSCRIPT,
                        onClick = { viewMode = ViewMode.TRANSCRIPT }
                    )
                    BrutalistSmallButton(
                        text = "AI ENHANCED",
                        isSelected = viewMode == ViewMode.AI_ENHANCED,
                        onClick = { viewMode = ViewMode.AI_ENHANCED }
                    )
                    BrutalistSmallButton(
                        text = "PREVIEW",
                        isSelected = viewMode == ViewMode.PREVIEW,
                        onClick = { viewMode = ViewMode.PREVIEW }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Content based on view mode
            when {
                // If note has distinct versions, show content based on selected tab
                note?.hasDistinctVersions() == true -> {
                    when (viewMode) {
                        ViewMode.TRANSCRIPT -> {
                            OutlinedTextField(
                                value = note?.getOriginalTranscriptContent() ?: "",
                                onValueChange = { updatedText ->
                                    note?.let {
                                        val updatedNote = it.copy(originalTranscript = updatedText)
                                        viewModel.updateNote(updatedNote)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(textFieldHeight),
                                label = { Text("Raw AI Voice Model Output") },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }
                        ViewMode.AI_ENHANCED -> {
                            OutlinedTextField(
                                value = note?.getAiProcessedContent() ?: "",
                                onValueChange = { updatedText ->
                                    note?.let {
                                        val updatedNote = it.copy(aiProcessed = updatedText)
                                        viewModel.updateNote(updatedNote)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(textFieldHeight),
                                label = { Text("AI Enhancement/Post-processing Output") },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }
                        ViewMode.PREVIEW -> {
                            // Rich text preview of Markdown content
                            val markwon = remember { Markwon.create(context) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(textFieldHeight)
                                    .border(
                                        width = 2.dp,
                                        color = colorResource(id = R.color.nb_pink),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        TextView(ctx).apply {
                                            setTextColor(android.graphics.Color.WHITE)
                                            textSize = 14f
                                        }
                                    },
                                    update = { textView ->
                                        val contentToPreview = note?.getAiProcessedContent() ?: ""
                                        markwon.setMarkdown(textView, contentToPreview)
                                    }
                                )
                            }
                        }
                    }
                }
                // If note doesn't have distinct versions, show single content field
                else -> {
                    OutlinedTextField(
                        value = note?.content ?: "",
                        onValueChange = { updatedText ->
                            note?.let {
                                val updatedNote = it.copy(content = updatedText)
                                viewModel.updateNote(updatedNote)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(textFieldHeight),
                        label = { Text("Note Content") },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrutalistSmallButton(text = "COPY", onClick = {
                    val contentToCopy = when {
                        note?.hasDistinctVersions() == true -> {
                            when (viewMode) {
                                ViewMode.TRANSCRIPT -> note?.getOriginalTranscriptContent() ?: ""
                                ViewMode.AI_ENHANCED -> note?.getAiProcessedContent() ?: ""
                                ViewMode.PREVIEW -> note?.getAiProcessedContent() ?: ""
                            }
                        }
                        else -> note?.content ?: ""
                    }
                    context.copy(contentToCopy)
                })
                BrutalistSmallButton(text = "SHARE", onClick = {
                    val contentToShare = when {
                        note?.hasDistinctVersions() == true -> {
                            when (viewMode) {
                                ViewMode.TRANSCRIPT -> note?.getOriginalTranscriptContent() ?: ""
                                ViewMode.AI_ENHANCED -> note?.getAiProcessedContent() ?: ""
                                ViewMode.PREVIEW -> note?.getAiProcessedContent() ?: ""
                            }
                        }
                        else -> note?.content ?: ""
                    }
                    context.share(contentToShare)
                })
                var showReformatDialog by remember { mutableStateOf(false) }
                if (showReformatDialog) {
                    ReformatDialog(
                        onDismiss = { showReformatDialog = false },
                        onPromptSelected = { prompt ->
                            val coroutineScope = CoroutineScope(Dispatchers.Main)
                            coroutineScope.launch {
                                val networkManager = NetworkManager()
                                val settingsManager = SettingsManager(context)
                                val secureApiKeyManager = SecureApiKeyManager.getInstance(context)
                                val aiProcessingManager = AIProcessingManager(
                                    context,
                                    networkManager,
                                    settingsManager,
                                    secureApiKeyManager
                                )
                                val reformattedContent = aiProcessingManager.processWithAI(
                                    note?.content ?: "",
                                    "",
                                    "",
                                    "",
                                    false
                                )
                                if (reformattedContent != null) {
                                    note?.let {
                                        val updatedNote = it.copy(aiProcessed = reformattedContent)
                                        viewModel.updateNote(updatedNote)
                                    }
                                }
                            }
                        }
                    )
                }
                BrutalistSmallButton(text = "REFORMAT", onClick = { showReformatDialog = true })
                BrutalistSmallButton(text = "DELETE", onClick = {
                    note?.let {
                        viewModel.deleteNote(it)
                        navController.popBackStack()
                    }
                })
            }
        }
    }
}