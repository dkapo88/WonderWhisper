package com.slumdog88.dictationkeyboardai.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedPeriodCorrectionEngineTest {

    @Test
    fun computeTargetSegment_previousPeriodOnSameLine() {
        val text = "First sentence. next bit."
        val snapshot = TypedPeriodCorrectionEngine.EditorSnapshot(text, text.length, text.length)

        val result = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, text.length)

        assertNotNull(result)
        assertEquals(" next bit.", result!!.rawSegment)
        assertEquals("next bit.", result.trimmedSegment)
        assertEquals(" ", result.leadingWhitespace)
    }

    @Test
    fun computeTargetSegment_noPreviousPeriodOnLineUsesLineStart() {
        val text = "Hello world."
        val snapshot = TypedPeriodCorrectionEngine.EditorSnapshot(text, text.length, text.length)

        val result = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, text.length)

        assertNotNull(result)
        assertEquals(0, result!!.start)
        assertEquals("Hello world.", result.rawSegment)
    }

    @Test
    fun computeTargetSegment_previousPeriodOnPriorLineIgnored() {
        val text = "Line one.\nSecond line done."
        val snapshot = TypedPeriodCorrectionEngine.EditorSnapshot(text, text.length, text.length)

        val result = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, text.length)

        assertNotNull(result)
        assertEquals("Second line done.", result!!.rawSegment)
    }

    @Test
    fun computeTargetSegment_questionMarkTriggersAndUsesNearestSentenceEnder() {
        val text = "First sentence! next question?"
        val snapshot = TypedPeriodCorrectionEngine.EditorSnapshot(text, text.length, text.length)

        val result = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, text.length)

        assertNotNull(result)
        assertEquals(" next question?", result!!.rawSegment)
        assertEquals("next question?", result.trimmedSegment)
    }

    @Test
    fun computeTargetSegment_exclamationMarkTriggersAndUsesLineStartWhenNeeded() {
        val text = "Shout now!"
        val snapshot = TypedPeriodCorrectionEngine.EditorSnapshot(text, text.length, text.length)

        val result = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, text.length)

        assertNotNull(result)
        assertEquals("Shout now!", result!!.rawSegment)
    }

    @Test
    fun computeTargetSegment_capturesLeadingWhitespaceAndTrimmedText() {
        val text = "Hi.   spaced text."
        val snapshot = TypedPeriodCorrectionEngine.EditorSnapshot(text, text.length, text.length)

        val result = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, text.length)

        assertNotNull(result)
        assertEquals("   spaced text.", result!!.rawSegment)
        assertEquals("spaced text.", result.trimmedSegment)
        assertEquals("   ", result.leadingWhitespace)
    }

    @Test
    fun computeTargetSegment_decimalPointIsSkipped() {
        val text = "Value 3."
        val snapshot = TypedPeriodCorrectionEngine.EditorSnapshot(text, text.length, text.length)

        val result = TypedPeriodCorrectionEngine.computeTargetSegment(snapshot, text.length)

        assertNull(result)
    }

    @Test
    fun findExactReplacementRange_prefersHintMatch() {
        val text = "A. segment."
        val original = " segment."

        val range = TypedPeriodCorrectionEngine.findExactReplacementRange(text, original, 2)

        assertEquals(2..10, range)
    }

    @Test
    fun findExactReplacementRange_fallsBackToUniqueRelocatedMatch() {
        val text = "ZZ Hello there. YY"
        val original = "Hello there."

        val range = TypedPeriodCorrectionEngine.findExactReplacementRange(text, original, 0)

        assertEquals(3..14, range)
    }

    @Test
    fun findExactReplacementRange_ambiguousDuplicateReturnsNull() {
        val text = "Repeat. Repeat."
        val original = "Repeat."

        val range = TypedPeriodCorrectionEngine.findExactReplacementRange(text, original, 99)

        assertNull(range)
    }

    @Test
    fun buildContextPayload_truncatesDeterministically() {
        val fullText = "a".repeat(120) + "MIDDLE" + "z".repeat(120)
        val payload = TypedPeriodCorrectionEngine.buildContextPayload(fullText, 80)

        assertEquals(80, payload.length)
        assertTrue(payload.contains("...[truncated]..."))
        assertTrue(payload.startsWith("a"))
        assertTrue(payload.endsWith("z"))
    }
}
