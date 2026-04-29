package com.slumdog88.dictationkeyboardai.ui

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

class KeyboardDetectionManager(
    private val context: Context,
    private val callback: KeyboardDetectionCallback
) {

    interface KeyboardDetectionCallback {
        fun onKeyboardVisibilityChanged(visible: Boolean)
        fun isKeyboardAwareBubbleEnabled(): Boolean
        fun isRecording(): Boolean
    }

    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    fun setupKeyboardDetection(view: View?) {
        // This functionality is now handled by the polling mechanism in BubbleOverlayService
    }

    fun cleanupKeyboardDetection(view: View?) {
        // This functionality is now handled by the polling mechanism in BubbleOverlayService
    }
}
