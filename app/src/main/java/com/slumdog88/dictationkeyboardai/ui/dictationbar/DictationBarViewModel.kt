package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Sealed class representing the three states of the dynamic button.
 * State is derived from recording state and pending retry status.
 *
 * Priority order:
 * 1. Cancel - shown during recording or processing
 * 2. Retry - shown when transcription failed and retry is pending
 * 3. Undo - default state when idle
 */
sealed class DynamicButtonState {
    object Undo : DynamicButtonState()
    object Cancel : DynamicButtonState()
    data class Retry(val isRetrying: Boolean = false) : DynamicButtonState()
}

/**
 * Entry for the undo stack that captures enough information for "true undo"
 * (restoring previous text, not just deleting inserted text).
 *
 * @property insertedText The transcription text that was inserted
 * @property insertedLength Length of the inserted text (for deletion)
 * @property previousText What was selected/replaced before insert (empty if no selection)
 * @property cursorPosition Cursor position before insert (for restoration)
 */
data class UndoEntry(
    val insertedText: String,
    val insertedLength: Int,
    val previousText: String,
    val cursorPosition: Int
)

/**
 * Simplified state holder for the dictation bar keyboard.
 *
 * This is a plain Kotlin class (NOT an AndroidX ViewModel) that holds observable state
 * for the dictation bar UI. It uses Compose's mutableStateOf for state observation.
 *
 * Initialization: Created via lazy property in DictationImeService, survives view recreation.
 * Cleanup: MUST call cleanup() from DictationImeService.onDestroy() to prevent coroutine leaks.
 *
 * State updates come from:
 * - BubbleOverlayService broadcasts (recording state, amplitude, streaming)
 * - DictationImeService (selection state, AI enabled setting)
 * - User actions (start/stop recording via UI)
 */
class DictationBarViewModel {

    // ============================================
    // PUBLIC OBSERVABLE STATE
    // ============================================

    /** Whether dictation recording is currently active */
    var isRecording by mutableStateOf(false)

    /** Whether transcription processing is in progress */
    var isProcessing by mutableStateOf(false)

    /** Audio amplitude for waveform visualization (0-32767) */
    var currentAmplitude by mutableStateOf(0)

    /** Whether Soniox streaming mode is active */
    var isStreamingMode by mutableStateOf(false)
        private set

    /** Current streaming transcription text */
    var streamingText by mutableStateOf("")
        private set

    /** Status message like "Listening...", "Finalizing..." */
    var streamingStatus by mutableStateOf("")
        private set

    /** Whether AI post-processing is enabled */
    var isAiEnabled by mutableStateOf(false)

    /** Quick toggle state for typed sentence-end autocorrect */
    var isTypedSentenceEndAutocorrectEnabled by mutableStateOf(false)

    /** Quick toggle state for including screen context in dictation */
    var isScreenContextEnabled by mutableStateOf(false)

    /** Whether text is selected in current field */
    var hasSelection by mutableStateOf(false)

    /** Whether undo is available (has history) */
    var canUndo by mutableStateOf(false)
        private set

    /** Whether hold-to-talk is currently active (finger down > 300ms) */
    var isHolding by mutableStateOf(false)
        private set

    /** Last transcription text for paste functionality */
    var lastTranscription: String? = null
        private set

    /** Current EditorInfo imeOptions for Return button morphing */
    var imeOptions by mutableStateOf(0)
        private set

    /** Current EditorInfo inputType for numeric field detection */
    var inputType by mutableStateOf(0)
        private set

    /**
     * Whether the current field expects numeric input.
     * True for: TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE, TYPE_CLASS_DATETIME
     * This enables automatic number row display for PIN fields, phone numbers, etc.
     */
    val isNumericInput: Boolean
        get() {
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            return inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
                   inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
                   inputClass == android.text.InputType.TYPE_CLASS_DATETIME
        }

    /**
     * Whether the current field expects email or URL input.
     * True for: TYPE_TEXT_VARIATION_EMAIL_ADDRESS, TYPE_TEXT_VARIATION_URI,
     *           TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
     * This enables automatic email/URL row display for quick character access.
     */
    val isEmailOrUrlInput: Boolean
        get() {
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            val inputVariation = inputType and android.text.InputType.TYPE_MASK_VARIATION

            // Must be text class
            if (inputClass != android.text.InputType.TYPE_CLASS_TEXT) return false

            return inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                   inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_URI ||
                   inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        }

    /** Whether the manual number row is visible (toggled by long-press on special char key) */
    var isManualNumberRowVisible by mutableStateOf(false)
        private set

    /**
     * Toggle the manual number row visibility.
     * Called on long-press of the special character key.
     */
    fun toggleManualNumberRow() {
        isManualNumberRowVisible = !isManualNumberRowVisible
    }

    /** Whether the expanded QWERTY keyboard is visible */
    var isKeyboardExpanded by mutableStateOf(false)
        private set

    /**
     * Toggle the expanded keyboard visibility.
     * Called on long-press of the keyboard switch button.
     */
    fun toggleKeyboardExpanded() {
        isKeyboardExpanded = !isKeyboardExpanded
    }

    /**
     * Collapse the expanded keyboard.
     * Called when switching IME or on explicit collapse.
     */
    fun collapseKeyboard() {
        isKeyboardExpanded = false
    }

    /** Whether current field has content (for select all enablement) */
    var fieldHasContent by mutableStateOf(false)

    /** Whether a failed transcription is pending retry */
    var hasPendingRetry by mutableStateOf(false)
        private set

    /** Whether a retry is currently in progress */
    var isRetrying by mutableStateOf(false)
        private set

    /** Path to the last failed audio file for retry */
    var lastFailedAudioPath: String? = null
        private set

    /** Service name from the last failed transcription */
    var lastFailedServiceName: String? = null
        private set

    /**
     * Derived state for the dynamic button (cached via derivedStateOf).
     * Priority: Cancel (during recording) > Retry (pending failure) > Undo (default)
     */
    val dynamicButtonState: DynamicButtonState by derivedStateOf {
        when {
            isRecording || isProcessing -> DynamicButtonState.Cancel
            hasPendingRetry -> DynamicButtonState.Retry(isRetrying = isRetrying)
            else -> DynamicButtonState.Undo
        }
    }

    // ============================================
    // PRIVATE STATE
    // ============================================

    /** Private history stack for multi-level undo (max 10 entries) */
    private val transcriptionHistory = mutableListOf<UndoEntry>()

    /** Timestamp of last user action for intent priority */
    private var lastUserActionTime: Long = 0

    /** Was last user action start (true) or stop (false) */
    private var lastUserActionIsStart: Boolean = false

    /** Whether we're in Smart Stop finalizing state */
    private var isFinalizing: Boolean = false

    /** Timestamp of last amplitude update for throttling */
    private var lastAmplitudeTime: Long = 0

    /** Throttle constant for amplitude updates (ms) */
    private companion object {
        const val AMPLITUDE_THROTTLE_MS = 100L
    }

    // ============================================
    // COROUTINE SCOPE
    // ============================================

    /**
     * CoroutineScope for async operations within this ViewModel.
     * MUST be cancelled in cleanup() to prevent leaks.
     */
    val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ============================================
    // PUBLIC METHODS - Recording Control
    // ============================================

    /**
     * Called when user initiates recording start via UI.
     * Sets user intent priority flag to prevent stale broadcast interference.
     * Clears any pending retry state since user is starting fresh.
     */
    fun startRecording() {
        clearRetryState()  // Clear pending retry on new recording
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = true
        isRecording = true
    }

    /**
     * Called when user initiates recording stop via UI.
     * Sets user intent priority flag to prevent stale broadcast interference.
     */
    fun stopRecording() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = false
        isRecording = false
    }

    // ============================================
    // PUBLIC METHODS - Hold-to-Talk Control
    // ============================================

    /**
     * Called when hold-to-talk gesture activates (300ms threshold reached).
     * Sets isHolding flag and starts recording.
     */
    fun startHolding() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = true
        isHolding = true
        isRecording = true
    }

    /**
     * Called when user releases after hold-to-talk.
     * Clears isHolding flag and stops recording.
     */
    fun stopHolding() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = false
        isHolding = false
        isRecording = false
    }

    // ============================================
    // PUBLIC METHODS - Streaming Control
    // ============================================

    /**
     * Called when streaming session starts.
     * Resets finalizing state, clears text, sets initial status.
     */
    fun startStreaming() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = true
        isFinalizing = false
        isStreamingMode = true
        streamingText = ""
        streamingStatus = "Initializing..."
    }

    /**
     * Smart Stop: Enter finalizing state without immediately closing UI.
     * Waits for service to send final text before closing.
     */
    fun stopStreaming() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = false
        isFinalizing = true
        streamingStatus = "Finalizing..."
        // Do NOT set isStreamingMode = false here - wait for service confirmation
    }

    /**
     * Immediate cancellation: Close streaming UI right away.
     * Use for force-quit scenarios.
     */
    fun cancelStreaming() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = false
        isFinalizing = false
        isStreamingMode = false
        streamingText = ""
        streamingStatus = ""
    }

    /**
     * Update streaming state from BubbleOverlayService broadcast.
     *
     * Uses User Intent Priority pattern (copied from KeyboardViewModel):
     * - If user recently (within 1s) performed an action, ignore contradicting broadcasts
     * - Exception: During finalization, allow updates to capture final text
     *
     * @param isActive Whether streaming is active
     * @param status Status message
     * @param text Current transcription text
     */
    fun setStreamingState(isActive: Boolean, status: String, text: String) {
        // User Intent Priority:
        // If the user recently (within 1s) performed an action (Start/Stop),
        // we ignore service updates that contradict that action.
        // This prevents:
        // 1. Flicker on Stop: Stale "active" update arriving after user stops.
        // 2. Lag on Start: Stale "inactive" update arriving after user starts.

        // EXCEPTION: If we are in "Finalizing" state (Smart Stop), we ALLOW updates
        // even if they are "active", because we are waiting for the tail of the transcript.

        if (!isFinalizing) {
            val timeSinceAction = System.currentTimeMillis() - lastUserActionTime
            if (timeSinceAction < 1000) {
                if (lastUserActionIsStart != isActive) {
                    // Update contradicts recent user action; ignore it.
                    return
                }
            }
        }

        // If we are finalizing, we expect isActive=true until the end.
        // If we receive isActive=false, it means the service is truly done.
        if (isFinalizing && !isActive) {
            isFinalizing = false
        }

        isStreamingMode = isActive
        streamingStatus = if (isFinalizing) "Finalizing..." else status
        streamingText = text
    }

    // ============================================
    // PUBLIC METHODS - Amplitude Updates
    // ============================================

    /**
     * Update amplitude value with throttling to prevent recomposition storm.
     * Only updates if AMPLITUDE_THROTTLE_MS has elapsed since last update.
     *
     * @param amplitude Audio amplitude value (0-32767)
     */
    fun updateAmplitude(amplitude: Int) {
        val now = System.currentTimeMillis()
        if (now - lastAmplitudeTime >= AMPLITUDE_THROTTLE_MS) {
            lastAmplitudeTime = now
            currentAmplitude = amplitude
        }
    }

    // ============================================
    // PUBLIC METHODS - Utility Operations
    // ============================================

    /**
     * Record a transcription for undo and paste.
     * Called when text is successfully inserted.
     * Captures both inserted text and what was replaced for true undo.
     * Limit history to 10 entries to prevent memory issues.
     *
     * @param text The inserted transcription text
     * @param previousText What was selected/replaced (empty if no selection)
     * @param cursorPosition Cursor position before insert
     */
    fun recordTranscription(text: String, previousText: String = "", cursorPosition: Int = 0) {
        if (transcriptionHistory.size >= 10) {
            transcriptionHistory.removeAt(0)  // Drop oldest
        }
        transcriptionHistory.add(UndoEntry(
            insertedText = text,
            insertedLength = text.length,
            previousText = previousText,
            cursorPosition = cursorPosition
        ))
        lastTranscription = text
        canUndo = true

        // Clear retry state when new transcription succeeds
        clearRetryState()
    }

    /**
     * Pop the most recent undo entry.
     * Returns the entry for true undo, or null if history is empty.
     */
    fun popUndoEntry(): UndoEntry? {
        val entry = transcriptionHistory.removeLastOrNull()
        canUndo = transcriptionHistory.isNotEmpty()
        return entry
    }

    /**
     * Pop undo length for deletion.
     * Returns the length to delete, or 0 if no history.
     * @deprecated Use popUndoEntry() for true undo support
     */
    fun popUndoLength(): Int {
        val entry = transcriptionHistory.removeLastOrNull()
        canUndo = transcriptionHistory.isNotEmpty()
        return entry?.insertedLength ?: 0
    }

    /**
     * Clear undo history on field change.
     * Call from onStartInput when not restarting.
     */
    fun clearUndoHistory() {
        transcriptionHistory.clear()
        canUndo = false
    }

    /**
     * Update from EditorInfo for Return button behavior and numeric input detection.
     */
    fun updateEditorInfo(info: android.view.inputmethod.EditorInfo) {
        imeOptions = info.imeOptions
        inputType = info.inputType
    }

    // ============================================
    // PUBLIC METHODS - Retry State Management
    // ============================================

    /**
     * Set pending retry state from transcription failure.
     * Called when BubbleOverlayService broadcasts a failure.
     *
     * @param audioPath Path to the failed audio file
     * @param serviceName Name of the transcription service that failed
     */
    fun setFailedTranscription(audioPath: String, serviceName: String) {
        hasPendingRetry = true
        lastFailedAudioPath = audioPath
        lastFailedServiceName = serviceName
    }

    /**
     * Start a retry attempt.
     * Sets isRetrying to show animation.
     */
    fun startRetry() {
        isRetrying = true
    }

    /**
     * Clear retry state.
     * Called on retry success, new recording start, or keyboard dismiss.
     */
    fun clearRetryState() {
        hasPendingRetry = false
        isRetrying = false
        lastFailedAudioPath = null
        lastFailedServiceName = null
    }

    // ============================================
    // LIFECYCLE - CRITICAL
    // ============================================

    /**
     * Clean up resources when the service is destroyed.
     *
     * MUST be called from DictationImeService.onDestroy() to prevent coroutine leaks.
     * Failure to call this will result in memory leaks.
     */
    fun cleanup() {
        viewModelScope.cancel()
    }
}
