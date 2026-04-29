package com.slumdog88.dictationkeyboardai.ui.keyboard.logic

import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Handles basic text processing logic:
 * - Auto-capitalization
 * - Double-space period
 */
class TextLogic(
    private val inputConnectionProvider: () -> InputConnection?,
    private val editorInfoProvider: () -> EditorInfo?
) {
    private var lastSpaceTime: Long = 0
    private val DOUBLE_SPACE_THRESHOLD_MS = 500L
    private var justInsertedPeriod = false

    /**
     * Returns true if the next character should be capitalized.
     */
    fun shouldAutoCapitalize(): Boolean {
        val ic = inputConnectionProvider() ?: return false
        val info = editorInfoProvider() ?: return false

        // Check if input field expects auto-caps
        val inputType = info.inputType
        if (inputType == InputType.TYPE_NULL) return false
        
        // If it's not a text class, don't auto cap
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false
        }

        // Check for specific flags
        var reqModes = 0
        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            reqModes = reqModes or TextUtils.CAP_MODE_CHARACTERS
        }
        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
            reqModes = reqModes or TextUtils.CAP_MODE_WORDS
        }
        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
            reqModes = reqModes or TextUtils.CAP_MODE_SENTENCES
        }
        
        // If no specific caps flags are set, we shouldn't auto-cap
        if (reqModes == 0) return false

        val capsMode = ic.getCursorCapsMode(reqModes)
        return capsMode != 0
    }

    /**
     * Handles space key logic.
     * Returns true if a double-space period replacement was performed.
     */
    fun onSpacePressed(): Boolean {
        val currentTime = System.currentTimeMillis()
        val delta = currentTime - lastSpaceTime
        lastSpaceTime = currentTime

        if (delta < DOUBLE_SPACE_THRESHOLD_MS && !justInsertedPeriod) {
            val ic = inputConnectionProvider() ?: return false
            
            // Check context before cursor (up to 2 chars)
            // We need to see "X " where X is not a period or space
            val textBefore = ic.getTextBeforeCursor(2, 0) ?: ""
            
            if (textBefore.length == 2 && textBefore.endsWith(" ")) {
                val charBeforeSpace = textBefore[0]
                if (charBeforeSpace != '.' && charBeforeSpace != ' ' && 
                    charBeforeSpace != '?' && charBeforeSpace != '!') {
                    
                    // Valid context: "Word " -> "Word. "
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    justInsertedPeriod = true
                    return true
                }
            }
        }
        justInsertedPeriod = false
        return false
    }
}
