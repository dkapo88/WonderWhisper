package com.slumdog88.dictationkeyboardai.ui.bubble

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Controller for the Compose-based bubble overlay.
 * Manages lifecycle, window management, and state for the floating bubble.
 */
class BubbleComposeController(
    private val context: Context,
    private val callbacks: Callbacks
) {
    companion object {
        private const val TAG = "BubbleComposeController"
        private const val BASE_BUBBLE_SIZE = 56 // dp
        private const val SHADOW_MARGIN = 8 // dp - shadow space around bubble
        // Compact window must fit amplitude ring: (bubbleRadius + maxBarHeight) * 2 = (28 + 14) * 2 = 84dp
        // Add padding for shadow = 90dp
        private const val COMPACT_WINDOW_SIZE = 90 // dp - bubble + amplitude ring + shadow
        private const val EXPANDED_WINDOW_SIZE = 200 // dp - with full menu space
    }

    /**
     * Callback interface for bubble interactions.
     */
    interface Callbacks {
        fun onRecordingStartRequested()
        fun onRecordingStopRequested()
        fun onBubbleHideRequested()
        fun onTrashRequested()
        fun isRecording(): Boolean
        fun getServiceScope(): CoroutineScope
        fun onKeyboardDetectionSetupRequested()
        fun onKeyboardDetectionCleanupRequested()
        fun isKeyboardAwareBubbleEnabled(): Boolean
        fun onNotificationUpdateRequested()

        // Quick action callbacks
        fun onSelectAllRequested()
        fun onReprocessRequested()
        fun onAIProcessingToggleRequested()
        fun onOpenSettingsRequested()
        fun isAIProcessingEnabled(): Boolean
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val settingsManager = SettingsManager(context)
    private val amplitudeBuffer = AmplitudeBuffer(24)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State management
    private val _bubbleState = MutableStateFlow<BubbleState>(BubbleState.Idle)
    val bubbleState: StateFlow<BubbleState> = _bubbleState.asStateFlow()

    private val _bubbleConfig = MutableStateFlow(BubbleConfig())
    val bubbleConfig: StateFlow<BubbleConfig> = _bubbleConfig.asStateFlow()

    private val _isAIEnabled = MutableStateFlow(false)

    // View management
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    // Position tracking
    private var currentX = 0
    private var currentY = 0
    private var preExpansionX = 0  // Store position before menu expansion
    private var preExpansionY = 0
    private var lastBubbleVisibilityFromKeyboard = false

    /**
     * Show the bubble overlay.
     */
    fun show() {
        Log.d(TAG, "show() called")

        if (composeView != null) {
            Log.d(TAG, "Bubble view exists - making visible")
            mainHandler.post {
                composeView?.visibility = View.VISIBLE
            }
            if (callbacks.isKeyboardAwareBubbleEnabled()) {
                callbacks.onKeyboardDetectionSetupRequested()
            }
            callbacks.onNotificationUpdateRequested()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Log.e(TAG, "Overlay permission not granted")
                return
            }
        }

        createBubbleView()
        callbacks.onNotificationUpdateRequested()
    }

    /**
     * Hide the bubble overlay.
     */
    fun hide() {
        if (callbacks.isRecording()) {
            Log.d(TAG, "Cannot hide bubble while recording")
            return
        }

        mainHandler.post {
            composeView?.visibility = View.GONE
        }
        callbacks.onKeyboardDetectionCleanupRequested()
        callbacks.onNotificationUpdateRequested()
    }

    /**
     * Remove and release the bubble view completely.
     */
    fun release() {
        // Cleanup keyboard detection
        callbacks.onKeyboardDetectionCleanupRequested()

        mainHandler.post {
            lifecycleOwner?.handleDetach()
            lifecycleOwner = null

            if (isAttached) {
                try {
                    composeView?.let { windowManager.removeView(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing bubble view", e)
                }
                isAttached = false
            }

            composeView?.disposeComposition()
            composeView = null
            layoutParams = null
        }

        // Cancel the controller's coroutine scope
        scope.cancel()
    }

    /**
     * Update the bubble's recording state.
     */
    fun setRecordingState(isRecording: Boolean) {
        if (isRecording) {
            amplitudeBuffer.clear()
            _bubbleState.value = BubbleState.Recording()
        } else {
            _bubbleState.value = BubbleState.Idle
        }
    }

    /**
     * Update the bubble's processing state.
     */
    fun setProcessingState(isProcessing: Boolean, message: String = "Processing...") {
        if (isProcessing) {
            _bubbleState.value = BubbleState.Processing(message)
        } else {
            _bubbleState.value = BubbleState.Idle
        }
    }

    /**
     * Update amplitude visualization.
     */
    fun updateAmplitude(rawAmplitude: Int) {
        val normalized = AmplitudeBuffer.normalizeAmplitude(rawAmplitude)
        amplitudeBuffer.add(normalized)

        _bubbleState.update { currentState ->
            when (currentState) {
                is BubbleState.Recording -> currentState.copy(
                    amplitude = normalized,
                    amplitudeHistory = amplitudeBuffer.getHistory()
                )
                else -> currentState
            }
        }
    }

    /**
     * Update bubble appearance (opacity and size).
     */
    fun updateAppearance(opacity: Int, size: Int) {
        val normalizedOpacity = (opacity / 100f).coerceIn(0.1f, 1f)
        val normalizedScale = (size / 100f).coerceIn(0.5f, 1.5f)

        _bubbleConfig.update { it.copy(opacity = normalizedOpacity, scale = normalizedScale) }

        // Update window size only if not in menu mode (menu handles its own sizing)
        val isMenuOpen = _bubbleState.value is BubbleState.MenuOpen
        if (!isMenuOpen) {
            mainHandler.post {
                layoutParams?.let { params ->
                    val density = context.resources.displayMetrics.density
                    val containerSize = (COMPACT_WINDOW_SIZE * density * normalizedScale).toInt()
                    params.width = containerSize
                    params.height = containerSize

                    if (isAttached) {
                        try {
                            composeView?.let { windowManager.updateViewLayout(it, params) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating bubble layout", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Update AI enabled state for menu display.
     */
    fun updateAIState(aiEnabled: Boolean) {
        _isAIEnabled.value = aiEnabled
    }

    /**
     * Handle keyboard visibility change for keyboard-aware bubble.
     */
    fun handleKeyboardVisibilityChange(isVisible: Boolean) {
        if (!callbacks.isKeyboardAwareBubbleEnabled()) return

        mainHandler.post {
            if (isVisible && composeView?.visibility != View.VISIBLE) {
                composeView?.visibility = View.VISIBLE
                lastBubbleVisibilityFromKeyboard = true
            } else if (!isVisible && lastBubbleVisibilityFromKeyboard) {
                composeView?.visibility = View.GONE
                lastBubbleVisibilityFromKeyboard = false
            }
        }
    }

    /**
     * Check if bubble is currently visible.
     */
    fun isVisible(): Boolean = composeView?.visibility == View.VISIBLE

    private fun createBubbleView() {
        mainHandler.post {
            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner

            val view = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val state by _bubbleState.collectAsState()
                    val config by _bubbleConfig.collectAsState()
                    val isAIEnabled by _isAIEnabled.collectAsState()

                    AppTheme {
                        BubbleOverlayCompose(
                            state = state,
                            config = config,
                            isAIEnabled = isAIEnabled,
                            onTap = { handleTap() },
                            onLongPress = { handleLongPress() },
                            onDrag = { delta -> handleDrag(delta) },
                            onDragEnd = { handleDragEnd() },
                            onQuickAction = { action -> handleQuickAction(action) }
                        )
                    }
                }
            }

            composeView = view
            view.setTag(androidx.lifecycle.runtime.R.id.view_tree_lifecycle_owner, owner)
            view.setTag(androidx.savedstate.R.id.view_tree_saved_state_registry_owner, owner)

            // Restore position from settings
            restorePosition()

            layoutParams = createLayoutParams()
            tryAddView(view)
            owner.handleAttach()

            // Apply initial appearance
            applyInitialAppearance()

            // Setup keyboard detection if enabled
            if (callbacks.isKeyboardAwareBubbleEnabled()) {
                callbacks.onKeyboardDetectionSetupRequested()
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val scale = _bubbleConfig.value.scale
        // Start with compact window size - bubble centered with minimal shadow margin
        val containerSize = (COMPACT_WINDOW_SIZE * density * scale).toInt()

        return WindowManager.LayoutParams(
            containerSize,
            containerSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentX
            y = currentY
        }
    }

    private fun tryAddView(view: ComposeView) {
        val params = layoutParams ?: createLayoutParams().also { layoutParams = it }
        try {
            windowManager.addView(view, params)
            isAttached = true
            Log.d(TAG, "Bubble view added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add bubble view", e)
            isAttached = false
            // Reset view state so show() can retry on next call
            view.disposeComposition()
            composeView = null
            layoutParams = null
            lifecycleOwner = null
        }
    }

    private fun restorePosition() {
        val savedX = settingsManager.getBubbleLastPositionX()
        val savedY = settingsManager.getBubbleLastPositionY()
        if (savedX >= 0 && savedY >= 0) {
            currentX = savedX
            currentY = savedY
        } else {
            // Default position: right side, 1/3 down
            val displayMetrics = context.resources.displayMetrics
            val density = displayMetrics.density
            currentX = displayMetrics.widthPixels - (100 * density).toInt()
            currentY = displayMetrics.heightPixels / 3
        }
        _bubbleConfig.update { it.copy(position = IntOffset(currentX, currentY)) }
    }

    private fun applyInitialAppearance() {
        val opacity = settingsManager.getBubbleOpacity()
        val size = settingsManager.getBubbleSize()
        updateAppearance(opacity, size)

        // Update AI state for menu
        _isAIEnabled.value = callbacks.isAIProcessingEnabled()
    }

    private fun handleTap() {
        val currentState = _bubbleState.value

        // Close menu if open
        if (currentState is BubbleState.MenuOpen) {
            dismissMenu()
            return
        }

        // Toggle recording
        if (callbacks.isRecording()) {
            callbacks.onRecordingStopRequested()
        } else {
            callbacks.onRecordingStartRequested()
        }
    }

    private fun handleLongPress() {
        // Open the quick action menu with unidirectional expansion
        val currentState = _bubbleState.value
        if (currentState !is BubbleState.MenuOpen) {
            transitionToMenuOpen(currentState)
        }
    }

    /**
     * Transition to menu open state with unidirectional window expansion.
     * The window expands away from the nearest screen edge, keeping the anchor edge fixed.
     */
    private fun transitionToMenuOpen(previousState: BubbleState) {
        val density = context.resources.displayMetrics.density
        val displayMetrics = context.resources.displayMetrics
        val scale = _bubbleConfig.value.scale
        val compactSize = (COMPACT_WINDOW_SIZE * density * scale).toInt()
        val expandedSize = (EXPANDED_WINDOW_SIZE * density * scale).toInt()
        val sizeDiff = expandedSize - compactSize

        // Store pre-expansion position for contraction
        preExpansionX = currentX
        preExpansionY = currentY

        // Determine which edge bubble is nearest to (based on bubble center)
        val bubbleScreenCenterX = currentX + compactSize / 2
        val screenCenterX = displayMetrics.widthPixels / 2
        val isNearRightEdge = bubbleScreenCenterX > screenCenterX

        // Calculate menu direction (opposite to nearest edge)
        val menuDirection = if (isNearRightEdge) MenuDirection.LEFT else MenuDirection.RIGHT

        // Calculate new window position - anchor edge stays fixed
        val newX: Int
        val newY: Int

        if (isNearRightEdge) {
            // Bubble near right edge - RIGHT edge of window stays fixed
            // Window expands LEFT
            // currentX + compactSize = newX + expandedSize
            newX = currentX + compactSize - expandedSize
            newY = currentY - sizeDiff / 2  // Center vertically
        } else {
            // Bubble near left edge - LEFT edge of window stays fixed
            // Window expands RIGHT
            newX = currentX
            newY = currentY - sizeDiff / 2
        }

        // Ensure window stays within screen bounds
        val clampedX = newX.coerceIn(0, displayMetrics.widthPixels - expandedSize)
        val clampedY = newY.coerceIn(0, displayMetrics.heightPixels - expandedSize)

        // Update layout params
        layoutParams?.apply {
            width = expandedSize
            height = expandedSize
            x = clampedX
            y = clampedY
        }

        // Update WindowManager AND state together in same frame to avoid jank
        mainHandler.post {
            try {
                composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to expand window", e)
            }
            // Update state AFTER window layout to keep bubble position stable
            _bubbleState.value = BubbleState.MenuOpen(
                previousState = previousState,
                menuDirection = menuDirection
            )
        }
    }

    /**
     * Transition from menu open state, contracting window back to compact size.
     */
    private fun transitionFromMenuOpen() {
        val currentState = _bubbleState.value as? BubbleState.MenuOpen ?: return
        val density = context.resources.displayMetrics.density
        val scale = _bubbleConfig.value.scale
        val compactSize = (COMPACT_WINDOW_SIZE * density * scale).toInt()
        val previousState = currentState.previousState

        // Restore to pre-expansion position
        currentX = preExpansionX
        currentY = preExpansionY

        layoutParams?.apply {
            width = compactSize
            height = compactSize
            x = currentX
            y = currentY
        }

        // Update WindowManager AND state together in same frame to avoid jank
        mainHandler.post {
            try {
                composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to contract window", e)
            }
            // Restore previous state AFTER window layout to keep bubble position stable
            _bubbleState.value = previousState
        }
    }

    private fun handleDrag(delta: Offset) {
        // Update position
        currentX += delta.x.toInt()
        currentY += delta.y.toInt()

        // Constrain to screen bounds
        val displayMetrics = context.resources.displayMetrics
        val containerSize = layoutParams?.width ?: 0
        currentX = currentX.coerceIn(0, displayMetrics.widthPixels - containerSize)
        currentY = currentY.coerceIn(0, displayMetrics.heightPixels - containerSize)

        // Update layout
        layoutParams?.let { params ->
            params.x = currentX
            params.y = currentY
            if (isAttached) {
                try {
                    composeView?.let { windowManager.updateViewLayout(it, params) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating bubble position", e)
                }
            }
        }

        _bubbleConfig.update { it.copy(position = IntOffset(currentX, currentY)) }
    }

    private fun handleDragEnd() {
        // Save position
        settingsManager.saveBubbleLastPosition(currentX, currentY)
    }

    private fun handleQuickAction(action: QuickAction) {
        when (action) {
            QuickAction.CANCEL_RECORDING -> {
                if (callbacks.isRecording()) {
                    callbacks.onTrashRequested()
                }
            }
            QuickAction.DISMISS_MENU -> {
                // Just dismiss, no additional action needed
            }
            QuickAction.SELECT_ALL -> {
                callbacks.onSelectAllRequested()
            }
            QuickAction.REPROCESS -> {
                callbacks.onReprocessRequested()
            }
            QuickAction.TOGGLE_AI -> {
                callbacks.onAIProcessingToggleRequested()
                // Update display
                _isAIEnabled.value = callbacks.isAIProcessingEnabled()
            }
            QuickAction.OPEN_SETTINGS -> {
                callbacks.onOpenSettingsRequested()
            }
        }
        dismissMenu()
    }

    private fun dismissMenu() {
        if (_bubbleState.value is BubbleState.MenuOpen) {
            transitionFromMenuOpen()
        }
    }

    /**
     * Lifecycle owner for the overlay ComposeView.
     */
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun handleAttach() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun handleDetach() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry
    }
}
