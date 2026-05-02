package com.slumdog88.dictationkeyboardai

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collectLatest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import android.content.ComponentName
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import android.content.ClipboardManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ViewTreeObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.slumdog88.dictationkeyboardai.utils.TextProcessingUtils
import com.slumdog88.dictationkeyboardai.offline.OFFLINE_TRANSCRIPTION_OPTION_LABEL
import com.slumdog88.dictationkeyboardai.offline.OfflinePcmRecorder
import com.slumdog88.dictationkeyboardai.offline.OfflineWavWriter
import com.slumdog88.dictationkeyboardai.utils.AudioFileManager
import com.slumdog88.dictationkeyboardai.utils.AudioFocusManager
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.ui.BubbleUIManager
import com.slumdog88.dictationkeyboardai.ui.KeyboardDetectionManager
import com.slumdog88.dictationkeyboardai.ui.bubble.BubbleComposeController
import com.slumdog88.dictationkeyboardai.ui.ServiceNotificationManager
import com.slumdog88.dictationkeyboardai.network.GroqProxyConfig
import com.slumdog88.dictationkeyboardai.network.NetworkManager
import com.slumdog88.dictationkeyboardai.transcription.TranscriptionServiceManager
import com.slumdog88.dictationkeyboardai.transcription.streaming.StreamingDictationSession
import com.slumdog88.dictationkeyboardai.transcription.streaming.StreamingResult
import com.slumdog88.dictationkeyboardai.transcription.streaming.StreamingStartResult
import com.slumdog88.dictationkeyboardai.transcription.streaming.StreamingUiState
import com.slumdog88.dictationkeyboardai.transcription.streaming.StopReason
import com.slumdog88.dictationkeyboardai.ui.streaming.StreamingOverlayController
import com.slumdog88.dictationkeyboardai.ai.AIProcessingManager
import com.slumdog88.dictationkeyboardai.PerformanceMetrics
import com.slumdog88.dictationkeyboardai.ReformatPrompt
import com.slumdog88.dictationkeyboardai.ReformatPromptManager
import com.slumdog88.dictationkeyboardai.PerformanceMetricsBuilder
import com.slumdog88.dictationkeyboardai.utils.StatisticsManager
import com.slumdog88.dictationkeyboardai.utils.LogStorageManager

class BubbleOverlayService : Service() {
    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var offlineRecorder: OfflinePcmRecorder? = null
    private var offlineRecordingSamples: FloatArray? = null
    private var offlineRecordingSampleRate: Int = 16_000
    private var isOfflineRecordingMode: Boolean = false
    private var audioFile: File? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Note recording state
    private var isRecordingNote = false
    private var noteMediaRecorder: MediaRecorder? = null
    private var noteAudioFile: File? = null
    private var tempNoteId: String? = null
    private val notePadManager: NotePadManager by lazy { NotePadManager(this) }

    // Voice recognize mode (for external keyboard integration via RECOGNIZE_SPEECH intent)
    private var isVoiceRecognizeMode = false
    // Track processing phase to prevent re-entrancy and race conditions
    private var voiceRecognizeProcessing = false

    // Secure API key manager
    private val secureApiKeyManager: SecureApiKeyManager by lazy {
        SecureApiKeyManager.getInstance(this)
    }

    /**
     * Performance fix: Safe MediaRecorder cleanup to prevent resource leaks
     * Validates state before each operation and handles all exceptions
     */
    private fun safeStopAndReleaseRecorder(recorder: MediaRecorder?, wasRecording: Boolean = true): MediaRecorder? {
        recorder ?: return null
        try {
            recorder.setOnErrorListener(null)
            recorder.setOnInfoListener(null)
        } catch (_: Exception) { }

        // Only stop if we were actually recording
        if (wasRecording) {
            try {
                recorder.stop()
            } catch (e: IllegalStateException) {
                Log.w("BubbleOverlayService", "Recorder not in recording state", e)
            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Error stopping recorder", e)
            }
        }

        try {
            recorder.reset() // Reset for clean state before release
            recorder.release()
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error releasing recorder", e)
        }
        return null
    }

    // Utility managers
    private val audioFileManager: AudioFileManager by lazy { AudioFileManager(this) }
    private val audioFocusManager: AudioFocusManager by lazy { AudioFocusManager(this) }
    private val settingsManager: SettingsManager by lazy { SettingsManager(this) }

    // Network and processing managers
    private val networkManager: NetworkManager by lazy { NetworkManager() }
    private val transcriptionServiceManager: TranscriptionServiceManager by lazy {
        TranscriptionServiceManager(this, networkManager, settingsManager, audioFileManager, secureApiKeyManager)
    }
    private val aiProcessingManager: AIProcessingManager by lazy {
        AIProcessingManager(this, networkManager, settingsManager, secureApiKeyManager)
    }

    // Broadcast receiver for bubble appearance updates
    private val bubbleAppearanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("BubbleOverlayService", "=== BROADCAST RECEIVED ===")
            Log.d("BubbleOverlayService", "Intent action: ${intent?.action}")

            if (intent?.action == "com.slumdog88.dictationkeyboardai.UPDATE_BUBBLE_APPEARANCE") {
                val opacity = intent.getIntExtra("opacity", 100)
                val size = intent.getIntExtra("size", 100)
                Log.d("BubbleOverlayService", "Updating bubble appearance - Opacity: $opacity%, Size: $size%")
                updateBubbleAppearance(opacity, size)
            } else {
                Log.w("BubbleOverlayService", "Received broadcast with unexpected action: ${intent?.action}")
            }
        }
    }

    // Track if bubble appearance receiver was successfully registered
    private var bubbleAppearanceReceiverRegistered = false

    // Retry transcription receiver for keyboard dynamic button
    private val retryTranscriptionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_RETRY_TRANSCRIPTION) {
                val audioPath = intent.getStringExtra("audio_file_path")
                if (audioPath != null) {
                    retryTranscription(audioPath)
                }
            }
        }
    }

    // UI managers
    private val bubbleUIManager: BubbleUIManager by lazy { BubbleUIManager(this, bubbleUICallback) }
    private var bubbleComposeController: BubbleComposeController? = null
    // Cache the compose bubble mode at startup to prevent switching mid-session
    private var cachedUseComposeBubble: Boolean? = null
    private val keyboardDetectionManager: KeyboardDetectionManager by lazy { KeyboardDetectionManager(this, keyboardDetectionCallback) }
    private val serviceNotificationManager: ServiceNotificationManager by lazy { ServiceNotificationManager(this) }
    private val logStorageManager: LogStorageManager by lazy { LogStorageManager.getInstance(this) }

    private var streamingSession: StreamingDictationSession? = null
    private var streamingStateJob: Job? = null
    private var isStreamingModeActive: Boolean = false
    private var isSmartStreamingActive: Boolean = false
    @Volatile private var lastStreamingBroadcastAt: Long = 0L
    @Volatile private var lastStreamingBroadcastText: String = ""
    @Volatile private var lastStreamingBroadcastStatus: String = ""
    @Volatile private var lastStreamingBroadcastActive: Boolean = false
    private val streamingBroadcastMinIntervalMs = 100L
    private val streamingOverlayController: StreamingOverlayController by lazy {
        StreamingOverlayController(this, streamingOverlayCallbacks)
    }
    private val streamingOverlayCallbacks = object : StreamingOverlayController.Callbacks {
        override fun onStopRequested() {
            stopStreamingWithReason(StopReason.COMPLETED)
        }

        override fun onCancelRequested() {
            stopStreamingWithReason(StopReason.CANCELED)
        }

        override fun onCopyRequested(text: String) {
            copyTextToClipboard(text)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this@BubbleOverlayService,
                    "Streaming transcript copied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Polling handler for keyboard-aware bubble
    // Performance fix: Reduced polling frequency from 500ms to 1500ms
    private val focusCheckHandler = Handler(Looper.getMainLooper())
    private var focusCheckRunnable: Runnable? = null
    private var lastFocusState: Boolean? = null // Cache to avoid redundant updates

    // UI manager callbacks
    private val bubbleUICallback = object : BubbleUIManager.BubbleUICallback {
        override fun onRecordingStartRequested() = startRecording(forceStreaming = false, uiMode = null)
        override fun onStreamingStartRequested() = startRecording(forceStreaming = true, uiMode = null)
        override fun onRecordingStopRequested() = stopRecording()
        override fun onBubbleHideRequested() = hideBubble()
        override fun onTrashRequested() = trashRecording()
        override fun isRecording(): Boolean = this@BubbleOverlayService.isRecording
        override fun getServiceScope(): CoroutineScope = serviceScope
        override fun onKeyboardDetectionSetupRequested() = startFocusPolling()
        override fun onKeyboardDetectionCleanupRequested() = stopFocusPolling()
        override fun isKeyboardAwareBubbleEnabled(): Boolean = settingsManager.isKeyboardAwareBubbleEnabled()
        override fun onNotificationUpdateRequested() = updateNotification()

        override fun onSelectAllRequested() {
            performSelectAll()
        }

        override fun onReprocessRequested() {
            reprocessSelectedText()
        }

        override fun onAIProcessingToggleRequested() {
            toggleAIProcessing()
        }

        override fun onOpenSettingsRequested() {
            openSettings()
        }

        override fun hasTextSelection(): Boolean {
            return DictationAccessibilityService.getSelectedTextDirect()?.isNotEmpty() == true
        }

        override fun isAIProcessingEnabled(): Boolean {
            return settingsManager.getSettings().getBoolean("enable_postprocess", false)
        }
    }

    // Compose bubble controller callbacks
    private val composeBubbleCallback = object : BubbleComposeController.Callbacks {
        override fun onRecordingStartRequested() = startRecording(forceStreaming = false, uiMode = null)
        override fun onRecordingStopRequested() = stopRecording()
        override fun onBubbleHideRequested() = hideBubble()
        override fun onTrashRequested() = trashRecording()
        override fun isRecording(): Boolean = this@BubbleOverlayService.isRecording
        override fun getServiceScope(): CoroutineScope = serviceScope
        override fun onKeyboardDetectionSetupRequested() = startFocusPolling()
        override fun onKeyboardDetectionCleanupRequested() = stopFocusPolling()
        override fun isKeyboardAwareBubbleEnabled(): Boolean = settingsManager.isKeyboardAwareBubbleEnabled()
        override fun onNotificationUpdateRequested() = updateNotification()

        override fun onSelectAllRequested() {
            performSelectAll()
        }

        override fun onReprocessRequested() {
            reprocessSelectedText()
        }

        override fun onAIProcessingToggleRequested() {
            toggleAIProcessing()
        }

        override fun onOpenSettingsRequested() {
            openSettings()
        }

        override fun isAIProcessingEnabled(): Boolean {
            return settingsManager.getSettings().getBoolean("enable_postprocess", false)
        }
    }

    private val keyboardDetectionCallback = object : KeyboardDetectionManager.KeyboardDetectionCallback {
        override fun onKeyboardVisibilityChanged(visible: Boolean) {
            if (settingsManager.isBubbleOverlayEnabled()) {
                if (useComposeBubble()) {
                    bubbleComposeController?.handleKeyboardVisibilityChange(visible)
                } else {
                    bubbleUIManager.handleKeyboardVisibilityChange(visible)
                }
            } else {
                // Ensure bubble stays hidden when disabled
                hideBubble()
                updateNotification()
            }
        }
        override fun isKeyboardAwareBubbleEnabled(): Boolean = settingsManager.isKeyboardAwareBubbleEnabled()
        override fun isRecording(): Boolean = this@BubbleOverlayService.isRecording
    }

    // Store context when recording starts
    private var selectedTextContext: String? = null
    private var currentAppContext: String? = null
    private var screenContext: String? = null

    private var reprocessReceiver: BroadcastReceiver? = null

    // Track recent IME activity (timestamp ms)
    @Volatile private var lastImeActiveAt: Long = 0L
    @Volatile private var isDictationImeActive: Boolean = false
    private var contextCollectionJob: Job? = null
    private var amplitudeJob: Job? = null
    @Volatile private var lastAmplitudeSentAt: Long = 0L
    @Volatile private var lastAmplitudeValue: Int = -1
    private val amplitudeMinIntervalMs = 80L
    private val amplitudeChangeThreshold = 250
    private var useStreamingOverlayUi: Boolean = false

    private val imeActiveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_IME_ACTIVE -> {
                    isDictationImeActive = true
                    lastImeActiveAt = System.currentTimeMillis()
                    Log.d("BubbleOverlayService", "IME active ping received; hiding bubble")
                    hideBubble()
                }
                ACTION_IME_INACTIVE -> {
                    isDictationImeActive = false
                    Log.d("BubbleOverlayService", "IME inactive; showing bubble")
                    showBubble()
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.slumdog88.dictationkeyboardai.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.slumdog88.dictationkeyboardai.HIDE_BUBBLE"
        const val ACTION_ENABLE_BUBBLE = "com.slumdog88.dictationkeyboardai.ENABLE_BUBBLE"
        const val ACTION_DISABLE_BUBBLE = "com.slumdog88.dictationkeyboardai.DISABLE_BUBBLE"
        const val ACTION_REPROCESS_AUDIO = "com.slumdog88.dictationkeyboardai.ACTION_REPROCESS_AUDIO"
        const val ACTION_REPROCESS_AUDIO_DIRECT = "com.slumdog88.dictationkeyboardai.ACTION_REPROCESS_AUDIO_DIRECT"
        const val ACTION_REPROCESS_NOTE = "com.slumdog88.dictationkeyboardai.ACTION_REPROCESS_NOTE"
        const val ACTION_REPROCESS_TEXT = "com.slumdog88.dictationkeyboardai.ACTION_REPROCESS_TEXT"
        const val ACTION_START_NOTE = "com.slumdog88.dictationkeyboardai.ACTION_START_NOTE"
        const val ACTION_STOP_NOTE = "com.slumdog88.dictationkeyboardai.ACTION_STOP_NOTE"
        const val ACTION_START_DICTATION = "com.slumdog88.dictationkeyboardai.ACTION_START_DICTATION"
        const val ACTION_STOP_DICTATION = "com.slumdog88.dictationkeyboardai.ACTION_STOP_DICTATION"
        const val ACTION_IME_ACTIVE = "com.slumdog88.dictationkeyboardai.ACTION_IME_ACTIVE"
        const val ACTION_IME_INACTIVE = "com.slumdog88.dictationkeyboardai.ACTION_IME_INACTIVE"
        const val ACTION_IME_INSERT = "com.slumdog88.dictationkeyboardai.ACTION_IME_INSERT"
        const val ACTION_IME_RECORDING_STATE = "com.slumdog88.dictationkeyboardai.ACTION_IME_RECORDING_STATE"
        const val ACTION_AMPLITUDE_UPDATE = "com.slumdog88.dictationkeyboardai.ACTION_AMPLITUDE_UPDATE"
        const val ACTION_PROCESSING_STATE = "com.slumdog88.dictationkeyboardai.ACTION_PROCESSING_STATE"
        const val ACTION_STREAMING_UPDATE = "com.slumdog88.dictationkeyboardai.ACTION_STREAMING_UPDATE"

        // Voice recognition actions (for external keyboard integration)
        const val ACTION_START_VOICE_RECOGNIZE = "com.slumdog88.dictationkeyboardai.ACTION_START_VOICE_RECOGNIZE"
        const val ACTION_STOP_VOICE_RECOGNIZE = "com.slumdog88.dictationkeyboardai.ACTION_STOP_VOICE_RECOGNIZE"

        // Retry transcription actions (for keyboard dynamic button)
        const val ACTION_TRANSCRIPTION_FAILURE = "com.slumdog88.dictationkeyboardai.ACTION_TRANSCRIPTION_FAILURE"
        const val ACTION_RETRY_TRANSCRIPTION = "com.slumdog88.dictationkeyboardai.ACTION_RETRY_TRANSCRIPTION"

        // Retry lifecycle actions (for history screen state synchronization)
        const val ACTION_RETRY_STARTED = "com.slumdog88.dictationkeyboardai.ACTION_RETRY_STARTED"
        const val ACTION_RETRY_COMPLETED = "com.slumdog88.dictationkeyboardai.ACTION_RETRY_COMPLETED"

        // Performance fix: Reduced focus polling from 500ms to 1500ms
        private const val FOCUS_POLL_INTERVAL_MS = 1500L

        @Volatile
        private var isServiceRunning = false

        // Static instance for direct communication
        private var instance: BubbleOverlayService? = null

        fun isRunning(): Boolean = isServiceRunning

        fun getInstance(): BubbleOverlayService? = instance

        /**
         * Direct method to update bubble appearance without broadcast
         */
        fun updateBubbleAppearanceDirect(opacity: Int, size: Int): Boolean {
            val serviceInstance = instance
            return if (serviceInstance != null) {
                try {
                    serviceInstance.updateBubbleAppearance(opacity, size)
                    Log.d("BubbleOverlayService", "Direct bubble appearance update successful - Opacity: $opacity%, Size: $size%")
                    true
                } catch (e: Exception) {
                    Log.e("BubbleOverlayService", "Error in direct bubble appearance update", e)
                    false
                }
            } else {
                Log.w("BubbleOverlayService", "Cannot update bubble appearance - service instance is null")
                false
            }
        }
    }

    // Pre-compiled regex patterns for performance optimization (moved to TextProcessingUtils)
    // Keeping these for backward compatibility in case any methods still use them
    private val whitespaceRegex = "\\s+".toRegex()
    private val punctuationRegex = Regex("[.,!?;:]")

    // Caching and HTTP clients moved to individual managers

    // Temporary gson instance for backward compatibility during refactoring
    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .disableHtmlEscaping()
            .create()
    }

    // HTTP clients moved to NetworkManager
    // Temporary references for backward compatibility during refactoring
    private val transcriptionHttpClient get() = networkManager.transcriptionHttpClient
    private val aiProcessingHttpClient get() = networkManager.aiProcessingHttpClient

    fun isRecordingNote(): Boolean = isRecordingNote

    /**
     * Returns true if dictation recording is currently active.
     * Used by DictationImeService for state reconciliation on view recreation.
     */
    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Gets the public WunderWhisper directory for storing audio files
     * Creates the directory if it doesn't exist
     */
    private fun getPublicAudioDirectory(): File {
        return audioFileManager.getPublicAudioDirectory()
    }

    /**
     * Migrates existing audio files from the old private directory to the new public WunderWhisper directory
     * This runs once when the service is created to ensure users don't lose existing recordings
     */
    private fun migrateExistingAudioFiles() {
        audioFileManager.migrateExistingAudioFiles()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning = true
        Log.d("BubbleOverlayService", "=== SERVICE ONCREATE CALLED ===")

        // Pre-warm SecureApiKeyManager to avoid lazy initialization race condition
        // during first transcription. EncryptedSharedPreferences.create() can be slow.
        serviceScope.launch {
            try {
                // Touch the lazy property to trigger initialization on background thread
                secureApiKeyManager.getApiKey("groq")  // Any key name works, just triggers init
                Log.d("BubbleOverlayService", "SecureApiKeyManager pre-warmed successfully")
            } catch (e: Exception) {
                Log.w("BubbleOverlayService", "SecureApiKeyManager pre-warm failed (non-fatal)", e)
            }
        }

        serviceNotificationManager.createNotificationChannel()
        setupReprocessReceiver()

        // Register broadcast receiver for bubble appearance updates with proper API level checks
        try {
            val filter = IntentFilter("com.slumdog88.dictationkeyboardai.UPDATE_BUBBLE_APPEARANCE")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bubbleAppearanceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(bubbleAppearanceReceiver, filter)
            }
            bubbleAppearanceReceiverRegistered = true
            Log.d("BubbleOverlayService", "Bubble appearance receiver registered successfully")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error registering bubble appearance receiver", e)
            bubbleAppearanceReceiverRegistered = false
        }

        // Register IME active receiver
        try {
            val imeFilter = IntentFilter().apply {
                addAction(ACTION_IME_ACTIVE)
                addAction(ACTION_IME_INACTIVE)
            }
            // Use ContextCompat to always specify NOT_EXPORTED across API levels
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                imeActiveReceiver,
                imeFilter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d("BubbleOverlayService", "IME active receiver registered")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error registering IME active receiver", e)
        }

        // Register retry transcription receiver for keyboard dynamic button
        try {
            val retryFilter = IntentFilter(ACTION_RETRY_TRANSCRIPTION)
            ContextCompat.registerReceiver(
                this,
                retryTranscriptionReceiver,
                retryFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d("BubbleOverlayService", "Retry transcription receiver registered")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error registering retry transcription receiver", e)
        }

        // Migrate existing audio files to public directory
        migrateExistingAudioFiles()

        // Start foreground service immediately for better reliability
        startForeground(
            serviceNotificationManager.getNotificationId(),
            serviceNotificationManager.createNotification(
                isBubbleVisible = isBubbleVisible(),
                isNoteRecording = isRecordingNote
            )
        )

        // Pre-warm network connections for better performance
        networkManager.preWarmConnections(serviceScope, this)

        ensureStreamingSession()

        Log.d("BubbleOverlayService", "Service created and running in foreground")

        startFocusPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BubbleOverlayService", "=== SERVICE ONSTARTCOMMAND CALLED ===")
        Log.d("BubbleOverlayService", "Intent action: ${intent?.action}")
        Log.d("BubbleOverlayService", "Start ID: $startId")

        // Ensure reprocess receiver is always registered (in case service was already running)
        if (reprocessReceiver == null) {
            Log.d("BubbleOverlayService", "Reprocess receiver was null, setting up again")
            setupReprocessReceiver()
        } else {
            Log.d("BubbleOverlayService", "Reprocess receiver already exists")
        }

        when (intent?.action) {
            ACTION_SHOW -> {
                Log.d("BubbleOverlayService", "Handling ACTION_SHOW")
                showBubble()
            }
            ACTION_HIDE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_HIDE")
                hideBubble()
            }
            ACTION_ENABLE_BUBBLE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_ENABLE_BUBBLE")
                settingsManager.setBubbleOverlayEnabled(true)
                showBubble()
                updateNotification()
            }
            ACTION_DISABLE_BUBBLE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_DISABLE_BUBBLE")
                settingsManager.setBubbleOverlayEnabled(false)
                hideBubble()
                updateNotification()
            }
            ACTION_START_NOTE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_START_NOTE")
                startNoteRecording()
            }
            ACTION_STOP_NOTE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_STOP_NOTE")
                stopNoteRecording()
            }
            ACTION_START_DICTATION -> {
                Log.d("BubbleOverlayService", "Handling ACTION_START_DICTATION")
                val forceStreaming = intent.getBooleanExtra("force_streaming", false)
                val uiMode = intent.getStringExtra("ui_mode")
                val imeSelectedText = intent.getStringExtra("ime_selected_text")
                startRecording(forceStreaming, uiMode, imeSelectedText)
            }
            ACTION_STOP_DICTATION -> {
                Log.d("BubbleOverlayService", "Handling ACTION_STOP_DICTATION")
                val discardRecording = intent.getBooleanExtra("discard_recording", false)
                if (discardRecording) {
                    Log.d("BubbleOverlayService", "Discarding recording based on intent extra")
                    trashRecording()
                } else {
                    stopRecording()
                }
            }
            ACTION_START_VOICE_RECOGNIZE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_START_VOICE_RECOGNIZE")
                startVoiceRecognize()
            }
            ACTION_STOP_VOICE_RECOGNIZE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_STOP_VOICE_RECOGNIZE")
                val cancel = intent.getBooleanExtra("cancel", false)
                stopVoiceRecognize(cancel = cancel)
            }
            ACTION_REPROCESS_AUDIO_DIRECT -> {
                Log.d("BubbleOverlayService", "Handling ACTION_REPROCESS_AUDIO_DIRECT")
                val audioFilePath = intent.getStringExtra("audio_file_path")
                val audioFileName = intent.getStringExtra("audio_file_name")
                val contextData = intent.getStringExtra("context")

                Log.d("BubbleOverlayService", "Direct reprocess request for: $audioFileName")
                Log.d("BubbleOverlayService", "Audio file path: $audioFilePath")
                Log.d("BubbleOverlayService", "Context data: $contextData")

                if (audioFilePath != null && audioFileName != null) {
                    Log.d("BubbleOverlayService", "Starting direct reprocess coroutine...")
                    serviceScope.launch {
                        reprocessAudioFile(audioFilePath, audioFileName, contextData)
                    }
                } else {
                    Log.e("BubbleOverlayService", "Missing audio file data in direct call - path: $audioFilePath, name: $audioFileName")
                }
            }
            ACTION_REPROCESS_NOTE -> {
                Log.d("BubbleOverlayService", "Handling ACTION_REPROCESS_NOTE")
                val audioFileName = intent.getStringExtra("audio_file_name")
                if (audioFileName.isNullOrBlank()) {
                    Log.e("BubbleOverlayService", "Missing audio_file_name for note reprocess")
                } else {
                    val dir = notePadManager.getNotesAudioDirectory()
                    val audioFilePath = File(dir, audioFileName).absolutePath
                    serviceScope.launch {
                        // Full end-to-end reprocessing: transcribe and update the existing note
                        reprocessNoteAndUpdate(audioFilePath, audioFileName)
                    }
                }
            }
            ACTION_REPROCESS_TEXT -> {
                Log.d("BubbleOverlayService", "Handling ACTION_REPROCESS_TEXT")
                val textToProcess = intent.getStringExtra("text_to_process")
                if (textToProcess.isNullOrBlank()) {
                    Log.e("BubbleOverlayService", "Missing text_to_process for text reprocess")
                } else {
                    serviceScope.launch {
                        reprocessText(textToProcess)
                    }
                }
            }
            null -> {
                Log.d("BubbleOverlayService", "Intent action is null - service started for reprocessing")
            }
            else -> {
                Log.d("BubbleOverlayService", "Unknown action: ${intent.action}")
            }
        }
        Log.d("BubbleOverlayService", "onStartCommand completed, returning START_STICKY")
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        isServiceRunning = false
        contextCollectionJob?.cancel()
        contextCollectionJob = null
        amplitudeJob?.cancel()
        amplitudeJob = null
        streamingStateJob?.cancel()
        streamingStateJob = null
        streamingSession?.let { session ->
            try {
                runBlocking {
                    session.stop(StopReason.CANCELED)
                }
            } catch (e: Exception) {
                Log.w("BubbleOverlayService", "Failed to stop streaming session during destroy", e)
            }
        }
        streamingSession = null
        isStreamingModeActive = false
        mediaRecorder = safeStopAndReleaseRecorder(
            mediaRecorder,
            wasRecording = isRecording && !isOfflineRecordingMode
        )
        noteMediaRecorder = safeStopAndReleaseRecorder(
            noteMediaRecorder,
            wasRecording = isRecordingNote
        )
        try {
            offlineRecorder?.stop()
        } catch (e: Exception) {
            Log.w("BubbleOverlayService", "Failed to stop offline recorder during destroy", e)
        }
        offlineRecorder = null
        offlineRecordingSamples = null
        isOfflineRecordingMode = false
        isRecording = false
        isRecordingNote = false
        audioFocusManager.abandonFocus()
        streamingOverlayController.release()
        stopFocusPolling()
        removeBubble()
        serviceScope.cancel()
        super.onDestroy()

        // Unregister broadcast receivers
        reprocessReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("BubbleOverlayService", "Reprocess receiver unregistered")
            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Error unregistering reprocess receiver", e)
            }
        }

        // Only unregister bubble appearance receiver if it was successfully registered
        if (bubbleAppearanceReceiverRegistered) {
            try {
                unregisterReceiver(bubbleAppearanceReceiver)
                Log.d("BubbleOverlayService", "Bubble appearance receiver unregistered")
                bubbleAppearanceReceiverRegistered = false
            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Error unregistering bubble appearance receiver", e)
            }
        } else {
            Log.d("BubbleOverlayService", "Bubble appearance receiver was not registered, skipping unregistration")
        }

        // Unregister IME active receiver
        try { unregisterReceiver(imeActiveReceiver) } catch (_: Exception) { }

        // Unregister retry transcription receiver
        try { unregisterReceiver(retryTranscriptionReceiver) } catch (_: Exception) { }
    }

    private fun startFocusPolling() {
        if (focusCheckRunnable != null) {
            return
        }
        // Reset cache on start
        lastFocusState = null

        focusCheckRunnable = object : Runnable {
            override fun run() {
                if (settingsManager.isKeyboardAwareBubbleEnabled() && !isRecording) {
                    val hasFocus = DictationAccessibilityService.getInstance()?.hasFocusedEditableField() ?: false

                    // Performance fix: Only act on state changes to reduce unnecessary operations
                    if (hasFocus != lastFocusState) {
                        lastFocusState = hasFocus
                        if (hasFocus && !isBubbleVisible()) {
                            showBubble()
                        } else if (!hasFocus && isBubbleVisible()) {
                            hideBubble()
                        }
                    }
                }
                // Performance fix: Reduced polling from 500ms to 1500ms
                focusCheckHandler.postDelayed(this, FOCUS_POLL_INTERVAL_MS)
            }
        }
        focusCheckHandler.post(focusCheckRunnable!!)
    }

    private fun stopFocusPolling() {
        focusCheckRunnable?.let {
            focusCheckHandler.removeCallbacks(it)
        }
        focusCheckRunnable = null
        lastFocusState = null // Clear cache on stop
    }

    private fun updateNotification() {
        val bubbleVisible = isBubbleVisible()
        val noteRecording = isRecordingNote
        serviceNotificationManager.updateNotification(bubbleVisible, noteRecording)
    }

    /**
     * Check if the bubble is currently visible (works with both UI modes).
     */
    private fun isBubbleVisible(): Boolean {
        return if (useComposeBubble()) {
            bubbleComposeController?.isVisible() ?: false
        } else {
            bubbleUIManager.isBubbleVisible()
        }
    }

    private fun showBubble() {
        if (settingsManager.isBubbleOverlayEnabled()) {
            if (useComposeBubble()) {
                ensureComposeBubbleController()
                bubbleComposeController?.show()
            } else {
                bubbleUIManager.showBubble()
            }
        } else {
            Log.d("BubbleOverlayService", "Bubble overlay disabled by setting; ignoring showBubble()")
            updateNotification()
        }
    }

    private fun updateUIVisibility(isRecording: Boolean) {
        if (useComposeBubble()) {
            bubbleComposeController?.setRecordingState(isRecording)
        } else {
            bubbleUIManager.updateUIVisibility(isRecording)
        }
    }

    /**
     * Check if the new Compose-based bubble UI should be used.
     * The value is cached at first access to prevent switching mid-session.
     */
    private fun useComposeBubble(): Boolean {
        return cachedUseComposeBubble ?: run {
            val value = settingsManager.getSettings().getBoolean("use_compose_bubble", true)
            cachedUseComposeBubble = value
            value
        }
    }

    /**
     * Lazily create the compose bubble controller when needed.
     */
    private fun ensureComposeBubbleController() {
        if (bubbleComposeController == null) {
            bubbleComposeController = BubbleComposeController(this, composeBubbleCallback)
        }
    }

    /**
     * Update bubble appearance (works with both UI modes).
     */
    private fun updateBubbleAppearance(opacity: Int, size: Int) {
        if (useComposeBubble()) {
            bubbleComposeController?.updateAppearance(opacity, size)
        } else {
            bubbleUIManager.updateBubbleAppearance(opacity, size)
        }
    }

    // setupListeners method moved to BubbleUIManager

    private fun trashRecording() {
        if (isStreamingModeActive) {
            stopStreamingWithReason(StopReason.CANCELED)
            return
        }

        if (!isRecording) return
        
        // Set flag to false first to prevent accidental re-entry
        isRecording = false
        
        serviceScope.launch {
            try {
                // Performance fix: Use safe cleanup helper to prevent resource leaks
                mediaRecorder = safeStopAndReleaseRecorder(mediaRecorder, wasRecording = true)
                
                // Stop offline recorder if active
                if (isOfflineRecordingMode) {
                    offlineRecorder?.stop()
                    offlineRecorder = null
                    offlineRecordingSamples = null
                }
                
                // Cancel jobs
                contextCollectionJob?.cancel()
                amplitudeJob?.cancel()
                contextCollectionJob = null
                amplitudeJob = null
                
                // Delete the audio file
                audioFile?.let { file ->
                    if (file.exists()) {
                        file.delete()
                        Log.d("BubbleOverlayService", "Trashed recording: deleted file ${file.name}")
                    }
                }
                audioFile = null
                
                withContext(Dispatchers.Main) {
                    // Update UI state
                    if (useComposeBubble()) {
                        bubbleComposeController?.setRecordingState(false)
                    } else {
                        bubbleUIManager.updateRecordingState(false)
                    }
                    val bubbleVisible = if (useComposeBubble()) {
                        bubbleComposeController?.isVisible() ?: false
                    } else {
                        bubbleUIManager.isBubbleVisible()
                    }
                    serviceNotificationManager.updateNotification(
                        isBubbleVisible = bubbleVisible,
                        isNoteRecording = isRecordingNote
                    )
                    
                    // Reset IME recording state
                    val stateIntent = Intent(ACTION_IME_RECORDING_STATE)
                    stateIntent.putExtra("isRecording", false)
                    stateIntent.setPackage(packageName)
                    sendBroadcast(stateIntent)
                    
                    // Reset amplitude
                    val ampIntent = Intent(ACTION_AMPLITUDE_UPDATE)
                    ampIntent.putExtra("amplitude", 0)
                    ampIntent.setPackage(packageName)
                    sendBroadcast(ampIntent)
                    
                    Toast.makeText(this@BubbleOverlayService, "Recording discarded", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Error trashing recording", e)
            }
        }
    }

    private fun hideBubble() {
        if (useComposeBubble()) {
            bubbleComposeController?.hide()
        } else {
            bubbleUIManager.hideBubble()
        }
    }

    private fun removeBubble() {
        if (useComposeBubble()) {
            bubbleComposeController?.release()
            bubbleComposeController = null
        } else {
            bubbleUIManager.removeBubble()
        }
    }

    /**
     * Perform select all on the currently focused text field via accessibility service.
     */
    private fun performSelectAll() {
        try {
            // Request select all through accessibility service
            DictationAccessibilityService.getInstance()?.let { service ->
                val focusedNode = service.rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                if (focusedNode != null) {
                    val text = focusedNode.text?.toString()
                    if (!text.isNullOrEmpty()) {
                        val args = android.os.Bundle().apply {
                            putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                            putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                        }
                        focusedNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
                        mainHandler.post {
                            Toast.makeText(this, "Text selected", Toast.LENGTH_SHORT).show()
                        }
                    }
                    focusedNode.recycle()
                } else {
                    mainHandler.post {
                        Toast.makeText(this, "No text field focused", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: run {
                mainHandler.post {
                    Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error performing select all", e)
            mainHandler.post {
                Toast.makeText(this, "Could not select text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Reprocess the currently selected text through AI.
     */
    private fun reprocessSelectedText() {
        try {
            val selectedText = DictationAccessibilityService.getSelectedTextDirect()
            if (selectedText.isNullOrEmpty()) {
                mainHandler.post {
                    Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Send to reprocess
            serviceScope.launch {
                reprocessText(selectedText)
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error reprocessing selected text", e)
            mainHandler.post {
                Toast.makeText(this, "Could not reprocess text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Toggle AI post-processing on/off.
     */
    private fun toggleAIProcessing() {
        val prefs = settingsManager.getSettings()
        val current = prefs.getBoolean("enable_postprocess", false)
        val newValue = !current

        prefs.edit()
            .putBoolean("enable_postprocess", newValue)
            .apply()

        // Show feedback
        mainHandler.post {
            val status = if (newValue) "enabled" else "disabled"
            Toast.makeText(this, "AI Processing $status", Toast.LENGTH_SHORT).show()
        }

        // Update compose bubble if using it
        bubbleComposeController?.updateAIState(newValue)
    }

    /**
     * Open the settings activity.
     */
    private fun openSettings() {
        try {
            val intent = Intent(this, SimpleSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Failed to open settings", e)
            mainHandler.post {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupReprocessReceiver() {
        reprocessReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("BubbleOverlayService", "=== REPROCESS BROADCAST RECEIVED ===")
                Log.d("BubbleOverlayService", "Intent action: ${intent?.action}")
                Log.d("BubbleOverlayService", "Expected action: $ACTION_REPROCESS_AUDIO")

                if (intent?.action == ACTION_REPROCESS_AUDIO) {
                    val audioFilePath = intent.getStringExtra("audio_file_path")
                    val audioFileName = intent.getStringExtra("audio_file_name")
                    val contextData = intent.getStringExtra("context")

                    Log.d("BubbleOverlayService", "Received reprocess request for: $audioFileName")
                    Log.d("BubbleOverlayService", "Audio file path: $audioFilePath")
                    Log.d("BubbleOverlayService", "Context data: $contextData")

                    if (audioFilePath != null && audioFileName != null) {
                        Log.d("BubbleOverlayService", "Starting reprocess coroutine...")
                        serviceScope.launch {
                            reprocessAudioFile(audioFilePath, audioFileName, contextData)
                        }
                    } else {
                        Log.e("BubbleOverlayService", "Missing audio file path or name - path: $audioFilePath, name: $audioFileName")
                    }
                } else {
                    Log.w("BubbleOverlayService", "Received broadcast with wrong action: ${intent?.action}")
                }
            }
        }

        // Register the receiver
        val filter = IntentFilter(ACTION_REPROCESS_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(reprocessReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(reprocessReceiver, filter)
        }
        Log.d("BubbleOverlayService", "Reprocess receiver registered with action: $ACTION_REPROCESS_AUDIO")
    }

    private suspend fun reprocessAudioFile(audioFilePath: String, audioFileName: String, contextData: String?) {
        // Broadcast retry started for history screen state sync
        broadcastRetryStarted(audioFilePath)
        broadcastProcessingState(true)
        var success = false
        try {
            Log.d("BubbleOverlayService", "=== STARTING REPROCESS AUDIO FILE ===")
            Log.d("BubbleOverlayService", "Audio file path: $audioFilePath")
            Log.d("BubbleOverlayService", "Audio file name: $audioFileName")
            Log.d("BubbleOverlayService", "Context data: $contextData")

            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                Log.e("BubbleOverlayService", "Audio file not found for reprocessing: $audioFilePath")
                return
            }

            Log.d("BubbleOverlayService", "Audio file exists, starting reprocess transcription for: $audioFileName")

            // Parse context data
            if (contextData != null) {
                Log.d("BubbleOverlayService", "Parsing context data...")
                parseContextData(contextData)
            } else {
                Log.d("BubbleOverlayService", "No context data, clearing contexts")
                // Clear context for reprocessing
                selectedTextContext = null
                currentAppContext = null
            }

            // Perform transcription with current settings with timeout
            Log.d("BubbleOverlayService", "Starting transcription...")
            val transcription = withTimeout(75_000) { // 75 second timeout
                performTranscription(audioFile)
            }
            Log.d("BubbleOverlayService", "Transcription completed, length: ${transcription.length}")

            if (transcription.isNotBlank()) {
                Log.d("BubbleOverlayService", "Reprocess transcription result: '$transcription'")

                // Store the original audio file reference for logging
                this.audioFile = audioFile

                // Process for reprocessing (creates new log entry instead of inserting text)
                Log.d("BubbleOverlayService", "Sending transcription for reprocessing...")
                sendTranscriptionForReprocessing(transcription, audioFileName)

                Log.d("BubbleOverlayService", "Reprocess completed for: $audioFileName")
                success = true
            } else {
                Log.w("BubbleOverlayService", "Reprocess transcription was empty")
            }

        } catch (e: TimeoutCancellationException) {
            Log.e("BubbleOverlayService", "Transcription timeout during reprocessing - taking too long", e)
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error during audio reprocessing", e)
            e.printStackTrace()
        } finally {
            broadcastProcessingState(false)
            // Broadcast retry completed for history screen state sync
            broadcastRetryCompleted(audioFilePath, success)
        }
    }

    private fun parseContextData(contextData: String) {
        // Parse the context data from the log entry
        val lines = contextData.split("\n")
        selectedTextContext = null
        currentAppContext = null
        screenContext = null

        for (line in lines) {
            when {
                line.startsWith("App: ") -> {
                    currentAppContext = line.substring(5)
                }
                line.startsWith("Selected Text: ") -> {
                    val selectedText = line.substring(15)
                    if (selectedText.isNotBlank()) {
                        selectedTextContext = selectedText
                    }
                }
                line.startsWith("Screen: ") -> {
                    val scr = line.substring(8)
                    if (scr.isNotBlank()) {
                        screenContext = scr
                    }
                }
            }
        }

        Log.d("BubbleOverlayService", "Parsed context - App: '$currentAppContext', Selected: '$selectedTextContext', Screen: '$screenContext'")
    }

    private suspend fun collectRecordingContext() {
        try {
            Log.d("BubbleOverlayService", "Collecting dictation context after recording start")

            val prefs = overrideForSimpleMode(getSharedPreferences("app_settings", Context.MODE_PRIVATE))
            val includeScreenContext = prefs.getBoolean("include_screen_context", false)

            // Only fetch if not already provided (e.g. by IME)
            if (selectedTextContext == null) {
                val selectedText = withContext(Dispatchers.Main) {
                    DictationAccessibilityService.getSelectedTextDirect()
                }
                selectedTextContext = selectedText?.takeIf { it.isNotBlank() }
                Log.d("BubbleOverlayService", "Selected text context result (Accessibility): '$selectedTextContext'")
            } else {
                Log.d("BubbleOverlayService", "Selected text context result (IME): '$selectedTextContext'")
            }

            val appContext = withContext(Dispatchers.Main) {
                getCurrentAppContext()
            }
            currentAppContext = appContext
            Log.d("BubbleOverlayService", "Current app context result: '$currentAppContext'")

            if (includeScreenContext) {
                val screenText = withContext(Dispatchers.Main) {
                    DictationAccessibilityService.getScreenTextDirect()
                }
                screenContext = screenText?.takeIf { it.isNotBlank() }
                Log.d("BubbleOverlayService", "Screen context result: '$screenContext'")
            } else {
                screenContext = null
                Log.d("BubbleOverlayService", "Screen context disabled")
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error collecting recording context", e)
        }
    }

    private fun broadcastImeRecordingState(isRecording: Boolean) {
        try {
            val intent = Intent(ACTION_IME_RECORDING_STATE).apply {
                setPackage(packageName)
                putExtra("isRecording", isRecording)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w("BubbleOverlayService", "Failed to broadcast IME recording state", e)
        }
    }

    private fun broadcastAmplitude(amplitude: Int) {
        try {
            val intent = Intent(ACTION_AMPLITUDE_UPDATE).apply {
                setPackage(packageName)
                putExtra("amplitude", amplitude)
            }
            sendBroadcast(intent)

            // Also update compose bubble controller if active
            bubbleComposeController?.updateAmplitude(amplitude)
        } catch (_: Exception) { }
    }

    private fun broadcastProcessingState(isProcessing: Boolean) {
        try {
            val intent = Intent(ACTION_PROCESSING_STATE).apply {
                setPackage(packageName)
                putExtra("isProcessing", isProcessing)
            }
            sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    private fun ensureStreamingSession(): StreamingDictationSession {
        val existing = streamingSession
        if (existing != null) {
            return existing
        }

        val session = StreamingDictationSession(
            context = this,
            scope = serviceScope,
            settingsManager = settingsManager,
            secureApiKeyManager = secureApiKeyManager,
            transcriptionServiceManager = transcriptionServiceManager,
            networkManager = networkManager,
            aiProcessingManager = aiProcessingManager,
            contextProvider = {
                StreamingDictationSession.ConversationContext(
                    selectedText = selectedTextContext ?: "",
                    screenContext = screenContext ?: "",
                    appContext = currentAppContext ?: ""
                )
            }
        )
        streamingSession = session

        streamingStateJob?.cancel()
        streamingStateJob = serviceScope.launch {
            session.uiState.collectLatest { state ->
                handleStreamingUiState(state)
            }
        }

        return session
    }

    private fun handleStreamingUiState(state: StreamingUiState) {
        val shouldAllowTerminalKeyboardUpdate =
            isStreamingInKeyboardMode && lastStreamingBroadcastActive && !state.isActive

        if (!state.isActive && state.statusMessage.isBlank() && !state.hasError && !shouldAllowTerminalKeyboardUpdate) {
            return
        }
        
        // Broadcast amplitude for waveform visualization during streaming
        if (state.isRecording && state.amplitude > 0) {
            broadcastAmplitude(state.amplitude)
        }
        
        // Broadcast update to IME (In-Keyboard Mode)
        if (isStreamingInKeyboardMode) {
            val text = state.formattedTranscript.ifEmpty { state.livePreview }
            val now = SystemClock.elapsedRealtime()
            val activeChanged = state.isActive != lastStreamingBroadcastActive
            val statusChanged = state.statusMessage != lastStreamingBroadcastStatus
            val textChanged = text != lastStreamingBroadcastText
            val shouldBroadcast = activeChanged || statusChanged || textChanged
            val timeOk = now - lastStreamingBroadcastAt >= streamingBroadcastMinIntervalMs

            if (shouldBroadcast && (timeOk || activeChanged)) {
                lastStreamingBroadcastAt = now
                lastStreamingBroadcastActive = state.isActive
                lastStreamingBroadcastStatus = state.statusMessage
                lastStreamingBroadcastText = text

                val intent = Intent(ACTION_STREAMING_UPDATE).apply {
                    putExtra("is_active", state.isActive)
                    putExtra("status", state.statusMessage)
                    putExtra("text", text)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        } else if (useStreamingOverlayUi) {
            // Bubble Mode
            Log.d(
                "BubbleOverlayService",
                "Streaming UI state update -> active=${state.isActive}, recording=${state.isRecording}, status='${state.statusMessage}', error='${state.errorMessage}'"
            )
            if (state.isActive) {
                streamingOverlayController.updateState(state)
            } else {
                streamingOverlayController.hide()
            }
        }
    }

    // Track if we are in "In-Keyboard" streaming mode
    private var isStreamingInKeyboardMode: Boolean = false

    // ==================== Voice Recognize Mode (External Keyboard Integration) ====================

    /**
     * Starts voice recognition for external keyboard integration (RECOGNIZE_SPEECH intent).
     * Similar to startRecording but doesn't show bubble and sends results back to VoiceRecognizeActivity.
     */
    private fun startVoiceRecognize() {
        // Guard against any active recorder state (regular recording, note recording, streaming, or existing voice recognize)
        if (isRecording || isVoiceRecognizeMode || isRecordingNote || isStreamingModeActive) {
            Log.w("BubbleOverlayService", "Already recording or in voice recognize mode")
            sendVoiceRecognizeError("Recording already in progress")
            return
        }

        Log.d("BubbleOverlayService", "Starting voice recognition for external keyboard")
        isVoiceRecognizeMode = true
        voiceRecognizeProcessing = false

        // Don't show bubble - the activity shows its own UI
        // Use internal cache directory for ephemeral voice recognize recordings (privacy)
        try {
            // Request audio focus before recording
            Log.d("BubbleOverlayService", "Requesting audio focus for voice recognize")
            val focusGranted = audioFocusManager.requestFocus()
            if (!focusGranted) {
                Log.w("BubbleOverlayService", "Audio focus denied for voice recognize - continuing anyway")
            }

            val dir = cacheDir
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            audioFile = File(dir, "voice_recognize_$timestamp.m4a")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            broadcastVoiceRecognizeProcessing(false) // Not processing yet, just recording
            startAmplitudeMonitoringForVoiceRecognize()

            Log.d("BubbleOverlayService", "Voice recognition recording started")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Failed to start voice recognition recording - ${e.javaClass.simpleName}: ${e.message}", e)
            // Clean up any partially created resources
            audioFocusManager.abandonFocus()
            mediaRecorder = safeStopAndReleaseRecorder(mediaRecorder, wasRecording = false)
            audioFile?.delete()
            audioFile = null
            isVoiceRecognizeMode = false
            voiceRecognizeProcessing = false
            sendVoiceRecognizeError("Failed to start recording: ${e.message}")
        }
    }

    /**
     * Stops voice recognition. If cancel is false, transcribes the audio and sends result.
     */
    private fun stopVoiceRecognize(cancel: Boolean = false) {
        if (!isVoiceRecognizeMode) {
            Log.w("BubbleOverlayService", "Not in voice recognize mode")
            return
        }

        // Re-entrancy guard: if we're already processing, ignore subsequent STOPs
        if (voiceRecognizeProcessing) {
            Log.w("BubbleOverlayService", "Voice recognize already processing, ignoring duplicate STOP")
            return
        }

        Log.d("BubbleOverlayService", "Stopping voice recognition, cancel=$cancel")
        amplitudeJob?.cancel()

        try {
            mediaRecorder = safeStopAndReleaseRecorder(mediaRecorder, wasRecording = isRecording)
            isRecording = false
            audioFocusManager.abandonFocus()

            if (cancel) {
                // User cancelled - just clean up
                isVoiceRecognizeMode = false
                voiceRecognizeProcessing = false
                audioFile?.delete()
                audioFile = null
                return
            }

            val file = audioFile
            if (file != null && file.exists() && file.length() > 0) {
                // Set processing flag BEFORE launching coroutine to prevent race
                voiceRecognizeProcessing = true
                broadcastVoiceRecognizeProcessing(true)

                serviceScope.launch {
                    try {
                        val transcription = performTranscription(file)

                        if (isSuccessfulTranscription(transcription)) {
                            // Apply text replacements
                            val processedText = applyCustomTextReplacements(transcription)
                            Log.d("BubbleOverlayService", "Voice recognize transcription: $processedText")

                            // Send result back to activity
                            sendVoiceRecognizeResult(processedText)
                        } else {
                            Log.w("BubbleOverlayService", "Voice recognize transcription failed: $transcription")
                            sendVoiceRecognizeError(transcription ?: "Transcription failed")
                        }
                    } catch (e: Exception) {
                        Log.e("BubbleOverlayService", "Voice recognize transcription error", e)
                        sendVoiceRecognizeError("Transcription error: ${e.message}")
                    } finally {
                        isVoiceRecognizeMode = false
                        voiceRecognizeProcessing = false
                        broadcastVoiceRecognizeProcessing(false)
                        // Clean up temp audio file
                        file.delete()
                        audioFile = null
                    }
                }
            } else {
                Log.e("BubbleOverlayService", "No audio file for voice recognize")
                isVoiceRecognizeMode = false
                voiceRecognizeProcessing = false
                sendVoiceRecognizeError("No audio recorded")
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error stopping voice recognition", e)
            isVoiceRecognizeMode = false
            voiceRecognizeProcessing = false
            sendVoiceRecognizeError("Error: ${e.message}")
        }
    }

    /**
     * Monitors amplitude during voice recognition and broadcasts to the activity.
     */
    private fun startAmplitudeMonitoringForVoiceRecognize() {
        amplitudeJob?.cancel()
        amplitudeJob = serviceScope.launch {
            while (isRecording && isVoiceRecognizeMode) {
                try {
                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                    val intent = Intent(VoiceRecognizeActivity.ACTION_VOICE_RECOGNIZE_AMPLITUDE).apply {
                        putExtra("amplitude", amplitude)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.w("BubbleOverlayService", "Error getting amplitude for voice recognize", e)
                }
                delay(100)
            }
        }
    }

    /**
     * Sends successful transcription result back to VoiceRecognizeActivity.
     */
    private fun sendVoiceRecognizeResult(text: String) {
        val intent = Intent(VoiceRecognizeActivity.ACTION_VOICE_RECOGNIZE_RESULT).apply {
            putExtra("text", text)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d("BubbleOverlayService", "Sent voice recognize result: $text")
    }

    /**
     * Sends error back to VoiceRecognizeActivity.
     */
    private fun sendVoiceRecognizeError(error: String) {
        val intent = Intent(VoiceRecognizeActivity.ACTION_VOICE_RECOGNIZE_ERROR).apply {
            putExtra("error", error)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d("BubbleOverlayService", "Sent voice recognize error: $error")
    }

    /**
     * Broadcasts processing state to VoiceRecognizeActivity.
     */
    private fun broadcastVoiceRecognizeProcessing(isProcessing: Boolean) {
        val intent = Intent(VoiceRecognizeActivity.ACTION_VOICE_RECOGNIZE_PROCESSING).apply {
            putExtra("isProcessing", isProcessing)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ==================== End Voice Recognize Mode ====================

    private fun startRecording(forceStreaming: Boolean = false, uiMode: String? = null, imeSelectedText: String? = null) {
        if (isRecording) {
            // Allow switching to smart streaming if we are currently recording but NOT in smart streaming mode.
            // This handles switching from:
            // 1. Standard/Legacy recording -> Smart Streaming
            // 2. Soniox recording (which sets isStreamingModeActive=true but isSmartStreamingActive=false) -> Smart Streaming
            if (forceStreaming && !isSmartStreamingActive) {
                Log.d("BubbleOverlayService", "Switching from current recording to smart streaming")
                
                // Stop the current session properly before starting the new one
                if (isStreamingModeActive) {
                     // We must stop the current streaming session (e.g. Soniox)
                     // Since stop() is suspend, and we need to proceed, we launch a new coroutine
                     // to handle the restart sequence.
                     serviceScope.launch(Dispatchers.Main) {
                         streamingSession?.stop(StopReason.CANCELED)
                         isStreamingModeActive = false
                         isSmartStreamingActive = false
                         
                         // Recursively call startRecording to start the new session
                         startRecording(forceStreaming, uiMode, imeSelectedText)
                     }
                     return
                }

                // Synchronously cleanup standard recording to free microphone
                isRecording = false
                amplitudeJob?.cancel()
                amplitudeJob = null
                contextCollectionJob?.cancel()
                contextCollectionJob = null
                
                try {
                    if (isOfflineRecordingMode) {
                        offlineRecorder?.stop()
                        offlineRecorder = null
                        offlineRecordingSamples = null
                    } else {
                        mediaRecorder?.apply {
                            try { stop() } catch(e: Exception) {}
                            release()
                        }
                        mediaRecorder = null
                    }
                } catch (e: Exception) {
                    Log.e("BubbleOverlayService", "Error stopping recorder during switch", e)
                }
                
                audioFile?.delete()
                audioFile = null
                
                updateUIVisibility(false)
            } else {
                return
            }
        }

        Log.d("BubbleOverlayService", "Starting dictation recording (forceStreaming=$forceStreaming, uiMode=$uiMode)")

        // Request audio focus before starting any recording mode (streaming, offline, or standard)
        Log.d("BubbleOverlayService", "Requesting audio focus before recording")
        val focusGranted = audioFocusManager.requestFocus()
        if (!focusGranted) {
            Log.w("BubbleOverlayService", "Audio focus denied - another app may hold focus or a call is active. Continuing anyway.")
            // Continue anyway - some devices work without explicit focus
        }

        val service = settingsManager.getTranscriptionService()
        if (forceStreaming || settingsManager.isStreamingDictationEnabled() || service == "Soniox Real-Time") {
            val serviceOverride = if (forceStreaming || settingsManager.isStreamingDictationEnabled()) {
                "Groq Whisper v3 Turbo"
            } else {
                null
            }

            val isSonioxNativeStreaming = service == "Soniox Real-Time" && serviceOverride == null

            // Only use keyboard streaming UI when the IME explicitly requested it, or when
            // Soniox was started while this app's IME is actually active.
            isStreamingInKeyboardMode = (uiMode == "keyboard") || (isSonioxNativeStreaming && isDictationImeActive)

            // For Soniox started from the floating bubble with a third-party IME, keep the
            // bubble visible so the user still has a persistent stop control.
            useStreamingOverlayUi = !isStreamingInKeyboardMode && !isSonioxNativeStreaming

            val session = ensureStreamingSession()
            when (val result = session.start(serviceOverride)) {
                StreamingStartResult.Started -> {
                    Log.d("BubbleOverlayService", "Streaming dictation session started")
                    isStreamingModeActive = true
                    isSmartStreamingActive = (serviceOverride != null)
                    audioFile = null
                    if (isStreamingInKeyboardMode || useStreamingOverlayUi) {
                        hideBubble()
                    } else {
                        streamingOverlayController.hide()
                        showBubble()
                    }
                    isRecording = true
                    updateUIVisibility(isRecording)
                    updateNotification()
                    broadcastImeRecordingState(true)
                    HapticUtils.performHapticFeedback(this@BubbleOverlayService)
                    clearContexts()
                    contextCollectionJob?.cancel()
                    contextCollectionJob = serviceScope.launch {
                        collectRecordingContext()
                    }
                    return
                }
                is StreamingStartResult.Failure -> {
                    Log.e("BubbleOverlayService", "Streaming start failed: ${result.message}")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            this,
                            result.message ?: "Unable to start streaming dictation",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                StreamingStartResult.Unsupported -> {
                    Log.d("BubbleOverlayService", "Streaming not available; falling back to legacy recording")
                }
            }
        }

        val offlineSelected = settingsManager.getTranscriptionService() == OFFLINE_TRANSCRIPTION_OPTION_LABEL
        isOfflineRecordingMode = offlineSelected
        offlineRecordingSamples = null
        offlineRecorder = null
        isSmartStreamingActive = false

        try {
            if (offlineSelected) {
                offlineRecordingSampleRate = 16_000
                val wavFile = audioFileManager.createTempPcmAudioFile()
                this.audioFile = wavFile

                val recorder = OfflinePcmRecorder(offlineRecordingSampleRate)
                offlineRecorder = recorder
                recorder.start()

                isRecording = true
                updateUIVisibility(isRecording)
                updateNotification()
                broadcastImeRecordingState(true)
                HapticUtils.performHapticFeedback(this@BubbleOverlayService)
                Log.d("BubbleOverlayService", "Offline PCM recording started successfully")

                clearContexts()
                // Set from IME if available
                if (imeSelectedText != null) {
                    selectedTextContext = imeSelectedText
                }
                contextCollectionJob?.cancel()
                contextCollectionJob = serviceScope.launch {
                    collectRecordingContext()
                }
                return
            }

            val audioFile = audioFileManager.createTempAudioFile()
            this.audioFile = audioFile

            val targetSampleRate = 44100
            val targetBitrate = 128000

            @Suppress("DEPRECATION")
            mediaRecorder = MediaRecorder().apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    Log.d("BubbleOverlayService", "Using VOICE_RECOGNITION audio source")
                } catch (e: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    Log.d("BubbleOverlayService", "VOICE_RECOGNITION not supported, using MIC source")
                }

                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                try {
                    setAudioSamplingRate(targetSampleRate)
                    setAudioEncodingBitRate(targetBitrate)
                    setAudioChannels(1)
                    Log.d("BubbleOverlayService", "Audio settings: ${targetSampleRate} Hz, ${targetBitrate} bps AAC, Mono (offlineSelected=false)")
                } catch (e: Exception) {
                    Log.w("BubbleOverlayService", "Could not set preferred audio settings: ${e.message}")
                    try {
                        setAudioSamplingRate(16000)
                        setAudioEncodingBitRate(64000)
                        setAudioChannels(1)
                        Log.d("BubbleOverlayService", "Using fallback audio: 16kHz, 64kbps AAC, Mono")
                    } catch (e2: Exception) {
                        Log.w("BubbleOverlayService", "Using device default audio settings")
                    }
                }

                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true

            // Start amplitude polling
            amplitudeJob?.cancel()
            amplitudeJob = serviceScope.launch {
                while (isActive && isRecording) {
                    val amp = try { mediaRecorder?.maxAmplitude ?: 0 } catch(e: Exception) { 0 }
                    val now = SystemClock.elapsedRealtime()
                    val delta = kotlin.math.abs(amp - lastAmplitudeValue)
                    if (lastAmplitudeValue == -1 || delta >= amplitudeChangeThreshold || now - lastAmplitudeSentAt >= amplitudeMinIntervalMs) {
                        lastAmplitudeSentAt = now
                        lastAmplitudeValue = amp
                        broadcastAmplitude(amp)
                    }
                    delay(50)
                }
            }

            updateUIVisibility(isRecording)
            updateNotification()
            broadcastImeRecordingState(true)
            HapticUtils.performHapticFeedback(this@BubbleOverlayService)

            Log.d("BubbleOverlayService", "Audio recording started successfully")

            clearContexts()
            // Set from IME if available
            if (imeSelectedText != null) {
                selectedTextContext = imeSelectedText
            }
            contextCollectionJob?.cancel()
            contextCollectionJob = serviceScope.launch {
                collectRecordingContext()
            }
        } catch (e: Exception) {
            // Enhanced diagnostic logging for debugging microphone issues
            Log.e("BubbleOverlayService", "Failed to start recording - Exception type: ${e.javaClass.simpleName}", e)
            Log.e("BubbleOverlayService", "Recording failure details - message: ${e.message}, cause: ${e.cause?.message}")

            // Abandon audio focus since recording failed
            audioFocusManager.abandonFocus()

            isRecording = false
            updateUIVisibility(isRecording)
            updateNotification()
            broadcastImeRecordingState(false)

            // Show user feedback so they know recording failed
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    "Unable to start recording. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Keep running in the foreground so a restart does not race foreground deadlines.
            offlineRecorder = null
            offlineRecordingSamples = null
            isOfflineRecordingMode = false
        }
    }

    private fun stopRecording() {
        if (isStreamingModeActive) {
            stopStreamingWithReason(StopReason.COMPLETED)
        } else {
            stopRecordingAndTranscribe()
        }
    }

    private fun stopStreamingWithReason(reason: StopReason) {
        val session = streamingSession
        if (session == null) {
            Log.w("BubbleOverlayService", "stopStreamingWithReason invoked without session (reason=$reason)")
            isStreamingModeActive = false
            isRecording = false
            audioFocusManager.abandonFocus()
            updateUIVisibility(isRecording)
            updateNotification()
            broadcastImeRecordingState(false)
            if (isStreamingInKeyboardMode) {
                try {
                    val intent = Intent(ACTION_STREAMING_UPDATE).apply {
                        putExtra("is_active", false)
                        putExtra("status", "")
                        putExtra("text", "")
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                } catch (_: Exception) { }
            }
            return
        }

        isStreamingModeActive = false
        isRecording = false
        audioFocusManager.abandonFocus()
        updateUIVisibility(isRecording)
        updateNotification()
        broadcastImeRecordingState(false)
        HapticUtils.performHapticFeedback(this@BubbleOverlayService)

        contextCollectionJob?.cancel()
        contextCollectionJob = null

        serviceScope.launch {
            val result = try {
                session.stop(reason)
            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Streaming session stop failed", e)
                StreamingResult.Failed(e.message)
            }
            withContext(Dispatchers.Main) {
                handleStreamingResult(result)
            }
        }
    }

    private fun handleStreamingResult(result: StreamingResult) {
        when (result) {
            is StreamingResult.Completed -> {
                Log.d("BubbleOverlayService", "Streaming completed with ${result.text.length} chars")
                val selectedSnapshot = selectedTextContext
                val appSnapshot = currentAppContext
                val screenSnapshot = screenContext
                
                // Check if LLM Post-Processing is enabled
                val isPostProcessEnabled = settingsManager.getSettings().getBoolean("enable_postprocess", false)
                
                if (isPostProcessEnabled && result.text.isNotBlank()) {
                    broadcastProcessingState(true)
                    serviceScope.launch {
                        try {
                            val aiModel = settingsManager.getAIModel()
                            Log.d("BubbleOverlayService", "Post-processing streaming text with $aiModel")
                            
                            val useCommandForSelectedText = settingsManager.isUseCommandPromptForSelectedTextEnabled()
                            val hasSelectedText = !selectedSnapshot.isNullOrBlank()
                            val isCommandMode = useCommandForSelectedText && hasSelectedText

                            val processedText = aiProcessingManager.processWithAI(
                                transcription = result.text,
                                context = selectedSnapshot ?: "",
                                screenContext = screenSnapshot ?: "",
                                currentAppContext = appSnapshot ?: "",
                                isCommandMode = isCommandMode
                            )
                            
                            val finalText = if (isSuccessfulAiProcessingResult(processedText)) {
                                processedText!!
                            } else {
                                result.text
                            }

                            Handler(Looper.getMainLooper()).post {
                                insertText(finalText)
                            }
                            if (!isSuccessfulAiProcessingResult(processedText)) {
                                showAiFallbackToast(processedText)
                            }
                            persistStreamingLogEntry(finalText, selectedSnapshot, appSnapshot, screenSnapshot)
                        } catch (e: Exception) {
                            Log.e("BubbleOverlayService", "Post-processing error", e)
                            Handler(Looper.getMainLooper()).post {
                                insertText(result.text)
                            }
                            showAiFallbackToast(e.message)
                            persistStreamingLogEntry(result.text, selectedSnapshot, appSnapshot, screenSnapshot)
                        } finally {
                            broadcastProcessingState(false)
                        }
                    }
                } else {
                    // Direct insertion
                    Handler(Looper.getMainLooper()).post {
                        insertText(result.text)
                    }
                    persistStreamingLogEntry(result.text, selectedSnapshot, appSnapshot, screenSnapshot)
                }

                // We need to tell the IME that streaming has finished!
                if (isStreamingInKeyboardMode) {
                    val intent = Intent(ACTION_STREAMING_UPDATE).apply {
                        putExtra("is_active", false)
                        putExtra("status", "Completed")
                        putExtra("text", result.text)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }

                streamingOverlayController.hide()
                if (settingsManager.isBubbleOverlayEnabled()) {
                    mainHandler.post { showBubble() }
                }
                
                selectedTextContext = null
                currentAppContext = null
                screenContext = null
            }
            StreamingResult.Canceled -> {
                Log.d("BubbleOverlayService", "Streaming session canceled by user")
                
                if (isStreamingInKeyboardMode) {
                    val intent = Intent(ACTION_STREAMING_UPDATE).apply {
                        putExtra("is_active", false)
                        putExtra("status", "Canceled")
                        putExtra("text", "")
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }
                
                streamingOverlayController.hide()
                if (settingsManager.isBubbleOverlayEnabled()) {
                    mainHandler.post { showBubble() }
                }
            }
            is StreamingResult.Failed -> {
                Log.e("BubbleOverlayService", "Streaming session failed: ${result.message}")
                
                if (isStreamingInKeyboardMode) {
                    val intent = Intent(ACTION_STREAMING_UPDATE).apply {
                        putExtra("is_active", false)
                        putExtra("status", "Error")
                        putExtra("text", result.message ?: "Failed")
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }
                
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this,
                        result.message ?: "Streaming session failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
                streamingOverlayController.hide()
                if (settingsManager.isBubbleOverlayEnabled()) {
                    mainHandler.post { showBubble() }
                }
            }
            StreamingResult.Unsupported -> {
                Log.d("BubbleOverlayService", "Streaming result unsupported; legacy pipeline should handle output")
                streamingOverlayController.hide()
                if (settingsManager.isBubbleOverlayEnabled()) {
                    mainHandler.post { showBubble() }
                }
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        amplitudeJob?.cancel()
        try {
            val offlineMode = isOfflineRecordingMode
            var capturedSamples: FloatArray? = null

            if (offlineMode) {
                val recorder = offlineRecorder
                offlineRecorder = null
                if (recorder != null) {
                    try {
                        capturedSamples = recorder.stop()
                        offlineRecordingSamples = capturedSamples
                    } catch (e: Exception) {
                        Log.e("BubbleOverlayService", "Error stopping offline recorder", e)
                        capturedSamples = FloatArray(0)
                        offlineRecordingSamples = capturedSamples
                    }
                }
                // Performance fix: Use safe cleanup helper
                mediaRecorder = safeStopAndReleaseRecorder(mediaRecorder, wasRecording = true)
            } else {
                // Performance fix: Use safe cleanup helper
                mediaRecorder = safeStopAndReleaseRecorder(mediaRecorder, wasRecording = true)
        }

        isRecording = false
        updateUIVisibility(isRecording)
        updateNotification()
        broadcastImeRecordingState(false)

            // Provide haptic feedback for recording stop
            HapticUtils.performHapticFeedback(this@BubbleOverlayService)

            Log.d("BubbleOverlayService", "Recording stopped, starting transcription")

            var targetFile = audioFile
            if (offlineMode) {
                if (targetFile == null) {
                    targetFile = audioFileManager.createTempPcmAudioFile().also { audioFile = it }
                }
                val sampleCount = capturedSamples?.size ?: 0
                if (offlineMode) {
                    val durationSec = if (offlineRecordingSampleRate > 0) sampleCount.toFloat() / offlineRecordingSampleRate else 0f
                    Log.d(
                        "BubbleOverlayService",
                        "Offline recording captured ${sampleCount} samples (~${"%.2f".format(durationSec)} s)"
                    )
                }

                if (capturedSamples != null && capturedSamples.isNotEmpty() && targetFile != null) {
                    try {
                        OfflineWavWriter.write16BitMono(targetFile, capturedSamples, offlineRecordingSampleRate)
                    } catch (e: Exception) {
                        Log.e("BubbleOverlayService", "Failed writing offline WAV file", e)
                    }
                }
            }

            val file = targetFile
            if (file != null && (file.exists() || (offlineMode && capturedSamples != null && capturedSamples.isNotEmpty()))) {
                broadcastProcessingState(true)
                serviceScope.launch {
                    try {
                        // Save audio BEFORE transcription API call - ensures audio is never lost
                        val savedAudioFileName = withContext(Dispatchers.IO) {
                            moveAudioToFinalLocation()
                        }

                        // Use the moved file for transcription (original temp file no longer exists)
                        val transcriptionFile = if (savedAudioFileName != null) {
                            File(getAudioFilePath(savedAudioFileName))
                        } else {
                            file  // Fallback to original if move failed
                        }

                        contextCollectionJob?.join()
                        contextCollectionJob = null

                        // Start total processing timer
                        val totalMetricsBuilder = PerformanceMetricsBuilder().startTotalProcessing()

                        val (transcription, transcriptionMetrics) = performTranscriptionWithMetrics(
                            transcriptionFile,
                            offlineSamples = if (offlineMode) capturedSamples else null,
                            offlineSampleRate = offlineRecordingSampleRate
                        )

                        if (isSuccessfulTranscription(transcription)) {
                            Log.d("BubbleOverlayService", "Raw transcription: '$transcription'")

                            sendTranscriptionForProcessingWithMetrics(transcription, transcriptionMetrics, totalMetricsBuilder)

                            Log.d("BubbleOverlayService", "Text processing completed: '$transcription'")
                        } else {
                            // Save failed entry with preserved audio and show service-specific toast
                            val serviceName = settingsManager.getTranscriptionService()
                            saveFailedLogEntry(
                                audioFileName = savedAudioFileName,
                                failurePoint = FailurePoint.TRANSCRIPTION,
                                serviceName = serviceName,
                                errorMessage = transcription ?: "Unknown error"
                            )
                            showFailureToast(FailurePoint.TRANSCRIPTION, serviceName)
                            // Notify keyboard for retry button
                            broadcastTranscriptionFailure(
                                audioFilePath = savedAudioFileName?.let { getAudioFilePath(it) },
                                serviceName = serviceName
                            )
                            Log.w("BubbleOverlayService", "Transcription failed: $transcription")
                        }
                    } catch (e: Exception) {
                        Log.e("BubbleOverlayService", "Error during transcription", e)
                    } finally {
                        broadcastProcessingState(false)
                    }
                }
            } else {
                Log.e("BubbleOverlayService", "Audio file is null or doesn't exist")
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error stopping recording", e)
        } finally {
            // Always release audio focus when recording stops (success or failure)
            audioFocusManager.abandonFocus()
        }
        isOfflineRecordingMode = false
    }

    private suspend fun performTranscription(
        audioFile: File,
        offlineSamples: FloatArray? = null,
        offlineSampleRate: Int = 16_000
    ): String {
        return transcriptionServiceManager.performTranscription(audioFile, offlineSamples, offlineSampleRate)
    }

    private suspend fun performTranscriptionWithMetrics(
        audioFile: File,
        offlineSamples: FloatArray? = null,
        offlineSampleRate: Int = 16_000
    ): Pair<String, PerformanceMetrics> {
        return transcriptionServiceManager.performTranscriptionWithMetrics(audioFile, offlineSamples, offlineSampleRate)
    }

    /**
     * Retry transcription for a previously failed audio file.
     * Uses current transcription settings.
     * Called from retryTranscriptionReceiver when keyboard sends retry request.
     */
    private fun retryTranscription(audioFilePath: String) {
        val audioFile = File(audioFilePath)
        if (!audioFile.exists()) {
            Log.e("BubbleOverlayService", "Retry failed: Audio file not found at $audioFilePath")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Audio file no longer available", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d("BubbleOverlayService", "Retrying transcription for: $audioFilePath")

        // Broadcast retry started for history screen state sync
        broadcastRetryStarted(audioFilePath)
        // Broadcast processing state to update UI
        broadcastProcessingState(true)

        // Run transcription on background thread
        serviceScope.launch {
            var success = false
            try {
                val (transcription, metrics) = performTranscriptionWithMetrics(audioFile)
                val serviceName = settingsManager.getTranscriptionService()

                withContext(Dispatchers.Main) {
                    if (isSuccessfulTranscription(transcription)) {
                        // Success - insert text via existing mechanism
                        Log.d("BubbleOverlayService", "Retry transcription success: ${transcription.take(50)}...")
                        sendTranscriptionForProcessing(transcription)
                        // Note: sendTranscriptionForProcessing will handle text insertion
                        success = true
                    } else {
                        // Failed again - show toast and re-broadcast failure
                        Toast.makeText(
                            this@BubbleOverlayService,
                            "Retry failed: ${transcription.take(50)}",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Re-broadcast failure to keep retry state
                        broadcastTranscriptionFailure(audioFilePath, serviceName)
                    }
                }
            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Retry transcription error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BubbleOverlayService,
                        "Retry failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                broadcastProcessingState(false)
                // Broadcast retry completed for history screen state sync
                broadcastRetryCompleted(audioFilePath, success)
            }
        }
    }

    private suspend fun performWhisperTranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("openai_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "OpenAI API key is missing")
                return "No OpenAI API key configured"
            }

            Log.d("BubbleOverlayService", "Starting Whisper API transcription")

            // Build custom vocabulary prompt for OpenAI Whisper
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val customVocabulary = prefs.getString("custom_vocabulary", "") ?: ""
            val languageConfig = settingsManager.getCustomLanguageConfig()
            val customSpelling = prefs.getString("custom_spelling", "") ?: ""
            val vocabularyPrompt = buildString {
                val vocabularyItems = mutableListOf<String>()

                // Add regular vocabulary terms
                if (customVocabulary.isNotBlank()) {
                    vocabularyItems.addAll(customVocabulary.split(",").map { it.trim() }.filter { it.isNotBlank() })
                }

                // Add custom spelling terms (both original and replacement forms)
                if (customSpelling.isNotBlank()) {
                    val spellingPairs = customSpelling.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it.contains("=") }
                        .mapNotNull { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                val from = parts[0].trim().removePrefix("\"").removeSuffix("\"").trim()
                                val to = parts[1].trim().removePrefix("\"").removeSuffix("\"").trim()
                                if (from.isNotBlank() && to.isNotBlank()) {
                                    Pair(from, to)
                                } else null
                            } else null
                        }

                    // Add both forms to vocabulary
                    spellingPairs.forEach { (from, to) ->
                        vocabularyItems.add(from)
                        vocabularyItems.add(to)
                    }
                }

                if (vocabularyItems.isNotEmpty()) {
                    // Limit to 20 words maximum for Whisper API accuracy
                    append(vocabularyItems.take(20).joinToString(", "))
                }
            }

            // Use shared HTTP client with connection pooling for better performance
            val client = transcriptionHttpClient
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "en")
                .addFormDataPart("response_format", "json")

            // Add custom vocabulary prompt if available
            if (vocabularyPrompt.isNotBlank()) {
                requestBuilder.addFormDataPart("prompt", vocabularyPrompt)
                Log.d("BubbleOverlayService", "Using custom vocabulary prompt: '$vocabularyPrompt'")
            }

            val requestBody = requestBuilder.build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            Log.d("BubbleOverlayService", "Making API call to Whisper...")
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val responseBody = response.body?.string()
            Log.d("BubbleOverlayService", "Whisper API response received - status: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("BubbleOverlayService", "Whisper API error: ${response.code} $responseBody")
                return "API error: ${response.code}"
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                Log.d("BubbleOverlayService", "Parsing Whisper response...")
                val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                val transcriptionResult = jsonResponse["text"]?.toString() ?: "Empty transcription"
                Log.d("BubbleOverlayService", "Whisper transcription completed: '${transcriptionResult.take(100)}...'")
                return transcriptionResult
            } else {
                Log.e("BubbleOverlayService", "Whisper API response was unsuccessful or empty")
                return "API error: ${response.code}"
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Whisper transcription error", e)
            return "Whisper transcription failed: ${e.message}"
        }
    }

    private suspend fun performGPT4oTranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("openai_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "OpenAI API key is missing")
                return "No OpenAI API key configured"
            }

            Log.d("BubbleOverlayService", "Starting GPT-4o Transcribe API transcription")

            // Build custom vocabulary prompt for GPT-4o Transcribe
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val customVocabulary = prefs.getString("custom_vocabulary", "") ?: ""
            val customSpelling = prefs.getString("custom_spelling", "") ?: ""
            val languageConfig = settingsManager.getCustomLanguageConfig()
            val vocabularyPrompt = buildString {
                val vocabularyItems = mutableListOf<String>()

                // Add regular vocabulary terms
                if (customVocabulary.isNotBlank()) {
                    vocabularyItems.addAll(customVocabulary.split(",").map { it.trim() }.filter { it.isNotBlank() })
                }

                // Add custom spelling terms (both original and replacement forms)
                if (customSpelling.isNotBlank()) {
                    val spellingPairs = customSpelling.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it.contains("=") }
                        .mapNotNull { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                val from = parts[0].trim().removePrefix("\"").removeSuffix("\"").trim()
                                val to = parts[1].trim().removePrefix("\"").removeSuffix("\"").trim()
                                if (from.isNotBlank() && to.isNotBlank()) {
                                    Pair(from, to)
                                } else null
                            } else null
                        }

                    // Add both forms to vocabulary
                    spellingPairs.forEach { (from, to) ->
                        vocabularyItems.add(from)
                        vocabularyItems.add(to)
                    }
                }

                if (vocabularyItems.isNotEmpty()) {
                    // Limit to 20 words maximum for Whisper API accuracy
                    append(vocabularyItems.take(20).joinToString(", "))
                }
            }

            // Use shared HTTP client with connection pooling for better performance
            val client = transcriptionHttpClient
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model", "gpt-4o-transcribe")
                .addFormDataPart("language", "en")
                .addFormDataPart("response_format", "json")

            // Add custom vocabulary prompt if available
            if (vocabularyPrompt.isNotBlank()) {
                requestBuilder.addFormDataPart("prompt", vocabularyPrompt)
                Log.d("BubbleOverlayService", "Using custom vocabulary prompt for GPT-4o: '$vocabularyPrompt'")
            }

            val requestBody = requestBuilder.build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            Log.d("BubbleOverlayService", "Making API call to GPT-4o Transcribe...")
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val responseBody = response.body?.string()
            Log.d("BubbleOverlayService", "GPT-4o Transcribe API response received - status: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("BubbleOverlayService", "GPT-4o Transcribe API error: ${response.code} $responseBody")
                return "API error: ${response.code}"
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                Log.d("BubbleOverlayService", "Parsing GPT-4o Transcribe response...")
                val jsonResponse = com.google.gson.Gson().fromJson(responseBody, Map::class.java)
                val transcriptionResult = jsonResponse["text"]?.toString() ?: "Empty transcription"
                Log.d("BubbleOverlayService", "GPT-4o Transcribe transcription completed: '${transcriptionResult.take(100)}...'")
                return transcriptionResult
            } else {
                Log.e("BubbleOverlayService", "GPT-4o Transcribe API response was unsuccessful or empty")
                return "API error: ${response.code}"
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "GPT-4o Transcribe transcription error", e)
            return "GPT-4o Transcribe transcription failed: ${e.message}"
        }
    }

    private suspend fun performGeminiTranscription(audioFile: File, model: String): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("google_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "Google API key is missing")
                return "No Google API key configured"
            }

            Log.d("BubbleOverlayService", "Starting Gemini transcription with model: $model")

            // Load preferences (respecting Simple mode overrides) for downstream settings
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)

            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Determine if this is a Pro model for enhanced processing
            val isProModel = model.contains("pro", ignoreCase = true)
            val is25Model = model.contains("2.5", ignoreCase = true)

            // Build clean transcription prompt WITHOUT vocabulary to prevent echoing
            val transcriptionPrompt = buildString {
                if (isProModel) {
                    append("You are an advanced speech-to-text transcription system. ")
                    append("Generate a highly accurate transcript of the speech in this audio file. ")
                    append("Pay attention to context, speaker nuances, and ensure proper punctuation and formatting. ")
                    append("Provide a clean, properly formatted transcript with appropriate punctuation, ")
                    append("capitalization, and paragraph breaks where natural speech pauses indicate new thoughts or topics.")
                } else {
                    append("Transcribe the speech in this audio file accurately. ")
                    append("Provide only the transcript text with proper punctuation and capitalization.")
                }
            }

            // Read audio file
            val audioBytes = audioFile.readBytes()
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)

            // Determine MIME type based on file extension
            val mimeType = when (audioFile.extension.lowercase()) {
                "mp3" -> "audio/mp3"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                "flac" -> "audio/flac"
                "ogg" -> "audio/ogg"
                else -> "audio/mp4" // Default to mp4 for m4a files
            }

            // Configure generation settings based on model capabilities
            val maxTokens = when {
                is25Model -> 4096 // Gemini 2.5 Pro has enhanced output capabilities
                isProModel -> 3072 // Pro models can handle more complex outputs
                else -> 2048 // Flash models optimized for speed
            }

            val temperature = when {
                isProModel -> 0.05 // Pro models benefit from very low temperature for accuracy
                else -> 0.1 // Flash models with slightly higher temperature
            }

            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to transcriptionPrompt),
                            mapOf(
                                "inline_data" to mapOf(
                                    "mime_type" to mimeType,
                                    "data" to base64Audio
                                )
                            )
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to temperature,
                    "maxOutputTokens" to maxTokens,
                    "topP" to 0.95
                )
            )

            val jsonBody = gson.toJson(requestBody)

            // Use shared HTTP client with connection pooling for better performance
            val client = transcriptionHttpClient

            val request = okhttp3.Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d("BubbleOverlayService", "Making API call to Gemini $model...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d("BubbleOverlayService", "Gemini API response received - status: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("BubbleOverlayService", "Gemini API error: ${response.code} $responseBody")
                return "Gemini API error: ${response.code}"
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                Log.d("BubbleOverlayService", "Parsing Gemini response...")
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val candidates = jsonResponse["candidates"] as? List<Map<String, Any>>
                val content = candidates?.get(0)?.get("content") as? Map<String, Any>
                val parts = content?.get("parts") as? List<Map<String, Any>>
                var transcriptionResult = parts?.get(0)?.get("text") as? String ?: "Empty transcription"

                // Apply custom vocabulary replacements post-transcription
                transcriptionResult = applyCustomVocabularyReplacements(transcriptionResult, languageConfig)

                Log.d("BubbleOverlayService", "Gemini $model transcription completed: '${transcriptionResult.take(100)}...'")
                return transcriptionResult
            } else {
                Log.e("BubbleOverlayService", "Gemini API response was unsuccessful or empty")
                return "Gemini API error: ${response.code}"
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Gemini transcription error", e)
            return "Gemini transcription failed: ${e.message}"
        }
    }

    // Helper method to apply vocabulary replacements post-transcription
    private fun applyCustomVocabularyReplacements(
        text: String,
        languageConfig: SettingsManager.CustomLanguageConfig
    ): String {
        return TextProcessingUtils.applyCustomVocabularyReplacements(
            text,
            languageConfig.vocabularyItems,
            languageConfig.replacementRules
        )
    }

    private suspend fun performElevenLabsTranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("elevenlabs_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "ElevenLabs API key is missing")
                return "No ElevenLabs API key configured"
            }

            Log.d("BubbleOverlayService", "Starting ElevenLabs Scribe API transcription")

            // Use shared HTTP client with connection pooling for better performance
            val client = transcriptionHttpClient

            // ElevenLabs supports multiple audio formats including m4a
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model_id", "scribe_v1")
                .addFormDataPart("language_code", "en")
                .addFormDataPart("tag_audio_events", "false")
                .addFormDataPart("timestamps_granularity", "word")
                .build()

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/speech-to-text")
                .addHeader("xi-api-key", apiKey)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("BubbleOverlayService", "ElevenLabs API error: ${response.code} $responseBody")
                return "ElevenLabs API error: ${response.code}"
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonResponse = com.google.gson.Gson().fromJson(responseBody, Map::class.java)
                val transcribedText = jsonResponse["text"]?.toString() ?: "Empty transcription"

                Log.d("BubbleOverlayService", "ElevenLabs transcription successful: '${transcribedText.take(100)}...'")
                return transcribedText
            } else {
                Log.e("BubbleOverlayService", "ElevenLabs API response was unsuccessful or empty")
                return "ElevenLabs API error: ${response.code}"
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "ElevenLabs transcription error", e)
            return "ElevenLabs transcription failed: ${e.message}"
        }
    }

    private suspend fun performGroqTranscription(audioFile: File, model: String = "whisper-large-v3"): String {
        try {
            val userApiKey = secureApiKeyManager.getApiKey("groq_api_key") ?: ""
            val useProxy = GroqProxyConfig.shouldUseProxy(userApiKey)

            val apiKey = if (useProxy) {
                Log.d("BubbleOverlayService", "Using hosted Groq proxy for transcription")
                ""
            } else if (userApiKey.isNotBlank()) {
                Log.d("BubbleOverlayService", "Using user's Groq API key for transcription")
                userApiKey
            } else {
                Log.e("BubbleOverlayService", "No Groq API key available")
                return "No Groq API key configured"
            }

            if (!useProxy && apiKey.isBlank()) {
                Log.e("BubbleOverlayService", "No Groq API key available")
                return "No Groq API key configured"
            }

            Log.d("BubbleOverlayService", "Starting Groq transcription with model: $model")
            Log.d("BubbleOverlayService", "Audio file: ${audioFile.name}, size: ${audioFile.length()} bytes")

            // Build custom vocabulary prompt for Groq (OpenAI-compatible)
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val customVocabulary = prefs.getString("custom_vocabulary", "") ?: ""
            val customSpelling = prefs.getString("custom_spelling", "") ?: ""
            val vocabularyPrompt = buildString {
                val vocabularyItems = mutableListOf<String>()

                // Add regular vocabulary terms
                if (customVocabulary.isNotBlank()) {
                    vocabularyItems.addAll(customVocabulary.split(",").map { it.trim() }.filter { it.isNotBlank() })
                }

                // Add custom spelling terms (both original and replacement forms)
                if (customSpelling.isNotBlank()) {
                    val spellingPairs = customSpelling.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it.contains("=") }
                        .mapNotNull { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                val from = parts[0].trim().removePrefix("\"").removeSuffix("\"").trim()
                                val to = parts[1].trim().removePrefix("\"").removeSuffix("\"").trim()
                                if (from.isNotBlank() && to.isNotBlank()) {
                                    Pair(from, to)
                                } else null
                            } else null
                        }

                    // Add both forms to vocabulary
                    spellingPairs.forEach { (from, to) ->
                        vocabularyItems.add(from)
                        vocabularyItems.add(to)
                    }
                }

                if (vocabularyItems.isNotEmpty()) {
                    // Limit to 20 words maximum for Whisper API accuracy
                    append(vocabularyItems.take(20).joinToString(", "))
                }
            }

            // Use shared HTTP client with connection pooling for better performance
            val client = transcriptionHttpClient

            // Groq supports multiple audio formats including m4a
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("model", model)
                .addFormDataPart("language", "en")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0.0")

            // Add custom vocabulary prompt if available
            if (vocabularyPrompt.isNotBlank()) {
                requestBuilder.addFormDataPart("prompt", vocabularyPrompt)
                Log.d("BubbleOverlayService", "Using custom vocabulary prompt for Groq: '$vocabularyPrompt'")
            }

            val requestBody = requestBuilder.build()

            val requestBuilderHttp = Request.Builder()
                .url(GroqProxyConfig.endpoint("/openai/v1/audio/transcriptions", useProxy))
                .post(requestBody)

            GroqProxyConfig.applyHeaders(requestBuilderHttp, apiKey, useProxy)

            val request = requestBuilderHttp.build()

            Log.d("BubbleOverlayService", "Sending Groq transcription request...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d("BubbleOverlayService", "Groq response code: ${response.code}")
            Log.d("BubbleOverlayService", "Groq response headers: ${response.headers}")

            if (!response.isSuccessful) {
                Log.e("BubbleOverlayService", "Groq API error: ${response.code} $responseBody")
                return "Groq API error: ${response.code} - $responseBody"
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                Log.d("BubbleOverlayService", "Groq response body: $responseBody")

                try {
                    // Try to parse as JSON first (standard OpenAI format)
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                    val transcribedText = jsonResponse["text"]?.toString() ?: "Empty transcription"

                    Log.d("BubbleOverlayService", "Groq transcription successful (JSON): '${transcribedText.take(100)}...'")
                    return transcribedText
                } catch (jsonException: Exception) {
                    Log.d("BubbleOverlayService", "JSON parsing failed, trying as plain text: ${jsonException.message}")

                    // If JSON parsing fails, treat the response as plain text
                    // Some Groq endpoints might return plain text instead of JSON
                    val cleanedText = responseBody.trim()
                    if (cleanedText.isNotEmpty()) {
                        Log.d("BubbleOverlayService", "Groq transcription successful (plain text): '${cleanedText.take(100)}...'")
                        return cleanedText
                    } else {
                        Log.e("BubbleOverlayService", "Groq returned empty response")
                        return "Empty transcription"
                    }
                }
            } else {
                Log.e("BubbleOverlayService", "Groq API response was unsuccessful or empty")
                return "Groq API error: ${response.code}"
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Groq transcription error", e)
            return "Groq transcription failed: ${e.message}"
        }
    }

    private suspend fun performAssemblyAITranscription(audioFile: File): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("assemblyai_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "AssemblyAI API key is missing")
                return "No AssemblyAI API key configured"
            }

            Log.d("BubbleOverlayService", "Starting AssemblyAI transcription")

            // Use shared HTTP client with connection pooling for better performance
            val client = transcriptionHttpClient

            // Step 1: Upload the audio file to AssemblyAI
            Log.d("BubbleOverlayService", "Uploading audio file to AssemblyAI")
            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val uploadRequestBody = audioFile.asRequestBody(mediaType)

            val uploadRequest = Request.Builder()
                .url("https://api.assemblyai.com/v5/upload")
                .addHeader("authorization", apiKey)
                .addHeader("content-type", "audio/m4a")
                .post(uploadRequestBody)
                .build()

            val uploadResponse = client.newCall(uploadRequest).execute()
            val uploadResponseBody = uploadResponse.body?.string()

            if (!uploadResponse.isSuccessful) {
                Log.e("BubbleOverlayService", "AssemblyAI upload error: ${uploadResponse.code} $uploadResponseBody")
                return "AssemblyAI upload error: ${uploadResponse.code}"
            }

            val uploadJson = com.google.gson.Gson().fromJson(uploadResponseBody, Map::class.java)
            val audioUrl = uploadJson["upload_url"]?.toString()

            if (audioUrl.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "Failed to get upload URL from AssemblyAI")
                return "AssemblyAI upload failed: No upload URL returned"
            }

            Log.d("BubbleOverlayService", "Audio uploaded successfully. Starting transcription...")

            // Step 2: Request transcription
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val customVocabulary = prefs.getString("custom_vocabulary", "") ?: ""
            val customSpelling = prefs.getString("custom_spelling", "") ?: ""

            // Parse custom vocabulary (comma-separated) into array for keyterms_prompt
            val keytermsPrompt = if (customVocabulary.isNotBlank()) {
                customVocabulary.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(1000) // AssemblyAI limit
            } else {
                emptyList()
            }

            // Parse custom spelling into AssemblyAI format
            // Note: AssemblyAI requires "to" field to contain only single words
            val customSpellingList = if (customSpelling.isNotBlank()) {
                customSpelling.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.contains("=") }
                    .mapNotNull { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) {
                            val from = parts[0].trim().removePrefix("\"").removeSuffix("\"").trim()
                            val to = parts[1].trim().removePrefix("\"").removeSuffix("\"").trim()

                            // AssemblyAI only accepts single words for "to" field
                            if (from.isNotBlank() && to.isNotBlank() && !to.contains(" ")) {
                                mapOf("to" to to, "from" to listOf(from))
                            } else {
                                if (to.contains(" ")) {
                                    Log.d("BubbleOverlayService", "Skipping multi-word replacement for AssemblyAI: '$from' -> '$to'")
                                }
                                null
                            }
                        } else null
                    }
            } else {
                emptyList()
            }

            val transcriptionRequestBody = mutableMapOf<String, Any>(
                "audio_url" to audioUrl,
                "speech_model" to "slam-1",
                "language_code" to "en_us",
                "punctuate" to true,
                "format_text" to true,
                "auto_highlights" to false,
                "speaker_labels" to false
            )

            // Add keyterms_prompt if we have custom vocabulary
            if (keytermsPrompt.isNotEmpty()) {
                transcriptionRequestBody["keyterms_prompt"] = keytermsPrompt
                Log.d("BubbleOverlayService", "Using keyterms_prompt: $keytermsPrompt")
            }

            // Add custom_spelling if we have custom spelling mappings
            if (customSpellingList.isNotEmpty()) {
                transcriptionRequestBody["custom_spelling"] = customSpellingList
                Log.d("BubbleOverlayService", "Using custom_spelling for AssemblyAI (single-word only): $customSpellingList")
            } else if (customSpelling.isNotBlank()) {
                Log.d("BubbleOverlayService", "No single-word custom spelling entries found for AssemblyAI. Multi-word replacements will be applied post-transcription.")
            }

            val gson = com.google.gson.Gson()
            val jsonBody = gson.toJson(transcriptionRequestBody)

            Log.d("BubbleOverlayService", "AssemblyAI request parameters:")
            Log.d("BubbleOverlayService", "- speech_model: slam-1 (best English accuracy)")
            Log.d("BubbleOverlayService", "- language_code: en_us")
            Log.d("BubbleOverlayService", "- language_detection: false (not compatible with slam-1)")
            Log.d("BubbleOverlayService", "- keyterms_prompt count: ${keytermsPrompt.size}")
            Log.d("BubbleOverlayService", "- custom_spelling count: ${customSpellingList.size}")
            Log.d("BubbleOverlayService", "Complete JSON request body: $jsonBody")

            val transcriptionRequest = Request.Builder()
                .url("https://api.assemblyai.com/v2/transcript")
                .addHeader("authorization", apiKey)
                .addHeader("content-type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val transcriptionResponse = client.newCall(transcriptionRequest).execute()
            val transcriptionResponseBody = transcriptionResponse.body?.string()

            if (!transcriptionResponse.isSuccessful) {
                Log.e("BubbleOverlayService", "AssemblyAI transcription request error:")
                Log.e("BubbleOverlayService", "- Status Code: ${transcriptionResponse.code}")
                Log.e("BubbleOverlayService", "- Response Body: $transcriptionResponseBody")
                Log.e("BubbleOverlayService", "- Request Body: $jsonBody")

                // Try to parse error details from response
                try {
                    val errorJson = gson.fromJson(transcriptionResponseBody, Map::class.java)
                    val errorMessage = errorJson["error"]?.toString() ?: "Unknown error"
                    Log.e("BubbleOverlayService", "- Error Message: $errorMessage")
                    return "AssemblyAI error: $errorMessage"
                } catch (e: Exception) {
                    Log.e("BubbleOverlayService", "Could not parse error response", e)
                    return "AssemblyAI transcription error: ${transcriptionResponse.code}"
                }
            }

            val transcriptionJson = gson.fromJson(transcriptionResponseBody, Map::class.java)
            val transcriptId = transcriptionJson["id"]?.toString()

            if (transcriptId.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "Failed to get transcript ID from AssemblyAI")
                return "AssemblyAI transcription failed: No transcript ID returned"
            }

            Log.d("BubbleOverlayService", "Transcription started with ID: $transcriptId. Polling for results...")

            // Step 3: Poll for transcription results
            var attempts = 0
            val maxAttempts = 60 // 2 minutes max wait time

            while (attempts < maxAttempts) {
                delay(2000) // Wait 2 seconds between polls (non-blocking)
                attempts++

                val pollRequest = Request.Builder()
                    .url("https://api.assemblyai.com/v2/transcript/$transcriptId")
                    .addHeader("authorization", apiKey)
                    .get()
                    .build()

                val pollResponse = client.newCall(pollRequest).execute()
                val pollResponseBody = pollResponse.body?.string()

                if (!pollResponse.isSuccessful) {
                    Log.e("BubbleOverlayService", "AssemblyAI polling error: ${pollResponse.code} $pollResponseBody")
                    continue
                }

                val pollJson = gson.fromJson(pollResponseBody, Map::class.java)
                val status = pollJson["status"]?.toString()

                when (status) {
                    "completed" -> {
                        val transcribedText = pollJson["text"]?.toString() ?: "Empty transcription"
                        Log.d("BubbleOverlayService", "AssemblyAI transcription successful: '${transcribedText.take(100)}...'")

                        // Check if paragraph formatting is enabled
                        val enableParagraphs = prefs.getBoolean("enable_paragraphs", false)
                        if (enableParagraphs && transcribedText.length > 100) { // Only for longer texts
                            Log.d("BubbleOverlayService", "Fetching paragraph formatting for transcript: $transcriptId")
                            val paragraphText = fetchParagraphsFromAssemblyAI(transcriptId, apiKey, client, gson)
                            if (paragraphText != null) {
                                Log.d("BubbleOverlayService", "Paragraph formatting successful")
                                return paragraphText
                            } else {
                                Log.w("BubbleOverlayService", "Paragraph formatting failed, returning original text")
                            }
                        }

                        return transcribedText
                    }
                    "error" -> {
                        val error = pollJson["error"]?.toString() ?: "Unknown error"
                        Log.e("BubbleOverlayService", "AssemblyAI transcription failed: $error")
                        return "AssemblyAI transcription failed: $error"
                    }
                    "processing", "queued" -> {
                        Log.d("BubbleOverlayService", "AssemblyAI transcription status: $status (attempt $attempts/$maxAttempts)")
                        continue
                    }
                    else -> {
                        Log.w("BubbleOverlayService", "Unknown AssemblyAI status: $status")
                        continue
                    }
                }
            }

            Log.e("BubbleOverlayService", "AssemblyAI transcription timed out after $maxAttempts attempts")
            return "AssemblyAI transcription timed out. Please try again."

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "AssemblyAI transcription error", e)
            return "AssemblyAI transcription failed: ${e.message}"
        }
    }

    private suspend fun fetchParagraphsFromAssemblyAI(
        transcriptId: String,
        apiKey: String,
        client: OkHttpClient,
        gson: com.google.gson.Gson
    ): String? {
        return try {
            Log.d("BubbleOverlayService", "Fetching paragraphs for transcript: $transcriptId")

            val paragraphsRequest = Request.Builder()
                .url("https://api.assemblyai.com/v2/transcript/$transcriptId/paragraphs")
                .addHeader("authorization", apiKey)
                .get()
                .build()

            val paragraphsResponse = client.newCall(paragraphsRequest).execute()
            val paragraphsResponseBody = paragraphsResponse.body?.string()

            if (!paragraphsResponse.isSuccessful) {
                Log.e("BubbleOverlayService", "AssemblyAI paragraphs error: ${paragraphsResponse.code} $paragraphsResponseBody")
                return null
            }

            val paragraphsJson = gson.fromJson(paragraphsResponseBody, Map::class.java)
            val paragraphsList = paragraphsJson["paragraphs"] as? List<Map<String, Any>>

            if (paragraphsList.isNullOrEmpty()) {
                Log.w("BubbleOverlayService", "No paragraphs found in response")
                return null
            }

            // Join paragraphs with double line breaks for better formatting
            val formattedText = paragraphsList
                .mapNotNull { paragraph -> paragraph["text"]?.toString() }
                .joinToString("\n\n")

            if (formattedText.isBlank()) {
                Log.w("BubbleOverlayService", "Formatted text is empty")
                return null
            }

            Log.d("BubbleOverlayService", "Successfully formatted ${paragraphsList.size} paragraphs")
            return formattedText

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error fetching paragraphs from AssemblyAI", e)
            null
        }
    }

    private suspend fun performDeepgramTranscription(audioFile: File, model: String = "nova-3"): String {
        try {
            val apiKey = secureApiKeyManager.getApiKey("deepgram_api_key")

            if (apiKey.isNullOrBlank()) {
                Log.e("BubbleOverlayService", "Deepgram API key is missing")
                return "No Deepgram API key configured"
            }

            Log.d("BubbleOverlayService", "Starting Deepgram Nova-3 transcription")

            // Build vocabulary keyterms from existing custom vocabulary system
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val languageConfig = settingsManager.getCustomLanguageConfig()

            // Parse vocabulary into keyterms (up to 100 limit for Nova-3)
            val keyterms = languageConfig.vocabularyItems.take(100)

            Log.d("BubbleOverlayService", "Deepgram Nova-3 configuration:")
            Log.d("BubbleOverlayService", "- Model: $model")
            Log.d("BubbleOverlayService", "- Smart formatting: enabled (automatic punctuation & paragraphs)")
            Log.d("BubbleOverlayService", "- Keyterms count: ${keyterms.size}")
            Log.d("BubbleOverlayService", "- Keyterms: $keyterms")

            // Build URL with parameters
            val urlBuilder = StringBuilder("https://api.deepgram.com/v1/listen")
            urlBuilder.append("?smart_format=true") // Enable Nova-3 smart formatting
            urlBuilder.append("&language=en")
            urlBuilder.append("&model=$model")

            // Add keyterms for vocabulary recognition
            keyterms.forEach { term ->
                try {
                    val encodedTerm = java.net.URLEncoder.encode(term, "UTF-8")
                    urlBuilder.append("&keyterm=$encodedTerm")
                } catch (e: Exception) {
                    Log.w("BubbleOverlayService", "Failed to encode keyterm: $term", e)
                }
            }

            val finalUrl = urlBuilder.toString()
            Log.d("BubbleOverlayService", "Deepgram API URL: $finalUrl")

            // Use shared HTTP client with connection pooling for better performance
            val client = transcriptionHttpClient

            val mediaType = "audio/m4a".toMediaTypeOrNull()
            val requestBody = audioFile.asRequestBody(mediaType)

            val request = Request.Builder()
                .url(finalUrl)
                .addHeader("Authorization", "Token $apiKey")
                .addHeader("Content-Type", "audio/m4a")
                .post(requestBody)
                .build()

            Log.d("BubbleOverlayService", "Making API call to Deepgram Nova-3...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d("BubbleOverlayService", "Deepgram API response received - status: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("BubbleOverlayService", "Deepgram API error: ${response.code} $responseBody")
                return "Deepgram API error: ${response.code}"
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                Log.d("BubbleOverlayService", "Parsing Deepgram response...")
                val gson = com.google.gson.Gson()
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val results = jsonResponse["results"] as? Map<String, Any>
                val channels = results?.get("channels") as? List<Map<String, Any>>
                val alternatives = channels?.get(0)?.get("alternatives") as? List<Map<String, Any>>
                val alternative = alternatives?.get(0)

                // Use the formatted paragraphs transcript if available (Nova-3 smart_format feature)
                val paragraphs = alternative?.get("paragraphs") as? Map<String, Any>
                val transcriptionResult = if (paragraphs != null) {
                    // Use the smart-formatted transcript with automatic paragraphs
                    val formattedTranscript = paragraphs["transcript"] as? String
                    if (!formattedTranscript.isNullOrBlank()) {
                        Log.d("BubbleOverlayService", "Using Nova-3 smart-formatted transcript with paragraphs")
                        formattedTranscript
                    } else {
                        alternative?.get("transcript") as? String ?: "Empty transcription"
                    }
                } else {
                    alternative?.get("transcript") as? String ?: "Empty transcription"
                }

                // Apply any additional custom spelling replacements post-transcription
                // (keyterms are already handled by Nova-3, this is for complex replacements)
                val finalResult = applyCustomVocabularyReplacements(transcriptionResult, languageConfig)

                Log.d("BubbleOverlayService", "Deepgram Nova-3 transcription completed: '${finalResult.take(100)}...'")
                Log.d("BubbleOverlayService", "Smart formatting applied: ${paragraphs != null}")
                Log.d("BubbleOverlayService", "Keyterms processed: ${keyterms.size}")

                return finalResult
            } else {
                Log.e("BubbleOverlayService", "Deepgram API response was unsuccessful or empty")
                return "Deepgram API error: ${response.code}"
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Deepgram transcription error", e)
            return "Deepgram transcription failed: ${e.message}"
        }
    }

    private suspend fun processWithChatGPT(transcription: String, context: String, screenContext: String = ""): String? = withContext(Dispatchers.IO) {
        try {
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val aiModel = prefs.getString("ai_model", "groq/openai/gpt-oss-20b") ?: "groq/openai/gpt-oss-20b"

            // Check cache first
            val cachedResult = getCachedAIResponse(transcription, "$context|$screenContext", aiModel)
            if (cachedResult != null) {
                return@withContext cachedResult
            }

            val apiKey = secureApiKeyManager.getApiKey("openai_api_key") ?: ""
            val customVocabulary = prefs.getString("custom_vocabulary", "") ?: ""

            if (apiKey.isBlank()) {
                Log.e("BubbleOverlayService", "API Key is missing for ChatGPT processing")
                return@withContext "Processing failed: No API key"
            }

            // Detect command mode and get appropriate prompt
            val commandWords = prefs.getString("command_word", "command") ?: "command"
            val commandWordsList = commandWords.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val words = transcription.trim().split(whitespaceRegex)
            val firstWordRaw = words.firstOrNull() ?: ""
            val firstWordClean = firstWordRaw.lowercase().replace(punctuationRegex, "")
            val isCommandMode = words.isNotEmpty() && commandWordsList.contains(firstWordClean)

            // Get the appropriate prompt and processed transcription
            val (processedTranscription, baseSystemMessage) = if (isCommandMode) {
                // Remove command word and get command prompt
                val commandTranscription = words.drop(1).joinToString(" ").trim()
                val commandPrompt = prefs.getString("command_prompt", getDefaultCommandPrompt()) ?: getDefaultCommandPrompt()
                Pair(commandTranscription, commandPrompt)
            } else {
                // Use full transcription and dictation prompt
                val dictationPrompt = prefs.getString("dictation_prompt", getDefaultDictationPrompt()) ?: getDefaultDictationPrompt()
                Pair(transcription, dictationPrompt)
            }

            Log.d("BubbleOverlayService", "=== COMMAND DETECTION DEBUG ===")
            Log.d("BubbleOverlayService", "Command words configured: '$commandWords'")
            Log.d("BubbleOverlayService", "Command words list: $commandWordsList")
            Log.d("BubbleOverlayService", "First word raw: '$firstWordRaw'")
            Log.d("BubbleOverlayService", "First word clean: '$firstWordClean'")
            Log.d("BubbleOverlayService", "Is command mode: $isCommandMode")
            Log.d("BubbleOverlayService", "Mode detected: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("BubbleOverlayService", "Original transcription: '$transcription'")
            Log.d("BubbleOverlayService", "Processed transcription: '$processedTranscription'")
            Log.d("BubbleOverlayService", "=== END COMMAND DETECTION DEBUG ===")

            // Build the new structured system and user messages using helper functions
            val languageConfig = settingsManager.getCustomLanguageConfig()

            val systemMessage = TextProcessingUtils.buildStructuredSystemMessage(
                baseSystemMessage,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs,
                !settingsManager.isSimpleMode()
            )
            val userMessage = settingsManager.buildUserMessage(
                processedTranscription,
                context,
                currentAppContext ?: "",
                screenContext,
                languageConfig.vocabularyItems,
                languageConfig.spellingPairs
            )

            Log.d("BubbleOverlayService", "ChatGPT user message: '${userMessage.take(200)}...'")
            Log.d("BubbleOverlayService", "=== FULL USER MESSAGE BEING SENT TO CHATGPT ===")
            Log.d("BubbleOverlayService", userMessage)
            Log.d("BubbleOverlayService", "=== END FULL USER MESSAGE ===")
            Log.d("BubbleOverlayService", "=== SYSTEM MESSAGE BEING SENT TO CHATGPT ===")
            Log.d("BubbleOverlayService", systemMessage)
            Log.d("BubbleOverlayService", "=== END SYSTEM MESSAGE ===")
            if (customVocabulary.isNotBlank()) {
                Log.d("BubbleOverlayService", "Using custom vocabulary: '$customVocabulary'")
            }

            val requestBody = mapOf(
                "model" to aiModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemMessage),
                    mapOf("role" to "user", "content" to userMessage)
                ),
                "max_tokens" to 1000,
                "temperature" to 0.3
            )

            val jsonBody = gson.toJson(requestBody)

            // Add comprehensive debug logging for investigation
            Log.d("BubbleOverlayService", "=== CHATGPT API CALL DEBUG ===")
            Log.d("BubbleOverlayService", "Model: $aiModel")
            Log.d("BubbleOverlayService", "Temperature: 0.3")
            Log.d("BubbleOverlayService", "Max Tokens: 1000")
            Log.d("BubbleOverlayService", "System Message: \"$systemMessage\"")
            Log.d("BubbleOverlayService", "User Message: \"$userMessage\"")
            Log.d("BubbleOverlayService", "Selected Context: \"$context\"")
            Log.d("BubbleOverlayService", "Screen Context: \"$screenContext\"")
            Log.d("BubbleOverlayService", "App Context: \"$currentAppContext\"")
            Log.d("BubbleOverlayService", "Raw Transcription: \"$transcription\"")
            Log.d("BubbleOverlayService", "Custom Vocabulary: \"$customVocabulary\"")
            Log.d("BubbleOverlayService", "Mode: ${if (isCommandMode) "COMMAND" else "DICTATION"}")
            Log.d("BubbleOverlayService", "Complete JSON Payload: $jsonBody")
            Log.d("BubbleOverlayService", "=== END DEBUG ===")

            // Use shared HTTP client with connection pooling for better performance
            val client = aiProcessingHttpClient
            val request = okhttp3.Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d("BubbleOverlayService", "Sending to ChatGPT - Model: $aiModel, Prompt: '${systemMessage.take(100)}...'")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("BubbleOverlayService", "ChatGPT API error: ${response.code} $responseBody")
                return@withContext "Processing failed: API error ${response.code}"
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                val choices = jsonResponse["choices"] as? List<Map<String, Any>>
                val message = choices?.get(0)?.get("message") as? Map<String, Any>
                val content = message?.get("content") as? String

                if (!content.isNullOrBlank()) {
                    Log.d("BubbleOverlayService", "ChatGPT processing successful: '${content.take(100)}...'")
                    // Extract content from XML tags (always use FORMATTED_TEXT for both modes)
                    val extractedContent = extractXmlTagContent(content, "FORMATTED_TEXT")

                    // Cache the successful result
                    cacheAIResponse(transcription, "$context|$screenContext", aiModel, extractedContent)

                    return@withContext extractedContent
                } else {
                    Log.e("BubbleOverlayService", "ChatGPT returned empty content")
                    return@withContext "Processing failed: Empty response"
                }
            } else {
                Log.e("BubbleOverlayService", "ChatGPT API response was unsuccessful or empty")
                return@withContext "Processing failed: Invalid response"
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "ChatGPT processing error", e)
            return@withContext "Processing failed: ${e.message}"
        }
    }

    // processWithGemini method moved to AIProcessingManager

    // processWithClaude method moved to AIProcessingManager

    // processWithGroq method moved to AIProcessingManager

    // processWithOpenRouter method moved to AIProcessingManager

    private fun getCurrentAppContext(): String? {
        return try {
            val accessibilityService = DictationAccessibilityService.getInstance()
            if (accessibilityService != null) {
                val rootNode = accessibilityService.rootInActiveWindow
                if (rootNode != null) {
                    val packageName = rootNode.packageName?.toString()
                    @Suppress("DEPRECATION")
                    rootNode.recycle()

                    if (!packageName.isNullOrBlank()) {
                        // Convert package name to readable app name
                        val appName = getAppName(packageName)
                        Log.d("BubbleOverlayService", "Detected app: $appName (package: $packageName)")
                        return appName
                    }
                }
            }
            Log.d("BubbleOverlayService", "No app context available")
            return null
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error getting app context", e)
            return null
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(applicationInfo).toString()
            Log.d("BubbleOverlayService", "App name lookup: $packageName -> $appName")
            appName
        } catch (e: Exception) {
            // Fallback to package name if we can't get the app name
            Log.w("BubbleOverlayService", "Could not resolve app name for $packageName, using package name")
            packageName
        }
    }





    private fun vibrateShort() {
        HapticUtils.performHapticFeedback(this)
    }

    // Notification methods moved to ServiceNotificationManager

    private fun startNoteRecording() {
        // Prevent concurrent recording
        if (isRecording || isRecordingNote) {
            Log.w("BubbleOverlayService", "Cannot start note recording - already recording")
            return
        }

        try {
            // Request audio focus before recording
            Log.d("BubbleOverlayService", "Requesting audio focus for note recording")
            val focusGranted = audioFocusManager.requestFocus()
            if (!focusGranted) {
                Log.w("BubbleOverlayService", "Audio focus denied for note recording - continuing anyway")
            }

            // Generate audio file name
            val audioFileName = notePadManager.generateNoteAudioFileName()
            val audioDirectory = notePadManager.getNotesAudioDirectory()
            noteAudioFile = File(audioDirectory, audioFileName)

            // Setup MediaRecorder for note recording
            noteMediaRecorder = MediaRecorder().apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                } catch (e: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    Log.d("BubbleOverlayService", "VOICE_RECOGNITION not supported for note, using MIC source")
                }

                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(noteAudioFile!!.absolutePath)

                // High-quality settings optimized for speech recognition
                try {
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                } catch (e: Exception) {
                    Log.w("BubbleOverlayService", "Could not set high quality audio settings for note", e)
                }

                prepare()
                start()
            }

            isRecordingNote = true
            updateNotification()

            // Provide haptic feedback
            HapticUtils.performHapticFeedback(this@BubbleOverlayService)

            Log.d("BubbleOverlayService", "Note recording started successfully")

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Failed to start note recording - ${e.javaClass.simpleName}: ${e.message}", e)
            audioFocusManager.abandonFocus()
            isRecordingNote = false
            try { noteMediaRecorder?.setOnErrorListener(null) } catch (_: Exception) {}
            try { noteMediaRecorder?.setOnInfoListener(null) } catch (_: Exception) {}
            noteMediaRecorder?.release()
            noteMediaRecorder = null
            noteAudioFile?.delete()
            noteAudioFile = null
            updateNotification()

            // Show user feedback
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Unable to start note recording. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopNoteRecording() {
        if (!isRecordingNote) {
            Log.d("BubbleOverlayService", "Note recording not in progress")
            return
        }

        Log.d("BubbleOverlayService", "Stopping note recording...")

        try {
            noteMediaRecorder?.apply {
                try {
                    setOnErrorListener(null)
                    setOnInfoListener(null)
                } catch (_: Exception) { }
                stop()
                release()
            }
            noteMediaRecorder = null
            isRecordingNote = false

            val audioFile = noteAudioFile
            if (audioFile != null && audioFile.exists()) {
                Log.d("BubbleOverlayService", "Note audio file saved: ${audioFile.absolutePath}")

                // Create a temporary note with basic info that will be updated after processing
                tempNoteId = System.currentTimeMillis().toString()
                val tempNote = Note(
                    id = tempNoteId!!,
                    title = "Processing Note...",
                    content = "Processing audio...",
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    audioFileName = audioFile.name
                )

                // Save the temporary note first so processing can update it reliably
                notePadManager.saveNote(tempNote)
                Log.d("BubbleOverlayService", "Temporary note saved successfully: ${tempNote.id}")

                // Notify UI that a processing note exists
                val noteUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_NOTE_UPDATED")
                noteUpdateIntent.setPackage(packageName) // Ensure broadcast stays within app
                sendBroadcast(noteUpdateIntent)
                Log.d("BubbleOverlayService", "Note update broadcast sent")

                // Now process the note audio in background to add content
                processNoteAudio(audioFile)
            } else {
                Log.e("BubbleOverlayService", "Note audio file not found or null")
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error stopping note recording", e)
        } finally {
            // Always release audio focus when note recording stops (success or failure)
            audioFocusManager.abandonFocus()
            noteAudioFile = null
            updateNotification()
        }
    }

    private fun processNoteAudio(audioFile: File) {
        serviceScope.launch {
            try {
                Log.d("BubbleOverlayService", "Processing note audio: ${audioFile.name}")

                // Transcribe the audio (reuse existing transcription logic)
                val originalTranscript = performTranscription(audioFile)

                if (originalTranscript.isNotEmpty()) {
                    // Enhance with AI using auto-detect prompt if post-processing is enabled
                    val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val prefs = overrideForSimpleMode(originalPrefs)
                    val postProcessEnabled = prefs.getBoolean("enable_postprocess", false)

                    val aiProcessedContent = if (postProcessEnabled) {
                        try {
                            // Get the auto-detect prompt for automatic note processing
                            val reformatPromptManager = ReformatPromptManager(this@BubbleOverlayService)
                            val autoDetectPrompt = reformatPromptManager.getPromptById("auto_detect")

                            val processedResult = if (autoDetectPrompt != null) {
                                Log.d("BubbleOverlayService", "Using auto-detect prompt for automatic note processing")
                                // Use the new specific prompt processing method
                                aiProcessingManager.processWithReformatPrompt(originalTranscript, autoDetectPrompt)
                            } else {
                                Log.w("BubbleOverlayService", "Auto-detect prompt not found, falling back to generic AI processing")
                                // Fallback to generic AI processing if auto-detect prompt is not available
                                aiProcessingManager.processWithAI(originalTranscript, "", "", currentAppContext ?: "", false)
                            }

                            // Check for API key related errors and fallback
                            val hasApiKeyError = processedResult == null ||
                                                   processedResult.contains("API key not configured") == true ||
                                                   processedResult.contains("No API key") == true ||
                                                   processedResult.startsWith("Error:") == true

                            if (hasApiKeyError) {
                                Log.w("BubbleOverlayService", "API key error detected for note processing, falling back to default Groq model")
                                if (autoDetectPrompt != null) {
                                    aiProcessingManager.processWithReformatPrompt(originalTranscript, autoDetectPrompt, "openai/gpt-oss-120b")
                                } else {
                                    aiProcessingManager.processWithAIAndMetrics(originalTranscript, "", "", currentAppContext ?: "", false, "openai/gpt-oss-120b").first
                                }
                            } else {
                                processedResult
                            }
                        } catch (e: Exception) {
                            Log.e("BubbleOverlayService", "AI enhancement failed for note", e)
                            null
                        }
                    } else {
                        null // No AI processing performed
                    }

                    // Use AI-processed version for title generation if available, otherwise use original
                    val contentForTitle = aiProcessedContent ?: originalTranscript
                    val aiGeneratedTitle = generateNoteTitle(contentForTitle)

                    // Find the temporary note we created earlier
                    val existingNotes = notePadManager.getAllNotes()
                    val tempNote = existingNotes.find { it.id == tempNoteId }

                    if (tempNote != null) {
                        // Update the existing note with the transcribed content
                        val updatedNote = tempNote.copy(
                            title = aiGeneratedTitle, // Use AI-generated title as the main title
                            content = aiProcessedContent ?: originalTranscript, // For backward compatibility
                            originalTranscript = originalTranscript,
                            aiProcessed = aiProcessedContent,
                            aiGeneratedTitle = aiGeneratedTitle // Store the AI-generated title
                        )

                        notePadManager.saveNote(updatedNote)
                        Log.d("BubbleOverlayService", "Note updated successfully with AI title: $aiGeneratedTitle")
                    } else {
                        // Create a new note if the temporary one wasn't found
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val note = Note(
                            id = tempNoteId ?: UUID.randomUUID().toString(),
                            title = aiGeneratedTitle, // Use AI-generated title as the main title
                            content = aiProcessedContent ?: originalTranscript, // For backward compatibility
                            timestamp = timestamp,
                            audioFileName = audioFile.name,
                            originalTranscript = originalTranscript,
                            aiProcessed = aiProcessedContent,
                            aiGeneratedTitle = aiGeneratedTitle // Store the AI-generated title
                        )

                        notePadManager.saveNote(note)
                        Log.d("BubbleOverlayService", "Note saved successfully with AI title: $aiGeneratedTitle")
                    }

                    // Send broadcast to notify notepad fragment that a new note was added
                    val noteUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_NOTE_ADDED")
                    noteUpdateIntent.setPackage(packageName)
                    sendBroadcast(noteUpdateIntent)
                    Log.d("BubbleOverlayService", "Note added broadcast sent")

                } else {
                    Log.w("BubbleOverlayService", "Note transcription failed or was empty")
                    // Create a failure note instead of deleting the audio file
                    createFailureNote(audioFile, "Transcription failed - audio file preserved for retry")
                }

            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Error processing note audio", e)
                // Create an error note instead of deleting the audio file
                createFailureNote(audioFile, "Processing error: ${e.message} - audio file preserved for retry")
            }
        }
    }

    private fun createFailureNote(audioFile: File, failureMessage: String) {
        try {
            Log.d("BubbleOverlayService", "Creating failure note for: ${audioFile.name}")

            // Create a failure note with preserved audio file
            val failureNote = Note(
                id = tempNoteId ?: UUID.randomUUID().toString(),
                title = "Note Processing Failed",
                content = failureMessage,
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                audioFileName = audioFile.name,
                originalTranscript = null,
                aiProcessed = null,
                aiGeneratedTitle = null
            )

            // Save the failure note
            notePadManager.saveNote(failureNote)
            Log.d("BubbleOverlayService", "Failure note saved: ${failureNote.id}")

            // Send broadcast to update note list
            val noteUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_NOTE_ADDED")
            noteUpdateIntent.setPackage(packageName)
            sendBroadcast(noteUpdateIntent)
            Log.d("BubbleOverlayService", "Failure note broadcast sent")

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error creating failure note", e)
            // As a last resort, still delete the audio file if we can't even create a failure note
            audioFile.delete()
        }
    }

    private suspend fun generateNoteTitle(content: String): String {
        return try {
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)

            // Clean and prepare content for title generation
            val cleanContent = content.trim()
                .replace(Regex("\\s+"), " ") // Normalize whitespace
                .take(2000) // Limit content length for processing

            if (cleanContent.isBlank()) {
                // Fallback to timestamp-based title for empty content
                val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                return "Empty Note $timestamp"
            }

            // Check if we have AI processing available
            val aiEnabled = prefs.getBoolean("enable_postprocess", false)

            if (!aiEnabled) {
                Log.d("BubbleOverlayService", "AI processing disabled, using fallback title generation")
                return createFallbackTitle(cleanContent)
            }

            // Use AI to analyze content and generate a meaningful title
            val prompt = """Analyze this note content and create a concise, descriptive title (3-5 words maximum).

CONTENT TO ANALYZE:
"$cleanContent"

INSTRUCTIONS:
- Identify the main topic, subject, or purpose
- Create a title that captures the essence of the content
- Use 3-5 words maximum
- Focus on key nouns, verbs, or concepts
- Make it specific and meaningful for easy identification
- Avoid generic terms like "Note", "Meeting", "Discussion"

EXAMPLES:
- Content about quarterly sales planning → "Q4 Sales Strategy Review"
- Content about cookie recipe → "Chocolate Chip Cookies Recipe"
- Content about project deadlines → "Project Timeline Planning"
- Content about customer complaints → "Customer Issue Resolution"
- Content about app development → "App Product Development"
- Content about budget review → "Budget Analysis Meeting"

Return ONLY the title, no explanations or quotes:"""

            Log.d("BubbleOverlayService", "Generating AI title for content: '${cleanContent.take(100)}...'")

            val titleResult = aiProcessingManager.processWithAI(prompt, "", "", currentAppContext ?: "", false)

            // Check for API key related errors and fallback
            val hasApiKeyError = titleResult == null ||
                                   titleResult.contains("API key not configured") == true ||
                                   titleResult.contains("No API key") == true ||
                                   titleResult.startsWith("Error:") == true ||
                                   titleResult.startsWith("Processing failed") == true

            val finalTitleResult = if (hasApiKeyError) {
                Log.w("BubbleOverlayService", "API key error detected for title generation, falling back to default Groq model")
                aiProcessingManager.processWithAIAndMetrics(prompt, "", "", currentAppContext ?: "", false, "openai/gpt-oss-120b").first
            } else {
                titleResult
            }

            if (finalTitleResult.isNullOrBlank() || finalTitleResult.startsWith("Processing failed")) {
                Log.d("BubbleOverlayService", "AI title generation failed, using fallback")
                return createFallbackTitle(cleanContent)
            }

            val cleanedTitleResult = finalTitleResult

            // Clean up the title and ensure it's not too long
            val cleanTitle = finalTitleResult.trim()
                .replace("\"", "") // Remove quotes
                .replace("Title:", "")
                .replace("title:", "")
                .replace(Regex("^[-\\s]*"), "") // Remove leading dashes or spaces
                .replace(Regex("[-\\s]*$"), "") // Remove trailing dashes or spaces
                .replace(Regex("Processing failed.*"), "") // Remove any error messages
                .trim()

            // Validate the generated title
            val wordCount = cleanTitle.split("\\s+".toRegex()).filter { it.isNotBlank() }.size

            if (cleanTitle.isNotEmpty() && cleanTitle.length <= 60 && wordCount in 2..6) {
                Log.d("BubbleOverlayService", "AI generated title: '$cleanTitle' ($wordCount words)")
                cleanTitle
            } else {
                Log.d("BubbleOverlayService", "AI title validation failed (length: ${cleanTitle.length}, words: $wordCount), using fallback")
                // Create a fallback title based on content analysis
                createFallbackTitle(cleanContent)
            }

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error generating note title", e)
            // Create a fallback title based on content analysis
            createFallbackTitle(content)
        }
    }

    private fun createFallbackTitle(content: String): String {
        return try {
            val cleanContent = content.trim()

            // Try to identify meaningful keywords and create a semantic title
            val words = cleanContent.split("\\s+".toRegex())
                .filter { word ->
                    word.isNotBlank() &&
                    word.length > 2 && // Skip very short words
                    !word.matches(Regex("^(the|a|an|and|or|but|in|on|at|to|for|of|with|by)$", RegexOption.IGNORE_CASE)) // Skip common articles/prepositions
                }
                .take(10) // Take more words to analyze

            if (words.isNotEmpty()) {
                // Look for potential topic indicators (capitalize and filter)
                val meaningfulWords = words
                    .map { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
                    .filter { word ->
                        // Keep words that look like they could be part of a title
                        word.matches(Regex("^[A-Z][a-zA-Z]{2,}$")) || // Capitalized words
                        word.matches(Regex("^[A-Z]{2,}$")) // Acronyms
                    }
                    .take(4) // Limit to 4 words for title

                if (meaningfulWords.size >= 2) {
                    val title = meaningfulWords.joinToString(" ")
                    if (title.length <= 50) {
                        Log.d("BubbleOverlayService", "Fallback title generated: '$title'")
                        return title
                    }
                }
            }

            // If we can't extract meaningful words, try sentence-based approach
            val sentences = cleanContent.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
            val firstSentence = sentences.firstOrNull()?.trim()

            if (firstSentence != null && firstSentence.length > 10) {
                // Extract key nouns/verbs from the first sentence
                val sentenceWords = firstSentence.split("\\s+".toRegex())
                    .filter { word ->
                        word.isNotBlank() &&
                        word.length > 3 && // Skip very short words
                        !word.matches(Regex("^(the|a|an|and|or|but|in|on|at|to|for|of|with|by|this|that|these|those)$", RegexOption.IGNORE_CASE))
                    }
                    .take(4)
                    .map { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

                if (sentenceWords.size >= 2) {
                    val title = sentenceWords.joinToString(" ")
                    if (title.length <= 50) {
                        Log.d("BubbleOverlayService", "Sentence-based fallback title: '$title'")
                        return title
                    }
                }
            }

            // Final fallback to timestamp-based title
            val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            Log.d("BubbleOverlayService", "Using timestamp fallback title: 'Note $timestamp'")
            "Note $timestamp"

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error in fallback title generation", e)
            // Final fallback
            val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            "Note $timestamp"
        }
    }

    private fun saveLogEntry(transcription: String, finalText: String, context: String?, appContext: String? = null, screenContext: String? = null) {
        try {
            Log.d("BubbleOverlayService", "=== SAVING LOG ENTRY ===")
            Log.d("BubbleOverlayService", "Transcription: '$transcription'")
            Log.d("BubbleOverlayService", "Final Text: '$finalText'")
            Log.d("BubbleOverlayService", "Context: '$context'")

            Log.d("BubbleOverlayService", "App Context: '$appContext'")

            // Move audio file to permanent location (on background thread if needed)
            val finalAudioFileName = if (Looper.myLooper() == Looper.getMainLooper()) {
                // We're on main thread, move to background
                runBlocking(Dispatchers.IO) {
                    moveAudioToFinalLocation()
                }
            } else {
                // Already on background thread
                moveAudioToFinalLocation()
            }

            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // Get current model settings
            val transcriptionService = prefs.getString("transcription_service", "Groq Whisper v3 Turbo") ?: "Groq Whisper v3 Turbo"
            val aiModel = prefs.getString("ai_model", "mistral-saba-24b") ?: "mistral-saba-24b"
            val postProcessEnabled = prefs.getBoolean("enable_postprocess", false)

            fun sanitizeForLog(text: String?): String {
                if (text.isNullOrEmpty()) return text ?: ""
                // Prevent accidental entry splitting/truncation by neutralising lines that are exactly '---'
                return text.replace(Regex("(?m)^(---)\\s*$"), "\u200B$1")
            }

            val logEntry = buildString {
                append("[$timestamp]\n")
                append("Audio: ${finalAudioFileName ?: "unknown.m4a"}\n")

                // Add transcription service info
                append("Transcription Service: $transcriptionService\n")

                // Add AI model info if post-processing was enabled and used
                if (postProcessEnabled && finalText != transcription) {
                    val displayModel = if (aiModel == "OpenRouter") {
                        val openRouterModel = prefs.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6") ?: "anthropic/claude-sonnet-4-6"
                        "OpenRouter ($openRouterModel)"
                    } else {
                        aiModel
                    }
                    append("AI Model: $displayModel\n")
                }

                // Add app context if available
                if (!appContext.isNullOrBlank()) {
                    append("App: ${sanitizeForLog(appContext)}\n")
                }

                // Add selected text context if available
                if (!context.isNullOrBlank()) {
                    append("Selected Text: ${sanitizeForLog(context)}\n")
                }

                // Add screen context if available
                if (!screenContext.isNullOrBlank()) {
                    append("Screen: ${sanitizeForLog(screenContext)}\n")
                }

                append("Transcription: ${sanitizeForLog(transcription)}\n")

                // Only add AI processed line if it's different from original
                if (finalText != transcription) {
                    append("AI Processed: ${sanitizeForLog(finalText)}\n")
                }

                // Include the actual prompts that were sent to the LLM for accurate history details
                aiProcessingManager.getLastSystemMessage()?.let { sys ->
                    if (sys.isNotBlank()) {
                        append("LLM Prompt (System): \n")
                        append(sanitizeForLog(sys))
                        append("\n")
                    }
                }
                aiProcessingManager.getLastUserMessage()?.let { usr ->
                    if (usr.isNotBlank()) {
                        append("LLM Prompt (User): \n")
                        append(sanitizeForLog(usr))
                        append("\n")
                    }
                }

                append("---\n")
            }

            // Track audio files for cleanup
            val existingAudioFiles = prefs.getStringSet("audio_files", LinkedHashSet()) ?: LinkedHashSet()
            val updatedAudioFiles = LinkedHashSet(existingAudioFiles)
            finalAudioFileName?.let { updatedAudioFiles.add("${it}:$timestamp") }

            // Batch both operations into a single edit
            logStorageManager.appendLog(logEntry)
            prefs.edit()
                .putStringSet("audio_files", updatedAudioFiles)
                .apply()

            // Enforce recording history limit
            LogsActivity.enforceRecordingHistoryLimit(this)

            // Send broadcast to update log view - try multiple approaches
            val logUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED")
            logUpdateIntent.putExtra("is_reprocessed", true)
            logUpdateIntent.setPackage(packageName) // Ensure broadcast stays within app

            // Send as regular broadcast
            sendBroadcast(logUpdateIntent)
            Log.d("BubbleOverlayService", "Log update broadcast sent with reprocessed flag")

            // Also send as explicit broadcast
            val explicitIntent = Intent(logUpdateIntent)
            explicitIntent.setClassName(packageName, "${packageName}.LogsActivity")
            sendBroadcast(explicitIntent)
            Log.d("BubbleOverlayService", "Explicit log update broadcast sent")

            Log.d("BubbleOverlayService", "Log entry saved successfully")

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error saving log entry", e)
        }
    }

    /**
     * Save a log entry for a failed transcription or AI processing attempt.
     * Creates a history entry with error state so users can see and retry failed recordings.
     */
    private fun saveFailedLogEntry(
        audioFileName: String?,
        failurePoint: FailurePoint,
        serviceName: String,
        errorMessage: String
    ) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val logEntry = buildString {
                append("[$timestamp]\n")
                append("Audio: ${audioFileName ?: "unknown.m4a"}\n")
                append("Transcription Service: $serviceName\n")
                append("Error: ${failurePoint.name}\n")
                append("Error Service: $serviceName\n")
                append("Error Message: $errorMessage\n")
                append("---\n")
            }

            logStorageManager.appendLog(logEntry)

            // Update audio file tracking (same as successful entries)
            val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val existingAudioFiles = prefs.getStringSet("audio_files", LinkedHashSet()) ?: LinkedHashSet()
            val updatedAudioFiles = LinkedHashSet(existingAudioFiles)
            audioFileName?.let { updatedAudioFiles.add("${it}:$timestamp") }
            prefs.edit().putStringSet("audio_files", updatedAudioFiles).apply()

            // Enforce history limit (same as successful entries)
            LogsActivity.enforceRecordingHistoryLimit(this)

            // Broadcast update so history screen refreshes
            val logUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED")
            logUpdateIntent.setPackage(packageName)
            sendBroadcast(logUpdateIntent)

            Log.d("BubbleOverlayService", "Failed log entry saved: $failurePoint - $serviceName")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error saving failed log entry", e)
        }
    }

    /**
     * Show a toast notification with the specific service that failed.
     */
    private fun showFailureToast(failurePoint: FailurePoint, serviceName: String) {
        val message = when (failurePoint) {
            FailurePoint.TRANSCRIPTION -> "$serviceName transcription failed"
            FailurePoint.AI_PROCESSING -> "$serviceName AI post-processing failed"
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@BubbleOverlayService, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun isSuccessfulAiProcessingResult(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return !trimmed.startsWith("Processing failed") && !trimmed.startsWith("Error:")
    }

    private fun aiFallbackToastMessage(failureText: String?): String {
        val message = failureText?.trim().orEmpty()
        return when {
            message.isBlank() -> "AI post-processing unavailable, inserted raw transcription"
            message.contains("no api key", ignoreCase = true) ||
                message.contains("key not configured", ignoreCase = true) -> {
                "AI post-processing unavailable: no Groq API key, inserted raw transcription"
            }
            message.contains("timeout", ignoreCase = true) -> {
                "AI post-processing timed out, inserted raw transcription"
            }
            message.contains("api error", ignoreCase = true) -> {
                "AI post-processing request failed, inserted raw transcription"
            }
            else -> "AI post-processing failed, inserted raw transcription"
        }
    }

    private fun showAiFallbackToast(failureText: String?) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@BubbleOverlayService, aiFallbackToastMessage(failureText), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Broadcast transcription failure to keyboard for retry button state.
     * Called after saveFailedLogEntry() to notify IME of failure.
     *
     * @param audioFilePath Path to the preserved audio file (may be null)
     * @param serviceName Name of the transcription service that failed
     */
    private fun broadcastTranscriptionFailure(audioFilePath: String?, serviceName: String) {
        if (audioFilePath == null) return  // No audio to retry

        try {
            val intent = Intent(ACTION_TRANSCRIPTION_FAILURE).apply {
                setPackage(packageName)
                putExtra("audio_file_path", audioFilePath)
                putExtra("service_name", serviceName)
            }
            sendBroadcast(intent)
            Log.d("BubbleOverlayService", "Broadcast transcription failure: $audioFilePath")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Failed to broadcast transcription failure", e)
        }
    }

    /**
     * Broadcast that a retry has started for the given audio file.
     * Used by history screen to show spinner and prevent duplicate retries.
     */
    private fun broadcastRetryStarted(audioFilePath: String) {
        try {
            val intent = Intent(ACTION_RETRY_STARTED).apply {
                setPackage(packageName)
                putExtra("audio_file_path", audioFilePath)
            }
            sendBroadcast(intent)
            Log.d("BubbleOverlayService", "Broadcast retry started: $audioFilePath")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error broadcasting retry started", e)
        }
    }

    /**
     * Broadcast that a retry has completed for the given audio file.
     * Used by history screen to remove spinner and refresh entry state.
     *
     * @param audioFilePath Path to the audio file that was retried
     * @param success True if retry succeeded, false if it failed again
     */
    private fun broadcastRetryCompleted(audioFilePath: String, success: Boolean) {
        try {
            val intent = Intent(ACTION_RETRY_COMPLETED).apply {
                setPackage(packageName)
                putExtra("audio_file_path", audioFilePath)
                putExtra("success", success)
            }
            sendBroadcast(intent)
            Log.d("BubbleOverlayService", "Broadcast retry completed: $audioFilePath, success=$success")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error broadcasting retry completed", e)
        }
    }

    /**
     * Get the full file path for an audio file in the WonderWhisper directory.
     */
    private fun getAudioFilePath(fileName: String): String {
        return File(getPublicAudioDirectory(), fileName).absolutePath
    }

    private suspend fun saveLogEntryWithMetrics(
        transcription: String,
        finalText: String,
        context: String?,
        appContext: String? = null,
        screenContext: String? = null,
        performanceMetrics: PerformanceMetrics
    ) {
        try {
            Log.d("BubbleOverlayService", "=== SAVING LOG ENTRY WITH METRICS ===")
            Log.d("BubbleOverlayService", "Transcription: '$transcription'")
            Log.d("BubbleOverlayService", "Final Text: '$finalText'")
            Log.d("BubbleOverlayService", "Context: '$context'")
            Log.d("BubbleOverlayService", "App Context: '$appContext'")
            Log.d("BubbleOverlayService", "Performance: ${performanceMetrics.getFormattedTimingString()}")
            val (logUpdateIntent, explicitIntent) = withContext(Dispatchers.IO) {
                val finalAudioFileName = moveAudioToFinalLocation()

                val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val prefs = overrideForSimpleMode(originalPrefs)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                // Get current model settings
                val transcriptionService = prefs.getString("transcription_service", "Groq Whisper v3 Turbo") ?: "Groq Whisper v3 Turbo"
                val aiModel = prefs.getString("ai_model", "mistral-saba-24b") ?: "mistral-saba-24b"
                val postProcessEnabled = prefs.getBoolean("enable_postprocess", false)

                fun sanitizeForLog(text: String?): String {
                    if (text.isNullOrEmpty()) return text ?: ""
                    return text.replace(Regex("(?m)^(---)\\s*$"), "\u200B$1")
                }

                val logEntry = buildString {
                    append("[$timestamp]\n")
                    append("Audio: ${finalAudioFileName ?: "unknown.m4a"}\n")

                    // Add transcription service info
                    append("Transcription Service: $transcriptionService\n")

                    // Add AI model info if post-processing was enabled and used
                    if (postProcessEnabled && finalText != transcription) {
                        val displayModel = if (aiModel == "OpenRouter") {
                            val openRouterModel = prefs.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6") ?: "anthropic/claude-sonnet-4-6"
                            "OpenRouter ($openRouterModel)"
                        } else {
                            aiModel
                        }
                        append("AI Model: $displayModel\n")
                    }

                    // Add performance metrics
                    if (performanceMetrics.hasTimingData()) {
                        append(performanceMetrics.toLogEntryFormat())
                    }

                    // Add app context if available
                    if (!appContext.isNullOrBlank()) {
                        append("App: ${sanitizeForLog(appContext)}\n")
                    }

                    // Add selected text context if available
                    if (!context.isNullOrBlank()) {
                        append("Selected Text: ${sanitizeForLog(context)}\n")
                    }

                    // Add screen context if available
                    if (!screenContext.isNullOrBlank()) {
                        append("Screen: ${sanitizeForLog(screenContext)}\n")
                    }

                    append("Transcription: ${sanitizeForLog(transcription)}\n")

                    // Only add AI processed line if it's different from original
                    if (finalText != transcription) {
                        append("AI Processed: ${sanitizeForLog(finalText)}\n")
                    }

                    // Include the actual prompts that were sent to the LLM for accurate history details
                    aiProcessingManager.getLastSystemMessage()?.let { sys ->
                        if (sys.isNotBlank()) {
                            append("LLM Prompt (System): \n")
                            append(sanitizeForLog(sys))
                            append("\n")
                        }
                    }
                    aiProcessingManager.getLastUserMessage()?.let { usr ->
                        if (usr.isNotBlank()) {
                            append("LLM Prompt (User): \n")
                            append(sanitizeForLog(usr))
                            append("\n")
                        }
                    }

                    append("---\n")
                }

                // Track audio files for cleanup
                val existingAudioFiles = prefs.getStringSet("audio_files", LinkedHashSet()) ?: LinkedHashSet()
                val updatedAudioFiles = LinkedHashSet(existingAudioFiles)
                finalAudioFileName?.let { updatedAudioFiles.add("${it}:$timestamp") }

                // Batch both operations into a single edit
                logStorageManager.appendLog(logEntry)
                prefs.edit()
                    .putStringSet("audio_files", updatedAudioFiles)
                    .apply()

                // Enforce recording history limit
                LogsActivity.enforceRecordingHistoryLimit(this@BubbleOverlayService)

                val baseIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED").apply {
                    putExtra("is_reprocessed", true)
                    setPackage(packageName)
                }

                val explicitIntent = Intent(baseIntent).apply {
                    setClassName(packageName, "${packageName}.LogsActivity")
                }

                Pair(baseIntent, explicitIntent)
            }

            // Send broadcasts after background work is finished
            sendBroadcast(logUpdateIntent)
            Log.d("BubbleOverlayService", "Log update broadcast sent with reprocessed flag")

            sendBroadcast(explicitIntent)
            Log.d("BubbleOverlayService", "Explicit log update broadcast sent")

            Log.d("BubbleOverlayService", "Log entry with metrics saved successfully")

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error saving log entry with metrics", e)
        }
    }

    private fun moveAudioToFinalLocation(): String? {
        return try {
            val tempFile = audioFile
            if (tempFile == null || !tempFile.exists()) {
                Log.e("BubbleOverlayService", "Temp audio file is null or doesn't exist")
                return null
            }

            // Generate final filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val finalFileName = "recording_${timestamp}.m4a"

            // Create final location directory - public WonderWhisper folder (for backup)
            val outputDir = getPublicAudioDirectory()
            val finalFile = File(outputDir, finalFileName)

            // Create cache directory for sharing
            val cacheAudioDir = File(this.cacheDir, "audio")
            if (!cacheAudioDir.exists()) {
                cacheAudioDir.mkdirs()
            }
            val cacheFile = File(cacheAudioDir, finalFileName)

            // First, move/copy file to Downloads location (primary backup)
            var success = false
            if (tempFile.renameTo(finalFile)) {
                Log.d("BubbleOverlayService", "Audio file moved to Downloads: ${finalFile.absolutePath}")
                audioFile = finalFile // Update reference
                success = true
            } else {
                // If rename fails, try copy and delete
                try {
                    tempFile.inputStream().use { input ->
                        finalFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.delete()
                    audioFile = finalFile // Update reference
                    Log.d("BubbleOverlayService", "Audio file copied to Downloads: ${finalFile.absolutePath}")
                    success = true
                } catch (e: Exception) {
                    Log.e("BubbleOverlayService", "Failed to copy to Downloads folder", e)
                }
            }

            // Also copy to cache directory for reliable sharing (secondary copy)
            if (success && finalFile.exists()) {
                try {
                    finalFile.inputStream().use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("BubbleOverlayService", "Audio file also cached for sharing: ${cacheFile.absolutePath}");
                } catch (e: Exception) {
                    Log.e("BubbleOverlayService", "Failed to cache audio file for sharing", e)
                    // Don't fail the operation if cache copy fails - Downloads copy is primary
                }
            }

            return if (success) finalFileName else null

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error moving audio file to final location", e)
            return audioFile?.name
        }
    }

    private fun sendTranscriptionForProcessing(transcriptionText: String) {
        Log.d("BubbleOverlay", "Processing transcription: '$transcriptionText'")
        Log.d("BubbleOverlay", "=== TRANSCRIPTION LENGTH DEBUG ===")
        Log.d("BubbleOverlay", "Transcription length: ${transcriptionText.length} characters")
        Log.d("BubbleOverlay", "Transcription: '$transcriptionText'")

        // PRIVACY: Clear any expired clipboard data before AI processing

        // Use structured concurrency for parallel processing
        serviceScope.launch {
            processTranscriptionWithParallelOptimization(transcriptionText)
        }
    }

    private fun sendTranscriptionForProcessingWithMetrics(
        transcriptionText: String,
        transcriptionMetrics: PerformanceMetrics,
        totalMetricsBuilder: PerformanceMetricsBuilder
    ) {
        Log.d("BubbleOverlay", "Processing transcription with metrics: '$transcriptionText'")
        Log.d("BubbleOverlay", "=== TRANSCRIPTION LENGTH DEBUG ===")
        Log.d("BubbleOverlay", "Transcription length: ${transcriptionText.length} characters")
        Log.d("BubbleOverlay", "Transcription: '$transcriptionText'")

        // PRIVACY: Clear any expired clipboard data before AI processing

        // Use structured concurrency for parallel processing
        serviceScope.launch {
            processTranscriptionWithParallelOptimizationAndMetrics(transcriptionText, transcriptionMetrics, totalMetricsBuilder)
        }
    }

    private suspend fun processTranscriptionWithParallelOptimization(transcriptionText: String) = coroutineScope {
        // Start all independent operations in parallel
        val textReplacementDeferred = async(Dispatchers.Default) {
            applyCustomTextReplacements(transcriptionText)
        }

        val preferencesDeferred = async(Dispatchers.IO) {
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            overrideForSimpleMode(originalPrefs)
        }

        val contextDataDeferred = async(Dispatchers.Default) {
            // Capture context data early while other operations are running
            Pair(selectedTextContext, screenContext)
        }

        // Wait for parallel operations to complete
        val textWithReplacements = textReplacementDeferred.await()
        val sharedPrefs = preferencesDeferred.await()
        contextDataDeferred.await()

        if (textWithReplacements != transcriptionText) {
            Log.d("BubbleOverlay", "Applied text replacements: '$transcriptionText' -> '$textWithReplacements'")
        }

        val postProcessEnabled = sharedPrefs.getBoolean("enable_postprocess", false)
        val shouldUseAI = postProcessEnabled && !shouldSkipAIProcessing(textWithReplacements)

        if (shouldUseAI) {
            val aiModel = sharedPrefs.getString("ai_model", "groq/openai/gpt-oss-20b") ?: "groq/openai/gpt-oss-20b"
            val isGeminiModel = aiModel.startsWith("gemini", ignoreCase = true)
            val isClaudeModel = aiModel.startsWith("claude", ignoreCase = true)
            val isGroqModel = aiModel.startsWith("mistral", ignoreCase = true) ||
                              aiModel == "mistral-saba-24b" ||
                              aiModel == "openai/gpt-oss-120b" ||
                              aiModel == "groq/openai/gpt-oss-20b"
            val isOpenRouterModel = aiModel == "OpenRouter"

            val modelType = when {
                isGeminiModel -> "Gemini"
                isClaudeModel -> "Claude"
                isGroqModel -> "Groq"
                isOpenRouterModel -> "OpenRouter"
                else -> "OpenAI"
            }

            Log.d("BubbleOverlay", "AI post-processing is enabled, routing to $modelType")
            Log.d("BubbleOverlay", "=== SENDING TO AI FOR PROCESSING ===")
            Log.d("BubbleOverlay", "Input text length: ${textWithReplacements.length}")
            Log.d("BubbleOverlay", "Input text: '$textWithReplacements'")
            Log.d("BubbleOverlay", "Screen context available: ${!screenContext.isNullOrBlank()}")
            Log.d("BubbleOverlay", "Selected context available: ${!selectedTextContext.isNullOrBlank()}")

            // Detect command mode
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val commandWords = prefs.getString("command_word", "command") ?: "command"
            val commandWordsList = commandWords.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val words = textWithReplacements.trim().split(whitespaceRegex)
            val firstWordRaw = words.firstOrNull() ?: ""
            val firstWordClean = firstWordRaw.lowercase().replace(punctuationRegex, "")
            val isCommandMode = words.isNotEmpty() && commandWordsList.contains(firstWordClean)

            serviceScope.launch {
                val processedText = aiProcessingManager.processWithAI(
                    textWithReplacements,
                    selectedTextContext ?: "",
                    screenContext ?: "",
                    currentAppContext ?: "",
                    isCommandMode
                )

                Log.d("BubbleOverlay", "=== AI PROCESSING RESULT ===")
                Log.d("BubbleOverlay", "Processed text: '$processedText'")
                Log.d("BubbleOverlay", "Original text: '$textWithReplacements'")
                Log.d("BubbleOverlay", "Text changed: ${processedText != textWithReplacements}")

                val finalText = if (isSuccessfulAiProcessingResult(processedText)) {
                    Log.d("BubbleOverlay", "AI processing successful")
                    processedText!!
                } else {
                    Log.e("BubbleOverlay", "AI processing failed, using text with replacements")
                    textWithReplacements
                }

                val selectedContextSnapshot = selectedTextContext
                val appContextSnapshot = currentAppContext
                val screenContextSnapshot = screenContext

                withContext(Dispatchers.Main) {
                    insertText(finalText)
                    trackTranscriptionStats(finalText, null)
                }
                if (!isSuccessfulAiProcessingResult(processedText)) {
                    showAiFallbackToast(processedText)
                }

                saveLogEntry(
                    transcriptionText,
                    finalText,
                    selectedContextSnapshot,
                    appContextSnapshot,
                    screenContextSnapshot
                )

                // Clear contexts after all processing is complete
                selectedTextContext = null
                currentAppContext = null
                screenContext = null
            }
        } else {
            if (postProcessEnabled) {
                Log.d("BubbleOverlay", "Skipping AI post-processing for short/simple transcription")
            } else {
                Log.d("BubbleOverlay", "AI post-processing disabled, using text with replacements")
            }

            val selectedContextSnapshot = selectedTextContext
            val appContextSnapshot = currentAppContext
            val screenContextSnapshot = screenContext

            withContext(Dispatchers.Main) {
                insertText(textWithReplacements)
                trackTranscriptionStats(textWithReplacements, null)
            }

            saveLogEntry(
                transcriptionText,
                textWithReplacements,
                selectedContextSnapshot,
                appContextSnapshot,
                screenContextSnapshot
            )

            // Clear contexts after processing
            selectedTextContext = null
            currentAppContext = null
            screenContext = null
        }
    }

    private suspend fun processTranscriptionWithParallelOptimizationAndMetrics(
        transcriptionText: String,
        transcriptionMetrics: PerformanceMetrics,
        totalMetricsBuilder: PerformanceMetricsBuilder
    ) = coroutineScope {
        // Start all independent operations in parallel
        val textReplacementDeferred = async(Dispatchers.Default) {
            applyCustomTextReplacements(transcriptionText)
        }

        val preferencesDeferred = async(Dispatchers.IO) {
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            overrideForSimpleMode(originalPrefs)
        }

        val contextDataDeferred = async(Dispatchers.Default) {
            // Capture context data early while other operations are running
            Pair(selectedTextContext, screenContext)
        }

        // Wait for parallel operations to complete
        val textWithReplacements = textReplacementDeferred.await()
        val sharedPrefs = preferencesDeferred.await()
        contextDataDeferred.await()

        if (textWithReplacements != transcriptionText) {
            Log.d("BubbleOverlay", "Applied text replacements: '$transcriptionText' -> '$textWithReplacements'")
        }

        val postProcessEnabled = sharedPrefs.getBoolean("enable_postprocess", false)
        val shouldUseAI = postProcessEnabled && !shouldSkipAIProcessing(textWithReplacements)

        if (shouldUseAI) {
            val aiModel = sharedPrefs.getString("ai_model", "groq/openai/gpt-oss-20b") ?: "groq/openai/gpt-oss-20b"
            val isGeminiModel = aiModel.startsWith("gemini", ignoreCase = true)
            val isClaudeModel = aiModel.startsWith("claude", ignoreCase = true)
            val isGroqModel = aiModel.startsWith("mistral", ignoreCase = true) ||
                              aiModel == "mistral-saba-24b" ||
                              aiModel == "openai/gpt-oss-120b" ||
                              aiModel == "groq/openai/gpt-oss-20b"
            val isOpenRouterModel = aiModel == "OpenRouter"

            val modelType = when {
                isGeminiModel -> "Gemini"
                isClaudeModel -> "Claude"
                isGroqModel -> "Groq"
                isOpenRouterModel -> "OpenRouter"
                else -> "ChatGPT"
            }

            Log.d("BubbleOverlay", "AI post-processing enabled with $modelType model: $aiModel")

            // Check for command mode
            // Use processed preferences (with Simple Mode overrides applied)
            val prefs = sharedPrefs
            val punctuationRegex = Regex("[^\\w\\s]")
            val whitespaceRegex = Regex("\\s+")
            val commandWords = prefs.getString("command_word", "command") ?: "command"
            val commandWordsList = commandWords.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val words = textWithReplacements.trim().split(whitespaceRegex)
            val firstWordRaw = words.firstOrNull() ?: ""
            val firstWordClean = firstWordRaw.lowercase().replace(punctuationRegex, "")
            
            val useCommandForSelectedText = settingsManager.isUseCommandPromptForSelectedTextEnabled()
            val hasSelectedText = !selectedTextContext.isNullOrBlank()
            val isCommandMode = (words.isNotEmpty() && commandWordsList.contains(firstWordClean)) || (useCommandForSelectedText && hasSelectedText)

            // Use structured concurrency with the service scope
            serviceScope.launch {
                val (processedText, aiMetrics) = aiProcessingManager.processWithAIAndMetrics(
                    textWithReplacements,
                    selectedTextContext ?: "",
                    screenContext ?: "",
                    currentAppContext ?: "",
                    isCommandMode
                )

                totalMetricsBuilder.endTotalProcessing()

                // Combine all metrics
                val combinedMetrics = PerformanceMetrics(
                    transcriptionTimeMs = transcriptionMetrics.transcriptionTimeMs,
                    aiProcessingTimeMs = aiMetrics.aiProcessingTimeMs,
                    totalProcessingTimeMs = totalMetricsBuilder.build().totalProcessingTimeMs,
                    transcriptionCacheHit = transcriptionMetrics.transcriptionCacheHit,
                    aiProcessingCacheHit = aiMetrics.aiProcessingCacheHit,
                    transcriptionService = transcriptionMetrics.transcriptionService,
                    aiModel = aiMetrics.aiModel
                )

                Log.d("BubbleOverlay", "=== AI PROCESSING RESULT ===")
                Log.d("BubbleOverlay", "Processed text: '$processedText'")
                Log.d("BubbleOverlay", "Original text: '$textWithReplacements'")
                Log.d("BubbleOverlay", "Text changed: ${processedText != textWithReplacements}")
                Log.d("BubbleOverlay", "Performance metrics: ${combinedMetrics.getFormattedTimingString()}")

                val finalText = if (isSuccessfulAiProcessingResult(processedText)) {
                    Log.d("BubbleOverlay", "AI processing successful")
                    processedText!!
                } else {
                    Log.e("BubbleOverlay", "AI processing failed, using text with replacements")
                    textWithReplacements
                }

                withContext(Dispatchers.Main) {
                    insertText(finalText)
                    trackTranscriptionStats(finalText, combinedMetrics) // Track stats with metrics
                }
                if (!isSuccessfulAiProcessingResult(processedText)) {
                    showAiFallbackToast(processedText)
                }

                val selectedContextSnapshot = selectedTextContext
                val appContextSnapshot = currentAppContext
                val screenContextSnapshot = screenContext

                saveLogEntryWithMetrics(
                    transcriptionText,
                    finalText,
                    selectedContextSnapshot,
                    appContextSnapshot,
                    screenContextSnapshot,
                    combinedMetrics
                )

                // Clear contexts after all processing is complete
                selectedTextContext = null
                currentAppContext = null
                screenContext = null
            }
        } else {
            if (postProcessEnabled) {
                Log.d("BubbleOverlay", "Skipping AI post-processing for short/simple transcription")
            }
            totalMetricsBuilder.endTotalProcessing()
            val finalMetrics = PerformanceMetrics(
                transcriptionTimeMs = transcriptionMetrics.transcriptionTimeMs,
                aiProcessingTimeMs = 0L,
                totalProcessingTimeMs = totalMetricsBuilder.build().totalProcessingTimeMs,
                transcriptionCacheHit = transcriptionMetrics.transcriptionCacheHit,
                aiProcessingCacheHit = false,
                transcriptionService = transcriptionMetrics.transcriptionService,
                aiModel = ""
            )

            Log.d("BubbleOverlay", "AI post-processing disabled, using text with replacements")
            withContext(Dispatchers.Main) {
                insertText(textWithReplacements)
                trackTranscriptionStats(textWithReplacements, finalMetrics) // Track stats with metrics
            }

            val selectedContextSnapshot = selectedTextContext
            val appContextSnapshot = currentAppContext
            val screenContextSnapshot = screenContext

            saveLogEntryWithMetrics(
                transcriptionText,
                textWithReplacements,
                selectedContextSnapshot,
                appContextSnapshot,
                screenContextSnapshot,
                finalMetrics
            )

            // Clear contexts after processing
            selectedTextContext = null
            currentAppContext = null
            screenContext = null
        }
    }

    private fun applyCustomTextReplacements(text: String): String {
        val replacementRules = settingsManager.getCustomLanguageConfig().replacementRules
        return TextProcessingUtils.applyCustomTextReplacements(text, replacementRules)
    }

    private fun shouldSkipAIProcessing(text: String): Boolean {
        val skipEnabled = settingsManager.getBooleanSetting("llm_skip_simple_transcriptions", false)
        if (!skipEnabled) return false

        return text.length < 10 ||
               text.matches(Regex("^[a-zA-Z0-9\\s.,!?]{1,20}$")) ||
               !containsComplexFormatting(text)
    }

    private fun containsComplexFormatting(text: String): Boolean {
        // Check if text might benefit from AI processing
        return text.contains(Regex("[;:]")) || // Lists or complex punctuation
               text.split("\\s+".toRegex()).size > 15 || // Long sentences
               text.contains(Regex("\\b(and|but|however|therefore|because)\\b", RegexOption.IGNORE_CASE)) // Complex connectors
    }

    private fun clearContexts() {
        contextCollectionJob?.cancel()
        contextCollectionJob = null
        selectedTextContext = null
        currentAppContext = null
        screenContext = null
    }

    // getCacheKey method moved to individual managers

    // Cache methods moved to individual managers
    // Temporary methods for backward compatibility during refactoring
    private fun getCachedAIResponse(text: String, context: String, aiModel: String): String? {
        return aiProcessingManager.getCachedResponse(text, context, aiModel)
    }

    private fun cacheAIResponse(text: String, context: String, aiModel: String, result: String) {
        // Cache is handled internally by AIProcessingManager
    }

    private fun getAudioFileHash(audioFile: File): String {
        return audioFileManager.getAudioFileHash(audioFile)
    }

    // Pre-warming moved to NetworkManager

    private fun sendTranscriptionForReprocessing(transcriptionText: String, originalAudioFileName: String) {
        Log.d("BubbleOverlay", "Processing transcription for reprocessing: '$transcriptionText'")

        // Apply universal text replacements first (before any AI processing)
        val textWithReplacements = applyCustomTextReplacements(transcriptionText)
        if (textWithReplacements != transcriptionText) {
            Log.d("BubbleOverlay", "Applied text replacements: '$transcriptionText' -> '$textWithReplacements'")
        }

        val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val sharedPrefs = overrideForSimpleMode(originalPrefs)
        val postProcessEnabled = sharedPrefs.getBoolean("enable_postprocess", false)
        val shouldUseAI = postProcessEnabled && !shouldSkipAIProcessing(textWithReplacements)

        if (shouldUseAI) {
            val aiModel = sharedPrefs.getString("ai_model", "groq/openai/gpt-oss-20b") ?: "groq/openai/gpt-oss-20b"
            val isGeminiModel = aiModel.startsWith("gemini", ignoreCase = true)
            val isClaudeModel = aiModel.startsWith("claude", ignoreCase = true)
            val isGroqModel = aiModel.startsWith("mistral", ignoreCase = true) ||
                              aiModel == "mistral-saba-24b" ||
                              aiModel == "openai/gpt-oss-120b" ||
                              aiModel == "groq/openai/gpt-oss-20b"
            val isOpenRouterModel = aiModel == "OpenRouter"

            val modelType = when {
                isGeminiModel -> "Gemini"
                isClaudeModel -> "Claude"
                isGroqModel -> "Groq"
                isOpenRouterModel -> "OpenRouter"
                else -> "OpenAI"
            }

            Log.d("BubbleOverlay", "AI post-processing is enabled for reprocessing, routing to $modelType")

            // Detect command mode for reprocessing
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val commandWords = prefs.getString("command_word", "command") ?: "command"
            val commandWordsList = commandWords.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val words = textWithReplacements.trim().split(whitespaceRegex)
            val firstWordRaw = words.firstOrNull() ?: ""
            val firstWordClean = firstWordRaw.lowercase().replace(punctuationRegex, "")
            val isCommandMode = words.isNotEmpty() && commandWordsList.contains(firstWordClean)

            // Use structured concurrency with the service scope
            serviceScope.launch {
                val processedText = aiProcessingManager.processWithAI(
                    textWithReplacements,
                    selectedTextContext ?: "",
                    screenContext ?: "",
                    currentAppContext ?: "",
                    isCommandMode
                )

                val finalText = if (!processedText.isNullOrBlank() && !processedText.startsWith("Processing failed")) {
                    Log.d("BubbleOverlay", "AI processing successful for reprocessing")
                    processedText
                } else {
                    Log.e("BubbleOverlay", "AI processing failed for reprocessing, using text with replacements")
                    textWithReplacements
                }

                saveReprocessedLogEntry(transcriptionText, finalText, originalAudioFileName)

                // Clear contexts after all processing is complete
                selectedTextContext = null
                currentAppContext = null
                screenContext = null
            }
        } else {
            if (postProcessEnabled) {
                Log.d("BubbleOverlay", "Skipping AI post-processing for reprocessing (short/simple transcription)")
            } else {
                Log.d("BubbleOverlay", "AI post-processing disabled for reprocessing, using text with replacements")
            }

            saveReprocessedLogEntry(transcriptionText, textWithReplacements, originalAudioFileName)

            // Clear contexts after processing
            selectedTextContext = null
            currentAppContext = null
            screenContext = null
        }
    }

    private suspend fun reprocessNoteAndUpdate(audioFilePath: String, audioFileName: String) {
        try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                Log.e("BubbleOverlayService", "reprocessNoteAndUpdate: audio file not found: $audioFilePath")
                return
            }

            // 1) Transcribe using current transcription settings
            val originalTranscript = performTranscription(audioFile)
            if (originalTranscript.isBlank()) {
                Log.w("BubbleOverlayService", "reprocessNoteAndUpdate: empty transcript")
                return
            }

            // 2) Optionally post-process with AI (same logic as processNoteAudio)
            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val postProcessEnabled = prefs.getBoolean("enable_postprocess", false)
            val aiProcessedContent = if (postProcessEnabled) {
                try {
                    val reformatPromptManager = ReformatPromptManager(this@BubbleOverlayService)
                    val autoDetectPrompt = reformatPromptManager.getPromptById("auto_detect")
                    val processedResult = if (autoDetectPrompt != null) {
                        aiProcessingManager.processWithReformatPrompt(originalTranscript, autoDetectPrompt)
                    } else {
                        aiProcessingManager.processWithAI(originalTranscript, "", "", currentAppContext ?: "", false)
                    }

                    val hasApiKeyError = processedResult == null ||
                        processedResult.contains("API key not configured") == true ||
                        processedResult.contains("No API key") == true ||
                        processedResult.startsWith("Error:") == true

                    if (hasApiKeyError) {
                        if (autoDetectPrompt != null) {
                            aiProcessingManager.processWithReformatPrompt(originalTranscript, autoDetectPrompt, "openai/gpt-oss-120b")
                        } else {
                            aiProcessingManager.processWithAIAndMetrics(originalTranscript, "", "", currentAppContext ?: "", false, "openai/gpt-oss-120b").first
                        }
                    } else processedResult
                } catch (e: Exception) {
                    Log.e("BubbleOverlayService", "AI enhancement failed during reprocess", e)
                    null
                }
            } else null

            val contentForTitle = aiProcessedContent ?: originalTranscript
            val aiGeneratedTitle = generateNoteTitle(contentForTitle)

            // 3) Find existing note by audio file name and update
            val existingNotes = notePadManager.getAllNotes()
            val note = existingNotes.find { it.audioFileName == audioFileName }
            if (note != null) {
                val updated = note.copy(
                    title = aiGeneratedTitle,
                    content = aiProcessedContent ?: originalTranscript,
                    originalTranscript = originalTranscript,
                    aiProcessed = aiProcessedContent,
                    aiGeneratedTitle = aiGeneratedTitle
                )
                notePadManager.saveNote(updated)
                // Notify UI
                val noteUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_NOTE_UPDATED")
                noteUpdateIntent.setPackage(packageName)
                sendBroadcast(noteUpdateIntent)
            } else {
                Log.w("BubbleOverlayService", "reprocessNoteAndUpdate: existing note not found for $audioFileName")
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error in reprocessNoteAndUpdate", e)
        }
    }

    private suspend fun reprocessText(textToProcess: String) {
        Log.d("BubbleOverlayService", "Reprocessing selected text: '${textToProcess.take(20)}...'")
        Log.d("BubbleOverlayService", "Forcing AI processing for manual reprocess action")
        
        try {
            // Process with AI using current settings, bypassing 'enable_postprocess' check
            val processedText = aiProcessingManager.processWithAI(
                textToProcess,
                selectedTextContext ?: "", 
                screenContext ?: "",
                currentAppContext ?: "",
                false // Not command mode
            )

            if (!processedText.isNullOrBlank() && !processedText.startsWith("Processing failed")) {
                Log.d("BubbleOverlayService", "Text reprocessed successfully")
                // Insert back into IME - since it's a reprocess of selection, this insert should replace the selection
                insertText(processedText)
                
                // Save to logs
                saveReprocessedLogEntry(textToProcess, processedText, "(text selection)")
            } else {
                Log.e("BubbleOverlayService", "Text reprocessing returned empty or failed")
                // If failed, maybe toast user? Or just do nothing?
                // Ideally we'd inform the user, but for now logging is sufficient as insertText won't be called
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error reprocessing text", e)
        }
    }

    private fun saveReprocessedLogEntry(transcription: String, finalText: String, originalAudioFileName: String) {
        try {
            Log.d("BubbleOverlayService", "=== SAVING REPROCESSED LOG ENTRY ===")
            Log.d("BubbleOverlayService", "Transcription: '$transcription'")
            Log.d("BubbleOverlayService", "Final Text: '$finalText'")
            Log.d("BubbleOverlayService", "Original Audio File: '$originalAudioFileName'")

            val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val prefs = overrideForSimpleMode(originalPrefs)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // Get current model settings
            val transcriptionService = prefs.getString("transcription_service", "Groq Whisper v3 Turbo") ?: "Groq Whisper v3 Turbo"
            val aiModel = prefs.getString("ai_model", "mistral-saba-24b") ?: "mistral-saba-24b"
            val postProcessEnabled = prefs.getBoolean("enable_postprocess", false)

            fun sanitizeForLog(text: String?): String {
                if (text.isNullOrEmpty()) return text ?: ""
                return text.replace(Regex("(?m)^(---)\\s*$"), "\u200B$1")
            }

            val logEntry = buildString {
                append("[$timestamp] (REPROCESSED)\n")
                append("Audio: $originalAudioFileName\n")

                // Add transcription service info
                append("Transcription Service: $transcriptionService\n")

                // Add AI model info if post-processing was enabled and used
                if (postProcessEnabled && finalText != transcription) {
                    val displayModel = if (aiModel == "OpenRouter") {
                        val openRouterModel = prefs.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6") ?: "anthropic/claude-sonnet-4-6"
                        "OpenRouter ($openRouterModel)"
                    } else {
                        aiModel
                    }
                    append("AI Model: $displayModel\n")
                }

                // Add app context if available
                if (!currentAppContext.isNullOrBlank()) {
                    append("App: ${sanitizeForLog(currentAppContext)}\n")
                }

                // Add selected text context if available
                if (!selectedTextContext.isNullOrBlank()) {
                    append("Selected Text: ${sanitizeForLog(selectedTextContext)}\n")
                }

                // Add screen context if available
                if (!screenContext.isNullOrBlank()) {
                    append("Screen: ${sanitizeForLog(screenContext)}\n")
                }

                append("Transcription: ${sanitizeForLog(transcription)}\n")

                // Only add AI processed line if it's different from original
                if (finalText != transcription) {
                    append("AI Processed: ${sanitizeForLog(finalText)}\n")
                }

                // Include the actual prompts that were sent to the LLM for accurate history details
                aiProcessingManager.getLastSystemMessage()?.let { sys ->
                    if (sys.isNotBlank()) {
                        append("LLM Prompt (System): \n")
                        append(sanitizeForLog(sys))
                        append("\n")
                    }
                }
                aiProcessingManager.getLastUserMessage()?.let { usr ->
                    if (usr.isNotBlank()) {
                        append("LLM Prompt (User): \n")
                        append(sanitizeForLog(usr))
                        append("\n")
                    }
                }

                append("---\n")
            }

            logStorageManager.appendLog(logEntry)

            // Don't add to audio_files list since we're reusing existing audio file

            // Enforce recording history limit
            LogsActivity.enforceRecordingHistoryLimit(this)

            // Send broadcast to update log view - try multiple approaches
            val logUpdateIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED")
            logUpdateIntent.putExtra("is_reprocessed", true)
            logUpdateIntent.setPackage(packageName) // Ensure broadcast stays within app

            // Send as regular broadcast
            sendBroadcast(logUpdateIntent)
            Log.d("BubbleOverlayService", "Log update broadcast sent with reprocessed flag")

            // Also send as explicit broadcast
            val explicitIntent = Intent(logUpdateIntent)
            explicitIntent.setClassName(packageName, "${packageName}.LogsActivity")
            sendBroadcast(explicitIntent)
            Log.d("BubbleOverlayService", "Explicit log update broadcast sent")

            // Verify the log was actually saved
            val verifyLogs = logStorageManager.readLogs()
            val logCount = verifyLogs.split("---\n").filter { it.isNotBlank() }.size
            Log.d("BubbleOverlayService", "Verification: Total log entries after save: $logCount")
            Log.d("BubbleOverlayService", "Verification: First few chars of logs: ${verifyLogs.take(100)}")

            Log.d("BubbleOverlayService", "Reprocessed log entry saved successfully")

        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Error saving reprocessed log entry", e)
        }
    }

    private fun persistStreamingLogEntry(
        finalText: String,
        selectedContext: String?,
        appContext: String?,
        screenContext: String?
    ) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val originalPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val prefs = overrideForSimpleMode(originalPrefs)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val transcriptionService = prefs.getString("transcription_service", "Groq Whisper v3 Turbo") ?: "Groq Whisper v3 Turbo"
                val streamingOverride = settingsManager.getStreamingAiModel()
                val fallbackAiModel = prefs.getString("ai_model", "mistral-saba-24b") ?: "mistral-saba-24b"
                val rawModel = streamingOverride.takeIf { it.isNotBlank() } ?: fallbackAiModel
                val displayModel = if (rawModel.equals("OpenRouter", true)) {
                    val openRouterModel = prefs.getString("openrouter_model_id", "anthropic/claude-sonnet-4-6") ?: "anthropic/claude-sonnet-4-6"
                    "OpenRouter ($openRouterModel)"
                } else {
                    rawModel
                }

                fun sanitizeForLog(text: String?): String {
                    if (text.isNullOrEmpty()) return text ?: ""
                    return text.replace(Regex("(?m)^(---)\\s*$"), "\u200B$1")
                }

                val logEntry = buildString {
                    append("[$timestamp]\n")
                    append("Audio: (streaming)\n")
                    append("Transcription Service: $transcriptionService\n")
                    if (displayModel.isNotBlank()) {
                        append("AI Model: $displayModel\n")
                    }
                    append("Mode: Streaming Dictation\n")
                    if (!appContext.isNullOrBlank()) {
                        append("App: ${sanitizeForLog(appContext)}\n")
                    }
                    if (!selectedContext.isNullOrBlank()) {
                        append("Selected Text: ${sanitizeForLog(selectedContext)}\n")
                    }
                    if (!screenContext.isNullOrBlank()) {
                        append("Screen: ${sanitizeForLog(screenContext)}\n")
                    }
                    append("Transcription: ${sanitizeForLog(finalText)}\n")
                    aiProcessingManager.getLastSystemMessage()?.let { sys ->
                        if (sys.isNotBlank()) {
                            append("LLM Prompt (System): \n")
                            append(sanitizeForLog(sys))
                            append("\n")
                        }
                    }
                    aiProcessingManager.getLastUserMessage()?.let { usr ->
                        if (usr.isNotBlank()) {
                            append("LLM Prompt (User): \n")
                            append(sanitizeForLog(usr))
                            append("\n")
                        }
                    }
                    append("---\n")
                }

                logStorageManager.appendLog(logEntry)
                LogsActivity.enforceRecordingHistoryLimit(this@BubbleOverlayService)

                val baseIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_LOG_UPDATED").apply {
                    putExtra("is_reprocessed", false)
                    setPackage(packageName)
                }
                sendBroadcast(baseIntent)

                val explicitIntent = Intent(baseIntent).apply {
                    setClassName(packageName, "${packageName}.LogsActivity")
                }
                sendBroadcast(explicitIntent)

                Log.d("BubbleOverlayService", "Streaming log entry saved")
            } catch (e: Exception) {
                Log.e("BubbleOverlayService", "Error saving streaming log entry", e)
            }
        }
    }

    private fun insertText(textToInsert: String) {
        // Safety: never insert provider error strings
        if (!isSuccessfulTranscription(textToInsert)) {
            Log.w("BubbleOverlay", "Not inserting text because it appears to be an error: '$textToInsert'")
            return
        }
        Log.d("BubbleOverlay", "Inserting text: '$textToInsert'")

        // 1) Prefer IME direct insert if IME is recently active (within 10s)
        val imeActive = (System.currentTimeMillis() - lastImeActiveAt) < 10_000
        if (imeActive) {
            try {
                val imeIntent = Intent(ACTION_IME_INSERT)
                imeIntent.setPackage(packageName)
                // Send text as-is; separation handled by editor or trailing space logic
                imeIntent.putExtra("text", textToInsert)
                sendBroadcast(imeIntent)
                Log.d("BubbleOverlay", "Sent IME insert broadcast (preferred path) with leading space")
                // Track statistics for successful insertion
                trackTranscriptionStats(textToInsert, null)
                return
            } catch (e: Exception) {
                Log.e("BubbleOverlay", "IME insert broadcast failed, will try fallbacks", e)
            }
        }

        // 2) Try Accessibility Service direct insertion
        var directSuccess = false
        try {
            directSuccess = DictationAccessibilityService.insertTextDirect(textToInsert)
            Log.d("BubbleOverlay", "Accessibility direct insert result: $directSuccess")
        } catch (e: Exception) {
            Log.e("BubbleOverlay", "Accessibility direct insert threw", e)
        }
        if (directSuccess) {
            // Track statistics for successful insertion
            trackTranscriptionStats(textToInsert, null)
            return
        }

        // 3) Try IME insert broadcast even if we didn't detect recent IME activity
        try {
            val imeIntent = Intent(ACTION_IME_INSERT)
            imeIntent.setPackage(packageName)
            // Send text as-is; separation handled by editor or trailing space logic
            imeIntent.putExtra("text", textToInsert)
            sendBroadcast(imeIntent)
            Log.d("BubbleOverlay", "Sent IME insert broadcast as fallback with leading space")
            // Track statistics for successful insertion (can't confirm success, but try anyway)
            trackTranscriptionStats(textToInsert, null)
            // Can't confirm success; proceed to also send accessibility broadcast as last resort
        } catch (e: Exception) {
            Log.e("BubbleOverlay", "IME insert fallback broadcast failed", e)
        }

        // 4) Send Accessibility Service broadcast (legacy path)
        try {
            val accIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_INSERT_TEXT")
            accIntent.putExtra("text", textToInsert)
            accIntent.setComponent(ComponentName("com.slumdog88.dictationkeyboardai", "com.slumdog88.dictationkeyboardai.DictationAccessibilityService"))
            sendBroadcast(accIntent)
            Log.d("BubbleOverlay", "Sent Accessibility insert broadcast (legacy)")
            // Track statistics for successful insertion (legacy path)
            trackTranscriptionStats(textToInsert, null)
        } catch (e: Exception) {
            Log.e("BubbleOverlay", "Accessibility broadcast failed", e)
        }

        // 5) Last-resort: copy to clipboard so user can paste manually
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("Dictation", textToInsert)
            clipboard.setPrimaryClip(clip)
            Log.d("BubbleOverlay", "Copied text to clipboard as last-resort fallback")
            // Track statistics for successful insertion (clipboard fallback)
            trackTranscriptionStats(textToInsert, null)
        } catch (e: Exception) {
            Log.e("BubbleOverlay", "Failed to copy text to clipboard fallback", e)
        }
    }

    private fun copyTextToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("Dictation", text)
            clipboard.setPrimaryClip(clip)
            Log.d("BubbleOverlayService", "Copied streaming text to clipboard (${text.length} chars)")
        } catch (e: Exception) {
            Log.e("BubbleOverlayService", "Failed to copy streaming text to clipboard", e)
        }
    }

    private fun getDefaultDictationPrompt(): String {
        return TextProcessingUtils.getDefaultDictationPrompt()
    }

    private fun getDefaultCommandPrompt(): String {
        return TextProcessingUtils.getDefaultCommandPrompt()
    }

    /**
     * Extract text content from between XML tags in AI response
     * @param response The full AI response text
     * @param tagName The XML tag name to extract from (e.g., "FORMATTED_TEXT")
     * @return The content between the tags, or the original response if tags not found
     */
    private fun extractXmlTagContent(response: String, tagName: String): String {
        return TextProcessingUtils.extractXmlTagContent(response, tagName)
    }

    /**
     * Track transcription statistics for persistent storage
     * @param finalText The final text that was inserted
     * @param metrics Optional performance metrics containing timing information
     */
    private fun trackTranscriptionStats(finalText: String, metrics: PerformanceMetrics?) {
        try {
            // Count words in the final text
            val wordCount = finalText.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size

            // Get transcription time from metrics if available, otherwise estimate
            val transcriptionTimeSeconds = if (metrics != null && metrics.transcriptionTimeMs > 0) {
                (metrics.transcriptionTimeMs / 1000).toInt()
            } else {
                // Estimate based on word count (rough approximation: 150 words per minute)
                maxOf(1, (wordCount.toDouble() / 150 * 60).toInt())
            }

            // Only track if we have meaningful data
            if (wordCount > 0 && transcriptionTimeSeconds > 0) {
                val statisticsManager = StatisticsManager(this)
                statisticsManager.addTranscriptionStats(wordCount, transcriptionTimeSeconds)
                Log.d("BubbleOverlay", "Tracked transcription stats: $wordCount words, ${transcriptionTimeSeconds}s")
            }
        } catch (e: Exception) {
            Log.e("BubbleOverlay", "Error tracking transcription stats", e)
        }
    }

    /**
     * Override settings for simple mode
     */
    private fun overrideForSimpleMode(prefs: SharedPreferences): SharedPreferences {
        return settingsManager.getSettings()
    }

    /**
     * Check if keyboard-aware bubble setting is enabled
     */
    private fun isKeyboardAwareBubbleEnabled(): Boolean {
        return settingsManager.isKeyboardAwareBubbleEnabled()
    }
    // Returns true only if the transcription looks like valid user text (not an error string)
    private fun isSuccessfulTranscription(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.trim()
        val lower = t.lowercase()
        if (lower.startsWith("error:")) return false
        if (lower.startsWith("api error")) return false
        if (lower.contains(" api error")) return false
        if (lower.contains("transcription failed")) return false
        if (lower.contains("groq api error")) return false
        return true
    }


    // Keyboard detection methods moved to KeyboardDetectionManager
}
