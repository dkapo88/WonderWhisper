package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import android.media.MediaPlayer
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.os.VibrationEffect
import android.os.Build
import kotlinx.coroutines.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.slumdog88.dictationkeyboardai.ui.screens.LogsScreenDM
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import com.slumdog88.dictationkeyboardai.utils.LogStorageManager

data class ContextInformation(
    val transcriptionService: String,
    val aiModel: String,
    val appContext: String,
    val selectedTextContext: String,

    val screenContext: String
) {
    fun hasAnyContext(): Boolean {
        return transcriptionService.isNotEmpty() || 
               aiModel.isNotEmpty() || 
               appContext.isNotEmpty() || 
               selectedTextContext.isNotEmpty() || 
               screenContext.isNotEmpty()
    }
    
    fun getContextSummary(): String {
        val items = mutableListOf<String>()
        
        if (transcriptionService.isNotEmpty()) {
            items.add("Voice: $transcriptionService")
        }
        
        if (aiModel.isNotEmpty()) {
            items.add("AI: $aiModel")
        }
        
        val contextTypes = mutableListOf<String>()
        if (appContext.isNotEmpty()) contextTypes.add("App")
        if (selectedTextContext.isNotEmpty()) contextTypes.add("Selection")

        if (screenContext.isNotEmpty()) contextTypes.add("Screen")
        
        if (contextTypes.isNotEmpty()) {
            items.add("Context: ${contextTypes.joinToString(", ")}")
        }
        
        return if (items.isNotEmpty()) {
            items.joinToString(" • ")
        } else {
            "No context available"
        }
    }
}

class LogsActivity : AppCompatActivity() {
    private lateinit var logUpdateReceiver: BroadcastReceiver
    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val logStorageManager by lazy { LogStorageManager.getInstance(this) }

    // Reactive state for log entries
    private var logEntries by mutableStateOf(emptyList<LogEntry>())

    /**
     * Gets the public WunderWhisper directory for storing audio files
     * Creates the directory if it doesn't exist
     */
    private fun getPublicAudioDirectory(): File {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val wonderWhisperDir = File(downloadsDir, "WonderWhisper")
        
        if (!wonderWhisperDir.exists()) {
            wonderWhisperDir.mkdirs()
            Log.d("LogsActivity", "Created WonderWhisper directory: ${wonderWhisperDir.absolutePath}")
        }
        
        return wonderWhisperDir
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load current recording history limit
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentLimit = prefs.getInt("recording_history_limit", 50)

        setContent {
            AppTheme {
                LogsScreenDM(
                    onBack = { finish() },
                    onBrowseAudio = { browseAllAudioFiles() },
                    onDeleteAll = { deleteAllLogsAndAudio() },
                    onApplyLimit = { limit ->
                        // Save the new limit
                        prefs.edit().putInt("recording_history_limit", limit).apply()

                        // Apply the limit immediately
                        LogsActivity.enforceRecordingHistoryLimit(this)

                        Toast.makeText(this, "Recording limit set to $limit", Toast.LENGTH_SHORT).show()
                        Log.d("LogsActivity", "Recording history limit updated to: $limit")
                    },
                    recordingHistoryLimit = currentLimit,
                    onRefresh = {
                        performHapticFeedback()
                        Log.d("LogsActivity", "Header tapped - refreshing logs")
                        updateLogView()
                    },
                    logEntries = logEntries,
                    onPlayAudio = { audioFileName ->
                        performHapticFeedback()
                        playAudioFile(audioFileName)
                    },
                    onReprocess = { entry ->
                        performHapticFeedback()
                        reprocessAudio(entry)
                    }
                )
            }
        }

        setupWindowInsets()
        setupLogUpdateReceiver()
        updateLogView()
    }

    private fun setupWindowInsets() {
        // Window insets are handled by Compose in the LogsScreenDM component
        // No need for manual setup since Compose handles this automatically
    }

    override fun onResume() {
        super.onResume()
        Log.d("LogsActivity", "onResume called - updating log view")
        updateLogView()
        try {
            val filter = IntentFilter("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(logUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(logUpdateReceiver, filter)
            }
            Log.d("LogsActivity", "Broadcast receiver registered")
        } catch (e: Exception) {
            Log.e("LogsActivity", "Error registering broadcast receiver", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logUpdateReceiver)
            Log.d("LogsActivity", "Broadcast receiver unregistered")
        } catch (e: Exception) {
            Log.e("LogsActivity", "Error unregistering broadcast receiver", e)
        }
        stopAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
        coroutineScope.cancel()
    }

    private fun setupLogUpdateReceiver() {
        logUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("LogsActivity", "=== LOG UPDATE BROADCAST RECEIVED ===")
                Log.d("LogsActivity", "Intent action: ${intent?.action}")
                Log.d("LogsActivity", "Intent extras: ${intent?.extras}")
                
                updateLogView()
                
                // Check if this was a reprocessing operation
                val isReprocessed = intent?.getBooleanExtra("is_reprocessed", false) ?: false
                Log.d("LogsActivity", "Is reprocessed: $isReprocessed")
                
                if (isReprocessed) {
                    Toast.makeText(this@LogsActivity, "Reprocessing completed! New log entry created.", Toast.LENGTH_SHORT).show()
                    Log.d("LogsActivity", "Reprocessing completed - showing toast")
                }
            }
        }
    }

    private fun updateLogView() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val logData = logStorageManager.readLogs()
        Log.d("LogsActivity", "Updating log view - raw log data length: ${logData.length}")
        
        val logList = if (logData.isNotEmpty()) {
            logData.split("---\n").filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        
        Log.d("LogsActivity", "Found ${logList.size} log entries after splitting")
        
        val parsedEntries = logList.mapNotNull { parseLogEntry(it) }
        Log.d("LogsActivity", "Successfully parsed ${parsedEntries.size} log entries")
        
        // Sort by timestamp (newest first)
        val sortedEntries = parsedEntries.sortedByDescending { entry ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                sdf.parse(entry.timestamp)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        
        Log.d("LogsActivity", "Updating ${sortedEntries.size} log entries")
        // Log the timestamps of the first few entries for debugging
        sortedEntries.take(3).forEachIndexed { index, entry ->
            Log.d("LogsActivity", "Entry $index: ${entry.timestamp} - ${entry.userMessage?.take(50)}")
        }

        // Update reactive state
        logEntries = sortedEntries
    }

    private fun parseLogEntry(rawLog: String): LogEntry? {
        val lines = rawLog.split("\n")
        if (lines.isEmpty()) {
            SecureLogger.w("LogsActivity", "Empty log entry")
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
                    // Handle both regular timestamps and reprocessed timestamps
                    if (line.contains("] (REPROCESSED)")) {
                        // Extract timestamp from "[2025-06-29 15:07:49] (REPROCESSED)"
                        val endIndex = line.indexOf("] (REPROCESSED)")
                        timestamp = line.substring(1, endIndex)
                        isReprocessed = true
                    } else {
                        // Regular timestamp "[2025-06-29 15:07:49]"
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
                    // Handle multi-line selected text
                    val selectedTextLines = mutableListOf<String>()
                    selectedTextLines.add(line.substring(15))
                    i++
                    
                    while (i < lines.size) {
                        val nextLine = lines[i]
                        if (nextLine.startsWith("Audio: ") ||
                            nextLine.startsWith("App: ") ||
                            nextLine.startsWith("Selected Text: ") ||
                            nextLine.startsWith("Clipboard: ") ||
                            nextLine.startsWith("Transcription: ") ||
                            nextLine.startsWith("AI Processed: ") ||
                            nextLine.startsWith("Screen: ") ||
                            nextLine.startsWith("Transcription Service: ") ||
                            nextLine.startsWith("AI Model: ") ||
                            nextLine.startsWith("[") ||
                            nextLine == "---") {
                            break
                        }
                        selectedTextLines.add(nextLine)
                        i++
                    }
                    
                    selectedText = selectedTextLines.joinToString("\n")
                }
                line.startsWith("Clipboard: ") -> {
                    // Handle multi-line clipboard text
                    val clipboardLines = mutableListOf<String>()
                    clipboardLines.add(line.substring(11))
                    i++
                    
                    while (i < lines.size) {
                        val nextLine = lines[i]
                        if (nextLine.startsWith("Audio: ") ||
                            nextLine.startsWith("App: ") ||
                            nextLine.startsWith("Selected Text: ") ||
                            nextLine.startsWith("Clipboard: ") ||
                            nextLine.startsWith("Transcription: ") ||
                            nextLine.startsWith("AI Processed: ") ||
                            nextLine.startsWith("Screen: ") ||
                            nextLine.startsWith("Transcription Service: ") ||
                            nextLine.startsWith("AI Model: ") ||
                            nextLine.startsWith("[") ||
                            nextLine == "---") {
                            break
                        }
                        clipboardLines.add(nextLine)
                        i++
                    }
                    
                    clipboardText = clipboardLines.joinToString("\n")
                }
                line.startsWith("Transcription: ") -> {
                    // Handle multi-line transcription (though usually single line)
                    val transcriptionLines = mutableListOf<String>()
                    transcriptionLines.add(line.substring(15))
                    i++
                    
                    while (i < lines.size) {
                        val nextLine = lines[i]
                        if (nextLine.startsWith("Audio: ") ||
                            nextLine.startsWith("App: ") ||
                            nextLine.startsWith("Selected Text: ") ||
                            nextLine.startsWith("Clipboard: ") ||
                            nextLine.startsWith("Transcription: ") ||
                            nextLine.startsWith("AI Processed: ") ||
                            nextLine.startsWith("Screen: ") ||
                            nextLine.startsWith("Transcription Service: ") ||
                            nextLine.startsWith("AI Model: ") ||
                            nextLine.startsWith("[") ||
                            nextLine == "---") {
                            break
                        }
                        transcriptionLines.add(nextLine)
                        i++
                    }
                    
                    transcription = transcriptionLines.joinToString("\n")
                }
                line.startsWith("AI Processed: ") -> {
                    // Handle multi-line AI processed text
                    val aiProcessedLines = mutableListOf<String>()
                    aiProcessedLines.add(line.substring(14)) // Add first line after "AI Processed: "
                    i++
                    
                    // Continue reading lines until we hit a line that starts with a field prefix or "---"
                    while (i < lines.size) {
                        val nextLine = lines[i]
                        if (nextLine.startsWith("Audio: ") ||
                            nextLine.startsWith("App: ") ||
                            nextLine.startsWith("Selected Text: ") ||
                            nextLine.startsWith("Clipboard: ") ||
                            nextLine.startsWith("Transcription: ") ||
                            nextLine.startsWith("AI Processed: ") ||
                            nextLine.startsWith("Screen: ") ||
                            nextLine.startsWith("Transcription Service: ") ||
                            nextLine.startsWith("AI Model: ") ||
                            nextLine.startsWith("[") ||
                            nextLine == "---") {
                            break
                        }
                        aiProcessedLines.add(nextLine)
                        i++
                    }
                    
                    aiProcessed = aiProcessedLines.joinToString("\n")
                    SecureLogger.d("LogsActivity", "Parsed multi-line AI processed text: ${aiProcessedLines.size} lines, ${aiProcessed?.length ?: 0} chars")
                }
                else -> {
                    i++
                }
            }
        }
        
        if (timestamp.isEmpty()) {
            SecureLogger.w("LogsActivity", "No timestamp found in log entry: ${rawLog.take(100)}...")
            return null
        }
        
        // Combine context information for display
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
        
        // Parse performance metrics from the log entry
        val performanceMetrics = PerformanceMetrics.fromLogEntry(rawLog) ?: PerformanceMetrics.empty()

        return LogEntry(
            timestamp = timestamp,
            audioFileName = audioFileName,
            context = context,
            userMessage = transcription,
            aiProcessed = aiProcessed,
            rawText = rawLog,
            isReprocessed = isReprocessed,
            performanceMetrics = performanceMetrics
        )
    }

    private fun playAudioFile(audioFileName: String) {
        stopAudio()
        
        val outputDir = getPublicAudioDirectory()
        val audioFile = File(outputDir, audioFileName)
        
        if (!audioFile.exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
            }
            Log.d("LogsActivity", "Playing audio: $audioFileName")
        } catch (e: Exception) {
            Log.e("LogsActivity", "Error playing audio: ${e.message}")
        }
    }

    private fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    private fun deleteAllLogsAndAudio() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        
        // Get all audio files before clearing logs
        val audioFiles = prefs.getStringSet("audio_files", LinkedHashSet())?.toSet() ?: emptySet()
        val outputDir = getPublicAudioDirectory()
        
        // Delete all audio files
        var deletedCount = 0
        for (audioFileEntry in audioFiles) {
            val parts = audioFileEntry.split(":")
            if (parts.size >= 1) {
                val fileName = parts[0]
                val file = File(outputDir, fileName)
                try {
                    if (file.exists()) {
                        file.delete()
                        deletedCount++
                        Log.d("LogsActivity", "Deleted audio file: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e("LogsActivity", "Error deleting audio file: $fileName", e)
                }
            }
        }
        
        // Clear all logs and audio metadata
        logStorageManager.clear()
        prefs.edit()
            .remove("audio_files")
            .apply()
        
        // Update the view
        updateLogView()
        
        Log.d("LogsActivity", "Deleted all logs and $deletedCount audio files")
    }

    private fun performHapticFeedback() {
        HapticUtils.performHapticFeedback(this)
    }
    
    private fun browseAllAudioFiles() {
        try {
            // Use same outputDir as other audio methods
            val outputDir = getPublicAudioDirectory()
            
            Log.d("LogsActivity", "Opening audio folder: ${outputDir.absolutePath}")
            
            // Check if directory exists and has files
            if (!outputDir.exists()) {
                Toast.makeText(this, "Audio folder not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val audioFiles = outputDir.listFiles { file -> 
                file.name.endsWith(".m4a") || file.name.endsWith(".wav") || file.name.endsWith(".mp3")
            }
            
            if (audioFiles.isNullOrEmpty()) {
                Toast.makeText(this, "No audio files found", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d("LogsActivity", "Found ${audioFiles.size} audio files")
            
            try {
                // Try to open the folder directly with a more reliable approach
                var opened = false
                
                // Method 1: Use ACTION_VIEW with the folder URI
                try {
                    val uri = android.net.Uri.fromFile(outputDir)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "resource/folder")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        Toast.makeText(this, "Opening WonderWhisper folder", Toast.LENGTH_SHORT).show()
                        opened = true
                    }
                } catch (e: Exception) {
                    Log.w("LogsActivity", "Method 1 failed", e)
                }
                
                // Method 2: Try with DocumentsUI (Files app)
                if (!opened) {
                    try {
                        val intent = Intent("android.intent.action.VIEW").apply {
                            setClassName("com.google.android.documentsui", "com.android.documentsui.files.FilesActivity")
                            putExtra("android.provider.extra.INITIAL_URI", android.net.Uri.fromFile(outputDir))
                        }
                        
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                            Toast.makeText(this, "Opening WonderWhisper folder", Toast.LENGTH_SHORT).show()
                            opened = true
                        }
                    } catch (e: Exception) {
                        Log.w("LogsActivity", "Method 2 failed", e)
                    }
                }
                
                // Method 3: Generic file manager intent
                if (!opened) {
                    try {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                            Toast.makeText(this, "Opening file manager\nNavigate to Downloads → WonderWhisper", Toast.LENGTH_LONG).show()
                            opened = true
                        }
                    } catch (e: Exception) {
                        Log.w("LogsActivity", "Method 3 failed", e)
                    }
                }
                
                // Last resort: copy path to clipboard
                if (!opened) {
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Audio Folder Path", outputDir.absolutePath)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(this, "Path copied to clipboard:\nDownloads/WonderWhisper (${audioFiles.size} files)", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("LogsActivity", "Error opening audio folder", e)
                // Fallback: copy path to clipboard
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Audio Folder Path", outputDir.absolutePath)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(this, "Path copied: ${audioFiles.size} files\n${outputDir.absolutePath}", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e("LogsActivity", "Error accessing audio folder", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareAudioFile(audioFileName: String) {
        try {
            // First, try to get the file from app cache (for sharing)
            val cacheDir = File(this.cacheDir, "audio")
            val cacheFile = File(cacheDir, audioFileName)
            
            // If cache file doesn't exist, try to recreate it from Downloads backup
            if (!cacheFile.exists()) {
                Log.d("LogsActivity", "Cache file not found, attempting to recreate from Downloads")
                val outputDir = getPublicAudioDirectory()
                val downloadsFile = File(outputDir, audioFileName)
                
                if (downloadsFile.exists()) {
                    cacheDir.mkdirs() // Ensure cache directory exists
                    try {
                        downloadsFile.copyTo(cacheFile, overwrite = true)
                        Log.d("LogsActivity", "Successfully recreated cache file from Downloads backup")
                    } catch (e: Exception) {
                        Log.e("LogsActivity", "Failed to recreate cache file from Downloads", e)
                    }
                } else {
                    Toast.makeText(this, "Audio file not found: $audioFileName", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            if (!cacheFile.exists()) {
                Toast.makeText(this, "Unable to prepare file for sharing: $audioFileName", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d("LogsActivity", "Sharing cached audio file: ${cacheFile.absolutePath}")
            Log.d("LogsActivity", "Cache file exists: ${cacheFile.exists()}, Size: ${cacheFile.length()} bytes")
            
            // Use FileProvider to create secure URI for cache file
            val uri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${this.packageName}.fileprovider",
                    cacheFile
                )
            } catch (e: Exception) {
                Log.e("LogsActivity", "Error creating FileProvider URI", e)
                Toast.makeText(this, "Error preparing file for sharing", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d("LogsActivity", "Created FileProvider URI: $uri")
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "WonderWhisper Recording")
                putExtra(Intent.EXTRA_TEXT, "WonderWhisper audio recording: ${cacheFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Check what apps can handle this intent
            val activities = this.packageManager.queryIntentActivities(shareIntent, 0)
            Log.d("LogsActivity", "Found ${activities.size} apps that can handle audio sharing")
            
            val chooser = Intent.createChooser(shareIntent, "Share audio file")
            
            try {
                startActivity(chooser)
                Log.d("LogsActivity", "Share dialog opened successfully")
            } catch (e: Exception) {
                Log.e("LogsActivity", "Error opening share dialog", e)
                
                // Fallback: copy Downloads file path to clipboard
                val outputDir = getPublicAudioDirectory()
                val downloadsFile = File(outputDir, audioFileName)
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Audio File Path", downloadsFile.absolutePath)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(this, "Sharing not available.\nFile path copied to clipboard:\n${downloadsFile.name}", Toast.LENGTH_LONG).show()
                Log.d("LogsActivity", "Fallback: copied Downloads file path to clipboard")
            }
            
        } catch (e: Exception) {
            Log.e("LogsActivity", "Error in shareAudioFile", e)
            Toast.makeText(this, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun parseContextInformation(entry: LogEntry): ContextInformation {
        val lines = entry.rawText.split("\n")
        var transcriptionService = ""
        var aiModel = ""
        var appContext = ""
        var selectedTextContext = ""
        
        var screenContext = ""
        
        for (line in lines) {
            when {
                line.startsWith("Transcription Service: ") -> {
                    transcriptionService = line.substring(23)
                }
                line.startsWith("AI Model: ") -> {
                    aiModel = line.substring(10)
                }
                line.startsWith("Processing Model: ") -> {
                    aiModel = line.substring(18)
                }
                line.startsWith("App: ") -> {
                    appContext = line.substring(5)
                }
                line.startsWith("Selected Text: ") && !line.endsWith("Selected Text: ") -> {
                    selectedTextContext = line.substring(15)
                }

                line.startsWith("Screen: ") && !line.endsWith("Screen: ") -> {
                    screenContext = line.substring(8)
                }
            }
        }
        
        // Default transcription service if not found
        if (transcriptionService.isEmpty()) {
            transcriptionService = "Unknown"
        }
        
        return ContextInformation(
            transcriptionService = transcriptionService,
            aiModel = aiModel,
            appContext = appContext,
            selectedTextContext = selectedTextContext,
            screenContext = screenContext
        )
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        Log.d("LogsActivity", "$label copied: ${text.take(50)}...")
    }

    private fun reprocessAudio(entry: LogEntry) {
        if (entry.audioFileName == null) {
            Toast.makeText(this, "No audio file available", Toast.LENGTH_SHORT).show()
            return
        }

        val outputDir = getPublicAudioDirectory()
        val audioFile = File(outputDir, entry.audioFileName)
        
        if (!audioFile.exists()) {
            Toast.makeText(this, "Audio file not found: ${entry.audioFileName}", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Reprocessing audio...", Toast.LENGTH_SHORT).show()
        Log.d("LogsActivity", "Starting reprocess for audio file: ${entry.audioFileName}")

        coroutineScope.launch {
            try {
                // Extract context from the original entry for reprocessing
                val originalContext = extractContextFromEntry(entry)
                
                // Ensure BubbleOverlayService is running before sending reprocess broadcast
                Log.d("LogsActivity", "Starting BubbleOverlayService for reprocessing")
                val serviceIntent = Intent(this@LogsActivity, BubbleOverlayService::class.java)
                val result = startService(serviceIntent)
                Log.d("LogsActivity", "Service start result: $result")
                
                // Wait a moment for service to fully start and register receivers
                delay(1000) // Increased delay to ensure service is fully ready
                
                // Start reprocessing by sending explicit intent to BubbleOverlayService
                val reprocessIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_REPROCESS_AUDIO")
                reprocessIntent.setClass(this@LogsActivity, BubbleOverlayService::class.java)
                reprocessIntent.putExtra("audio_file_path", audioFile.absolutePath)
                reprocessIntent.putExtra("audio_file_name", entry.audioFileName)
                originalContext?.let { 
                    reprocessIntent.putExtra("context", it)
                }
                
                // Try both broadcast and direct service call
                Log.d("LogsActivity", "Sending broadcast to BubbleOverlayService...")
                sendBroadcast(reprocessIntent)
                
                Log.d("LogsActivity", "Also sending direct service intent...")
                reprocessIntent.action = "com.slumdog88.dictationkeyboardai.ACTION_REPROCESS_AUDIO_DIRECT"
                startService(reprocessIntent)
                
                Log.d("LogsActivity", "Reprocess broadcast sent for ${entry.audioFileName}")
                Log.d("LogsActivity", "Audio file path: ${audioFile.absolutePath}")
                Log.d("LogsActivity", "Audio file exists: ${audioFile.exists()}")
                Log.d("LogsActivity", "Context data: $originalContext")
                
            } catch (e: Exception) {
                Log.e("LogsActivity", "Error during reprocessing", e)
                Toast.makeText(this@LogsActivity, "Error reprocessing audio: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractContextFromEntry(entry: LogEntry): String? {
        // Parse the raw text to extract context information
        val lines = entry.rawText.split("\n")
        val contextLines = mutableListOf<String>()
        
        for (line in lines) {
            when {
                line.startsWith("App: ") -> contextLines.add(line)
                line.startsWith("Selected Text: ") && !line.endsWith("Selected Text: ") -> contextLines.add(line)
                line.startsWith("Clipboard: ") && !line.endsWith("Clipboard: ") -> contextLines.add(line)
                line.startsWith("Screen: ") && !line.endsWith("Screen: ") -> contextLines.add(line)
            }
        }
        
        return if (contextLines.isNotEmpty()) {
            contextLines.joinToString("\n")
        } else {
            null
        }
    }






        


    // Removed LogAdapter class - replaced with Compose implementation

    companion object {
        /**
         * Gets the public WunderWhisper directory for storing audio files
         * Creates the directory if it doesn't exist
         */
        @JvmStatic
        private fun getPublicAudioDirectory(context: Context): File {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val wonderWhisperDir = File(downloadsDir, "WonderWhisper")
            
            if (!wonderWhisperDir.exists()) {
                wonderWhisperDir.mkdirs()
                Log.d("LogsActivity", "Created WonderWhisper directory: ${wonderWhisperDir.absolutePath}")
            }
            
            return wonderWhisperDir
        }
        
        @JvmStatic
        fun enforceRecordingHistoryLimit(context: Context) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val historyLimit = prefs.getInt("recording_history_limit", 50).coerceAtLeast(0)
            
            // If limit is 0, delete all logs and audio files
            if (historyLimit == 0) {
                deleteAllLogsAndAudio(context)
                Log.d("LogsActivity", "Recording history limit is 0 - deleted all logs and audio files")
                return
            }
            
            val logStorage = LogStorageManager.getInstance(context)
            val logData = logStorage.readLogs()
            if (logData.isEmpty()) {
                Log.d("LogsActivity", "No logs to process")
                return
            }
            
            // Parse logs into entries
            val logEntries = logData.split("---\n").filter { it.isNotBlank() }
            Log.d("LogsActivity", "Found ${logEntries.size} log entries, limit is $historyLimit")
            
            if (logEntries.size <= historyLimit) {
                Log.d("LogsActivity", "Log count within limit, no action needed")
                return
            }
            
            // Keep only the newest entries (limit number)
            val entriesToKeep = logEntries.takeLast(historyLimit)
            val entriesToDelete = logEntries.dropLast(historyLimit)
            
            // Get audio files to delete
            val audioFilesToDelete = mutableSetOf<String>()
            for (entry in entriesToDelete) {
                val lines = entry.split("\n")
                for (line in lines) {
                    if (line.startsWith("Audio: ")) {
                        val audioFileName = line.substring(7)
                        audioFilesToDelete.add(audioFileName)
                        Log.d("LogsActivity", "Marking audio file for deletion: $audioFileName")
                    }
                }
            }
            
            // Delete audio files
            val outputDir = getPublicAudioDirectory(context)
            var deletedAudioCount = 0
            for (audioFileName in audioFilesToDelete) {
                try {
                    val file = File(outputDir, audioFileName)
                    if (file.exists() && file.delete()) {
                        deletedAudioCount++
                        Log.d("LogsActivity", "Deleted audio file: $audioFileName")
                    }
                } catch (e: Exception) {
                    Log.e("LogsActivity", "Error deleting audio file: $audioFileName", e)
                }
            }
            
            // Update logs with only the entries to keep
            val updatedLogs = entriesToKeep.joinToString("---\n") + if (entriesToKeep.isNotEmpty()) "---\n" else ""

            // Update audio files tracking
            val existingAudioFiles = prefs.getStringSet("audio_files", LinkedHashSet()) ?: LinkedHashSet()
            val updatedAudioFiles = LinkedHashSet<String>()

            // Keep only audio files that are still referenced in the logs
            for (audioFile in existingAudioFiles) {
                val audioFileName = audioFile.split(":")[0]
                if (!audioFilesToDelete.contains(audioFileName)) {
                    updatedAudioFiles.add(audioFile)
                }
            }

            // Persist logs and update audio metadata
            logStorage.writeLogs(updatedLogs)
            prefs.edit()
                .putStringSet("audio_files", updatedAudioFiles)
                .apply()
            
            Log.d("LogsActivity", "Enforced recording history limit: kept ${entriesToKeep.size} entries, deleted ${entriesToDelete.size} entries and $deletedAudioCount audio files")
            
            // Send broadcast to update log view
            val logUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED")
            context.sendBroadcast(logUpdateIntent)
        }
        
        @JvmStatic
        private fun deleteAllLogsAndAudio(context: Context) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            
            // Get all audio files before clearing logs
            val audioFiles = prefs.getStringSet("audio_files", LinkedHashSet())?.toSet() ?: emptySet()
            val outputDir = getPublicAudioDirectory(context)
            
            // Delete all audio files
            var deletedCount = 0
            for (audioFileEntry in audioFiles) {
                val parts = audioFileEntry.split(":")
                if (parts.isNotEmpty()) {
                    val fileName = parts[0]
                    val file = File(outputDir, fileName)
                    try {
                        if (file.exists() && file.delete()) {
                            deletedCount++
                            Log.d("LogsActivity", "Deleted audio file: $fileName")
                        }
                    } catch (e: Exception) {
                        Log.e("LogsActivity", "Error deleting audio file: $fileName", e)
                    }
                }
            }
            
            // Clear all logs and audio metadata
            LogStorageManager.getInstance(context).clear()
            prefs.edit()
                .remove("audio_files")
                .apply()
            
            Log.d("LogsActivity", "Deleted all logs and $deletedCount audio files")
            
            // Send broadcast to update log view
            val logUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED")
            context.sendBroadcast(logUpdateIntent)
        }
    }
}
