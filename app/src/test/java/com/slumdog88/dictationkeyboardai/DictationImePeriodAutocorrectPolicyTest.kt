package com.slumdog88.dictationkeyboardai

import android.text.InputType
import com.slumdog88.dictationkeyboardai.utils.TypedPeriodCorrectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationImePeriodAutocorrectPolicyTest {

    @Test
    fun triggerPolicyMatrix() {
        assertFalse(
            DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = false,
                insertedText = ".",
                hadSelectionBeforeCommit = false,
                inputType = InputType.TYPE_CLASS_TEXT
            )
        )

        assertFalse(
            DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = true,
                insertedText = "a",
                hadSelectionBeforeCommit = false,
                inputType = InputType.TYPE_CLASS_TEXT
            )
        )

        assertFalse(
            DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = true,
                insertedText = ".",
                hadSelectionBeforeCommit = true,
                inputType = InputType.TYPE_CLASS_TEXT
            )
        )

        assertFalse(
            DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = true,
                insertedText = ".",
                hadSelectionBeforeCommit = false,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
        )

        assertTrue(
            DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = true,
                insertedText = ".",
                hadSelectionBeforeCommit = false,
                inputType = InputType.TYPE_CLASS_TEXT
            )
        )

        assertTrue(
            DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = true,
                insertedText = "?",
                hadSelectionBeforeCommit = false,
                inputType = InputType.TYPE_CLASS_TEXT
            )
        )

        assertTrue(
            DictationImePeriodAutocorrectPolicy.shouldTrigger(
                enabled = true,
                insertedText = "!",
                hadSelectionBeforeCommit = false,
                inputType = InputType.TYPE_CLASS_TEXT
            )
        )
    }

    @Test
    fun staleReplacementPolicy_missingExactSegmentSkips() {
        val range = TypedPeriodCorrectionEngine.findExactReplacementRange(
            currentText = "Completely changed text.",
            originalRawSegment = "Original sentence.",
            originalStartHint = 0
        )

        assertNull(range)
    }

    @Test
    fun cursorAdjustmentMath_handlesLengthChanges() {
        val (afterStart, afterEnd) = DictationImePeriodAutocorrectPolicy.adjustSelectionAfterReplacement(
            selectionStart = 12,
            selectionEnd = 12,
            replaceStart = 4,
            replaceEndExclusive = 9,
            replacementLength = 2
        )
        assertEquals(9, afterStart)
        assertEquals(9, afterEnd)

        val (insideStart, insideEnd) = DictationImePeriodAutocorrectPolicy.adjustSelectionAfterReplacement(
            selectionStart = 6,
            selectionEnd = 8,
            replaceStart = 4,
            replaceEndExclusive = 9,
            replacementLength = 7
        )
        assertEquals(11, insideStart)
        assertEquals(11, insideEnd)
    }

    @Test
    fun chainedSentenceEndersAreSkipped() {
        assertTrue(
            DictationImePeriodAutocorrectPolicy.shouldSkipForChainedSentenceEnders(
                rawSegment = "Wait...",
                nextChar = null
            )
        )
        assertTrue(
            DictationImePeriodAutocorrectPolicy.shouldSkipForChainedSentenceEnders(
                rawSegment = "Really?!",
                nextChar = null
            )
        )
        assertTrue(
            DictationImePeriodAutocorrectPolicy.shouldSkipForChainedSentenceEnders(
                rawSegment = "What?",
                nextChar = '!'
            )
        )
        assertFalse(
            DictationImePeriodAutocorrectPolicy.shouldSkipForChainedSentenceEnders(
                rawSegment = "All good.",
                nextChar = null
            )
        )
    }
}
