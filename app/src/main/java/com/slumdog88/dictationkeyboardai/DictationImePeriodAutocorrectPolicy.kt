package com.slumdog88.dictationkeyboardai

import android.text.InputType

/**
 * Pure policy helpers for period-triggered typed autocorrect.
 */
object DictationImePeriodAutocorrectPolicy {
    private val triggerCharacters = setOf(".", "?", "!")
    private val sentenceEnders = setOf('.', '?', '!')

    fun shouldTrigger(
        enabled: Boolean,
        insertedText: String,
        hadSelectionBeforeCommit: Boolean,
        inputType: Int
    ): Boolean {
        if (!enabled) return false
        if (!triggerCharacters.contains(insertedText)) return false
        if (hadSelectionBeforeCommit) return false
        if (isPasswordInputType(inputType)) return false
        return true
    }

    fun isPasswordInputType(inputType: Int): Boolean {
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> true
            else -> false
        }
    }

    /**
     * Detect chained punctuation patterns where autocorrect should be skipped.
     */
    fun shouldSkipForChainedSentenceEnders(rawSegment: String, nextChar: Char?): Boolean {
        val trimmed = rawSegment.trimEnd()
        if (trimmed.endsWith("...")) return true
        if (trimmed.endsWith("?!") || trimmed.endsWith("!?")) return true
        if (nextChar != null && sentenceEnders.contains(nextChar)) return true
        return false
    }

    /**
     * Returns adjusted selection after replacing [replaceStart, replaceEndExclusive).
     */
    fun adjustSelectionAfterReplacement(
        selectionStart: Int,
        selectionEnd: Int,
        replaceStart: Int,
        replaceEndExclusive: Int,
        replacementLength: Int
    ): Pair<Int, Int> {
        fun adjust(index: Int): Int {
            return when {
                index <= replaceStart -> index
                index >= replaceEndExclusive -> index + (replacementLength - (replaceEndExclusive - replaceStart))
                else -> replaceStart + replacementLength
            }
        }

        return adjust(selectionStart) to adjust(selectionEnd)
    }
}
