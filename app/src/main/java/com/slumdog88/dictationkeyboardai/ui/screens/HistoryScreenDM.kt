package com.slumdog88.dictationkeyboardai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build
import com.slumdog88.dictationkeyboardai.BubbleOverlayService

import com.slumdog88.dictationkeyboardai.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.slumdog88.dictationkeyboardai.ErrorState
import com.slumdog88.dictationkeyboardai.FailurePoint
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.LogEntry
import com.slumdog88.dictationkeyboardai.applyRecordingLimit
import com.slumdog88.dictationkeyboardai.deleteAllRecordings
import com.slumdog88.dictationkeyboardai.reprocessAudio
import com.slumdog88.dictationkeyboardai.shareAudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.slumdog88.dictationkeyboardai.utils.LogStorageManager



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreenDM() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val logStorageManager = remember { LogStorageManager.getInstance(context) }

    var isLoading by remember { mutableStateOf(true) }
    var recordingHistoryLimit by remember { mutableStateOf(loadRecordingLimit(context)) }
    var lastParsedLogsHash by remember { mutableIntStateOf(0) }
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    val mediaPlayer = remember { MediaPlayer() }
    var currentlyPlayingAudio by remember { mutableStateOf<String?>(null) }

    // Track entries currently being retried to show spinner and prevent duplicates
    var retryingEntries by remember { mutableStateOf(setOf<String>()) }

    // Helper to generate unique key for entry matching
    fun LogEntry.uniqueKey(): String = "$timestamp|${audioFileName ?: ""}"

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) { }
        }
    }

    fun loadRecordingHistory() {
        coroutineScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val logs = logStorageManager.readLogs()
            val logsHash = logs.hashCode()

            if (logsHash == lastParsedLogsHash && logs.isNotBlank()) {
                launch(Dispatchers.Main) { isLoading = false }
                return@launch
            }

            if (logs.isBlank()) {
                launch(Dispatchers.Main) {
                    logEntries = emptyList()
                    isLoading = false
                    lastParsedLogsHash = logsHash
                }
                return@launch
            }

            try {
                val historyLimit = prefs.getInt("recording_history_limit", 50).coerceAtLeast(0)
                val segments = logs.split("---\n")
                    .filter { it.isNotBlank() }

                val limitedSegments = when {
                    historyLimit <= 0 -> emptyList()
                    historyLimit >= segments.size -> segments
                    else -> segments.takeLast(historyLimit)
                }

                val entries = limitedSegments
                    .asReversed() // newest entries first
                    .mapNotNull { logText ->
                        var timestamp = ""
                        var audioFile = ""
                        var transcription = ""
                        var processedText = ""
                        var selectedText = ""
                        var errorType: String? = null
                        var errorService: String? = null
                        var errorMessage: String? = null

                        val lines = logText.trim().split("\n")
                        for (line in lines) {
                            when {
                                line.startsWith("[") -> {
                                    timestamp = line.substringAfter("[").substringBefore("]")
                                }
                                line.startsWith("Audio:") -> {
                                    audioFile = line.substringAfter("Audio: ").trim()
                                }
                                line.startsWith("Transcription:") -> {
                                    transcription = line.substringAfter("Transcription: ").trim()
                                }
                                line.startsWith("AI Processed:") -> {
                                    processedText = line.substringAfter("AI Processed: ").trim()
                                }
                                line.startsWith("Selected Text:") -> {
                                    selectedText = line.substringAfter("Selected Text: ").trim()
                                }
                                line.startsWith("Error:") -> {
                                    errorType = line.substringAfter("Error: ").trim()
                                }
                                line.startsWith("Error Service:") -> {
                                    errorService = line.substringAfter("Error Service: ").trim()
                                }
                                line.startsWith("Error Message:") -> {
                                    errorMessage = line.substringAfter("Error Message: ").trim()
                                }
                            }
                        }

                        // Construct error state if error fields are present
                        val errorState = if (errorType != null) {
                            ErrorState(
                                failurePoint = try {
                                    FailurePoint.valueOf(errorType)
                                } catch (e: Exception) {
                                    FailurePoint.TRANSCRIPTION
                                },
                                serviceName = errorService ?: "Unknown",
                                errorMessage = errorMessage ?: "Unknown error"
                            )
                        } else null

                        // Build entry and drop if it has no visible content
                        val entry = LogEntry(
                            timestamp = timestamp,
                            audioFileName = audioFile.ifBlank { null },
                            context = selectedText,
                            userMessage = transcription,
                            aiProcessed = processedText.ifBlank { transcription },
                            rawText = logText,
                            isReprocessed = logText.contains("(REPROCESSED)"),
                            errorState = errorState
                        )
                        if (entry.timestamp.isBlank()) null
                        else if (entry.isFailed()) entry  // Keep failed entries even with no content
                        else if (entry.getAiProcessedContent().isBlank()) null
                        else entry
                    }

                launch(Dispatchers.Main) {
                    logEntries = entries
                    isLoading = false
                    lastParsedLogsHash = logsHash
                }
            } catch (_: Exception) {
                launch(Dispatchers.Main) {
                    logEntries = emptyList()
                    isLoading = false
                    lastParsedLogsHash = logsHash
                }
            }
        }
    }
    
    // Listen for log updates to auto-refresh the list
    DisposableEffect(Unit) {
        val filter = android.content.IntentFilter("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED")
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                loadRecordingHistory()
            }
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) { }

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }

    // Listen for retry state updates to sync with keyboard and prevent duplicates
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BubbleOverlayService.ACTION_RETRY_STARTED)
            addAction(BubbleOverlayService.ACTION_RETRY_COMPLETED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val audioPath = intent?.getStringExtra("audio_file_path") ?: return
                // Extract filename from path to match with entries
                val audioFileName = audioPath.substringAfterLast("/")

                when (intent.action) {
                    BubbleOverlayService.ACTION_RETRY_STARTED -> {
                        // Find entry by audio filename and add to retrying set
                        logEntries.find { it.audioFileName == audioFileName }?.let { entry ->
                            retryingEntries = retryingEntries + entry.uniqueKey()
                        }
                    }
                    BubbleOverlayService.ACTION_RETRY_COMPLETED -> {
                        // Remove from retrying set
                        logEntries.find { it.audioFileName == audioFileName }?.let { entry ->
                            retryingEntries = retryingEntries - entry.uniqueKey()
                        }
                        // Refresh history to show updated entry
                        loadRecordingHistory()
                    }
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) { }

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }

    fun deleteRecording(entry: LogEntry) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val logs = logStorageManager.readLogs()
                
                // Remove the specific log segment
                if (logs.isNotBlank()) {
                    val segments = logs.split("---\n")
                        .filter { it.isNotBlank() }
                        .toMutableList()
                    
                    val targetIndex = segments.indexOfFirst { seg ->
                        val segTrim = seg.trim()
                        segTrim == entry.rawText.trim() || segTrim.startsWith("[${entry.timestamp}]")
                    }
                    
                    if (targetIndex >= 0) {
                        segments.removeAt(targetIndex)
                        val newLogs = if (segments.isEmpty()) {
                            ""
                        } else {
                            // Keep same delimiter formatting as original
                            segments.joinToString(separator = "---\n", postfix = "---\n")
                        }
                    logStorageManager.writeLogs(newLogs)
                    }
                }
                
                // Remove audio file and its metadata entry if present
                entry.audioFileName?.let { name ->
                    try {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val wwDir = java.io.File(downloadsDir, "WonderWhisper")
                        val audioFile = java.io.File(wwDir, name)
                        if (audioFile.exists()) {
                            audioFile.delete()
                        }
                    } catch (_: Exception) { }
                    
                    // Update stored set of audio file metadata
                    val existing = prefs.getStringSet("audio_files", java.util.LinkedHashSet())?.toMutableSet() ?: mutableSetOf()
                    val changed = existing.removeIf { it == name || it.startsWith("$name:") }
                    if (changed) {
                        prefs.edit().putStringSet("audio_files", existing).apply()
                    }
                }
                
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
                    HapticUtils.performHapticFeedback(context)
                    loadRecordingHistory()
                }
            } catch (_: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to delete entry", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    LaunchedEffect(Unit) { loadRecordingHistory() }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                title = { Text(text = "History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 16.dp,
                bottom = 32.dp
            )
        ) {
            item {
                // Actions Row - Optimized layout with proper alignment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left-aligned Delete All button with trashcan icon and text
                    Button(
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            deleteAllRecordings(context) { loadRecordingHistory() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("All")
                        }
                    }

                    // Spacer to push text field to center
                    Spacer(modifier = Modifier.weight(1f))

                    // Center-aligned compact number input with custom padding control
                    BasicTextField(
                        value = recordingHistoryLimit,
                        onValueChange = { input: String ->
                            val digits = input.filter { it.isDigit() }
                            recordingHistoryLimit = digits.take(4)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .width(70.dp)
                            .height(56.dp) // Compact height
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                    ) { innerTextField ->
                        // Custom layout with minimal padding
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 2.dp), // Minimal padding
                            contentAlignment = Alignment.Center
                        ) {
                            // Label positioned above the input
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Limit:",
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                )
                                innerTextField()
                            }
                        }
                    }

                    // Spacer to push Apply button to right
                    Spacer(modifier = Modifier.weight(1f))

                    // Right-aligned Apply button with text
                    Button(
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            val limit = recordingHistoryLimit.toIntOrNull() ?: 50
                            applyRecordingLimit(context, limit) { loadRecordingHistory() }
                        }
                    ) {
                        Text("Apply")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "0 = no logs. Older recordings are auto-deleted.",
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading history...",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else if (logEntries.isEmpty()) {
                    Card(
                        modifier = Modifier,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No history yet.\nYour dictation history will appear here.",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            itemsIndexed(
                items = logEntries,
                key = { index, item -> "${item.timestamp}|${item.audioFileName ?: ""}|$index" }
            ) { _, entry ->
                LogEntryCardDM(
                    entry = entry,
                    currentlyPlayingAudio = currentlyPlayingAudio,
                    isRetrying = retryingEntries.contains(entry.uniqueKey()),
                    onPlayToggle = { audioFileName ->
                        HapticUtils.performHapticFeedback(context)
                        try {
                            if (currentlyPlayingAudio == audioFileName) {
                                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                                mediaPlayer.reset()
                                currentlyPlayingAudio = null
                            } else {
                                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                                mediaPlayer.reset()

                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                val wwDir = java.io.File(downloadsDir, "WonderWhisper")
                                if (!wwDir.exists()) wwDir.mkdirs()
                                val audioFile = java.io.File(wwDir, audioFileName)
                                if (audioFile.exists()) {
                                    mediaPlayer.setDataSource(audioFile.absolutePath)
                                    mediaPlayer.prepare()
                                    mediaPlayer.start()
                                    currentlyPlayingAudio = audioFileName
                                    mediaPlayer.setOnCompletionListener {
                                        currentlyPlayingAudio = null
                                        try { it.reset() } catch (_: Exception) {}
                                    }
                                    Toast.makeText(context, "Playing: $audioFileName", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
                                    currentlyPlayingAudio = null
                                }
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show()
                            currentlyPlayingAudio = null
                            try { mediaPlayer.reset() } catch (_: Exception) {}
                        }
                    },
                    onShare = { audioFileName ->
                        HapticUtils.performHapticFeedback(context)
                        shareAudioFile(context, audioFileName)
                    },
                    onReprocess = { e ->
                        HapticUtils.performHapticFeedback(context)
                        reprocessAudio(context, e) { loadRecordingHistory() }
                    },
                    onRetry = { e ->
                        val key = e.uniqueKey()
                        if (retryingEntries.contains(key)) return@LogEntryCardDM  // Prevent duplicate
                        HapticUtils.performHapticFeedback(context)
                        retryingEntries = retryingEntries + key
                        reprocessAudio(context, e) {
                            // Callback fires immediately; actual completion tracked via broadcast
                        }
                    },
                    onDelete = { e ->
                        HapticUtils.performHapticFeedback(context)
                        deleteRecording(e)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LogEntryCardDM(
    entry: LogEntry,
    currentlyPlayingAudio: String?,
    isRetrying: Boolean,
    onPlayToggle: (String) -> Unit,
    onShare: (String) -> Unit,
    onReprocess: (LogEntry) -> Unit,
    onRetry: (LogEntry) -> Unit,
    onDelete: (LogEntry) -> Unit
) {
    val context = LocalContext.current
    val isFailedEntry = entry.isFailed()
    val accent = when {
        isFailedEntry -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val isPlaying = currentlyPlayingAudio == entry.audioFileName

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // No special background for failed entries - use subtle badge instead (per user decision)
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Subtle failure badge - small warning icon for failed entries
                if (isFailedEntry) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Failed",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = when {
                        isFailedEntry -> "${entry.timestamp} (FAILED)"
                        else -> entry.timestamp
                    },
                    color = accent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            HapticUtils.performHapticFeedback(context)
                            val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                            intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                            context.startActivity(intent)
                        }
                )
                if (entry.audioFileName != null) {
                    // Play
                    IconButton(onClick = { onPlayToggle(entry.audioFileName!!) }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = if (isPlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                    // Share
                    IconButton(onClick = { onShare(entry.audioFileName!!) }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Reprocess OR Retry button based on entry state
                    if (entry.isFailed()) {
                        // Show retry button with spinner for failed entries
                        IconButton(
                            onClick = { onRetry(entry) },
                            enabled = !isRetrying
                        ) {
                            if (isRetrying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry transcription",
                                    tint = MaterialTheme.colorScheme.error  // Use error color to indicate retry for failure
                                )
                            }
                        }
                    } else {
                        // Regular reprocess button for successful entries
                        IconButton(onClick = { onReprocess(entry) }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reprocess",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayText = remember(entry.aiProcessed, entry.userMessage, entry.errorState) {
                if (entry.isFailed()) {
                    // Show specific error type from errorState
                    when (entry.errorState?.failurePoint) {
                        FailurePoint.TRANSCRIPTION -> "Voice transcription failed"
                        FailurePoint.AI_PROCESSING -> "AI processing failed"
                        else -> "Transcription failed"
                    }
                } else {
                    entry.getAiProcessedContent().ifBlank { entry.getRawTranscriptionContent() }
                }
            }
            Text(
                text = displayText,
                color = if (isFailedEntry) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable {
                        HapticUtils.performHapticFeedback(context)
                        val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                        intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                        context.startActivity(intent)
                    }
            )
            // Details are shown in LogDetailActivity; no inline expansion here

            if (entry.audioFileName != null) {
                Spacer(modifier = Modifier.height(12.dp))
                // Only Copy buttons remain below text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            copyToClipboard(context, "Raw Text", entry.getRawTranscriptionContent())
                            HapticUtils.performHapticFeedback(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) { Text("Copy Raw") }
    
                    if (!entry.aiProcessed.isNullOrBlank()) {
                        Button(
                            onClick = {
                                copyToClipboard(context, "AI Processed Text", entry.getAiProcessedContent())
                                HapticUtils.performHapticFeedback(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) { Text("Copy AI") }
                    }
    
                    Spacer(modifier = Modifier.weight(1f))
    
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
        }
    }
}

private fun loadRecordingLimit(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getInt("recording_history_limit", 50).toString()
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
