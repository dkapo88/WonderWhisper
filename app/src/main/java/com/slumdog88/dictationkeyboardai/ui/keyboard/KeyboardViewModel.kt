package com.slumdog88.dictationkeyboardai.ui.keyboard

import android.content.Context
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardLayoutDefinition
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardLayouts
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardLayoutRepository
import com.slumdog88.dictationkeyboardai.ui.keyboard.emoji.EmojiRepository
import com.slumdog88.dictationkeyboardai.ui.keyboard.emoji.EmojiItem
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.ui.keyboard.logic.TextLogic
import android.view.inputmethod.EditorInfo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

data class KeyPreviewState(
    val label: String,
    val icon: Int?,
    val position: Offset,
    val width: Dp,
    val height: Dp,
    val isVisible: Boolean = false
)

/**
 * State holder for full keyboard UI.
 *
 * @deprecated Replaced by DictationBarViewModel in Phase 6. This class is kept dormant
 * to preserve the option to revisit full keyboard functionality. Do not remove.
 * The cleanup() method remains critical for coroutine scope management.
 */
@Deprecated(
    message = "Replaced by DictationBarViewModel. Kept dormant for potential future use.",
    replaceWith = ReplaceWith(
        "DictationBarViewModel()",
        "com.slumdog88.dictationkeyboardai.ui.dictationbar.DictationBarViewModel"
    ),
    level = DeprecationLevel.WARNING
)
class KeyboardViewModel {
    var isExpanded by mutableStateOf(false)
    var isVisible by mutableStateOf(false)
    var isShiftOn by mutableStateOf(false)
        private set
    var isCapsLockOn by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var isProcessing by mutableStateOf(false)
    var currentAmplitude by mutableStateOf(0)
    var isAiEnabled by mutableStateOf(false)
    var isAlwaysStreamingEnabled by mutableStateOf(false)
    var hasSelection by mutableStateOf(false)
    var lastTranscription: String? = null
        set(value) {
            field = value
            prefs()?.edit()?.putString(prefsKeyLastTranscription, value)?.apply()
        }
    var keyboardMode: KeyboardMode by mutableStateOf(KeyboardMode.Alphabet)
        private set
    var imeOptions: Int by mutableStateOf(0)
        private set
    private var editorInfo: EditorInfo? = null
    
    var showNumberRow: Boolean by mutableStateOf(true)
    
    var previewState by mutableStateOf(KeyPreviewState("", null, Offset.Zero, 0.dp, 0.dp))
        private set
    
    var currentAlphabetLayout: KeyboardLayoutDefinition by mutableStateOf(KeyboardLayouts.alphabet)
        private set
    var currentSymbolsLayout: KeyboardLayoutDefinition by mutableStateOf(KeyboardLayouts.symbols)
        private set
    var currentAltSymbolsLayout: KeyboardLayoutDefinition by mutableStateOf(KeyboardLayouts.symbols)
        private set
    var availableLayouts: List<KeyboardLayoutDefinition> by mutableStateOf(emptyList())

    // Emoji State
    var emojiCategories: List<String> by mutableStateOf(emptyList())
        private set
    var selectedEmojiCategory: String by mutableStateOf("")
    var emojiSearchQuery: String by mutableStateOf("")
    var emojiGridItems: List<EmojiItem> by mutableStateOf(emptyList())
        private set
    var isEmojiSearchActive: Boolean by mutableStateOf(false)
    
    // Streaming State
    var isStreamingMode: Boolean by mutableStateOf(false)
        private set // Only allow modification via setStreamingState
    var streamingText: String by mutableStateOf("")
        private set
    var streamingStatus: String by mutableStateOf("")
        private set
    var isFinalizing: Boolean by mutableStateOf(false)
        private set

    private var lastUserActionTime: Long = 0
    private var lastUserActionIsStart: Boolean = false
        
    private var inputConnection: InputConnection? = null
    private var characterCommitInterceptor: ((String) -> Unit)? = null
    private var layoutRepository: KeyboardLayoutRepository? = null
    private var appContext: Context? = null
    
    // Text Logic (Smart Punctuation)
    private val textLogic by lazy { 
        TextLogic(
            inputConnectionProvider = { inputConnection },
            editorInfoProvider = { editorInfo }
        )
    }

    private var previewDismissJob: Job? = null
    private var emojiSearchJob: Job? = null
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val prefsKeyShowNumberRow = "keyboard_show_number_row"
    private val prefsKeyLayout = "keyboard_layout_name"
    private val prefsKeyAiEnabled = "enable_postprocess"
    private val prefsKeyAlwaysStreaming = "always_use_streaming_dictation"
    private val prefsKeyLastTranscription = "last_transcription"
    
    private var cursorDragAccumulator: Float = 0f
    private val CURSOR_DRAG_SENSITIVITY = 15f // Lower = faster

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

    fun stopStreaming() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = false
        
        // Smart Stop: Don't close UI immediately.
        // Enter finalizing state and wait for service to send isActive=false
        // or for new text to arrive.
        isFinalizing = true
        streamingStatus = "Finalizing..."
        
        // We do NOT set isStreamingMode = false here.
        // We let the service update close the UI when it's done.
    }

    fun cancelStreaming() {
        // Immediate stop (Force Quit)
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = false
        isFinalizing = false
        isStreamingMode = false
    }

    fun startStreaming() {
        lastUserActionTime = System.currentTimeMillis()
        lastUserActionIsStart = true
        isFinalizing = false
        
        // Immediately switch UI to streaming mode to prevent lag
        isStreamingMode = true
        streamingText = ""
        streamingStatus = "Initializing..."
    }

    fun setInputConnection(ic: InputConnection?) {
        inputConnection = ic
    }

    fun setCharacterCommitInterceptor(interceptor: ((String) -> Unit)?) {
        characterCommitInterceptor = interceptor
    }

    fun showKeyPreview(label: String, icon: Int?, position: Offset, width: Dp, height: Dp) {
        previewDismissJob?.cancel()
        previewState = KeyPreviewState(
            label = label,
            icon = icon,
            position = position,
            width = width,
            height = height,
            isVisible = true
        )
    }

    fun hideKeyPreview() {
        previewDismissJob?.cancel()
        previewDismissJob = viewModelScope.launch {
            delay(70) // Linger timeout
            previewState = previewState.copy(isVisible = false)
        }
    }

    fun loadKeyboardLayouts(context: Context) {
        if (layoutRepository == null) {
            layoutRepository = KeyboardLayoutRepository(context.applicationContext)
        }
        appContext = context.applicationContext

        val repo = layoutRepository ?: return
        availableLayouts = repo.availableLayouts().filter { it.category == "alphabet" }.ifEmpty { listOf(KeyboardLayouts.alphabet) }
        
        val savedLayoutName = prefs()?.getString(prefsKeyLayout, null)
        val savedLayout = savedLayoutName?.let { repo.getLayout(it) }

        val alphabet = savedLayout ?: repo.defaultAlphabetLayout()
        currentAlphabetLayout = alphabet
        showNumberRow = loadNumberRowPreference(false) // Default to false (hidden) per user request
        isAiEnabled = appSettings()?.getBoolean(prefsKeyAiEnabled, false) ?: false
        isAlwaysStreamingEnabled = appSettings()?.getBoolean(prefsKeyAlwaysStreaming, false) ?: false
        lastTranscription = prefs()?.getString(prefsKeyLastTranscription, null)
        currentSymbolsLayout = repo.defaultSymbolsLayout()
        currentAltSymbolsLayout = repo.defaultAltSymbolsLayout()
        
        // Load Emojis
        loadEmojis()
    }

    private val RECENT_CATEGORY = "Recent"

    private fun loadEmojis() {
        val context = appContext ?: return
        viewModelScope.launch {
            EmojiRepository.load(context)
            // Prepend "Recent" to categories
            val cats = EmojiRepository.getCategories().toMutableList()
            cats.add(0, RECENT_CATEGORY)
            emojiCategories = cats
            
            if (selectedEmojiCategory.isEmpty() && emojiCategories.isNotEmpty()) {
                // Default to first category (Recent or Smileys)
                selectedEmojiCategory = emojiCategories.first()
                updateEmojiGridForCategory(selectedEmojiCategory)
            }
        }
    }

    fun onEmojiCategoryClick(category: String) {
        selectedEmojiCategory = category
        emojiSearchQuery = "" // Clear search
        updateEmojiGridForCategory(category)
    }
    
    private fun updateEmojiGridForCategory(category: String) {
        if (category == RECENT_CATEGORY) {
            emojiGridItems = EmojiRepository.getRecentEmojis()
        } else {
            emojiGridItems = EmojiRepository.getEmojisByCategory(category)
        }
    }

    fun onEmojiSearch(query: String) {
        emojiSearchQuery = query
        emojiSearchJob?.cancel()
        if (query.isNotEmpty()) {
            emojiSearchJob = viewModelScope.launch {
                delay(120)
                val currentQuery = emojiSearchQuery
                if (currentQuery.isEmpty()) return@launch
                val results = withContext(Dispatchers.Default) {
                    EmojiRepository.search(currentQuery)
                }
                if (currentQuery == emojiSearchQuery) {
                    emojiGridItems = results
                }
            }
        } else {
            // Restore category view
            updateEmojiGridForCategory(selectedEmojiCategory)
        }
    }
    
    fun toggleEmojiSearch(isActive: Boolean) {
        isEmojiSearchActive = isActive
        if (!isActive) {
            // When closing search, optionally clear query or keep it?
            // Usually we keep it until category switch.
            // But if we are cancelling search, maybe we just hide the keyboard.
        }
    }

    fun setLayout(layoutName: String) {
        val repo = layoutRepository ?: return
        val layout = repo.getLayout(layoutName) ?: return
        currentAlphabetLayout = layout
        prefs()?.edit()?.putString(prefsKeyLayout, layoutName)?.apply()
    }

    fun toggleNumberRow() {
        val newValue = !showNumberRow
        showNumberRow = newValue
        saveNumberRowPreference(newValue)
    }

    private fun prefs() = appContext?.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
    private fun appSettings() = appContext?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private fun loadNumberRowPreference(defaultValue: Boolean): Boolean {
        return prefs()?.getBoolean(prefsKeyShowNumberRow, defaultValue) ?: defaultValue
    }

    private fun saveNumberRowPreference(value: Boolean) {
        prefs()?.edit()?.putBoolean(prefsKeyShowNumberRow, value)?.apply()
    }

    fun updateImeOptions(options: Int) {
        imeOptions = options
    }

    fun updateSelectionState(hasSelection: Boolean) {
        this.hasSelection = hasSelection
        updateShiftState()
    }
    
    fun updateEditorInfo(info: EditorInfo) {
        editorInfo = info
        imeOptions = info.imeOptions
        updateShiftState()
    }
    
    private fun updateShiftState() {
        // Only auto-update shift if not in Caps Lock mode and in Alphabet mode
        if (!isCapsLockOn && keyboardMode == KeyboardMode.Alphabet) {
            isShiftOn = textLogic.shouldAutoCapitalize()
        }
    }

    private fun triggerHaptic() {
        appContext?.let { HapticUtils.performKeyClick(it) }
    }

    fun onKeyClick(code: String) {
        if (isEmojiSearchActive) {
            // Route input to emoji search query
            if (code == "\n") {
                // Enter closes search
                toggleEmojiSearch(false)
            } else {
                val newQuery = emojiSearchQuery + code
                onEmojiSearch(newQuery)
            }
            return
        }
        
        // If streaming, any key press should probably do nothing or maybe cancel streaming?
        // Currently the streaming view overlays the keys, so this shouldn't be reachable.
        
        // Handle Double-Space Period
        if (code == " " && textLogic.onSpacePressed()) {
            // Double space handled, just update shift state and return
            updateShiftState()
            return
        }

        // Commit text
        val textToCommit = if (isShiftOn && code.length == 1) code.uppercase() else code
        val interceptor = characterCommitInterceptor
        if (interceptor != null) {
            interceptor(textToCommit)
        } else {
            inputConnection?.commitText(textToCommit, 1)
        }
        
        // If in emoji mode, track usage
        if (keyboardMode == KeyboardMode.Emoji && !isEmojiSearchActive && code != "\n") {
            // Assume code is an emoji string if length > 1 or special check?
            // Actually, onKeyClick is generic. 
            // If we are in Emoji mode, any click is likely an emoji (unless backspace/enter handled separately)
            // Simple heuristic: if we are in emoji mode and it's not a control char
             appContext?.let { EmojiRepository.addRecent(textToCommit, it) }
             
             // If we are currently in Recent category, refresh the list to show the new order/item
             if (selectedEmojiCategory == RECENT_CATEGORY) {
                  emojiGridItems = EmojiRepository.getRecentEmojis()
             }
        }
        
        // Auto-off shift if not caps lock (simplified)
        if (isShiftOn && !isCapsLockOn) {
             isShiftOn = false
        }
        
        // Re-evaluate shift state after commit (e.g. if we typed a period or new sentence started)
        // But wait briefly for editor to update? Actually cursor update callback should handle it.
        // However, often IMEs predict the next state. 
        // Let's let onUpdateSelection handle it primarily, but we can force a check.
        // updateShiftState() // Doing this might be redundant if onUpdateSelection fires immediately.
    }

    fun onDeleteClick() {
        if (isEmojiSearchActive) {
            if (emojiSearchQuery.isNotEmpty()) {
                val newQuery = emojiSearchQuery.dropLast(1)
                onEmojiSearch(newQuery)
            }
            return
        }

        val ic = inputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    fun onShiftClick() {
        when (keyboardMode) {
            KeyboardMode.Symbols -> {
                keyboardMode = KeyboardMode.SymbolsAlt
                return
            }
            KeyboardMode.SymbolsAlt -> {
                keyboardMode = KeyboardMode.Symbols
                return
            }
            else -> {
                if (isCapsLockOn) {
                    isCapsLockOn = false
                    isShiftOn = false
                } else if (isShiftOn) {
                    isCapsLockOn = true
                } else {
                    isShiftOn = true
                }
            }
        }
    }

    fun onSymbolClick() {
        keyboardMode = if (keyboardMode == KeyboardMode.Alphabet) KeyboardMode.Symbols else KeyboardMode.Alphabet
        if (keyboardMode == KeyboardMode.Alphabet) {
            isShiftOn = false
            isCapsLockOn = false
        }
    }

    fun onEmojiClick() {
        hideKeyPreview()
        keyboardMode = if (keyboardMode == KeyboardMode.Emoji) KeyboardMode.Alphabet else KeyboardMode.Emoji
    }

    fun performImeAction() {
        val ic = inputConnection ?: return
        val actionId = imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION
        ic.performEditorAction(actionId)
    }

    fun onSpaceBarDrag(delta: Float) {
        val ic = inputConnection ?: return
        
        cursorDragAccumulator += delta
        
        if (Math.abs(cursorDragAccumulator) > CURSOR_DRAG_SENSITIVITY) {
            val steps = (cursorDragAccumulator / CURSOR_DRAG_SENSITIVITY).toInt()
            if (steps != 0) {
                // Reduce accumulator by the amount we consumed
                cursorDragAccumulator -= (steps * CURSOR_DRAG_SENSITIVITY)
                
                // Move cursor: steps > 0 is right, steps < 0 is left
                val keyCode = if (steps > 0) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
                val count = Math.abs(steps)
                for (i in 0 until count) {
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
                }
                
                // Provide subtle haptic feedback per character move
                triggerHaptic()
            }
        }
    }

    fun onSelectAll() {
        inputConnection?.performContextMenuAction(android.R.id.selectAll)
        triggerHaptic()
    }

    fun onReprocessSelection() {
        val ic = inputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            val intent = android.content.Intent(appContext, com.slumdog88.dictationkeyboardai.BubbleOverlayService::class.java)
            intent.action = "com.slumdog88.dictationkeyboardai.ACTION_REPROCESS_TEXT"
            intent.putExtra("text_to_process", selectedText.toString())
            appContext?.startService(intent)
            
            // Visual feedback (optional, but good for UX)
            triggerHaptic()
        }
    }

    fun onUndo() {
        // Send undo intent to service which tracks last inserted text length
        val intent = android.content.Intent("com.slumdog88.dictationkeyboardai.ACTION_IME_UNDO")
        intent.setPackage(appContext?.packageName)
        appContext?.sendBroadcast(intent)
    }

    fun onPasteLastTranscription() {
        val text = lastTranscription
        if (!text.isNullOrEmpty()) {
            inputConnection?.commitText(text, 1)
            triggerHaptic()
        }
    }

    fun onCut() {
        inputConnection?.performContextMenuAction(android.R.id.cut)
        triggerHaptic()
    }

    fun onCopy() {
        inputConnection?.performContextMenuAction(android.R.id.copy)
        triggerHaptic()
    }

    fun onPaste() {
        inputConnection?.performContextMenuAction(android.R.id.paste)
        triggerHaptic()
    }

    fun toggleExpand() {
        triggerHaptic()
        isExpanded = !isExpanded
    }

    fun onCancelRecording() {
        if (isRecording) {
            isRecording = false
            // Send intent to stop recording but discard result (service handles this logic if action differs)
            // For now, just stopping. If service needs specific cancel intent, we should add it.
            // Let's assume stopping without processing logic is handled by UI/Service coordination.
            // Actually, BubbleOverlayService has ACTION_STOP_DICTATION. 
            // If we want to CANCEL, we might need a flag or separate action.
            // For now, standard stop is what we have. 
            // Ideally, the UI would pass a "cancel" flag.
            // Let's toggle recording off here to update UI state.
            // The caller (KeyboardScreen) will handle the service intent.
            triggerHaptic()
        }
    }

    fun toggleRecording(): Boolean {
        triggerHaptic()
        isRecording = !isRecording
        return isRecording
    }

    fun toggleAi() {
        triggerHaptic()
        isAiEnabled = !isAiEnabled
        appSettings()?.edit()?.putBoolean(prefsKeyAiEnabled, isAiEnabled)?.apply()
    }
    
    fun reloadSettings() {
        // Called when settings might have changed (e.g. returning from settings activity)
        isAiEnabled = appSettings()?.getBoolean(prefsKeyAiEnabled, false) ?: false
        isAlwaysStreamingEnabled = appSettings()?.getBoolean(prefsKeyAlwaysStreaming, false) ?: false
    }

    /**
     * Clean up resources when the service is destroyed.
     * MUST be called from DictationImeService.onDestroy() to prevent coroutine leaks.
     */
    fun cleanup() {
        viewModelScope.cancel()
    }
}

enum class KeyboardMode {
    Alphabet,
    Symbols,
    SymbolsAlt,
    Emoji
}
