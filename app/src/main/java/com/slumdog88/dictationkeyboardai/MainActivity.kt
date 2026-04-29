package com.slumdog88.dictationkeyboardai

import com.slumdog88.dictationkeyboardai.ui.components.BrutalCard
import com.slumdog88.dictationkeyboardai.ui.components.BrutalCardTitle
import com.slumdog88.dictationkeyboardai.ui.components.BrutalAnimatedBackground
import com.slumdog88.dictationkeyboardai.ui.components.BrutalHeader
import com.slumdog88.dictationkeyboardai.LogEntry

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset as ComposeOffset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.provider.Settings
import android.os.Build
import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import android.view.LayoutInflater
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentContainerView
import com.slumdog88.dictationkeyboardai.network.NetworkManager
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.SecureApiKeyManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import com.slumdog88.dictationkeyboardai.NoteViewModelFactory
import com.slumdog88.dictationkeyboardai.utils.LogStorageManager
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var navigationCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Get the navigation target from intent
        val navigateTo = intent?.getStringExtra("navigate_to")
        val openNoteId = intent?.getStringExtra("note_id")

        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                MainScreen(initialRoute = navigateTo, initialNoteId = openNoteId)
            }
        }
    }


    fun setNavigationCallback(callback: () -> Unit) {
        navigationCallback = callback
    }
}

// UI Components

@Composable
fun BrutalistActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = colorResource(id = R.color.nb_pink)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Multi-layer shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 10.dp, y = 10.dp)
                .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main button
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .background(accentColor, RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
fun BrutalistSettingsCard(
    accentColor: Color = colorResource(id = R.color.nb_cyan),
    content: @Composable () -> Unit
) {
    val surfaceColor = Color(0xFF1F1F1F) // nb_charcoal per guide template
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Multi-layer shadow system (guide: 10dp colored, 5dp black)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 10.dp, y = 10.dp)
                .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )

        // Main card (guide: 16dp radius, 3dp black border, elevation 16dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .background(surfaceColor, RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(20.dp) // compact vs full 24dp per guide
        ) {
            content()
        }
    }
}

@Composable
fun BrutalistSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF1BE7FF) // Brand: Electric Cyan (Accent/Info)
    
    Box(
        modifier = modifier
    ) {
        // Shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(Color.Black, RoundedCornerShape(8.dp))
        )
        
        // Main button
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(8.dp),
                    ambientColor = Color.White.copy(alpha = 0.2f),
                    spotColor = accentColor
                )
                .background(accentColor, RoundedCornerShape(8.dp))
                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.3.sp
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun BrutalistEmptyState(
    icon: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            tint = Color(0xFF666666),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF666666),
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                letterSpacing = 0.3.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                fontSize = 14.sp,
                color = Color(0xFF666666),
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
            )
        )
    }
}

@Composable
fun LogEntryCard(
    entry: LogEntry,
    currentlyPlayingAudio: String?,
    onAudioClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onReprocessClick: (LogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = if (entry.isReprocessed) colorResource(id = R.color.nb_orange) else colorResource(id = R.color.nb_cyan)
    
    BrutalCard(accentColor = accentColor) {
        val isPlaying = currentlyPlayingAudio == entry.audioFileName
        
        Column {
            // Timestamp with inline play button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (entry.isReprocessed) "⟲ ${entry.timestamp} (REPROCESSED)" else entry.timestamp,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.2.sp
                    ),
                    modifier = Modifier
                        .clickable {
                            HapticUtils.performHapticFeedback(context)
                            val intent = android.content.Intent(context, LogDetailActivity::class.java)
                            intent.putExtra("rawLog", entry.rawText)
                            context.startActivity(intent)
                        }
                        .weight(1f)
                )
                
                // Inline play button (only show if audio file exists)
                if (entry.audioFileName != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                onAudioClick(entry.audioFileName!!)
                                HapticUtils.performHapticFeedback(context)
                            }
                            .background(
                                Color(0xFF00F5FF).copy(alpha = if (isPlaying) 0.2f else 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = if (isPlaying) Color(0xFF00F5FF) else Color(0xFF00F5FF).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00F5FF),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display AI processed text by default, fallback to raw transcription
            val displayText = entry.getAiProcessedContent().ifBlank { entry.getRawTranscriptionContent() }
            
            // Main content - open detailed view on tap
            Text(
                text = displayText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .clickable {
                        HapticUtils.performHapticFeedback(context)
                        val intent = android.content.Intent(context, LogDetailActivity::class.java)
                        intent.putExtra("rawLog", entry.rawText)
                        context.startActivity(intent)
                    }
                    .fillMaxWidth()
            )
            
            // Removed inline expansion; details shown on dedicated screen
            
            if (entry.audioFileName != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistSmallButton(
                        text = if (isPlaying) "PAUSE" else "PLAY",
                        onClick = { onAudioClick(entry.audioFileName!!) }
                    )
                    BrutalistSmallButton(
                        text = "SHARE",
                        onClick = { onShareClick(entry.audioFileName!!) }
                    )
                    BrutalistSmallButton(
                        text = "REPROCESS",
                        onClick = { onReprocessClick(entry) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val context = LocalContext.current
                    BrutalistSmallButton(
                        text = "COPY RAW",
                        onClick = {
                            val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                            val clip = ClipData.newPlainText("Raw Text", entry.getRawTranscriptionContent())
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Raw text copied", Toast.LENGTH_SHORT).show()
                            HapticUtils.performHapticFeedback(context)
                        }
                    )
                    if (!entry.aiProcessed.isNullOrBlank()) {
                        BrutalistSmallButton(
                            text = "COPY AI",
                            onClick = {
                                val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                                val clip = ClipData.newPlainText("AI Processed Text", entry.getAiProcessedContent())
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "AI processed text copied", Toast.LENGTH_SHORT).show()
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = colorResource(id = R.color.nb_lime)
    
    BrutalCard(accentColor = accentColor, modifier = modifier.clickable { onClick() }) {
        Column {
            // Title
            Text(
                text = note.title.uppercase(),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.2.sp
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content preview
            Text(
                text = note.content.take(100) + if (note.content.length > 100) "..." else "",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    lineHeight = 18.sp
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrutalistSmallButton(
                    text = "EDIT",
                    onClick = onClick
                )
                BrutalistSmallButton(
                    text = "DELETE",
                    onClick = onDeleteClick
                )
            }
        }
    }
}

// Helper functions
fun deleteAllRecordings(context: Context, callback: () -> Unit) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    // Get all audio files before clearing logs
    val audioFiles = prefs.getStringSet("audio_files", java.util.LinkedHashSet())?.toSet() ?: emptySet()
    val outputDir = getPublicAudioDirectory(context)
    
    // Delete all audio files
    var deletedCount = 0
    for (audioFileEntry in audioFiles) {
        val parts = audioFileEntry.split(":")
        if (parts.size >= 1) {
            val fileName = parts[0]
            val file = java.io.File(outputDir, fileName)
            try {
                if (file.exists()) {
                    file.delete()
                    deletedCount++
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error deleting audio file: $fileName", e)
            }
        }
    }
    
    // Clear all logs and audio metadata
    LogStorageManager.getInstance(context).clear()
    prefs.edit()
        .remove("audio_files")
        .apply()
    
    callback()
}

fun applyRecordingLimit(context: Context, limit: Int, callback: () -> Unit) {
    // Save the new limit
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putInt("recording_history_limit", limit).apply()
    
    // Apply the limit immediately
    LogsActivity.enforceRecordingHistoryLimit(context)
    
    callback()
}

fun playAudioFile(context: Context, audioFileName: String) {
    val mediaPlayer = android.media.MediaPlayer()
    try {
        val audioDir = getPublicAudioDirectory(context)
        val audioFile = java.io.File(audioDir, audioFileName)
        if (audioFile.exists()) {
            mediaPlayer.setDataSource(audioFile.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            android.widget.Toast.makeText(context, "Playing: $audioFileName", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Audio file not found", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error playing audio", e)
        android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
    }
    mediaPlayer.setOnCompletionListener {
        it.release()
    }
}

fun shareAudioFile(context: Context, audioFileName: String) {
    try {
        val audioDir = getPublicAudioDirectory(context)
        val audioFile = java.io.File(audioDir, audioFileName)
        if (audioFile.exists()) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                audioFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "WonderWhisper Recording")
                putExtra(Intent.EXTRA_TEXT, "WonderWhisper audio recording: ${audioFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share audio file"))
        } else {
            android.widget.Toast.makeText(context, "Audio file not found", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error sharing audio file", e)
        android.widget.Toast.makeText(context, "Error sharing audio file", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun reprocessAudio(context: Context, entry: LogEntry, callback: () -> Unit) {
    if (entry.audioFileName == null) {
        android.widget.Toast.makeText(context, "No audio file available", android.widget.Toast.LENGTH_SHORT).show()
        callback()
        return
    }

    val outputDir = getPublicAudioDirectory(context)
    val audioFile = java.io.File(outputDir, entry.audioFileName)
    
    if (!audioFile.exists()) {
        android.widget.Toast.makeText(context, "Audio file not found: ${entry.audioFileName}", android.widget.Toast.LENGTH_SHORT).show()
        callback()
        return
    }

    android.widget.Toast.makeText(context, "Reprocessing audio...", android.widget.Toast.LENGTH_SHORT).show()
    
    // Start BubbleOverlayService for reprocessing
    val serviceIntent = android.content.Intent(context, BubbleOverlayService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
    } else {
        context.startService(serviceIntent)
    }

    // Build context string from the log entry (App / Selected / Screen / Clipboard)
    val contextLines = mutableListOf<String>()
    entry.rawText.split("\n").forEach { line ->
        when {
            line.startsWith("App: ") -> contextLines.add(line)
            line.startsWith("Selected Text: ") && !line.endsWith("Selected Text: ") -> contextLines.add(line)
            line.startsWith("Screen: ") && !line.endsWith("Screen: ") -> contextLines.add(line)
            line.startsWith("Clipboard: ") && !line.endsWith("Clipboard: ") -> contextLines.add(line)
        }
    }
    val bundledContext = if (contextLines.isNotEmpty()) contextLines.joinToString("\n") else null

    // Send broadcast and direct service action for reliability
    val reprocessIntent = android.content.Intent(BubbleOverlayService.ACTION_REPROCESS_AUDIO).apply {
        setClass(context, BubbleOverlayService::class.java)
        putExtra("audio_file_path", audioFile.absolutePath)
        putExtra("audio_file_name", entry.audioFileName)
        if (bundledContext != null) putExtra("context", bundledContext)
    }
    context.sendBroadcast(reprocessIntent)

    val directIntent = android.content.Intent(BubbleOverlayService.ACTION_REPROCESS_AUDIO_DIRECT).apply {
        setClass(context, BubbleOverlayService::class.java)
        putExtra("audio_file_path", audioFile.absolutePath)
        putExtra("audio_file_name", entry.audioFileName)
        if (bundledContext != null) putExtra("context", bundledContext)
    }
    context.startService(directIntent)
    
    callback()
}

fun openNoteForEditing(context: Context, note: Note) {
    val intent = android.content.Intent(context, MainActivity::class.java)
    intent.putExtra("navigate_to", "note")
    intent.putExtra("note_id", note.id)
    context.startActivity(intent)
}

fun showDeleteConfirmation(context: Context, note: Note, callback: () -> Unit) {
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle("Delete Note")
        .setMessage("Are you sure you want to delete this note?")
        .setPositiveButton("Delete") { _, _ ->
            // Delete the note
            val notePadManager = NotePadManager(context)
            notePadManager.deleteNote(note.id)
            callback()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun startNoteRecording(context: Context) {
    val intent = android.content.Intent("com.slumdog88.dictationkeyboardai.ACTION_START_NOTE")
    context.sendBroadcast(intent)
}

fun stopNoteRecording(context: Context) {
    val intent = android.content.Intent("com.slumdog88.dictationkeyboardai.ACTION_STOP_NOTE")
    context.sendBroadcast(intent)
}

private fun getPublicAudioDirectory(context: Context): java.io.File {
    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
    val wonderWhisperDir = java.io.File(downloadsDir, "WonderWhisper")
    
    if (!wonderWhisperDir.exists()) {
        wonderWhisperDir.mkdirs()
    }
    
    return wonderWhisperDir
}

private fun parseLogEntry(rawLog: String): LogEntry? {
    val lines = rawLog.split("\n")
    if (lines.isEmpty()) {
        return null
    }
    
    var timestamp = ""
    var audioFileName: String? = null
    var appName: String? = null
    var selectedText: String? = null
    var clipboardText: String? = null
    var transcription: String? = null
    var aiProcessed: String? = null
    var isReprocessed = false
    
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("[") && (line.endsWith("]") || line.contains("] (REPROCESSED)")) -> {
                if (line.contains("] (REPROCESSED)")) {
                    val endIndex = line.indexOf("] (REPROCESSED)")
                    timestamp = line.substring(1, endIndex)
                    isReprocessed = true
                } else {
                    timestamp = line.substring(1, line.length - 1)
                    isReprocessed = false
                }
                i++
            }
            line.startsWith("Audio: ") -> {
                audioFileName = line.substring(7)
                i++
            }
            line.startsWith("App: ") -> {
                appName = line.substring(5)
                i++
            }
            line.startsWith("Selected Text: ") -> {
                selectedText = line.substring(15)
                i++
            }
            line.startsWith("Clipboard: ") -> {
                clipboardText = line.substring(11)
                i++
            }
            line.startsWith("Transcription: ") -> {
                transcription = line.substring(15)
                i++
            }
            line.startsWith("AI Processed: ") -> {
                aiProcessed = line.substring(14)
                i++
            }
            else -> {
                i++
            }
        }
    }
    
    if (timestamp.isEmpty()) {
        return null
    }
    
    val context = buildString {
        if (!appName.isNullOrBlank()) {
            append("App: $appName")
        }
        if (!selectedText.isNullOrBlank()) {
            if (isNotEmpty()) append(" | ")
            append("Selected: $selectedText")
        }
        if (!clipboardText.isNullOrBlank()) {
            if (isNotEmpty()) append(" | ")
            append("Clipboard: $clipboardText")
        }
    }.takeIf { it.isNotEmpty() }
    
    return LogEntry(
        timestamp = timestamp,
        audioFileName = audioFileName,
        context = context,
        userMessage = transcription,
        aiProcessed = aiProcessed,
        rawText = rawLog,
        isReprocessed = isReprocessed,
        performanceMetrics = PerformanceMetrics.empty()
    )
}

// Define navigation items
sealed class Screen(val route: String, val label: String, val icon: Int) {
    object Settings : Screen("settings", "SETTINGS", R.drawable.ic_settings)
    object History : Screen("history", "HISTORY", R.drawable.ic_history)
    object Notepad : Screen("notepad", "NOTEPAD", R.drawable.ic_edit_note)
    object HowToGuide : Screen("how_to_guide", "HOW-TO", R.drawable.ic_help)
}

val navigationItems = listOf(
    Screen.Settings,
    Screen.History,
    Screen.Notepad
)

@Composable
private fun BrutalTheme(content: @Composable () -> Unit) {
    val pink = Color(0xFFFF006E)
    val cyan = Color(0xFF00F5FF)
    val base = Color(0xFF1A1A1A)
    val onBase = Color(0xFFFFFFFF)
    val charcoal = Color(0xFF2B2B2B)
    val lime = Color(0xFF8AC926)
    val orange = Color(0xFFFF7F00)

    val scheme = darkColorScheme(
        primary = pink,
        onPrimary = onBase,
        secondary = cyan,
        onSecondary = onBase,
        background = base,
        surface = charcoal,
        onBackground = onBase,
        onSurface = onBase
    );

    MaterialTheme(
        colorScheme = scheme,
        content = content
    );
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(initialRoute: String? = null, initialNoteId: String? = null) {
    val navController = androidx.navigation.compose.rememberNavController()
    // Set initial route based on intent parameter, defaulting to Settings
    var selectedRoute by remember { 
        mutableStateOf(
            when (initialRoute) {
                "notepad" -> Screen.Notepad.route
                "history" -> Screen.History.route
                "settings" -> Screen.Settings.route
                else -> Screen.Settings.route
            }
        )
    }
    val context = LocalContext.current

    // Set up navigation callback for the activity (use Activity to mirror other card transitions)
    LaunchedEffect(navController) {
        if (context is MainActivity) {
            context.setNavigationCallback {
                context.startActivity(android.content.Intent(context, HowToGuideActivity::class.java))
            }
        }
    }

    // Observe overlay nav destination to control bottom bar visibility
    val overlayBackStackEntry by navController.currentBackStackEntryAsState()
    val overlayRoute = overlayBackStackEntry?.destination?.route
    val isOverlayActive = overlayRoute != null && overlayRoute != "__root__"

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        bottomBar = {
            if (!isOverlayActive) {
                com.slumdog88.dictationkeyboardai.ui.components.DarkBottomNav(
                    selectedRoute = selectedRoute,
                    onRouteSelected = { route ->
                        selectedRoute = route
                    }
                )
            }
        }
    ) { innerPadding ->
        // Reactive pager that follows the thumb
        val pages = remember { listOf(Screen.Settings, Screen.History, Screen.Notepad) }
        val initialPage = remember(initialRoute) {
            when (initialRoute) {
                "notepad" -> 2
                "history" -> 1
                else -> 0
            }
        }
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })

        // After a horizontal swipe completes, briefly disable further horizontal swipes
        // to give vertical lists first dibs on touch. This reduces the "stuck in swipe"
        // feeling when trying to scroll down immediately after paging.
        var pagerUserScrollEnabled by remember { mutableStateOf(true) }
        LaunchedEffect(pagerState.isScrollInProgress) {
            if (!pagerState.isScrollInProgress) {
                pagerUserScrollEnabled = false
                // Short cooldown is enough to prioritize vertical scroll start
                delay(160)
                pagerUserScrollEnabled = true
            }
        }

        // Prevent feedback loop between pager and bottom bar selection
        var isProgrammaticScroll by remember { mutableStateOf(false) }

        // Sync pager -> bottom bar (only when user swipes)
        LaunchedEffect(pagerState.currentPage) {
            if (!isProgrammaticScroll) {
                selectedRoute = pages[pagerState.currentPage].route
            }
        }
        // Sync bottom bar -> pager (animate and guard against feedback)
        LaunchedEffect(selectedRoute) {
            val targetIndex = pages.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
            if (targetIndex != pagerState.currentPage) {
                isProgrammaticScroll = true
                try {
                    pagerState.animateScrollToPage(targetIndex)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        // Nested scroll connection to bias vertical deltas over horizontal, and to lock out
        // horizontal consumption for a brief cooldown after a page settles.
        val verticalFirstConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val ax = abs(available.x)
                    val ay = abs(available.y)
                    // If the pager is still animating and the gesture is primarily vertical,
                    // consume horizontal deltas so the user can immediately scroll.
                    if (pagerState.isScrollInProgress && ay > ax) {
                        return Offset(x = available.x, y = 0f)
                    }
                    // During cooldown, block horizontal scroll entirely so vertical can start.
                    if (!pagerUserScrollEnabled) {
                        return Offset(x = available.x, y = 0f)
                    }
                    // If the user's movement is mostly vertical (thumb-diagonal),
                    // consume horizontal to avoid accidental side-swipes.
                    if (ay > ax * 0.8f) {
                        return Offset(x = available.x, y = 0f)
                    }
                    return Offset.Zero
                }
            }
        }

        // Layer main content (respecting Scaffold padding) and a full-screen overlay NavHost
        Box(modifier = Modifier.fillMaxSize()) {
            // Paged main content inside Scaffold's content area
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .nestedScroll(verticalFirstConnection)
                    .fillMaxSize()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = pagerUserScrollEnabled,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = tween<Float>(
                            durationMillis = 140,
                            easing = LinearOutSlowInEasing
                        )
                    )
                ) { page ->
                    when (pages[page]) {
                        Screen.Settings -> com.slumdog88.dictationkeyboardai.ui.screens.MainMenuScreen(navController = navController)
                        Screen.History -> com.slumdog88.dictationkeyboardai.ui.screens.HistoryScreenDM()
                        Screen.Notepad -> {
                            LaunchedEffect(initialNoteId) {
                                if (!initialNoteId.isNullOrBlank()) {
                                    navController.navigate("note/${initialNoteId}")
                                }
                            }
                            com.slumdog88.dictationkeyboardai.ui.screens.NotepadScreenDM(navController)
                        }
                        else -> { /* no-op */ }
                    }
                }
            }

            // Full-screen overlay NavHost (not affected by Scaffold bottomBar visibility)
            androidx.navigation.compose.NavHost(
                navController = navController,
                startDestination = "__root__",
                modifier = Modifier.matchParentSize()
            ) {
                // Use a full-screen placeholder so AnimatedContent size doesn't change (avoids scale/maximize/minimize)
                composable("__root__") { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) }
                composable(Screen.HowToGuide.route) {
                    com.slumdog88.dictationkeyboardai.ui.screens.HowToGuideScreen()
                }
                // Note editor route (overlay) with slide-in-from-right transitions (match pager feel)
                composable(
                    route = "note/{id}",
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(durationMillis = 250)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(durationMillis = 250)
                        )
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(durationMillis = 250)
                        )
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(durationMillis = 250)
                        )
                    }
                ) { backStackEntry ->
                    val noteId = backStackEntry.arguments?.getString("id").orEmpty()
                    com.slumdog88.dictationkeyboardai.ui.screens.NoteEditScreenDM(
                        noteId = noteId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun BrutelistNavigationBar(selectedRoute: String, onRouteSelected: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Define accent colors for each navigation item
    val navAccentColors = mapOf(
        Screen.Settings.route to Color(0xFFFF006E), // Pink
        Screen.History.route to Color(0xFF00F5FF), // Cyan
        Screen.Notepad.route to Color(0xFF8AC926)  // Lime
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        // Optimized single shadow for performance
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
        )

        // Main NavigationBar with optimized styling
        NavigationBar(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.2f),
                    spotColor = Color(0xFFFF006E).copy(alpha = 0.3f)
                )
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp)),
            containerColor = Color(0xFF2B2B2B), // Charcoal background
            tonalElevation = 0.dp
        ) {
            navigationItems.forEach { screen ->
                val isSelected = selectedRoute == screen.route
                val accentColor = navAccentColors[screen.route] ?: Color(0xFFFF006E)
                
                // Animated values for smooth transitions
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.03f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "scale"
                )
                val backgroundAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.1f,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "backgroundAlpha"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else Color.White,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "iconTint"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.White,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "textColor"
                )
                
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        onRouteSelected(screen.route)
                        coroutineScope.launch {
                            HapticUtils.performHapticFeedback(context)
                        }
                    },
                    icon = {
                        // Brutalist icon container with smooth animations
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .background(
                                    accentColor.copy(alpha = backgroundAlpha),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    2.dp,
                                    accentColor,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = screen.icon),
                                contentDescription = screen.label,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = screen.label,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.5.sp
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Transparent,
                        selectedTextColor = Color.Transparent,
                        unselectedIconColor = Color.Transparent,
                        unselectedTextColor = Color.Transparent,
                        indicatorColor = Color.Transparent, // We handle all styling ourselves
                        disabledIconColor = Color.Gray,
                        disabledTextColor = Color.Gray
                    )
                )
            }
        }
    }
}

// Placeholder Screens
@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavController,
    onNavigateToHowToGuideFromFragment: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isSimpleMode by remember { mutableStateOf(sharedPreferences.getBoolean("is_simple_mode", true)) }

    // Create animated background based on scroll position
    val scrollState = rememberScrollState()
    val scrollOffset by remember {
        derivedStateOf { scrollState.value.toFloat() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Animated background texture
    BrutalAnimatedBackground(scrollOffset = scrollOffset)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp)) // Reduced since we have system bar padding

            // Header with Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Box {
                        // Shadow layer
                        Text(
                            text = "WONDER",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF006E).copy(alpha = 0.6f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                lineHeight = 30.sp
                            ),
                            modifier = Modifier.offset(x = 3.dp, y = 3.dp)
                        )
                        // Outline layer
                        Text(
                            text = "WONDER",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF006E),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                lineHeight = 30.sp,
                                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        )
                        // Main text layer
                        Text(
                            text = "WONDER",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                lineHeight = 30.sp
                            )
                        )
                    }
                    Box {
                        // Shadow layer
                        Text(
                            text = "WHISPER",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF006E).copy(alpha = 0.6f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                lineHeight = 30.sp
                            ),
                            modifier = Modifier.offset(x = 3.dp, y = 3.dp)
                        )
                        // Outline layer
                        Text(
                            text = "WHISPER",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF006E),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                lineHeight = 30.sp,
                                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        )
                        // Main text layer
                        Text(
                            text = "WHISPER",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                lineHeight = 30.sp
                            )
                        )
                    }
                }
                // Mode Toggle - Enhanced visibility
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1F1F1F), RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    BrutelistToggleButton(
                        text = "SIMPLE",
                        isSelected = isSimpleMode,
                        onClick = {
                            isSimpleMode = true
                            sharedPreferences.edit().putBoolean("is_simple_mode", true).apply()
                            HapticUtils.performHapticFeedback(context)
                        }
                    )
                    BrutelistToggleButton(
                        text = "PRO",
                        isSelected = !isSimpleMode,
                        onClick = {
                            isSimpleMode = false
                            sharedPreferences.edit().putBoolean("is_simple_mode", false).apply()
                            HapticUtils.performHapticFeedback(context)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content based on mode
            if (isSimpleMode) {
                SimpleModeContent(navController)
            } else {
                ProModeContent(navController)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun BrutelistToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground
    
    // Dynamic shadow colors based on selection
    val shadowColor = if (isSelected) {
        Color(0xFFFF7F00) // Orange shadow for selected
    } else {
        Color(0xFF00F5FF) // Cyan shadow for unselected
    }

    // Brutalist button with enhanced shadow effect
    Box(
        modifier = Modifier
            .height(40.dp) // Increased height for better visibility
            .width(80.dp)   // Fixed width for consistency
            .clickable { onClick() }
    ) {
        // Multi-layer shadow for depth
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(shadowColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
        )
        // Main button layer with enhanced glow
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.White.copy(alpha = 0.2f),
                    spotColor = shadowColor.copy(alpha = 0.4f)
                )
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
        ) {
Text(
                text = text,
                style = TextStyle(
                    fontSize = 12.sp, // Slightly larger font
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.8.sp,
                    lineHeight = 14.sp
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun SimpleModeContent(navController: androidx.navigation.NavController) {
    val context = LocalContext.current

    // State for permission checks
    var hasAudioPermission by remember { mutableStateOf(false) }
    var canDrawOverlays by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isBatteryOptimizationDisabled by remember { mutableStateOf(false) }

    // Function to update all permission states
    val updatePermissions = {
        hasAudioPermission = hasAudioPermission(context)
        canDrawOverlays = canDrawOverlays(context)
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        isBatteryOptimizationDisabled = isBatteryOptimizationDisabled(context)
    }

    // Update permissions when the composable is first composed
    LaunchedEffect(Unit) {
        updatePermissions()
    }

    val allStepsComplete = hasAudioPermission && canDrawOverlays && isAccessibilityEnabled && isBatteryOptimizationDisabled

    Column {
        // Setup Steps Section
        if (!allStepsComplete) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                if (!isAccessibilityEnabled) {
                    SetupStepCard(
                        icon = R.drawable.ic_accessibility_official,
                        title = "Step 1: Enable Accessibility Service",
                        subtitle = "Required for the app to insert text into other apps",
                        isComplete = isAccessibilityEnabled,
                        onClick = { 
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            HapticUtils.performHapticFeedback(context)
                        }
                    )
                }
                if (!isBatteryOptimizationDisabled) {
                    SetupStepCard(
                        icon = R.drawable.ic_warning,
                        title = "Step 2: Disable Battery Optimization",
                        subtitle = "Prevents Android from stopping the app in the background",
                        isComplete = isBatteryOptimizationDisabled,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            HapticUtils.performHapticFeedback(context)
                        }
                    )
                }
                if (!hasAudioPermission || !canDrawOverlays) {
                    SetupStepCard(
                        icon = R.drawable.ic_warning,
                        title = "Step 3: Grant Permissions",
                        subtitle = "Audio: ${if (hasAudioPermission) "✅" else "❌"}, Overlay: ${if (canDrawOverlays) "✅" else "❌"}",
                        isComplete = hasAudioPermission && canDrawOverlays,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            HapticUtils.performHapticFeedback(context)
                        }
                    )
                }
            }
        }

        BrutelistMenuCard(
            icon = R.drawable.ic_key,
            text = "API Keys",
            onClick = {
                context.startActivity(Intent(context, ApiKeysActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_help,
            text = "Learn How to Use",
            onClick = {
                navController.navigate(Screen.HowToGuide.route)
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_settings,
            text = "Settings",
            onClick = { 
                context.startActivity(Intent(context, SimpleSettingsActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_keyboard, // Reusing keyboard icon if available or fallback to settings
            text = "Keyboard Settings",
            onClick = { 
                context.startActivity(Intent(context, KeyboardSettingsActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_book,
            text = "Custom Vocabulary",
            onClick = { 
                context.startActivity(Intent(context, VocabularyActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_lock,
            text = "Privacy & Permissions",
            onClick = { 
                context.startActivity(Intent(context, AccessibilityDisclosureActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_email,
            text = "Feedback & Bug Reports",
            onClick = { 
                context.startActivity(Intent(context, FeedbackActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_settings, // TODO: Needs unique icon
            text = "Bubble Appearance",
            onClick = { 
                context.startActivity(Intent(context, BubbleAppearanceActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_info,
            text = "About Wonder Whisper",
            onClick = { 
                context.startActivity(Intent(context, AboutActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
    }
}

@Composable
fun SetupStepCard(
    icon: Int,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    onClick: () -> Unit
) {
    val statusIcon = if (isComplete) "✅" else "❌"
    val titleColor = if (isComplete) Color.Gray else MaterialTheme.colorScheme.onBackground
    val accentColor = if (isComplete) Color(0xFF8AC926) else Color(0xFFD32F2F) // Lime for complete, Red for incomplete
    val shadowColor = accentColor.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        // Standard bottom-right shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 10.dp, y = 10.dp)
                .background(shadowColor, RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        // Main content with enhanced glow effect
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = shadowColor
                )
                .background(Color(0xFF1F1F1F), RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon with background
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accentColor.copy(alpha = 0.1f))
                    .border(2.dp, accentColor)
                    .padding(4.dp)
            ) {
                Text(
                    text = statusIcon,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Icon with enhanced styling
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accentColor.copy(alpha = 0.1f))
                    .border(2.dp, accentColor)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = icon),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = titleColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.3.sp,
                        lineHeight = 19.sp
                    )
                )
                Text(
                    text = subtitle.uppercase(),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCCCCC),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.8.sp,
                        lineHeight = 13.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = subtitle,
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun BrutelistMenuCard(icon: Int, text: String, onClick: () -> Unit) {
    // Dynamic colors based on icon type for visual variety
    val iconColors = mapOf(
        R.drawable.ic_help to Color(0xFF00F5FF), // Cyan for help
        R.drawable.ic_settings to Color(0xFFFF006E), // Pink for settings
        R.drawable.ic_book to Color(0xFF8AC926), // Lime for book/vocabulary
        R.drawable.ic_lock to Color(0xFFFF7F00), // Orange for privacy
        R.drawable.ic_email to Color(0xFF8AC926), // Lime for feedback
        R.drawable.ic_info to Color(0xFF00F5FF), // Cyan for about
        R.drawable.ic_key to Color(0xFFFF7F00), // Orange for API keys
        R.drawable.ic_megaphone to Color(0xFF00F5FF), // Cyan for AI prompt
        R.drawable.ic_mic_white to Color(0xFF8AC926), // Lime for dictation
        R.drawable.ic_accessibility_official to Color(0xFFFF006E), // Pink for accessibility
        R.drawable.ic_bug_report to Color(0xFFFF7F00), // Orange for debug
        R.drawable.ic_warning to Color(0xFFFF006E), // Pink for warnings
        R.drawable.ic_edit_note to Color(0xFF00F5FF) // Cyan for notepad
    )
    
    val accentColor = iconColors[icon] ?: MaterialTheme.colorScheme.primary
    val shadowColor = accentColor.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        // Multi-layer shadow for dramatic effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 10.dp, y = 10.dp)
                .background(shadowColor, RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        // Main content with enhanced glow effect
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = shadowColor
                )
                .background(Color(0xFF1F1F1F), RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(24.dp), // Increased padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with enhanced styling
            Box(
                modifier = Modifier
                    .size(32.dp) // Larger icon container
                    .background(accentColor.copy(alpha = 0.1f))
                    .border(2.dp, accentColor)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = icon),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(20.dp) // Slightly smaller icon to fit container
                        .align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.width(20.dp)) // Increased spacing
Text(
                    text = text.uppercase(),
                    style = TextStyle(
                        fontSize = 18.sp, // Slightly larger text
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.3.sp,
                        lineHeight = 20.sp
                    )
                )
        }
    }
}

@Composable
fun ProModeContent(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    Column {
        BrutelistMenuCard(
            icon = R.drawable.ic_key,
            text = "API Keys",
            onClick = { 
                context.startActivity(Intent(context, ApiKeysActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_help,
            text = "How-To Guide",
            onClick = {
                navController.navigate(Screen.HowToGuide.route)
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_megaphone,
            text = "AI Prompt",
            onClick = { 
                context.startActivity(Intent(context, AiPromptActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_book,
            text = "Custom Vocabulary",
            onClick = { 
                context.startActivity(Intent(context, VocabularyActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_settings,
            text = "AI Models & Settings",
            onClick = { 
                context.startActivity(Intent(context, AiModelsActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_keyboard,
            text = "Keyboard Settings",
            onClick = { 
                context.startActivity(Intent(context, KeyboardSettingsActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_mic_white,
            text = "Dictation Test",
            onClick = { 
                context.startActivity(Intent(context, DictationTestActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_settings, // TODO: Needs unique icon
            text = "Bubble Appearance",
            onClick = { 
                context.startActivity(Intent(context, BubbleAppearanceActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_accessibility_official,
            text = "Enable Accessibility Service",
            onClick = { 
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_lock,
            text = "Privacy & Permissions",
            onClick = { 
                context.startActivity(Intent(context, AccessibilityDisclosureActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_bug_report,
            text = "Debug & Testing",
            onClick = { 
                context.startActivity(Intent(context, DebugActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_email,
            text = "Feedback & Bug Reports",
            onClick = { 
                context.startActivity(Intent(context, FeedbackActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
        BrutelistMenuCard(
            icon = R.drawable.ic_info,
            text = "About Wonder Whisper",
            onClick = { 
                context.startActivity(Intent(context, AboutActivity::class.java))
                HapticUtils.performHapticFeedback(context)
            }
        )
    }
}

// Permission-checking helper functions
private fun hasAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private fun canDrawOverlays(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

private fun isBatteryOptimizationDisabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var recordingHistoryLimit by remember { mutableStateOf("50") }
    var isLoading by remember { mutableStateOf(true) }
    var lastParsedLogsHash by remember { mutableStateOf(0) }
    var currentlyPlayingAudio by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val mediaPlayer = remember { android.media.MediaPlayer() }

    // Cleanup MediaPlayer when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                android.util.Log.e("HistoryScreen", "Error disposing MediaPlayer", e)
            }
        }
    }

    fun loadRecordingHistory() {
        coroutineScope.launch(Dispatchers.IO) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val logs = LogStorageManager.getInstance(context).readLogs()
            
            // Check if logs have actually changed to avoid unnecessary parsing
            val logsHash = logs.hashCode()
            if (logsHash == lastParsedLogsHash && !logs.isBlank()) {
                // Logs haven't changed, no need to re-parse
                launch(Dispatchers.Main) {
                    isLoading = false
                }
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
                // Limit to 30 most recent entries to improve performance
                val entries = logs.split("---\n")
                    .filter { it.isNotBlank() }
                    .take(30)  // Reduced from 50 to 30 for better performance
                    .map { logText ->
                        // Parse more efficiently by iterating through lines once
                        var timestamp = ""
                        var audioFile = ""
                        var transcription = ""
                        var processedText = ""
                        var appContext = ""
                        var selectedText = ""
                        var screenContext = ""
                        var transcriptionService = ""
                        var aiModel = ""
                        
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
                                line.startsWith("App:") -> {
                                    appContext = line.substringAfter("App: ").trim()
                                }
                                line.startsWith("Selected Text:") -> {
                                    selectedText = line.substringAfter("Selected Text: ").trim()
                                }
                                line.startsWith("Screen:") -> {
                                    screenContext = line.substringAfter("Screen: ").trim()
                                }
                                line.startsWith("Transcription Service:") -> {
                                    transcriptionService = line.substringAfter("Transcription Service: ").trim()
                                }
                                line.startsWith("AI Model:") -> {
                                    aiModel = line.substringAfter("AI Model: ").trim()
                                }
                            }
                        }
                        
                        LogEntry(
                            timestamp = timestamp,
                            audioFileName = audioFile,
                            context = selectedText,
                            userMessage = transcription,
                            aiProcessed = processedText.ifBlank { transcription },
                            rawText = transcription,
                            isReprocessed = logText.contains("(REPROCESSED)")
                        )
                    }
                launch(Dispatchers.Main) {
                    logEntries = entries
                    isLoading = false
                    lastParsedLogsHash = logsHash
                }
            } catch (e: Exception) {
                Log.e("HistoryScreen", "Error parsing recording history", e)
                launch(Dispatchers.Main) {
                    logEntries = emptyList()
                    isLoading = false
                    lastParsedLogsHash = logsHash
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRecordingHistory()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Animated background texture
        BrutalAnimatedBackground(scrollOffset = 0f) // Fixed background to reduce jank
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Header
                Box {
                    // Shadow layer
                    Text(
                        text = "HISTORY",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF006E).copy(alpha = 0.6f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.5.sp,
                            lineHeight = 30.sp
                        ),
                        modifier = Modifier.offset(x = 3.dp, y = 3.dp)
                    )
                    // Outline layer
                    Text(
                        text = "HISTORY",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF006E),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.5.sp,
                            lineHeight = 30.sp,
                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    )
                    // Main text layer
                    Text(
                        text = "HISTORY",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            letterSpacing = 0.5.sp,
                            lineHeight = 30.sp
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Delete All Button
                BrutalistActionButton(
                    text = "🗑️ DELETE ALL",
                    onClick = {
                        HapticUtils.performHapticFeedback(context)
                        deleteAllRecordings(context) {
                            loadRecordingHistory()
                        }
                    },
                    accentColor = Color(0xFFFF006E)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Recording History Settings
                BrutalistSettingsCard {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "KEEP LAST",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                    letterSpacing = 0.3.sp,
                                    lineHeight = 20.sp
                                )
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = recordingHistoryLimit,
                                    onValueChange = { input ->
                                        val digitsOnly = input.filter { it.isDigit() }
                                        recordingHistoryLimit = digitsOnly.take(4)
                                    },
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(36.dp)
                                        .background(Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
                                        .border(3.dp, Color.Black, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                    ),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) { innerTextField() }
                                    }
                                )
                                
                                BrutalistSmallButton(
                                    text = "Apply",
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context)
                                        applyRecordingLimit(context, recordingHistoryLimit.toIntOrNull() ?: 50) {
                                            loadRecordingHistory()
                                        }
                                    },
                                    modifier = Modifier
                                        .height(36.dp)
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Text(
                            text = "0=no logs. Older recordings auto-deleted.",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = Color(0xFFCCCCCC),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Log Entries Header
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading history...",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp
                        )
                    }
                } else if (logEntries.isEmpty()) {
                    BrutalistEmptyState(
                        icon = R.drawable.ic_history,
                        title = "No history yet",
                        subtitle = "Your dictation history will appear here"
                    )
                }
            }
            
            // Log Entries
            items(logEntries) { entry ->
                LogEntryCard(
                    entry = entry,
                    currentlyPlayingAudio = currentlyPlayingAudio,
                    onAudioClick = { audioFileName ->
                        HapticUtils.performHapticFeedback(context)
                        if (currentlyPlayingAudio == audioFileName) {
                            // Stop current audio
                            try {
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.stop()
                                }
                                mediaPlayer.reset()
                            } catch (e: Exception) {
                                android.util.Log.e("HistoryScreen", "Error stopping audio", e)
                            }
                            currentlyPlayingAudio = null
                        } else {
                            // Stop any current audio and play new one
                            try {
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.stop()
                                }
                                mediaPlayer.reset()
                                
                                val audioDir = getPublicAudioDirectory(context)
                                val audioFile = java.io.File(audioDir, audioFileName)
                                if (audioFile.exists()) {
                                    mediaPlayer.setDataSource(audioFile.absolutePath)
                                    mediaPlayer.prepare()
                                    mediaPlayer.start()
                                    currentlyPlayingAudio = audioFileName
                                    
                                    mediaPlayer.setOnCompletionListener {
                                        currentlyPlayingAudio = null
                                    }
                                    
                                    android.widget.Toast.makeText(context, "Playing: $audioFileName", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Audio file not found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("HistoryScreen", "Error playing audio", e)
                                android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
                                currentlyPlayingAudio = null
                            }
                        }
                    },
                    onShareClick = { audioFileName ->
                        HapticUtils.performHapticFeedback(context)
                        shareAudioFile(context, audioFileName)
                    },
                    onReprocessClick = { entry ->
                        HapticUtils.performHapticFeedback(context)
                        reprocessAudio(context, entry) {
                            loadRecordingHistory()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}



@Composable
fun NotepadScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var isRecordingNote by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val notePadManager = remember { NotePadManager(context) }
    val coroutineScope = rememberCoroutineScope()

    fun loadNotes() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val loadedNotes = notePadManager.getAllNotes()
                launch(Dispatchers.Main) {
                    notes = loadedNotes
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("NotepadScreen", "Error loading notes", e)
                launch(Dispatchers.Main) {
                    notes = emptyList()
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNotes()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Animated background texture
        BrutalAnimatedBackground(scrollOffset = scrollState.value.toFloat())
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Box {
                // Shadow layer
                Text(
                    text = "NOTEPAD",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF006E).copy(alpha = 0.6f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        lineHeight = 30.sp
                    ),
                    modifier = Modifier.offset(x = 3.dp, y = 3.dp)
                )
                // Outline layer
                Text(
                    text = "NOTEPAD",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF006E),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        lineHeight = 30.sp,
                        drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                )
                // Main text layer
                Text(
                    text = "NOTEPAD",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        lineHeight = 30.sp
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Add Note Button
            BrutalistActionButton(
                text = if (isRecordingNote) "⏹️ STOP RECORDING" else "➕ ADD NEW NOTE",
                onClick = {
                    HapticUtils.performHapticFeedback(context)
                    if (isRecordingNote) {
                        stopNoteRecording(context)
                    } else {
                        startNoteRecording(context)
                    }
                    isRecordingNote = !isRecordingNote
                },
                accentColor = colorResource(id = R.color.nb_lime)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Notes List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading notes...",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    )
                }
            } else if (notes.isEmpty()) {
                BrutalistEmptyState(
                    icon = R.drawable.ic_mic_white,
                    title = "No notes yet",
                    subtitle = "Use the notification 'Take Note' button to create your first voice note"
                )
            } else {
                notes.forEach { note ->
                    NoteCard(
                        note = note,
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            navController.navigate("note/${note.id}")
                        },
                        onDeleteClick = {
                            HapticUtils.performHapticFeedback(context)
                            showDeleteConfirmation(context, note) {
                                loadNotes()
                            }
                        }
                        
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun BrutalistCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Multi-layer shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 10.dp, y = 10.dp)
                .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            content()
        }
    }
}
