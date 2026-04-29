package com.slumdog88.dictationkeyboardai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.content.res.ColorStateList
import android.os.Build
import android.util.Log
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.slumdog88.dictationkeyboardai.R
import androidx.core.widget.ImageViewCompat
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * Manager class for handling bubble UI operations including view creation,
 * animations, touch handling, and positioning.
 */
class BubbleUIManager(
    private val context: Context,
    private val callback: BubbleUICallback
) {

    private val settingsManager = SettingsManager(context)

    // Performance fix: ValueAnimator for smooth pulsing without recursive callbacks
    private var pulseAnimator: ValueAnimator? = null
    
    interface BubbleUICallback {
        fun onRecordingStartRequested()
        fun onStreamingStartRequested()
        fun onRecordingStopRequested()
        fun onBubbleHideRequested()
        fun onTrashRequested()
        fun isRecording(): Boolean
        fun getServiceScope(): CoroutineScope
        fun onKeyboardDetectionSetupRequested()
        fun onKeyboardDetectionCleanupRequested()
        fun isKeyboardAwareBubbleEnabled(): Boolean
        fun onNotificationUpdateRequested()

        // Quick action menu callbacks
        fun onSelectAllRequested()
        fun onReprocessRequested()
        fun onAIProcessingToggleRequested()
        fun onOpenSettingsRequested()
        fun hasTextSelection(): Boolean
        fun isAIProcessingEnabled(): Boolean
    }
    
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var micButton: ImageButton? = null
    private var trashButton: ImageButton? = null
    
    // Keyboard detection state
    private var lastBubbleVisibilityFromKeyboard = false
    
    /**
     * Show the bubble overlay
     */
    fun showBubble() {
        Log.d("BubbleUIManager", "showBubble() called - checking bubble state and keyboard-aware setting")
        
        if (bubbleView != null) {
            Log.d("BubbleUIManager", "Bubble view exists - making visible and checking keyboard detection")
            bubbleView?.visibility = View.VISIBLE
            
            // Re-enable keyboard detection if setting is on
            if (callback.isKeyboardAwareBubbleEnabled()) {
                Log.d("BubbleUIManager", "Re-enabling keyboard detection for manual show")
                callback.onKeyboardDetectionSetupRequested()
            } else {
                Log.d("BubbleUIManager", "Keyboard-aware bubble disabled - standard bubble behavior")
            }
            
            callback.onNotificationUpdateRequested()
            return
        }
        
        Log.d("BubbleUIManager", "Creating new bubble view")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                Log.e("BubbleUIManager", "Overlay permission not granted")
                Log.e("BubbleUIManager", "Overlay permission not granted; cannot show bubble")
                return
            }
        }
        
        createBubbleView()
        callback.onNotificationUpdateRequested()
    }
    
    /**
     * Hide the bubble overlay
     */
    fun hideBubble() {
        // Prevent hiding bubble during recording
        if (callback.isRecording()) {
            Log.d("BubbleUIManager", "Cannot hide bubble while recording")
            return
        }
        
        Log.d("BubbleUIManager", "Manual hide: Cleaning up keyboard detection and hiding bubble")
        
        // Clean up keyboard detection when manually hiding
        callback.onKeyboardDetectionCleanupRequested()
        
        // Reset keyboard awareness state so manual hide is respected
        lastBubbleVisibilityFromKeyboard = false
        
        bubbleView?.visibility = View.GONE
        Log.d("BubbleUIManager", "Bubble hidden manually (keyboard detection disabled)")
        callback.onNotificationUpdateRequested()
    }
    
    /**
     * Remove the bubble overlay completely
     */
    fun removeBubble() {
        // Clean up keyboard detection first
        callback.onKeyboardDetectionCleanupRequested()
        
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e("BubbleUIManager", "Error removing bubble", e)
            }
            bubbleView = null
        }
    }
    
    /**
     * Update UI appearance based on recording state - single button design
     */
    fun updateRecordingState(isRecording: Boolean) {
        updateUIVisibility(isRecording)
    }

    /**
     * Update UI appearance based on recording state - single button design
     */
    fun updateUIVisibility(isRecording: Boolean) {
        if (isRecording) {
            // Recording State - update mic button appearance and start pulsing
            micButton?.apply {
                setImageResource(R.drawable.ic_mic)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.black)))
                setBackgroundResource(R.drawable.bubble_background_recording)
                startPulsingAnimation()
            }
        } else {
            // Not Recording State - update mic button appearance and stop animations
            // Performance fix: Use dedicated stopPulsingAnimation method
            stopPulsingAnimation()
            micButton?.apply {
                clearAnimation()
                animate().cancel()
                setImageResource(R.drawable.ic_mic)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.white)))
                setBackgroundResource(R.drawable.circle_black_whiteborder)
            }
        }

        Log.d("BubbleUIManager", "Updated UI for single-button design - Recording: $isRecording")
    }

    /**
     * Single-button design doesn't need state-based window size changes
     * Window size remains consistent for optimal touch area
     */
    private fun updateWindowSizeForButtonState(showingBothButtons: Boolean) {
        Log.d("BubbleUIManager", "Single-button design - no window size changes needed")
        // No window size changes needed since we only have one button
        // This eliminates touch area optimization issues
    }



    /**
     * Handle keyboard visibility changes
     */
    fun handleKeyboardVisibilityChange(visible: Boolean) {
        if (visible) {
            // Keyboard appeared - show bubble immediately
            if (bubbleView?.visibility != View.VISIBLE) {
                Log.d("BubbleUIManager", "Keyboard appeared - showing bubble immediately")
                bubbleView?.visibility = View.VISIBLE
                lastBubbleVisibilityFromKeyboard = true
                callback.onNotificationUpdateRequested()
            }
        } else {
            // Keyboard disappeared - hide bubble if it was shown by keyboard
            if (bubbleView?.visibility == View.VISIBLE && lastBubbleVisibilityFromKeyboard) {
                Log.d("BubbleUIManager", "Keyboard disappeared - hiding bubble")
                bubbleView?.visibility = View.GONE
                lastBubbleVisibilityFromKeyboard = false
                callback.onNotificationUpdateRequested()
            }
        }
    }
    
    /**
     * Get the current bubble view
     */
    fun getBubbleView(): View? = bubbleView
    
    /**
     * Check if bubble is visible
     */
    fun isBubbleVisible(): Boolean = bubbleView?.visibility == View.VISIBLE
    
    /**
     * Get last bubble visibility from keyboard state
     */
    fun getLastBubbleVisibilityFromKeyboard(): Boolean = lastBubbleVisibilityFromKeyboard
    
    /**
     * Set last bubble visibility from keyboard state
     */
    fun setLastBubbleVisibilityFromKeyboard(value: Boolean) {
        lastBubbleVisibilityFromKeyboard = value
    }
    
    /**
     * Create the bubble view and add it to window manager
     */
    private fun createBubbleView() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        bubbleView = inflater.inflate(R.layout.bubble_layout_compact, null)

        micButton = bubbleView?.findViewById(R.id.mic_button)
        // Trash button removed - using context-sensitive long press instead
        
        updateUIVisibility(callback.isRecording())
        
        setupListeners()
        
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val density = context.resources.displayMetrics.density

        // Calculate compact window size that closely matches button dimensions
        // Create minimal touch area around the actual button
        val currentSize = settingsManager.getBubbleSize() // Get current size setting (50-150)
        val scale = currentSize / 100f // Convert to scale factor (0.5-1.5)
        val baseWidth = (84 * density).toInt()
        val baseHeight = (72 * density).toInt() // Compact: 56dp button + 8dp padding each side = 72dp
        val windowWidth = (baseWidth * scale).toInt()
        val windowHeight = (baseHeight * scale).toInt()

        params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params?.gravity = Gravity.TOP or Gravity.START

        // Restore last saved position
        val lastX = settingsManager.getBubbleLastPositionX()
        val lastY = settingsManager.getBubbleLastPositionY()
        if (lastX != -1 && lastY != -1) {
            params?.x = lastX
            params?.y = lastY
        } else {
            // Default position if not saved
            val actualContentWidth = (84 * density * scale).toInt()
            params?.x = screenWidth - actualContentWidth
            val originalButtonOffset = (108 * density * scale).toInt() 
            val newButtonOffset = (8 * density * scale).toInt() 
            val positionAdjustment = originalButtonOffset - newButtonOffset
            params?.y = screenHeight / 3 + positionAdjustment
        }
        
        windowManager?.addView(bubbleView, params)
        Log.d("BubbleUIManager", "Bubble shown")

        // Apply bubble customization settings
        applyBubbleCustomization()

        // Setup keyboard detection if enabled
        callback.onKeyboardDetectionSetupRequested()
    }
    
    /**
     * Start pulsing animation for mic button
     * Performance fix: Uses ValueAnimator instead of recursive callbacks
     * to prevent memory leaks and unbounded animation chains
     */
    private fun startPulsingAnimation() {
        // Cancel any existing animation first
        stopPulsingAnimation()

        pulseAnimator = ValueAnimator.ofFloat(1f, 1.08f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                micButton?.scaleX = scale
                micButton?.scaleY = scale
            }
            start()
        }
    }

    /**
     * Stop pulsing animation and reset scale
     */
    private fun stopPulsingAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        micButton?.scaleX = 1f
        micButton?.scaleY = 1f
    }

    /**
     * Setup touch listeners for bubble interaction
     */
    private fun setupListeners() {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressDetected = false
        var longPressJob: Job? = null
        var lastTapTime = 0L
        val doubleTapDelay = 400L

        micButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    longPressDetected = false
                    params?.let {
                        initialX = it.x
                        initialY = it.y
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    // Start context-sensitive long press detection
                    longPressJob = callback.getServiceScope().launch {
                        delay(500) // 500ms for long press
                        if (!isDragging) {
                            longPressDetected = true
                            // Visual feedback - slight scale animation
                            withContext(Dispatchers.Main) {
                                micButton?.animate()
                                    ?.scaleX(0.9f)
                                    ?.scaleY(0.9f)
                                    ?.setDuration(100)
                                    ?.withEndAction {
                                        micButton?.animate()
                                            ?.scaleX(1f)
                                            ?.scaleY(1f)
                                            ?.setDuration(100)
                                            ?.start()
                                    }
                                    ?.start()
                                // Provide haptic feedback for long press
                                HapticUtils.performHapticFeedback(context)

                                // Context-sensitive long press action
                                if (callback.isRecording()) {
                                    // Recording state: Cancel recording
                                    callback.onTrashRequested()
                                } else {
                                    // Resting state: Hide bubble
                                    callback.onBubbleHideRequested()
                                }
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                        // Cancel long press detection when dragging starts
                        longPressJob?.cancel()
                    }

                    if (isDragging) {
                        params?.let { layoutParams ->
                            val newX = initialX + dx.toInt()
                            val newY = initialY + dy.toInt()

                            // Apply boundary constraints to keep bubble on screen
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            val screenHeight = context.resources.displayMetrics.heightPixels
                            val density = context.resources.displayMetrics.density

                            // Get current scale for accurate content size calculation
                            val currentSize = settingsManager.getBubbleSize()
                            val scale = currentSize / 100f
                            val actualContentWidth = (84 * density * scale).toInt()
                            val actualContentHeight = (72 * density * scale).toInt() // Compact height

                            // Constrain X position (allow edge positioning based on actual content size)
                            layoutParams.x = newX.coerceIn(0, screenWidth - actualContentWidth)

                            // Constrain Y position (allow edge positioning based on actual content size)
                            layoutParams.y = newY.coerceIn(0, screenHeight - actualContentHeight)

                            windowManager?.updateViewLayout(bubbleView, layoutParams)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Cancel long press job if still running
                    longPressJob?.cancel()

                    if (isDragging) {
                        params?.let {
                            settingsManager.saveBubbleLastPosition(it.x, it.y)
                        }
                    }

                    if (!isDragging && !longPressDetected) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < doubleTapDelay) {
                            // Double tap detected
                            Log.d("BubbleUIManager", "Double tap detected - requesting streaming")
                            callback.onStreamingStartRequested()
                            lastTapTime = 0L
                        } else {
                            // Single tap detected
                            lastTapTime = currentTime
                            // This is a click (not a drag and not a long press)
                            if (!callback.isRecording()) {
                                callback.onRecordingStartRequested()
                            } else {
                                callback.onRecordingStopRequested()
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }

        // Trash button removed - using context-sensitive long press instead
    }

    /**
     * Apply bubble customization settings (opacity and size)
     */
    fun applyBubbleCustomization() {
        bubbleView?.let { bubble ->
            val opacity = settingsManager.getBubbleOpacity()
            val size = settingsManager.getBubbleSize()

            // Apply opacity (0-100 to 0.0-1.0)
            val alpha = opacity / 100f
            bubble.alpha = alpha

            // Apply size scaling with proper container bounds adjustment
            val scale = size / 100f
            updateBubbleScale(scale)

            Log.d("BubbleUIManager", "Applied bubble customization - Opacity: $opacity%, Size: $size%, Alpha: $alpha, Scale: $scale")
        }
    }

    /**
     * Update bubble appearance with new settings
     */
    fun updateBubbleAppearance(opacity: Int, size: Int) {
        bubbleView?.let { bubble ->
            // Apply opacity (0-100 to 0.0-1.0)
            val alpha = opacity / 100f
            bubble.alpha = alpha

            // Apply size scaling with proper container bounds adjustment
            val scale = size / 100f
            updateBubbleScale(scale)

            Log.d("BubbleUIManager", "Updated bubble appearance - Opacity: $opacity%, Size: $size%, Alpha: $alpha, Scale: $scale")
        }
    }

    /**
     * Update bubble scale with proper scaling that doesn't clip
     */
    private fun updateBubbleScale(scale: Float) {
        bubbleView?.let { bubble ->
            params?.let { layoutParams ->
                // Apply visual scaling to the bubble content
                bubble.scaleX = scale
                bubble.scaleY = scale

                // Adjust the pivot point to scale from center
                bubble.pivotX = bubble.width / 2f
                bubble.pivotY = bubble.height / 2f

                // Always adjust window size to match scaled content size
                // Use compact dimensions for minimal touch area
                val density = context.resources.displayMetrics.density
                val baseWidth = (84 * density).toInt()
                val baseHeight = (72 * density).toInt() // Compact: 56dp button + 8dp padding each side

                // Window size should always match the actual scaled content size
                layoutParams.width = (baseWidth * scale).toInt()
                layoutParams.height = (baseHeight * scale).toInt()

                // Update window layout
                windowManager?.updateViewLayout(bubble, layoutParams)

                Log.d("BubbleUIManager", "Applied bubble scale: $scale with window size: ${layoutParams.width}x${layoutParams.height}")
            }
        }
    }
}
