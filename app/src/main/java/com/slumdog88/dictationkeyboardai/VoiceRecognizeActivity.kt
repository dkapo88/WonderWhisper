package com.slumdog88.dictationkeyboardai

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import com.slumdog88.dictationkeyboardai.ui.voice.VoiceRecognizerScreen
import com.slumdog88.dictationkeyboardai.ui.voice.VoiceRecognizerState

/**
 * Activity that handles external voice recognition requests via RECOGNIZE_SPEECH intent.
 *
 * When other keyboards (like SwiftKey) press the dictation button, Android sends an
 * implicit intent with action "android.speech.action.RECOGNIZE_SPEECH". This activity
 * handles that intent, shows a floating popup, records/transcribes audio using
 * BubbleOverlayService, and returns the result to the calling app.
 */
class VoiceRecognizeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VoiceRecognizeActivity"

        // Broadcast actions for communication with BubbleOverlayService
        const val ACTION_VOICE_RECOGNIZE_RESULT = "com.slumdog88.dictationkeyboardai.ACTION_VOICE_RECOGNIZE_RESULT"
        const val ACTION_VOICE_RECOGNIZE_ERROR = "com.slumdog88.dictationkeyboardai.ACTION_VOICE_RECOGNIZE_ERROR"
        const val ACTION_VOICE_RECOGNIZE_AMPLITUDE = "com.slumdog88.dictationkeyboardai.ACTION_VOICE_RECOGNIZE_AMPLITUDE"
        const val ACTION_VOICE_RECOGNIZE_PROCESSING = "com.slumdog88.dictationkeyboardai.ACTION_VOICE_RECOGNIZE_PROCESSING"
    }

    // UI state
    private var state by mutableStateOf(VoiceRecognizerState.LISTENING)
    private var amplitude by mutableIntStateOf(0)
    private var errorMessage by mutableStateOf<String?>(null)

    // Track if we've already sent a stop command to prevent duplicate stops
    private var hasStopBeenSent = false
    // Track if we're in the process of ending (cancel or submit)
    private var isEnding = false

    // Permission launcher
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            state = VoiceRecognizerState.ERROR
            errorMessage = "Microphone permission is required for voice input"
        }
    }

    // Broadcast receiver for results from BubbleOverlayService
    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Ignore late results if we're already ending (cancelled)
            if (isEnding) {
                Log.d(TAG, "Ignoring broadcast, activity is ending")
                return
            }
            when (intent?.action) {
                ACTION_VOICE_RECOGNIZE_RESULT -> {
                    val text = intent.getStringExtra("text")
                    Log.d(TAG, "Received transcription result: $text")
                    if (!text.isNullOrBlank()) {
                        sendResultAndFinish(text)
                    } else {
                        state = VoiceRecognizerState.ERROR
                        errorMessage = "No speech detected"
                    }
                }
                ACTION_VOICE_RECOGNIZE_ERROR -> {
                    val error = intent.getStringExtra("error") ?: "Unknown error"
                    Log.e(TAG, "Received error: $error")
                    state = VoiceRecognizerState.ERROR
                    errorMessage = error
                }
                ACTION_VOICE_RECOGNIZE_AMPLITUDE -> {
                    val amp = intent.getIntExtra("amplitude", 0)
                    amplitude = amp
                }
                ACTION_VOICE_RECOGNIZE_PROCESSING -> {
                    val isProcessing = intent.getBooleanExtra("isProcessing", false)
                    if (isProcessing) {
                        state = VoiceRecognizerState.PROCESSING
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate - Intent action: ${intent?.action}")

        // Enable edge-to-edge for proper bottom bar padding
        enableEdgeToEdge()

        // Keep screen on during recognition
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AppTheme {
                VoiceRecognizerScreen(
                    state = state,
                    amplitude = amplitude,
                    errorMessage = errorMessage,
                    onCancel = { cancelAndFinish() },
                    onStop = { stopAndSubmit() },
                    onRetry = if (state == VoiceRecognizerState.ERROR) {
                        { retryRecognition() }
                    } else null
                )
            }
        }

        // Register broadcast receiver
        registerResultReceiver()

        // Check permission and start
        if (hasMicPermission()) {
            startVoiceRecognition()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterResultReceiver()
        // Only stop if we haven't already sent a stop command
        // Always cancel on destroy unless we explicitly submitted
        if (!hasStopBeenSent && state == VoiceRecognizerState.LISTENING) {
            stopVoiceRecognition(cancel = true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Don't call super - it would finish the activity before we set the result
        cancelAndFinish()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerResultReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_VOICE_RECOGNIZE_RESULT)
            addAction(ACTION_VOICE_RECOGNIZE_ERROR)
            addAction(ACTION_VOICE_RECOGNIZE_AMPLITUDE)
            addAction(ACTION_VOICE_RECOGNIZE_PROCESSING)
        }
        ContextCompat.registerReceiver(
            this,
            resultReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "Result receiver registered")
    }

    private fun unregisterResultReceiver() {
        try {
            unregisterReceiver(resultReceiver)
            Log.d(TAG, "Result receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver", e)
        }
    }

    private fun startVoiceRecognition() {
        Log.d(TAG, "Starting voice recognition")
        state = VoiceRecognizerState.LISTENING
        errorMessage = null
        amplitude = 0
        hasStopBeenSent = false
        isEnding = false

        // Start BubbleOverlayService in voice recognize mode
        val serviceIntent = Intent(this, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_START_VOICE_RECOGNIZE
        }
        startService(serviceIntent)
    }

    private fun stopVoiceRecognition(cancel: Boolean) {
        if (hasStopBeenSent) {
            Log.d(TAG, "Stop already sent, ignoring duplicate")
            return
        }
        hasStopBeenSent = true
        Log.d(TAG, "Stopping voice recognition, cancel=$cancel")
        val serviceIntent = Intent(this, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_STOP_VOICE_RECOGNIZE
            putExtra("cancel", cancel)
        }
        startService(serviceIntent)
    }

    private fun retryRecognition() {
        startVoiceRecognition()
    }

    private fun stopAndSubmit() {
        Log.d(TAG, "Stopping recording and submitting")
        state = VoiceRecognizerState.PROCESSING
        stopVoiceRecognition(cancel = false)
    }

    private fun sendResultAndFinish(result: String) {
        Log.d(TAG, "Sending result: $result")

        val returnIntent = Intent().apply {
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS,
                ArrayList(listOf(result))
            )
            putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1.0f))
        }

        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun cancelAndFinish() {
        if (isEnding) {
            Log.d(TAG, "Already ending, ignoring cancel")
            return
        }
        isEnding = true
        Log.d(TAG, "Cancelling and finishing")
        stopVoiceRecognition(cancel = true)
        setResult(RESULT_CANCELED)
        finish()
    }
}
