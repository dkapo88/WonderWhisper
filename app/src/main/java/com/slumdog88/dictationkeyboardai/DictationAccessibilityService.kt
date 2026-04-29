package com.slumdog88.dictationkeyboardai

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.view.View
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityWindowInfo
import com.slumdog88.dictationkeyboardai.utils.SmartTextInsertionFormatter
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DictationAccessibilityService : AccessibilityService() {
    private var lastFocusedNode: AccessibilityNodeInfo? = null
    private var lastSelectedText: String? = null
    private var lastSelectionStart: Int = -1
    private var lastSelectionEnd: Int = -1
    private var bubbleVisibilityMode: Int = 1 // Default to "Hide on Home Screen Only"

    // Event tracking for debugging
    private var eventCount = 0
    private var lastEventTime = 0L

    // Performance fix: Reduced timeout from 750ms to 500ms for faster text insertion

    // Event debouncing for performance (Fix #8)
    private val eventHandler = Handler(Looper.getMainLooper())
    private var pendingTextSelectionEvent: Runnable? = null

    companion object {
        private const val TAG = "DictationAccessibility"
        // Performance fix: Event debouncing delay
        private const val DEBOUNCE_MS = 150L
        private const val VERBOSE_HOT_PATH_LOGS = false

        const val MODE_ALWAYS_VISIBLE = 0
        const val MODE_HIDE_HOME_SCREEN = 1
        const val MODE_AUTO_HIDE_ORIGINAL = 2

        // Static instance for direct communication
        private var instance: DictationAccessibilityService? = null
        @Volatile
        private var isImeActive: Boolean = false


        fun getInstance(): DictationAccessibilityService? = instance

        // Direct method to insert text without broadcasts
        fun insertTextDirect(text: String): Boolean {
            if (isImeActive) {
                Log.d("DictationAccessibility", "IME is active, deferring text insertion to IME.")
                return false
            }
            return instance?.handleDirectTextInsertion(text) ?: false
        }


        // Direct method to get selected text for context
        fun getSelectedTextDirect(): String? {
            return instance?.getSelectedTextForContext()
        }

        // Direct method to extract screen text for context
        fun getScreenTextDirect(): String? {
            return instance?.extractScreenText()
        }
        fun setImeActive(active: Boolean) {
            isImeActive = active
            if (BuildConfig.DEBUG && VERBOSE_HOT_PATH_LOGS) {
                Log.d(TAG, "IME active status set to: $active")
            }
        }


        // Test method to check if service is working
        fun testServiceFunctionality(): String {
            val serviceInstance = instance
            if (serviceInstance == null) {
                return "❌ Service instance is null"
            }

            val sb = StringBuilder()
            sb.append("✅ Service instance exists\n")
            sb.append("📊 Events received: ${serviceInstance.eventCount}\n")

            val timeSinceLastEvent = if (serviceInstance.lastEventTime > 0) {
                (System.currentTimeMillis() - serviceInstance.lastEventTime) / 1000
            } else {
                -1
            }
            sb.append("⏰ Last event: ${if (timeSinceLastEvent >= 0) "${timeSinceLastEvent}s ago" else "never"}\n")

            try {
                val rootNode = serviceInstance.rootInActiveWindow
                if (rootNode != null) {
                    sb.append("✅ Can access root window\n")
                    sb.append("📱 Current app: ${rootNode.packageName}\n")

                    val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focusedNode != null) {
                        sb.append("✅ Found focused node: ${focusedNode.className}\n")
                        sb.append("📝 Is editable: ${serviceInstance.isEditable(focusedNode)}\n")
                        focusedNode.recycle()
                    } else {
                        sb.append("⚠️ No focused node found\n")
                    }

                    rootNode.recycle()
                } else {
                    sb.append("❌ Cannot access root window\n")
                }
            } catch (e: Exception) {
                sb.append("❌ Error testing functionality: ${e.message}\n")
            }

            return sb.toString()
        }
    }

    private val insertTextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.slumdog88.dictationkeyboardai.ACTION_INSERT_TEXT") {
                val transcriptionText = intent.getStringExtra("text") ?: return
                Log.d("DictationAccessibility", "Received broadcast to insert text: '$transcriptionText'")

                // Use clipboard + paste approach for universal compatibility
                insertTextViaClipboard(transcriptionText)
            }
        }
    }

    private val selectedTextRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.slumdog88.dictationkeyboardai.ACTION_REQUEST_SELECTED_TEXT") {
                Log.d("DictationAccessibility", "Selected text requested. Current: '$lastSelectedText'")

                // Try to get current selection if we don't have one stored
                var selectedText = lastSelectedText
                if (selectedText.isNullOrBlank()) {
                    selectedText = getCurrentSelection()
                    Log.d("DictationAccessibility", "Fallback selection: '$selectedText'")
                }

                val reply = Intent("com.slumdog88.dictationkeyboardai.ACTION_SELECTED_TEXT_RESULT")
                reply.putExtra("selected_text", selectedText ?: "")
                sendBroadcast(reply)

                Log.d("DictationAccessibility", "Sent selected text response: '${selectedText ?: ""}'")
            }
        }
    }

    // Test receiver to show bubble on demand
    private val testBubbleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.slumdog88.dictationkeyboardai.ACTION_TEST_SHOW_BUBBLE") {
                Log.d("DictationAccessibility", "Test show bubble requested")
                android.util.Log.wtf("DictationAccessibility", "Test broadcast received!")

                // First try the normal way
                showBubble()

                // Then try creating overlay directly
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    createDirectTestOverlay()
                }, 1000)
            } else if (intent?.action == "com.slumdog88.dictationkeyboardai.ACTION_TEST_INSERT_TEXT") {
                Log.d("DictationAccessibility", "Test text insertion requested")
                val testText = intent.getStringExtra("text") ?: "Test transcription text"

                // Simulate receiving a transcription broadcast
                val insertIntent = Intent("com.slumdog88.dictationkeyboardai.ACTION_INSERT_TEXT")
                insertIntent.putExtra("text", testText)
                insertTextReceiver.onReceive(context, insertIntent)
            }
        }
    }

    // Receiver to update bubble visibility mode
    private val bubbleModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.slumdog88.dictationkeyboardai.UPDATE_BUBBLE_MODE") {
                bubbleVisibilityMode = intent.getIntExtra("mode", MODE_HIDE_HOME_SCREEN)
                Log.d("DictationAccessibility", "Bubble visibility mode updated to: $bubbleVisibilityMode")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("DictationAccessibility", "=== ACCESSIBILITY SERVICE CONNECTED ===")

        // Set the static instance for direct communication
        instance = this
        Log.d("DictationAccessibility", "Static instance set for direct communication")

        // Load saved bubble visibility mode first
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        bubbleVisibilityMode = prefs.getInt("bubble_visibility_mode", MODE_HIDE_HOME_SCREEN)
        Log.d("DictationAccessibility", "Loaded bubble visibility mode: $bubbleVisibilityMode")

        val filter = IntentFilter("com.slumdog88.dictationkeyboardai.ACTION_INSERT_TEXT")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(insertTextReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(insertTextReceiver, filter)
        }
        val selFilter = IntentFilter("com.slumdog88.dictationkeyboardai.ACTION_REQUEST_SELECTED_TEXT")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(selectedTextRequestReceiver, selFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(selectedTextRequestReceiver, selFilter)
        }

        // Register test bubble receiver
        val testFilter = IntentFilter().apply {
            addAction("com.slumdog88.dictationkeyboardai.ACTION_TEST_SHOW_BUBBLE")
            addAction("com.slumdog88.dictationkeyboardai.ACTION_TEST_INSERT_TEXT")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testBubbleReceiver, testFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(testBubbleReceiver, testFilter)
        }

        // Register bubble mode receiver
        val bubbleModeFilter = IntentFilter("com.slumdog88.dictationkeyboardai.UPDATE_BUBBLE_MODE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleModeReceiver, bubbleModeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bubbleModeReceiver, bubbleModeFilter)
        }

        // Check if overlay permission is granted before starting bubble service
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        Log.d("DictationAccessibility", "Accessibility service connected, overlay permission: $hasOverlayPermission")

        if (!hasOverlayPermission) {
            Log.e("DictationAccessibility", "Overlay permission not granted, cannot start bubble service")
            return
        }

        // Automatically start the bubble service when accessibility service is enabled
        try {
            Log.d("DictationAccessibility", "Starting bubble service...")
            val bubbleIntent = Intent(this, BubbleOverlayService::class.java)
            // Start as regular service - it will become foreground only when recording starts
            startService(bubbleIntent)
            Log.d("DictationAccessibility", "Service connected and bubble service started successfully")
        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error starting bubble service", e)
            // Don't crash the accessibility service if bubble service fails to start
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            Log.w("DictationAccessibility", "Received null accessibility event")
            return
        }

        // Track events for debugging
        eventCount++
        lastEventTime = System.currentTimeMillis()

        if (BuildConfig.DEBUG && VERBOSE_HOT_PATH_LOGS) {
            Log.d(TAG, "=== ACCESSIBILITY EVENT RECEIVED (#$eventCount) ===")
            Log.d(TAG, "Event type: ${event.eventType} (${getEventTypeName(event.eventType)})")
            Log.d(TAG, "Package: ${event.packageName}")
            Log.d(TAG, "Class: ${event.className}")
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val sourceNode = event.source
                if (sourceNode != null && isEditable(sourceNode)) {
                    Log.d("DictationAccessibility", "Editable view focused: ${sourceNode.className}, caching node. Showing bubble.")
                    lastFocusedNode?.recycle()
                    lastFocusedNode = AccessibilityNodeInfo.obtain(sourceNode)
                    showBubble()
                } else {
                    Log.d("DictationAccessibility", "Non-editable view focused: ${sourceNode?.className}")
                    // Removed auto-hide logic - bubble stays visible for better user control
                }
                sourceNode?.recycle()
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                // Performance fix: Debounce text selection events to reduce processing load
                val sourceNode = event.source
                if (sourceNode != null && isEditable(sourceNode)) {
                    // Cache node immediately but debounce the heavy selection processing
                    lastFocusedNode?.recycle()
                    lastFocusedNode = AccessibilityNodeInfo.obtain(sourceNode)

                    // Debounce selection text extraction (fires rapidly during typing)
                    pendingTextSelectionEvent?.let { eventHandler.removeCallbacks(it) }
                    pendingTextSelectionEvent = Runnable {
                        try {
                            val sel = getCurrentSelection()
                            if (!sel.isNullOrBlank()) {
                                lastSelectedText = sel
                                lastFocusedNode?.let { node ->
                                    lastSelectionStart = node.textSelectionStart
                                    lastSelectionEnd = node.textSelectionEnd
                                }
                                Log.d("DictationAccessibility", "Cached selected text: '" + sel.take(80) + "'")
                            } else {
                                // If selection collapsed, clear cached selection
                                lastFocusedNode?.let { node ->
                                    if (node.textSelectionStart == node.textSelectionEnd) {
                                        lastSelectedText = null
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("DictationAccessibility", "Error processing debounced selection event", e)
                        }
                    }
                    eventHandler.postDelayed(pendingTextSelectionEvent!!, DEBOUNCE_MS)
                }
                sourceNode?.recycle()
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Handle bubble visibility based on window changes
                val packageName = event.packageName?.toString()
                Log.d("DictationAccessibility", "Window state changed to package: $packageName")

                if (isHomeScreen(packageName)) {
                    // Immediate home screen detection (no debouncing needed - system UI exclusions handle edge cases)
                    Log.d("DictationAccessibility", "Home screen detected: $packageName - hiding bubble immediately")
                    hideBubble()

                    // Clear cached node when going to home screen
                    lastFocusedNode?.recycle()
                    lastFocusedNode = null
                } else {
                    Log.d("DictationAccessibility", "Non-home screen package: $packageName - no action needed")
                }
            }
        }
    }

    private fun isEditable(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // Standard accessibility checks
        if (node.isEditable) {
            Log.d("DictationAccessibility", "Node is editable via isEditable flag")
            return true
        }

        if (node.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT) == true) {
            Log.d("DictationAccessibility", "Node supports ACTION_SET_TEXT")
            return true
        }

        // Enhanced detection for custom text inputs (like Notion, Samsung Notes)
        val className = node.className?.toString()
        val viewIdName = node.viewIdResourceName
        val contentDescription = node.contentDescription?.toString()
        val text = node.text?.toString()

        // Check for common text input class names
        val textInputClasses = setOf(
            "android.widget.EditText",
            "android.widget.TextView", // Some apps use TextView for editable text
            "android.widget.MultiAutoCompleteTextView",
            "android.widget.AutoCompleteTextView",
            "androidx.appcompat.widget.AppCompatEditText",
            "com.google.android.material.textfield.TextInputEditText",
            // Web view text inputs
            "android.webkit.WebView",
            "android.view.View", // Generic view that might be custom text input
        )

        if (textInputClasses.any { className?.contains(it, ignoreCase = true) == true }) {
            Log.d("DictationAccessibility", "Node detected as text input via className: $className")
            return true
        }

        // Check for common text input resource IDs
        val textInputIds = setOf(
            "edit", "text", "input", "editor", "content", "message", "note", "field",
            "search", "comment", "description", "title", "name", "email", "password"
        )

        if (textInputIds.any { viewIdName?.contains(it, ignoreCase = true) == true }) {
            Log.d("DictationAccessibility", "Node detected as text input via viewId: $viewIdName")
            return true
        }

        // Check content description for hints
        val textInputDescriptions = setOf(
            "edit", "text", "input", "type", "enter", "write", "compose", "note"
        )

        if (textInputDescriptions.any { contentDescription?.contains(it, ignoreCase = true) == true }) {
            Log.d("DictationAccessibility", "Node detected as text input via contentDescription: $contentDescription")
            return true
        }

        // Check if node is focusable and clickable (common for custom text inputs)
        if (node.isFocusable && node.isClickable && node.isFocused) {
            Log.d("DictationAccessibility", "Node is focusable, clickable, and focused - likely custom text input")
            return true
        }

        // Check for paste action support (indicates text input capability)
        if (node.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE) == true ||
            node.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CUT) == true ||
            node.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY) == true) {
            Log.d("DictationAccessibility", "Node supports text manipulation actions")
            return true
        }

        // Special handling for known problematic apps
        val packageName = node.packageName?.toString()
        when {
            packageName?.contains("notion") == true -> {
                // Notion uses custom text implementation
                if (node.isFocused && (node.isClickable || node.isFocusable)) {
                    Log.d("DictationAccessibility", "Notion app: treating focused interactive node as editable")
                    return true
                }
            }
            packageName?.contains("samsung") == true && packageName.contains("notes") -> {
                // Samsung Notes detection
                if (node.isFocused && className?.contains("View") == true) {
                    Log.d("DictationAccessibility", "Samsung Notes: treating focused view as editable")
                    return true
                }
            }
            // Add more app-specific detection as needed
        }

        Log.d("DictationAccessibility", "Node not detected as editable. Class: $className, ID: $viewIdName, Package: $packageName")
        return false
    }

    private fun isHomeScreen(packageName: String?): Boolean {
        if (packageName == null) return false

        // Exclude system UI packages that are NOT home screens
        val systemUIPackages = setOf(
            "com.android.systemui",           // Notification panel, quick settings
            "com.android.settings",           // Settings app
            "com.android.documentsui",        // File picker
            "com.google.android.packageinstaller", // Package installer
            "android",                        // System dialogs
            "com.android.permissioncontroller" // Permission dialogs
        )

        if (systemUIPackages.contains(packageName)) {
            Log.d("DictationAccessibility", "Detected system UI package: $packageName - NOT treating as home screen")
            return false
        }

        val commonLaunchers = setOf(
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.launcher",
            "com.samsung.android.app.launcher",
            "com.samsung.android.launcher",
            "com.oneplus.launcher",
            "com.huawei.android.launcher",
            "com.miui.home",
            "com.lge.launcher2",
            "com.sonyericsson.home"
        )

        if (commonLaunchers.contains(packageName)) {
            Log.d("DictationAccessibility", "Detected launcher package: $packageName - treating as home screen")
            return true
        }

        // Check if it's the default launcher
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homePackage = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val isDefaultLauncher = homePackage?.activityInfo?.packageName == packageName
            if (isDefaultLauncher) {
                Log.d("DictationAccessibility", "Detected default launcher: $packageName - treating as home screen")
            } else {
                Log.d("DictationAccessibility", "Regular app package: $packageName - NOT treating as home screen")
            }
            return isDefaultLauncher
        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error checking default launcher", e)
            return false
        }
    }

    override fun onInterrupt() {}

    private fun getEventTypeName(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "TYPE_VIEW_TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            else -> "UNKNOWN($eventType)"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clear static instance
        instance = null
        Log.d("DictationAccessibility", "Static instance cleared")

        // Clean up pending events (performance fix cleanup)
        pendingTextSelectionEvent?.let { eventHandler.removeCallbacks(it) }
        pendingTextSelectionEvent = null

        lastFocusedNode?.recycle()
        unregisterReceiver(insertTextReceiver)
        unregisterReceiver(selectedTextRequestReceiver)
        unregisterReceiver(testBubbleReceiver)
        unregisterReceiver(bubbleModeReceiver)
    }

    /**
     * Depth-first search for a focused editable node.
     * Returns an obtained copy that the caller must recycle.
     */
    private fun findFocusedEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isFocused && node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            try {
                val result = findFocusedEditableNode(child)
                if (result != null) return result
            } finally {
                child?.recycle()
            }
        }
        return null
    }

    /**
     * Looks up the focused editable node in the active window and guarantees recycling.
     */
    private inline fun <T> withFocusedEditableNode(block: (AccessibilityNodeInfo) -> T): T? {
        val rootNode = rootInActiveWindow ?: return null
        try {
            val focusedNode = findFocusedEditableNode(rootNode) ?: return null
            try {
                return block(focusedNode)
            } finally {
                focusedNode.recycle()
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Check if any editable text field currently has focus (for keyboard-aware bubble)
     * This is used by BubbleOverlayService for reliable keyboard hide detection
     */
    fun hasFocusedEditableField(): Boolean {
        return try {
            val result = withFocusedEditableNode { focusedNode ->
                if (BuildConfig.DEBUG && VERBOSE_HOT_PATH_LOGS) {
                    Log.d(TAG, "hasFocusedEditableField: true (${focusedNode.className})")
                }
                true
            }
            if (result == null && BuildConfig.DEBUG && VERBOSE_HOT_PATH_LOGS) {
                Log.d(TAG, "hasFocusedEditableField: false")
            }
            result ?: false
        } catch (e: Exception) {
            Log.w("DictationAccessibility", "Error checking focused editable field", e)
            false
        }
    }

    private fun getCurrentSelection(): String? {
        try {
            return withFocusedEditableNode { focusedNode ->
                val text = focusedNode.text?.toString()
                val selStart = focusedNode.textSelectionStart
                val selEnd = focusedNode.textSelectionEnd

                Log.d("DictationAccessibility", "getCurrentSelection - Text: '${text?.take(50)}...', Start: $selStart, End: $selEnd")

                if (text != null && selStart >= 0 && selEnd >= 0 && selStart != selEnd &&
                    selStart < text.length && selEnd <= text.length) {
                    val startIndex = minOf(selStart, selEnd)
                    val endIndex = maxOf(selStart, selEnd)
                    val selection = text.substring(startIndex, endIndex)
                    Log.d("DictationAccessibility", "Found current selection: '$selection'")
                    return@withFocusedEditableNode selection
                }
                null
            }
        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error getting current selection", e)
        }
        return null
    }

    private fun createDirectTestOverlay() {
        Log.e("DictationAccessibility", "Creating direct test overlay")
        android.util.Log.wtf("DictationAccessibility", "Direct overlay test starting")

        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val testView = View(this).apply {
                setBackgroundColor(0xFF00FF00.toInt()) // Green to distinguish from other tests
            }

            val params = WindowManager.LayoutParams(
                150, 150,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )

            params.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            params.x = 10
            params.y = 200

            windowManager.addView(testView, params)
            Log.e("DictationAccessibility", "Green test overlay created successfully!")
            android.util.Log.wtf("DictationAccessibility", "Green overlay added!")

            // Remove after 5 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(testView)
                    Log.e("DictationAccessibility", "Green test overlay removed")
                } catch (e: Exception) {
                    Log.e("DictationAccessibility", "Error removing test overlay", e)
                }
            }, 5000)

        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Failed to create direct overlay", e)
            android.util.Log.wtf("DictationAccessibility", "Direct overlay failed: ${e.message}")
        }
    }

    private fun showBubble() {
        Log.d("DictationAccessibility", "Telling Bubble Service to show bubble...")
        val intent = Intent(this, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_SHOW
        }
        // Start as regular service - it will become foreground only when recording starts
        startService(intent)
    }

    private fun hideBubble() {
        Log.d("DictationAccessibility", "Telling Bubble Service to hide bubble...")
        val intent = Intent(this, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_HIDE
        }
        // Start as regular service since we're just hiding the bubble
        startService(intent)
    }

    fun getLastSelectedText(): String? = lastSelectedText

    // Method to get selected text for context in transcription
    fun getSelectedTextForContext(): String? {
        Log.d("DictationAccessibility", "Getting selected text for context")

        // First try to get current selection
        val currentSelection = getCurrentSelection()
        if (!currentSelection.isNullOrBlank()) {
            Log.d("DictationAccessibility", "Found current selection: '$currentSelection'")
            return currentSelection
        }

        // Fallback to last stored selection
        if (!lastSelectedText.isNullOrBlank()) {
            Log.d("DictationAccessibility", "Using last stored selection: '$lastSelectedText'")
            return lastSelectedText
        }

        Log.d("DictationAccessibility", "No selected text found")
        return null
    }

    // Direct text insertion method for inter-service communication
    // Performance fix: Reduced timeout from 750ms to 500ms for faster text insertion
    fun handleDirectTextInsertion(textToPaste: String): Boolean {
        Log.d("DictationAccessibility", "Direct text insertion called with: '$textToPaste'")

        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Optimized CountDownLatch with reduced timeout (500ms vs 750ms)
            val latch = CountDownLatch(1)
            var result = false

            Handler(Looper.getMainLooper()).post {
                try {
                    result = insertTextDirectly(textToPaste)
                } catch (e: Exception) {
                    Log.e("DictationAccessibility", "Error performing direct insertion on main thread", e)
                    result = false
                } finally {
                    latch.countDown()
                }
            }

            try {
                // Performance fix: Reduced from 750ms to 500ms timeout
                if (!latch.await(500, TimeUnit.MILLISECONDS)) {
                    Log.w("DictationAccessibility", "Direct insertion timed out waiting for completion")
                    return false
                }
            } catch (e: InterruptedException) {
                Log.e("DictationAccessibility", "Direct insertion interrupted", e)
                return false
            }

            return result
        }

        return insertTextDirectly(textToPaste)
    }

    // Debug method to check service state
    fun debugServiceState(): String {
        val sb = StringBuilder()
        sb.append("=== Accessibility Service Debug ===\n")
        sb.append("Instance exists: ${instance != null}\n")
        sb.append("Service running: ${instance == this}\n")
        sb.append("Last focused node: ${lastFocusedNode != null}\n")
        if (lastFocusedNode != null) {
            sb.append("Last focused class: ${lastFocusedNode?.className}\n")
            sb.append("Last focused editable: ${lastFocusedNode?.let { isEditable(it) }}\n")
        }

        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            sb.append("Root node exists: true\n")
            val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            sb.append("Current focus node: ${focusNode != null}\n")
            if (focusNode != null) {
                sb.append("Current focus class: ${focusNode.className}\n")
                sb.append("Current focus editable: ${isEditable(focusNode)}\n")
                focusNode.recycle()
            }
            rootNode.recycle()
        } else {
            sb.append("Root node exists: false\n")
        }

        sb.append("=====================================")
        return sb.toString()
    }

    // Smart text insertion that tries direct methods first
    private fun insertTextDirectly(transcriptionText: String): Boolean {
        try {
            val trimmedPreview = transcriptionText.trim()
            if (trimmedPreview.isEmpty()) {
                Log.d("DictationAccessibility", "Received blank transcription, nothing to insert")
                return false
            }
            Log.d("DictationAccessibility", "Text prepared for insertion: '$trimmedPreview'")

            var insertSuccess = false

            Log.d("DictationAccessibility", "Trying direct text insertion (no clipboard)")
            val activeRootNode = rootInActiveWindow
            if (activeRootNode != null) {
                try {
                    val focusedNode = activeRootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focusedNode != null) {
                        try {
                            insertSuccess = tryDirectTextInsertion(focusedNode, transcriptionText)
                            if (insertSuccess) {
                                Log.d("DictationAccessibility", "SUCCESS: Direct text insertion worked (no clipboard used)")
                                return true
                            }
                        } finally {
                            focusedNode.recycle()
                        }
                    }
                } finally {
                    activeRootNode.recycle()
                }
            }

            if (!insertSuccess && lastFocusedNode != null) {
                Log.d("DictationAccessibility", "Trying direct insertion on cached node")
                insertSuccess = tryDirectTextInsertion(lastFocusedNode!!, transcriptionText)
                if (insertSuccess) {
                    Log.d("DictationAccessibility", "SUCCESS: Direct insertion on cached node worked (no clipboard used)")
                    return true
                }
            }

            if (!insertSuccess) {
                Log.d("DictationAccessibility", "Trying direct insertion on first editable node")
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        val editableNode = findFirstEditableNode(rootNode)
                        if (editableNode != null && isEditable(editableNode)) {
                            try {
                                insertSuccess = tryDirectTextInsertion(editableNode, transcriptionText)
                                if (insertSuccess) {
                                    Log.d("DictationAccessibility", "SUCCESS: Direct insertion on first editable worked (no clipboard used)")
                                    return true
                                }
                            } finally {
                                editableNode.recycle()
                            }
                        }
                    } finally {
                        rootNode.recycle()
                    }
                }
            }

            if (!insertSuccess) {
                Log.d("DictationAccessibility", "Direct insertion failed, falling back to clipboard method")
                insertSuccess = insertTextViaClipboard(transcriptionText)
            }

            return insertSuccess
        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error in direct text insertion", e)
            return false
        }
    }

    private fun tryDirectTextInsertion(node: AccessibilityNodeInfo?, dictatedText: String): Boolean {
        if (node == null || !isEditable(node)) {
            return false
        }
    
        try {
            val selectionStart = node.textSelectionStart
            val selectionEnd = node.textSelectionEnd
            val fullText = node.text?.toString() ?: ""
    
            // METHOD 1: Try to insert at the cursor position if selection info is available.
            if (selectionStart >= 0 && selectionEnd >= 0) {
                val start = min(selectionStart, selectionEnd).coerceAtLeast(0).coerceAtMost(fullText.length)
                val end = max(selectionStart, selectionEnd).coerceAtLeast(0).coerceAtMost(fullText.length)
                val context = buildInsertionContext(fullText, start, end)
                val formattedInsert = SmartTextInsertionFormatter.format(dictatedText, context)
                if (formattedInsert.isEmpty()) {
                    Log.d("DictationAccessibility", "Formatted insertion text is empty, skipping direct insert")
                    return false
                }
                val textBefore = fullText.substring(0, start)
                val textAfter = fullText.substring(end)
                val newText = textBefore + formattedInsert + textAfter
    
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    // After setting text, try to move cursor to the end of insertion.
                    val newCursorPos = textBefore.length + formattedInsert.length
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                    Log.d("DictationAccessibility", "SUCCESS: ACTION_SET_TEXT with cursor logic worked")
                    return true
                }
            }
            
            Log.d("DictationAccessibility", "Cursor-aware insertion failed or not possible, falling back to append/other methods.")
    
            // METHOD 2: Fallback to original append logic.
            // This is kept for nodes that might not report cursor position correctly but still work with SET_TEXT.
            val existingText = getActualUserText(node)
            val formattedInsert = formatForUnknownContext(existingText, dictatedText)
            if (formattedInsert.isEmpty()) return false
            val finalText = if (existingText.isNotEmpty()) existingText + formattedInsert else formattedInsert
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Log.d("DictationAccessibility", "SUCCESS: ACTION_SET_TEXT (append fallback) worked")
                return true
            }
    
            // Keep other fallbacks from original implementation
            // Try cursor positioning + set text (this seems redundant with the above, but might work differently)
            val cursorArguments = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, finalText.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, finalText.length)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArguments)
    
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Log.d("DictationAccessibility", "SUCCESS: Cursor positioning + ACTION_SET_TEXT worked")
                return true
            }
    
            // Try focus + click + set text for custom text inputs
            // Performance fix: Reduced delay and using SystemClock for efficiency
            if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                android.os.SystemClock.sleep(50) // Reduced from 100ms, using SystemClock for efficiency
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    Log.d("DictationAccessibility", "SUCCESS: Focus + click + set text worked")
                    return true
                }
            }

        } catch (e: Exception) {
            Log.w("DictationAccessibility", "Direct text insertion attempt failed", e)
        }

        return false
    }
    

    // Clipboard-based text insertion method (fallback only)
    private fun insertTextViaClipboard(transcriptionText: String): Boolean {
        Log.d("DictationAccessibility", "Starting clipboard-based text insertion: '$transcriptionText'")

        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Step 1: Save user's original clipboard content
            val originalClip = clipboardManager.primaryClip
            val originalText = originalClip?.getItemAt(0)?.text?.toString()
            Log.d("DictationAccessibility", "Saved original clipboard: '${originalText?.take(50)}...'")

            // Step 2: Prepare smart-formatted text for clipboard
            val textToInsert = prepareClipboardText(transcriptionText)
            if (textToInsert.isEmpty()) {
                Log.w("DictationAccessibility", "Clipboard insertion aborted: nothing to insert after formatting")
                return false
            }
            val transcriptionClip = ClipData.newPlainText("dictation_transcription", textToInsert)
            clipboardManager.setPrimaryClip(transcriptionClip)
            Log.d("DictationAccessibility", "Transcription placed in clipboard for fallback")

            // Step 3: Try multiple paste methods
            var pasteSuccess = false

            // Method 1: Try paste action on focused node
            val activeRootNode = rootInActiveWindow
            if (activeRootNode != null) {
                try {
                    val focusedNode = activeRootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focusedNode != null) {
                        pasteSuccess = focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        if (pasteSuccess) {
                            Log.d("DictationAccessibility", "SUCCESS: Focused node paste worked")
                        }
                        focusedNode.recycle()
                    }
                } finally {
                    activeRootNode.recycle()
                }
            }

            // Method 2: Try paste on cached focused node
            if (!pasteSuccess && lastFocusedNode != null) {
                Log.d("DictationAccessibility", "Trying cached node paste")
                pasteSuccess = lastFocusedNode!!.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                if (pasteSuccess) {
                    Log.d("DictationAccessibility", "SUCCESS: Cached node paste worked")
                }
            }

            // Method 3: Try focus + click + paste for stubborn apps
            // Performance fix: Reduced delays from 100ms to 50ms
            if (!pasteSuccess) {
                Log.d("DictationAccessibility", "Trying focus + click + paste")
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val editableNode = findFirstEditableNode(rootNode)
                    if (editableNode != null && isEditable(editableNode)) {
                        try {
                            if (editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                                android.os.SystemClock.sleep(50) // Reduced from 100ms
                                if (editableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                    android.os.SystemClock.sleep(50) // Reduced from 100ms
                                    pasteSuccess = editableNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                                    if (pasteSuccess) {
                                        Log.d("DictationAccessibility", "SUCCESS: Focus + click + paste worked")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("DictationAccessibility", "Focus + click + paste failed", e)
                        }
                        editableNode.recycle()
                    }
                    rootNode.recycle()
                }
            }

            // Step 4: Clean up clipboard
            if (pasteSuccess) {
                // Restore after short delay since we used clipboard
                restoreClipboardAfterDelay(clipboardManager, originalText, 500L)
            } else {
                Log.e("DictationAccessibility", "All clipboard paste methods failed!")
                // Restore clipboard immediately since paste failed
                restoreClipboardAfterDelay(clipboardManager, originalText, 0L)
            }

            return pasteSuccess

        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error in clipboard-based text insertion", e)
            return false
        }
    }

    private fun prepareClipboardText(rawText: String): String {
        val context = resolveCurrentInsertionContext()
        return SmartTextInsertionFormatter.format(rawText, context)
    }

    private fun resolveCurrentInsertionContext(): SmartTextInsertionFormatter.InsertionContext {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null) {
                val context = buildInsertionContext(focusedNode)
                focusedNode.recycle()
                rootNode.recycle()
                if (context != null) return context
            } else {
                rootNode.recycle()
            }
        }
        lastFocusedNode?.let { cached ->
            val context = buildInsertionContext(cached)
            if (context != null) return context
        }
        return SmartTextInsertionFormatter.defaultContext()
    }

    private fun buildInsertionContext(
        text: CharSequence?,
        selectionStart: Int,
        selectionEnd: Int
    ): SmartTextInsertionFormatter.InsertionContext {
        if (text.isNullOrEmpty()) {
            return SmartTextInsertionFormatter.defaultContext()
        }
        val safeStart = selectionStart.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(0, text.length)
        val before = if (safeStart > 0) text.subSequence(0, safeStart) else ""
        val after = if (safeEnd < text.length) text.subSequence(safeEnd, text.length) else ""
        return SmartTextInsertionFormatter.contextFrom(before, after)
    }

    private fun buildInsertionContext(node: AccessibilityNodeInfo): SmartTextInsertionFormatter.InsertionContext? {
        val text = node.text ?: return null
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (start < 0 || end < 0) return null
        val safeStart = min(start, end).coerceAtLeast(0).coerceAtMost(text.length)
        val safeEnd = max(start, end).coerceAtLeast(0).coerceAtMost(text.length)
        return buildInsertionContext(text, safeStart, safeEnd)
    }

    private fun formatForUnknownContext(existingText: String, dictatedText: String): String {
        val context = SmartTextInsertionFormatter.contextFrom(existingText, "")
        return SmartTextInsertionFormatter.format(dictatedText, context)
    }

    private fun restoreClipboardAfterDelay(clipboardManager: ClipboardManager, originalText: String?, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (originalText != null) {
                    // Restore user's original clipboard content
                    val restoreClip = ClipData.newPlainText("restored_clipboard", originalText)
                    clipboardManager.setPrimaryClip(restoreClip)
                    Log.d("DictationAccessibility", "Restored original clipboard content: '${originalText.take(50)}...'")
                } else {
                    // Clear clipboard if user had nothing before
                    val emptyClip = ClipData.newPlainText("empty", "")
                    clipboardManager.setPrimaryClip(emptyClip)
                    Log.d("DictationAccessibility", "Cleared clipboard (user had no content)")
                }
            } catch (e: Exception) {
                Log.e("DictationAccessibility", "Error restoring clipboard", e)
            }
        }, delayMs)
    }

    // Helper methods for text insertion (moved outside of receiver for reuse)
    private fun tryPasteToNode(node: AccessibilityNodeInfo?, textToAppend: String): Boolean {
        if (node == null) {
            Log.w("DictationAccessibility", "tryPasteToNode: node is null")
            return false
        }

        // Use our enhanced editable detection instead of just node.isEditable
        if (!isEditable(node)) {
            Log.w("DictationAccessibility", "tryPasteToNode: node is not detected as editable")
            return false
        }

        val className = node.className?.toString()
        val packageName = node.packageName?.toString()
        Log.d("DictationAccessibility", "Attempting to paste to: $className in $packageName")

        try {
            // Get existing text, filtering out placeholder text
            val existingText = getActualUserText(node)
            Log.d("DictationAccessibility", "Existing text: '$existingText'")

            // Combine existing text with new transcription (no added leading space)
            val finalText = if (existingText.isNotEmpty()) {
                existingText + textToAppend
            } else {
                textToAppend
            }

            Log.d("DictationAccessibility", "Final text to set: '$finalText'")

            // Try multiple NON-CLIPBOARD strategies first
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
            }

            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Log.d("DictationAccessibility", "SUCCESS: ACTION_SET_TEXT worked")
                return true
            }

            // Try to position cursor at end and set text
            Log.d("DictationAccessibility", "ACTION_SET_TEXT failed, trying cursor positioning")
            val currentText = node.text?.toString() ?: ""
            val cursorArguments = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, currentText.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentText.length)
            }

            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArguments)) {
                val pasteArguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, pasteArguments)) {
                    Log.d("DictationAccessibility", "SUCCESS: Cursor positioning + set text worked")
                    return true
                }
            }

            // Try focus + click + set text for custom text inputs (like Notion)
            // Performance fix: Reduced delays using SystemClock
            Log.d("DictationAccessibility", "Trying focus + click + set text for custom inputs")
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {

                    // Wait briefly for the input to become active (reduced from 100ms)
                    android.os.SystemClock.sleep(50)

                    // Try set text again after focus + click
                    val retryArguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
                    }
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, retryArguments)) {
                        Log.d("DictationAccessibility", "SUCCESS: Focus + click + set text worked")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w("DictationAccessibility", "Focus + click + set text failed", e)
            }

            // Try setting focus and then setting text again
            Log.d("DictationAccessibility", "Trying final focus + set text attempt")
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                    android.os.SystemClock.sleep(30) // Reduced from 50ms
                    val retryArguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
                    }
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, retryArguments)) {
                        Log.d("DictationAccessibility", "SUCCESS: Focus + retry SET_TEXT worked")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w("DictationAccessibility", "Focus + retry set text failed", e)
            }

            Log.d("DictationAccessibility", "All direct text insertion methods failed for node: ${node.className} in ${node.packageName}")
            return false

        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error in tryPasteToNode", e)
            return false
        }
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (isEditable(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findFirstEditableNode(child)
            child?.recycle()
            if (result != null) return result
        }

        return null
    }

    // Extract visible screen text for context (private implementation)
    private fun extractScreenText(): String? {
        try {
            Log.d("DictationAccessibility", "Starting screen text extraction...")

            // Try multiple approaches
            var screenText = extractFromAllWindows()
            if (screenText == null) {
                Log.d("DictationAccessibility", "All windows approach failed, trying active window...")
                screenText = extractFromActiveWindow()
            }

            return screenText

        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error extracting screen text", e)
            return null
        }
    }

    private fun extractFromAllWindows(): String? {
        try {
            val rootNodes = mutableListOf<AccessibilityNodeInfo>()

            // Get all windows
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val windowList = windows
                Log.d("DictationAccessibility", "Available windows: ${windowList?.size ?: 0}")
                windowList?.forEach { window ->
                    window?.root?.let { root ->
                        rootNodes.add(root)
                        Log.d("DictationAccessibility", "Added window root: ${root.packageName}")
                    }
                }
            }

            if (rootNodes.isEmpty()) {
                Log.d("DictationAccessibility", "No windows available")
                return null
            }

            return processTextNodes(rootNodes)

        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error in extractFromAllWindows", e)
            return null
        }
    }

    private fun extractFromActiveWindow(): String? {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.d("DictationAccessibility", "No active window available")
                return null
            }

            Log.d("DictationAccessibility", "Processing active window: ${rootNode.packageName}")
            return processTextNodes(listOf(rootNode))

        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error in extractFromActiveWindow", e)
            return null
        }
    }

    private fun processTextNodes(rootNodes: List<AccessibilityNodeInfo>): String? {
        Log.d("DictationAccessibility", "Processing ${rootNodes.size} root nodes...")
        val allText = mutableListOf<String>()

        try {
            for (rootNode in rootNodes) {
                extractTextFromNode(rootNode, allText)
            }

            Log.d("DictationAccessibility", "Raw text extracted: ${allText.size} items")
            allText.take(5).forEach { Log.d("DictationAccessibility", "Sample text: '$it'") }

            // Be less aggressive with filtering initially
            val filteredText = allText
                .filter { it.isNotBlank() }
                .filter { it.length in 3..200 } // Allow shorter text, skip very long blocks
                .distinct()
                .take(50) // Limit to 50 text elements
                .joinToString(" | ")

            Log.d("DictationAccessibility", "Filtered text (${filteredText.length} chars): ${filteredText.take(200)}...")

            return if (filteredText.length > 5) filteredText else {
                Log.d("DictationAccessibility", "Filtered text too short (${filteredText.length} chars), returning null")
                null
            }
        } finally {
            // Clean up root nodes collected for this extraction pass.
            for (rootNode in rootNodes) {
                try {
                    rootNode.recycle()
                } catch (e: Exception) {
                    Log.w("DictationAccessibility", "Error recycling node", e)
                }
            }
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?, textList: MutableList<String>) {
        if (node == null) return

        try {
            // Get text from this node
            val nodeText = node.text?.toString()?.trim()
            if (!nodeText.isNullOrBlank() && nodeText.length > 2 && nodeText.length <= 200) {
                textList.add(nodeText)
            }

            // Get content description
            val contentDesc = node.contentDescription?.toString()?.trim()
            if (!contentDesc.isNullOrBlank() && contentDesc.length > 2 && contentDesc.length <= 200) {
                textList.add(contentDesc)
            }

            // Recursively process children (limit depth to avoid performance issues)
            if (textList.size < 100) { // Stop if we have enough text
                for (i in 0 until minOf(node.childCount, 20)) { // Limit children processed
                    val child = node.getChild(i)
                    extractTextFromNode(child, textList)
                    child?.recycle()
                }
            }

        } catch (e: Exception) {
            Log.w("DictationAccessibility", "Error processing node for text extraction", e)
        }
    }

    private fun isCommonUIText(text: String): Boolean {
        val lowerText = text.lowercase()
        val commonUIElements = listOf(
            "back", "next", "done", "cancel", "ok", "close", "menu", "home", "search",
            "settings", "more", "share", "edit", "delete", "save", "send", "submit",
            "login", "sign in", "sign up", "password", "username", "email",
            "tab", "button", "checkbox", "switch", "slider", "loading", "refresh"
        )

        return commonUIElements.any { lowerText == it } ||
               lowerText.length <= 2 ||
               lowerText.matches("^[0-9\\s]*$".toRegex()) ||
               lowerText.startsWith("http") ||
               (lowerText.contains("@") && lowerText.contains("."))
    }

    /**
     * Determines whether the given text represents a placeholder rather than actual user input.
     * Uses MULTIPLE fallback strategies for robust detection across different app implementations.
     *
     * Detection strategy (in order of precedence):
     * 1. **Empty check** - Empty field is not placeholder, it's just empty
     * 2. **Hint text matching** (Language-agnostic) - If visible text matches hint text, it's a placeholder
     * 3. **Text selection state** (Language-agnostic) - If entire text is selected, likely a placeholder
     * 4. **Single-word heuristic** - If text is a single word and very short, likely placeholder
     * 5. **Cursor at start** - If cursor is at position 0, text might be placeholder
     * 6. **Cursor at end** (Real text indicator) - If cursor is at end, likely real user text
     * 7. **Default** - Treat as real user text if no other indicators suggest otherwise
     *
     * @param node The AccessibilityNodeInfo to analyze for placeholder text
     * @return Empty string if the text appears to be a placeholder, otherwise returns the actual text
     */
    private fun getActualUserText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        try {
            val selectionStart = node.textSelectionStart
            val selectionEnd = node.textSelectionEnd
            val nodeText = node.text?.toString() ?: ""
            val packageName = node.packageName?.toString()?.lowercase() ?: ""
            
            // STEP 0: If text is empty, return empty (not a placeholder, just an empty field)
            if (nodeText.isEmpty()) {
                Log.d("DictationAccessibility", "Field is empty, returning empty string")
                return ""
            }

            // STEP 1: PRIMARY - Check if text matches hint text (language-agnostic)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hintText = node.hintText?.toString()
                if (!hintText.isNullOrEmpty()) {
                    val normalizedText = nodeText.trim().lowercase()
                    val normalizedHint = hintText.trim().lowercase()
                    if (normalizedText == normalizedHint) {
                        Log.d("DictationAccessibility", "PLACEHOLDER: text matches hint ('$normalizedText')")
                        return ""
                    }
                }
            }

            // STEP 2: SECONDARY - Check if entire text is selected
            if (selectionStart == 0 && selectionEnd == nodeText.length && nodeText.isNotEmpty()) {
                Log.d("DictationAccessibility", "PLACEHOLDER: entire text selected ('$nodeText')")
                return ""
            }

            // STEP 3: ROBUST FALLBACK - Check if text looks like a placeholder heuristically
            // This handles cases where hint text API doesn't work or isn't exposed
            if (looksLikePlaceholder(nodeText, selectionStart, selectionEnd, packageName)) {
                Log.d("DictationAccessibility", "PLACEHOLDER: heuristic detection ('$nodeText')")
                return ""
            }

            // STEP 4: CURSOR AT END - Strong indicator of real user text
            if (selectionStart == selectionEnd && selectionStart == nodeText.length) {
                Log.d("DictationAccessibility", "REAL TEXT: cursor at end ('${nodeText.take(50)}')")
                return nodeText
            }

            // STEP 5: DEFAULT - Treat as real user text
            Log.d(
                "DictationAccessibility",
                "REAL TEXT (default): '${nodeText.take(50)}' (hint: '${node.hintText}', sel: $selectionStart-$selectionEnd)"
            )
            return nodeText

        } catch (e: Exception) {
            Log.e("DictationAccessibility", "Error checking for placeholder text", e)
            return node.text?.toString() ?: ""
        }
    }

    /**
     * Heuristic check to determine if text looks like a placeholder.
     * Used as a fallback when hint text API doesn't work.
     *
     * Indicators that text is likely a placeholder:
     * - Very short (1-20 characters)
     * - Single word (no spaces)
     * - Cursor at position 0 (not edited yet)
     * - Common placeholder patterns (generic words)
     *
     * @return true if text appears to be placeholder, false otherwise
     */
    private fun looksLikePlaceholder(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        packageName: String
    ): Boolean {
        val trimmed = text.trim()
        
        // Check 1: Very short text (typical placeholders are 1-20 chars)
        if (trimmed.length > 20) {
            return false // Real text is usually longer
        }
        
        // Check 2: Single word with no spaces (common for placeholders like "Message", "Nachricht")
        val isSingleWord = !trimmed.contains(" ") && !trimmed.contains("\n")
        
        // Check 3: Cursor at the start (placeholder hasn't been edited)
        val cursorAtStart = (selectionStart == 0 && selectionEnd == 0)
        
        // Check 4: For messaging apps, if it's a single short word, very likely placeholder
        val isMessagingApp = packageName.contains("whatsapp") || 
                            packageName.contains("telegram") ||
                            packageName.contains("signal") ||
                            packageName.contains("messenger")
        
        // Heuristic: Single word + (short length OR cursor at start OR messaging app)
        if (isSingleWord && trimmed.length <= 15) {
            if (cursorAtStart || isMessagingApp) {
                Log.d(
                    "DictationAccessibility",
                    "Heuristic match: singleWord=$isSingleWord, len=${trimmed.length}, cursorStart=$cursorAtStart, msgApp=$isMessagingApp"
                )
                return true
            }
        }
        
        return false
    }

}
