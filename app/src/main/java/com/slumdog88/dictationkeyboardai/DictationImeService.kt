package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.slumdog88.dictationkeyboardai.ai.AIProcessingManager
import com.slumdog88.dictationkeyboardai.network.NetworkManager
import com.slumdog88.dictationkeyboardai.utils.ServiceLifecycleOwner
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import com.slumdog88.dictationkeyboardai.utils.SmartTextInsertionFormatter
import com.slumdog88.dictationkeyboardai.utils.TypedPeriodCorrectionEngine
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardViewModel
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardScreen
import com.slumdog88.dictationkeyboardai.ui.dictationbar.DictationBarViewModel
import com.slumdog88.dictationkeyboardai.ui.dictationbar.StreamingDictationBar
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardTheme
import com.slumdog88.dictationkeyboardai.ui.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Modern Compose-based Dictation Keyboard IME.
 */
class DictationImeService : InputMethodService() {
    companion object {
        private const val KEY_TYPED_SENTENCE_END_AUTOCORRECT = "keyboard_ai_autocorrect_on_sentence_end"
        private const val KEY_TYPED_PERIOD_AUTOCORRECT_LEGACY = "keyboard_ai_autocorrect_on_period"
        private const val KEY_INCLUDE_SCREEN_CONTEXT = "include_screen_context"
        private const val KEY_ENABLE_POSTPROCESS = "enable_postprocess"
        private const val CONTEXT_MAX_CHARS = 4_000
        private const val FALLBACK_SNAPSHOT_WINDOW = 2_048
        private const val EXTRACTED_MAX_CHARS = 10_000
        private const val AI_TIMEOUT_MS = 10_000L
        private const val CHAINED_PUNCTUATION_DEBOUNCE_MS = 250L
    }

    // Just a plain state holder, not an AndroidX ViewModel
    private val keyboardState by lazy { KeyboardViewModel() }

    // Dictation bar state - survives onCreateInputView recreation
    // Will be used by DictationBarUI in Phase 3, wired now for state updates
    private val dictationState by lazy { DictationBarViewModel() }

    private val serviceLifecycleOwner = ServiceLifecycleOwner()

    // --- Manual Recomposer for the IME window ---
    private val imeRecomposerJob = SupervisorJob()
    private val imeRecomposerScope = CoroutineScope(
        imeRecomposerJob + AndroidUiDispatcher.Main
    )
    private val imeRecomposer: Recomposer by lazy {
        Recomposer(imeRecomposerScope.coroutineContext)
    }
    private val typedCorrectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val settingsManager by lazy { SettingsManager(applicationContext) }
    private val networkManager by lazy { NetworkManager() }
    private val secureApiKeyManager by lazy { SecureApiKeyManager.getInstance(applicationContext) }
    private val aiProcessingManager by lazy {
        AIProcessingManager(applicationContext, networkManager, settingsManager, secureApiKeyManager)
    }

    private data class TypedCorrectionRequest(
        val originalRawSegment: String,
        val originalStartHint: Int,
        val leadingWhitespace: String,
        val transcription: String,
        val contextPayload: String,
        val appContext: String,
        val sourceConnectionId: Int,
        val sourceInputSignature: InputSignature
    )

    private data class InputSignature(
        val packageName: String?,
        val fieldId: Int,
        val inputType: Int,
        val imeOptions: Int
    )

    @Volatile
    private var lastKnownHasSelection: Boolean = false

    override fun onCreate() {
        // Theme must be set before super.onCreate()
        setTheme(R.style.Theme_DictationKeyboardAI)
        super.onCreate()
        ThemeManager.load(this)
        serviceLifecycleOwner.onCreate()

        // Configure window to span full width in all orientations (fixes landscape gaps)
        window?.window?.let { w ->
            // Set width to match parent (full screen width)
            w.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            // Set gravity to bottom with horizontal fill (prevents centering)
            w.setGravity(android.view.Gravity.BOTTOM or android.view.Gravity.FILL_HORIZONTAL)

            // Configure window to render into display cutout areas
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                w.attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        keyboardState.loadKeyboardLayouts(applicationContext)

        // Register receivers for text insertion and undo
        registerImeInsertReceiver()
        registerImeUndoReceiver()

        // Combined state/streaming receivers (updates both keyboardState and dictationState)
        registerCombinedStateReceiver()
        registerCombinedStreamingReceiver()

        // Register failure receiver for Dynamic Button (Phase 10)
        registerTranscriptionFailureReceiver()

        // Start the manual Recomposer loop
        imeRecomposerScope.launch {
            imeRecomposer.runRecomposeAndApplyChanges()
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }


    override fun onCreateInputView(): View {
        android.util.Log.d("DictationImeService", "onCreateInputView called")
        android.util.Log.d("IME_VALIDATION", "onCreateInputView called - view recreated")

        return try {
            // Explicit root container so we control the view tree root
            val root = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                // Attach lifecycle/saved-state owners to the *root* of the IME window
                setViewTreeLifecycleOwner(serviceLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
            }

            val composeView = ComposeView(this).apply {
                // Use a disposal strategy that does NOT depend on a LifecycleOwner
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnDetachedFromWindow
                )

                // Make this ComposeView use our manual Recomposer instead of
                // creating/looking up a WindowRecomposer.
                setParentCompositionContext(imeRecomposer)

                setContent {
                    // Wrap in KeyboardTheme to provide proper colors based on user's theme preference
                    KeyboardTheme {
                        StreamingDictationBar(
                        viewModel = dictationState,
                        keyboardViewModel = keyboardState,
                        onRecordToggle = { shouldRecord ->
                            val intent = android.content.Intent(
                                this@DictationImeService,
                                BubbleOverlayService::class.java
                            )
                            if (shouldRecord) {
                                dictationState.startRecording()
                                intent.action = BubbleOverlayService.ACTION_START_DICTATION
                                // Capture selected text if available
                                val selectedText = currentInputConnection?.getSelectedText(0)?.toString()
                                if (!selectedText.isNullOrBlank()) {
                                    intent.putExtra("ime_selected_text", selectedText)
                                }
                            } else {
                                dictationState.stopRecording()
                                intent.action = BubbleOverlayService.ACTION_STOP_DICTATION
                            }
                            startService(intent)
                        },
                        onStreamingStart = {
                            // Double tap: start Soniox streaming mode
                            dictationState.startStreaming()
                            val intent = android.content.Intent(
                                this@DictationImeService,
                                BubbleOverlayService::class.java
                            ).apply {
                                action = BubbleOverlayService.ACTION_START_DICTATION
                                putExtra("force_streaming", true)
                                putExtra("ui_mode", "keyboard")
                                // Capture selected text if available
                                currentInputConnection?.getSelectedText(0)?.toString()?.let { selectedText ->
                                    if (selectedText.isNotBlank()) {
                                        putExtra("ime_selected_text", selectedText)
                                    }
                                }
                            }
                            startService(intent)
                        },
                        onStreamingCancel = {
                            // Cancel streaming without inserting text
                            dictationState.cancelStreaming()
                            val intent = android.content.Intent(
                                this@DictationImeService,
                                BubbleOverlayService::class.java
                            ).apply {
                                action = BubbleOverlayService.ACTION_STOP_DICTATION
                            }
                            startService(intent)
                        },
                        onMicHoldStart = {
                            // Hold-to-talk: start recording on 300ms threshold
                            dictationState.startHolding()
                            val intent = android.content.Intent(
                                this@DictationImeService,
                                BubbleOverlayService::class.java
                            ).apply {
                                action = BubbleOverlayService.ACTION_START_DICTATION
                                // Capture selected text if available
                                currentInputConnection?.getSelectedText(0)?.toString()?.let { selectedText ->
                                    if (selectedText.isNotBlank()) {
                                        putExtra("ime_selected_text", selectedText)
                                    }
                                }
                            }
                            startService(intent)
                        },
                        onMicHoldEnd = {
                            // Hold-to-talk: stop recording on release
                            dictationState.stopHolding()
                            val intent = android.content.Intent(
                                this@DictationImeService,
                                BubbleOverlayService::class.java
                            ).apply {
                                action = BubbleOverlayService.ACTION_STOP_DICTATION
                            }
                            startService(intent)
                        },
                        onSwitchKeyboard = {
                            switchToNextInputMethod()
                        },
                        onSelectAll = {
                            val ic = currentInputConnection
                            if (ic == null) {
                                android.util.Log.w("DictationImeService", "InputConnection null for selectAll")
                                return@StreamingDictationBar
                            }
                            ic.performContextMenuAction(android.R.id.selectAll)
                        },
                        onReprocess = {
                            // Reprocess selected text through AI
                            val selectedText = currentInputConnection?.getSelectedText(0)?.toString()
                            if (!selectedText.isNullOrBlank()) {
                                // Send to BubbleOverlayService for AI processing
                                val intent = android.content.Intent(
                                    this@DictationImeService,
                                    BubbleOverlayService::class.java
                                ).apply {
                                    action = BubbleOverlayService.ACTION_REPROCESS_TEXT
                                    putExtra("text_to_process", selectedText)
                                }
                                startService(intent)
                                android.util.Log.d("DictationImeService", "Reprocess requested for: ${selectedText.take(20)}...")
                            } else {
                                // No text selected - provide haptic feedback
                                HapticUtils.performGesturalFeedback(this@DictationImeService)
                                android.util.Log.d("DictationImeService", "Reprocess requested but no text selected")
                            }
                        },
                        onAiToggle = {
                            // Toggle AI enabled state
                            dictationState.isAiEnabled = !dictationState.isAiEnabled
                            // Persist preference
                            getSharedPreferences("app_settings", MODE_PRIVATE)
                                .edit()
                                .putBoolean(KEY_ENABLE_POSTPROCESS, dictationState.isAiEnabled)
                                .apply()
                        },
                        onTypedSentenceEndAutocorrectToggle = { enabled ->
                            setTypedSentenceEndAutocorrectEnabled(enabled)
                        },
                        onScreenContextToggle = { enabled ->
                            setScreenContextEnabled(enabled)
                        },
                        onReturn = {
                            // Perform EditorAction for Search/Go/Next; plain Enter for everything else
                            val imeAction = dictationState.imeOptions and EditorInfo.IME_MASK_ACTION
                            when (imeAction) {
                                EditorInfo.IME_ACTION_SEARCH,
                                EditorInfo.IME_ACTION_GO,
                                EditorInfo.IME_ACTION_NEXT -> {
                                    val ic = currentInputConnection
                                    if (ic != null) {
                                        ic.performEditorAction(imeAction)
                                    } else {
                                        android.util.Log.w("DictationImeService", "InputConnection null for performEditorAction")
                                        sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
                                    }
                                }
                                else -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
                            }
                        },
                        onPasteLast = {
                            val lastText = dictationState.lastTranscription
                            if (!lastText.isNullOrBlank()) {
                                val ic = currentInputConnection
                                if (ic == null) {
                                    android.util.Log.w("DictationImeService", "InputConnection null for pasteLast")
                                    return@StreamingDictationBar
                                }
                                ic.beginBatchEdit()
                                try {
                                    ic.commitText(lastText, 1)
                                } finally {
                                    ic.endBatchEdit()
                                }
                                dictationState.recordTranscription(lastText)
                            } else {
                                HapticUtils.performGesturalFeedback(this@DictationImeService)
                            }
                        },
                        onBackspace = {
                            val ic = currentInputConnection
                            if (ic == null) {
                                android.util.Log.w("DictationImeService", "InputConnection null for backspace")
                                return@StreamingDictationBar
                            }
                            // Delete single character or selected text
                            val selectedText = ic.getSelectedText(0)
                            if (selectedText.isNullOrEmpty()) {
                                ic.deleteSurroundingText(1, 0)
                            } else {
                                ic.commitText("", 1)
                            }
                        },
                        onBackspaceWord = {
                            val ic = currentInputConnection
                            if (ic == null) {
                                android.util.Log.w("DictationImeService", "InputConnection null for backspaceWord")
                                return@StreamingDictationBar
                            }

                            ic.beginBatchEdit()
                            try {
                                val selectedText = ic.getSelectedText(0)
                                if (!selectedText.isNullOrEmpty()) {
                                    ic.commitText("", 1)
                                    return@StreamingDictationBar
                                }

                                val textBefore = ic.getTextBeforeCursor(50, 0)
                                if (textBefore.isNullOrEmpty()) return@StreamingDictationBar

                                val text = textBefore.toString()
                                val bi = java.text.BreakIterator.getWordInstance()
                                bi.setText(text)

                                var pos = text.length
                                while (pos > 0) {
                                    val prev = bi.preceding(pos)
                                    if (prev == java.text.BreakIterator.DONE) {
                                        pos = 0
                                        break
                                    }
                                    val segment = text.substring(prev, pos)
                                    pos = prev
                                    if (segment.any { it.isLetterOrDigit() }) break
                                }

                                val wordLength = text.length - pos
                                if (wordLength > 0) {
                                    ic.deleteSurroundingText(wordLength, 0)
                                }
                            } finally {
                                ic.endBatchEdit()
                            }
                        },
                        onUndo = {
                            // True undo: delete inserted text and restore previous text
                            performTrueUndo()
                        },
                        onCancelRecording = {
                            // Cancel current recording without transcription
                            cancelRecording()
                        },
                        onRetry = {
                            // Retry last failed transcription
                            triggerRetry()
                        },
                        onInsertCharacter = { char ->
                            commitTypedCharacter(char)
                        },
                        onKeyboardLongPress = {
                            // Toggle expanded keyboard
                            dictationState.toggleKeyboardExpanded()
                        }
                    )
                    } // End KeyboardTheme
                }
            }

            root.addView(
                composeView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            root
        } catch (e: Exception) {
            android.util.Log.e("DictationImeService", "Error creating Compose Input View", e)

            // Simple fallback to a TextView
            android.widget.TextView(this).apply {
                text = "ERROR LOADING KEYBOARD\n${e.message}"
                textSize = 16f
                setTextColor(android.graphics.Color.RED)
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    500
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        keyboardState.isVisible = true
        keyboardState.setInputConnection(currentInputConnection)
        keyboardState.setCharacterCommitInterceptor(::commitTypedCharacter)
        keyboardState.reloadSettings()
        if (info != null) {
            keyboardState.updateImeOptions(info.imeOptions)
            // Also update dictationState for Phase 5 utility features
            dictationState.updateEditorInfo(info)
        }

        // State reconciliation: Query BubbleOverlayService for current recording state
        // This prevents desync when IME is recreated while recording is active (P4 pitfall fix)
        BubbleOverlayService.getInstance()?.let { service ->
            val isCurrentlyRecording = service.isCurrentlyRecording()
            if (keyboardState.isRecording != isCurrentlyRecording) {
                keyboardState.isRecording = isCurrentlyRecording
            }
            // Also reconcile dictationState for Phase 3 DictationBarUI
            if (dictationState.isRecording != isCurrentlyRecording) {
                dictationState.isRecording = isCurrentlyRecording
            }
        }

        // Load AI enabled setting for dictationState
        dictationState.isAiEnabled = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean(KEY_ENABLE_POSTPROCESS, false)
        dictationState.isTypedSentenceEndAutocorrectEnabled = isTypedSentenceEndAutocorrectEnabled()
        dictationState.isScreenContextEnabled = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean(KEY_INCLUDE_SCREEN_CONTEXT, false)

        android.util.Log.d("IME_VALIDATION", "onStartInputView - reconciled state: isRecording=${keyboardState.isRecording}")
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardState.isVisible = false
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Clear undo history on new field (but not when restarting same field)
        if (!restarting) {
            dictationState.clearUndoHistory()
        }

        keyboardState.setInputConnection(currentInputConnection)
        if (attribute != null) {
            keyboardState.updateEditorInfo(attribute)
        }
        DictationAccessibilityService.setImeActive(true)

        try {
            val intent = android.content.Intent("com.slumdog88.dictationkeyboardai.ACTION_IME_ACTIVE")
            intent.setPackage(packageName)
            sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        keyboardState.setInputConnection(null)
        keyboardState.setCharacterCommitInterceptor(null)
        lastKnownHasSelection = false
        DictationAccessibilityService.setImeActive(false)

        // Clear retry state when keyboard is dismissed (Phase 10)
        dictationState.clearRetryState()

        try {
            val intent = android.content.Intent("com.slumdog88.dictationkeyboardai.ACTION_IME_INACTIVE")
            intent.setPackage(packageName)
            sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val hasSelection = (newSelEnd - newSelStart) != 0
        lastKnownHasSelection = hasSelection
        keyboardState.updateSelectionState(hasSelection)
        // Also update dictationState for Phase 3 DictationBarUI
        dictationState.hasSelection = hasSelection

        // Track if field has content for Select All enablement
        // Use a heuristic: if cursor position is > 0 or selection exists, assume content
        dictationState.fieldHasContent = newSelEnd > 0 || hasSelection
    }

    /**
     * Switch to the next available input method.
     * Shows input method picker to allow user to choose different keyboard.
     */
    private fun switchToNextInputMethod() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = window?.window?.attributes?.token
        if (token != null) {
            // Try to switch to next IME directly, or show picker if not possible
            @Suppress("DEPRECATION")
            imm?.switchToNextInputMethod(token, false)
        } else {
            // Fallback: show input method picker
            imm?.showInputMethodPicker()
        }
    }

    // ============================================
    // Dynamic Button Actions (Phase 10)
    // ============================================

    /**
     * Perform true undo - delete inserted text and restore previous text.
     * Uses UndoEntry from dictationState to restore previous selection.
     */
    private fun performTrueUndo() {
        val ic = currentInputConnection ?: return
        val entry = dictationState.popUndoEntry() ?: return

        ic.beginBatchEdit()
        try {
            // Verify text at cursor matches what we inserted before deleting
            val textBeforeCursor = ic.getTextBeforeCursor(entry.insertedLength, 0)?.toString() ?: ""
            if (textBeforeCursor != entry.insertedText) {
                android.util.Log.w("DictationImeService",
                    "Undo verification failed: expected '${entry.insertedText.take(20)}' but found '${textBeforeCursor.take(20)}'. Falling back to system undo.")
                ic.performContextMenuAction(android.R.id.undo)
                return
            }

            ic.deleteSurroundingText(entry.insertedLength, 0)

            if (entry.previousText.isNotEmpty()) {
                ic.commitText(entry.previousText, 1)
            }
        } finally {
            ic.endBatchEdit()
        }

        android.util.Log.d("DictationImeService", "True undo: removed ${entry.insertedLength} chars, restored '${entry.previousText.take(20)}'")
    }

    /**
     * Cancel current recording without transcription.
     * Sends stop signal to BubbleOverlayService with cancelled flag.
     */
    private fun cancelRecording() {
        dictationState.stopRecording()
        val intent = android.content.Intent(
            this@DictationImeService,
            BubbleOverlayService::class.java
        ).apply {
            action = BubbleOverlayService.ACTION_STOP_DICTATION
            putExtra("cancelled", true)  // Signal to discard, not transcribe
        }
        startService(intent)
        android.util.Log.d("DictationImeService", "Recording cancelled")
    }

    /**
     * Trigger retry for the last failed transcription.
     * Sends broadcast to BubbleOverlayService with audio path.
     */
    private fun triggerRetry() {
        val audioPath = dictationState.lastFailedAudioPath
        if (audioPath == null) {
            android.util.Log.w("DictationImeService", "No audio path for retry")
            return
        }

        dictationState.startRetry()

        val intent = android.content.Intent(BubbleOverlayService.ACTION_RETRY_TRANSCRIPTION).apply {
            setPackage(packageName)
            putExtra("audio_file_path", audioPath)
        }
        sendBroadcast(intent)
        android.util.Log.d("DictationImeService", "Triggered retry for: $audioPath")
    }

    override fun onDestroy() {
        // Gracefully drain pending compositions before cancelling the scope
        imeRecomposer.close()
        imeRecomposerScope.cancel()
        typedCorrectionScope.cancel()
        keyboardState.setCharacterCommitInterceptor(null)

        // Clean up ViewModel coroutine scopes to prevent leaks (CRITICAL)
        keyboardState.cleanup()
        dictationState.cleanup()

        super.onDestroy()
        serviceLifecycleOwner.onDestroy()

        // Unregister receivers
        unregisterImeInsertReceiver()
        unregisterImeUndoReceiver()
        unregisterCombinedStateReceiver()
        unregisterCombinedStreamingReceiver()

        // Unregister failure receiver (Phase 10)
        unregisterTranscriptionFailureReceiver()

        DictationAccessibilityService.setImeActive(false)
    }

    private fun commitTypedCharacter(text: String) {
        val ic = currentInputConnection
        if (ic == null) {
            android.util.Log.w("DictationImeService", "InputConnection null for typed character commit")
            return
        }

        val hadSelectionBeforeCommit = hasActiveSelectionBeforeCommit(ic)
        ic.commitText(text, 1)

        val inputType = currentInputEditorInfo?.inputType ?: 0
        if (!DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = isTypedSentenceEndAutocorrectEnabled(),
                insertedText = text,
                hadSelectionBeforeCommit = hadSelectionBeforeCommit,
                inputType = inputType
            )
        ) {
            return
        }

        val snapshot = collectEditorSnapshot(ic) ?: return
        val cursorAfterPeriod = snapshot.selectionEnd.coerceIn(0, snapshot.text.length)
        val target = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, cursorAfterPeriod) ?: return

        val request = TypedCorrectionRequest(
            originalRawSegment = target.rawSegment,
            originalStartHint = target.start,
            leadingWhitespace = target.leadingWhitespace,
            transcription = target.trimmedSegment,
            contextPayload = TypedPeriodCorrectionEngine.buildContextPayload(snapshot.text, CONTEXT_MAX_CHARS),
            appContext = resolveCurrentAppContext(),
            sourceConnectionId = System.identityHashCode(ic),
            sourceInputSignature = currentInputSignature()
        )

        typedCorrectionScope.launch {
            processTypedCorrection(request)
        }
    }

    private suspend fun processTypedCorrection(request: TypedCorrectionRequest) {
        delay(CHAINED_PUNCTUATION_DEBOUNCE_MS)
        if (!isSameInputTarget(request)) return

        val preflightIc = currentInputConnection ?: return
        val preflightSnapshot = collectEditorSnapshot(preflightIc) ?: return
        val preflightRange = TypedPeriodCorrectionEngine.findExactReplacementRange(
            currentText = preflightSnapshot.text,
            originalRawSegment = request.originalRawSegment,
            originalStartHint = request.originalStartHint
        ) ?: return
        val endExclusive = preflightRange.last + 1
        val nextChar = preflightSnapshot.text.getOrNull(endExclusive)
        if (DictationImePeriodAutocorrectPolicy.shouldSkipForChainedSentenceEnders(
                rawSegment = request.originalRawSegment,
                nextChar = nextChar
            )
        ) {
            return
        }

        val processedText = withContext(Dispatchers.IO) {
            withTimeoutOrNull(AI_TIMEOUT_MS) {
                aiProcessingManager.processWithAI(
                    transcription = request.transcription,
                    context = request.contextPayload,
                    screenContext = "",
                    currentAppContext = request.appContext,
                    isCommandMode = false
                )
            }
        }

        if (processedText.isNullOrBlank() ||
            processedText.startsWith("Processing failed") ||
            processedText.startsWith("Error:")
        ) {
            return
        }

        if (!isSameInputTarget(request)) return
        val ic = currentInputConnection ?: return
        val latestSnapshot = collectEditorSnapshot(ic) ?: return
        val range = TypedPeriodCorrectionEngine.findExactReplacementRange(
            currentText = latestSnapshot.text,
            originalRawSegment = request.originalRawSegment,
            originalStartHint = request.originalStartHint
        ) ?: return

        val replacementText = request.leadingWhitespace + processedText.trim()
        val replaceStart = range.first
        val replaceEndExclusive = range.last + 1
        if (replaceStart < 0 || replaceEndExclusive > latestSnapshot.text.length || replaceEndExclusive <= replaceStart) {
            return
        }

        val (newSelStartRaw, newSelEndRaw) = DictationImePeriodAutocorrectPolicy.adjustSelectionAfterReplacement(
            selectionStart = latestSnapshot.selectionStart,
            selectionEnd = latestSnapshot.selectionEnd,
            replaceStart = replaceStart,
            replaceEndExclusive = replaceEndExclusive,
            replacementLength = replacementText.length
        )

        val newTextLength = latestSnapshot.text.length - (replaceEndExclusive - replaceStart) + replacementText.length
        val newSelStart = newSelStartRaw.coerceIn(0, newTextLength)
        val newSelEnd = newSelEndRaw.coerceIn(0, newTextLength)

        ic.beginBatchEdit()
        try {
            ic.setSelection(replaceStart, replaceEndExclusive)
            ic.commitText(replacementText, 1)
            ic.setSelection(newSelStart, newSelEnd)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun isSameInputTarget(request: TypedCorrectionRequest): Boolean {
        val ic = currentInputConnection ?: return false
        if (System.identityHashCode(ic) != request.sourceConnectionId) return false
        return currentInputSignature() == request.sourceInputSignature
    }

    private fun currentInputSignature(): InputSignature {
        val info = currentInputEditorInfo
        return InputSignature(
            packageName = info?.packageName,
            fieldId = info?.fieldId ?: -1,
            inputType = info?.inputType ?: 0,
            imeOptions = info?.imeOptions ?: 0
        )
    }

    private fun isTypedSentenceEndAutocorrectEnabled(): Boolean {
        val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        if (prefs.contains(KEY_TYPED_SENTENCE_END_AUTOCORRECT)) {
            return prefs.getBoolean(KEY_TYPED_SENTENCE_END_AUTOCORRECT, false)
        }

        if (!prefs.contains(KEY_TYPED_PERIOD_AUTOCORRECT_LEGACY)) {
            return false
        }

        val migratedValue = prefs.getBoolean(KEY_TYPED_PERIOD_AUTOCORRECT_LEGACY, false)
        prefs.edit()
            .putBoolean(KEY_TYPED_SENTENCE_END_AUTOCORRECT, migratedValue)
            .remove(KEY_TYPED_PERIOD_AUTOCORRECT_LEGACY)
            .apply()
        return migratedValue
    }

    private fun setTypedSentenceEndAutocorrectEnabled(enabled: Boolean) {
        dictationState.isTypedSentenceEndAutocorrectEnabled = enabled
        getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TYPED_SENTENCE_END_AUTOCORRECT, enabled)
            .remove(KEY_TYPED_PERIOD_AUTOCORRECT_LEGACY)
            .apply()
    }

    private fun setScreenContextEnabled(enabled: Boolean) {
        dictationState.isScreenContextEnabled = enabled
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INCLUDE_SCREEN_CONTEXT, enabled)
            .apply()
    }

    private fun hasActiveSelectionBeforeCommit(ic: InputConnection): Boolean {
        if (lastKnownHasSelection) return true

        val selectedText = try {
            ic.getSelectedText(0)
        } catch (_: Exception) {
            null
        }
        if (!selectedText.isNullOrEmpty()) return true

        val extracted = try {
            ic.getExtractedText(ExtractedTextRequest(), 0)
        } catch (_: Exception) {
            null
        } ?: return false

        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        return start >= 0 && end >= 0 && start != end
    }

    private fun collectEditorSnapshot(ic: InputConnection): TypedPeriodCorrectionEngine.EditorSnapshot? {
        val fromExtracted = collectEditorSnapshotFromExtractedText(ic)
        if (fromExtracted != null) return fromExtracted
        return collectEditorSnapshotFromCursorBuffers(ic)
    }

    private fun collectEditorSnapshotFromExtractedText(ic: InputConnection): TypedPeriodCorrectionEngine.EditorSnapshot? {
        return try {
            val request = ExtractedTextRequest().apply {
                hintMaxChars = EXTRACTED_MAX_CHARS
                hintMaxLines = 256
            }
            val extracted = ic.getExtractedText(request, 0) ?: return null
            val text = extracted.text?.toString() ?: return null
            if (extracted.selectionStart < 0 || extracted.selectionEnd < 0) return null
            val startOffset = extracted.startOffset.coerceAtLeast(0)

            val selectionStart = normalizeExtractedSelection(extracted.selectionStart, startOffset, text.length)
            val selectionEnd = normalizeExtractedSelection(extracted.selectionEnd, startOffset, text.length)
            TypedPeriodCorrectionEngine.EditorSnapshot(text, selectionStart, selectionEnd)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeExtractedSelection(selection: Int, startOffset: Int, textLength: Int): Int {
        val relative = selection - startOffset
        return when {
            relative in 0..textLength -> relative
            selection in 0..textLength -> selection
            else -> selection.coerceIn(0, textLength)
        }
    }

    private fun collectEditorSnapshotFromCursorBuffers(ic: InputConnection): TypedPeriodCorrectionEngine.EditorSnapshot? {
        return try {
            val before = ic.getTextBeforeCursor(FALLBACK_SNAPSHOT_WINDOW, 0)?.toString().orEmpty()
            val after = ic.getTextAfterCursor(FALLBACK_SNAPSHOT_WINDOW, 0)?.toString().orEmpty()
            val text = before + after
            val cursor = before.length
            TypedPeriodCorrectionEngine.EditorSnapshot(
                text = text,
                selectionStart = cursor,
                selectionEnd = cursor
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCurrentAppContext(): String {
        val packageName = currentInputEditorInfo?.packageName ?: return ""
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private var lastInsertedLength: Int = 0

    // --- Receivers ---

    private val imeInsertReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.slumdog88.dictationkeyboardai.ACTION_IME_INSERT") {
                val text = intent.getStringExtra("text")
                if (!text.isNullOrBlank()) {
                    val ic = currentInputConnection

                    ic?.beginBatchEdit()
                    try {
                        val selectedText = ic?.getSelectedText(0)?.toString() ?: ""

                        val textToInsert = if (ic != null) {
                            val before = ic.getTextBeforeCursor(64, 0)
                            val after = ic.getTextAfterCursor(64, 0)
                            val insertionContext = SmartTextInsertionFormatter.contextFrom(before, after)
                            SmartTextInsertionFormatter.format(text, insertionContext)
                        } else {
                            text
                        }

                        ic?.commitText(textToInsert, 1)
                        lastInsertedLength = textToInsert.length
                        keyboardState.lastTranscription = textToInsert

                        dictationState.recordTranscription(
                            text = textToInsert,
                            previousText = selectedText,
                            cursorPosition = 0
                        )

                        android.util.Log.d("DictationImeService", "Inserted text via broadcast: ${textToInsert.take(50)}...")
                    } finally {
                        ic?.endBatchEdit()
                    }
                }
            }
        }
    }

    private val imeUndoReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.slumdog88.dictationkeyboardai.ACTION_IME_UNDO") {
                if (lastInsertedLength > 0) {
                    currentInputConnection?.deleteSurroundingText(lastInsertedLength, 0)
                    lastInsertedLength = 0
                } else {
                    // Fallback to standard undo if no recent insert tracked
                    currentInputConnection?.performContextMenuAction(android.R.id.undo)
                }
            }
        }
    }

    /** Timestamp for receiver-level amplitude throttling (80ms) */
    private var lastReceiverAmplitudeTime: Long = 0

    /**
     * Combined broadcast receiver for recording/amplitude/processing state.
     * Updates both keyboardState and dictationState to eliminate duplicate receivers.
     */
    private val combinedStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                BubbleOverlayService.ACTION_IME_RECORDING_STATE -> {
                    val active = intent.getBooleanExtra("isRecording", false)
                    android.util.Log.d("IME_VALIDATION", "Received recording state: $active, current: ${keyboardState.isRecording}")
                    if (keyboardState.isRecording != active) {
                        keyboardState.isRecording = active
                    }
                    if (dictationState.isRecording != active) {
                        dictationState.isRecording = active
                    }
                }
                BubbleOverlayService.ACTION_AMPLITUDE_UPDATE -> {
                    val now = System.currentTimeMillis()
                    if (now - lastReceiverAmplitudeTime < 80) return
                    lastReceiverAmplitudeTime = now

                    val amp = intent.getIntExtra("amplitude", 0)
                    if (keyboardState.currentAmplitude != amp) {
                        keyboardState.currentAmplitude = amp
                    }
                    dictationState.updateAmplitude(amp)
                }
                BubbleOverlayService.ACTION_PROCESSING_STATE -> {
                    val processing = intent.getBooleanExtra("isProcessing", false)
                    if (keyboardState.isProcessing != processing) {
                        keyboardState.isProcessing = processing
                    }
                    if (dictationState.isProcessing != processing) {
                        dictationState.isProcessing = processing
                    }
                }
            }
        }
    }

    /**
     * Combined broadcast receiver for streaming state.
     * Updates both keyboardState and dictationState.
     */
    private val combinedStreamingReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == BubbleOverlayService.ACTION_STREAMING_UPDATE) {
                val isActive = intent.getBooleanExtra("is_active", false)
                val status = intent.getStringExtra("status") ?: ""
                val text = intent.getStringExtra("text") ?: ""
                android.util.Log.d("IME_VALIDATION", "Streaming update: active=$isActive, status=$status, text length=${text.length}")

                keyboardState.setStreamingState(isActive, status, text)
                dictationState.setStreamingState(isActive, status, text)

                if (!isActive) {
                    keyboardState.isRecording = false
                    dictationState.isRecording = false
                }
            }
        }
    }

    private fun registerImeInsertReceiver() {
        try {
            val filter = android.content.IntentFilter("com.slumdog88.dictationkeyboardai.ACTION_IME_INSERT")
            ContextCompat.registerReceiver(this, imeInsertReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) { }
    }

    private fun unregisterImeInsertReceiver() {
        try { unregisterReceiver(imeInsertReceiver) } catch (_: Exception) { }
    }

    private fun registerImeUndoReceiver() {
        try {
            val filter = android.content.IntentFilter("com.slumdog88.dictationkeyboardai.ACTION_IME_UNDO")
            ContextCompat.registerReceiver(this, imeUndoReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) { }
    }

    private fun unregisterImeUndoReceiver() {
        try { unregisterReceiver(imeUndoReceiver) } catch (_: Exception) { }
    }

    private fun registerCombinedStateReceiver() {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(BubbleOverlayService.ACTION_IME_RECORDING_STATE)
                addAction(BubbleOverlayService.ACTION_AMPLITUDE_UPDATE)
                addAction(BubbleOverlayService.ACTION_PROCESSING_STATE)
            }
            ContextCompat.registerReceiver(this, combinedStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) { }
    }

    private fun unregisterCombinedStateReceiver() {
        try { unregisterReceiver(combinedStateReceiver) } catch (_: Exception) { }
    }

    private fun registerCombinedStreamingReceiver() {
        try {
            val filter = android.content.IntentFilter(BubbleOverlayService.ACTION_STREAMING_UPDATE)
            ContextCompat.registerReceiver(this, combinedStreamingReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) { }
    }

    private fun unregisterCombinedStreamingReceiver() {
        try { unregisterReceiver(combinedStreamingReceiver) } catch (_: Exception) { }
    }

    // ============================================
    // Transcription Failure Receiver (Phase 10 Dynamic Button)
    // ============================================

    /**
     * Broadcast receiver for transcription failure events.
     * Updates dictationState to show retry button in keyboard UI.
     */
    private val transcriptionFailureReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == BubbleOverlayService.ACTION_TRANSCRIPTION_FAILURE) {
                val audioPath = intent.getStringExtra("audio_file_path")
                val serviceName = intent.getStringExtra("service_name") ?: "Unknown"

                if (audioPath != null) {
                    android.util.Log.d("DictationImeService", "Received transcription failure: $audioPath from $serviceName")
                    dictationState.setFailedTranscription(audioPath, serviceName)
                    // Haptic feedback to alert user of failure
                    HapticUtils.performGesturalFeedback(this@DictationImeService)
                }
            }
        }
    }

    private fun registerTranscriptionFailureReceiver() {
        try {
            val filter = android.content.IntentFilter(BubbleOverlayService.ACTION_TRANSCRIPTION_FAILURE)
            ContextCompat.registerReceiver(this, transcriptionFailureReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            android.util.Log.d("DictationImeService", "Transcription failure receiver registered")
        } catch (e: Exception) {
            android.util.Log.e("DictationImeService", "Failed to register failure receiver", e)
        }
    }

    private fun unregisterTranscriptionFailureReceiver() {
        try { unregisterReceiver(transcriptionFailureReceiver) } catch (_: Exception) { }
    }


}
