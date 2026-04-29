package com.slumdog88.dictationkeyboardai.utils
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartTextInsertionFormatterTest {

    @Test
    fun midSentenceInsertionAddsLeadingSpaceAndTrimsAfter() {
        val result = format("Hello", "there", " world")
        assertEquals(" there", result)
    }

    @Test
    fun afterPeriodCapitalizesFirstLetter() {
        val result = format("Hi.", "how are you", "")
        assertEquals(" How are you", result)
    }

    @Test
    fun uppercaseSingleWordAtEndAddsPeriod() {
        val result = format("I am", "Testing", "")
        assertEquals(" Testing.", result)
    }

    @Test
    fun uppercaseInsertionBetweenWordsAddsSpacesButNoPeriod() {
        val result = format("Hi.", "hello", "world")
        assertEquals(" Hello ", result)
    }

    @Test
    fun multiWordSentenceCaseGetsDecapitalizedMidSentence() {
        val result = format("Start", "The middle part", "")
        assertEquals(" the middle part", result)
    }

    @Test
    fun capitalDictationAfterPeriodKeepsCaseNoPeriodWhenNotTitleCase() {
        val result = format("End.", "New sentence", "")
        assertEquals(" New sentence", result)
    }

    @Test
    fun titleCaseDictationAfterPeriodAddsPeriod() {
        val result = format("End.", "New Sentence", "")
        assertEquals(" New Sentence.", result)
    }

    @Test
    fun trimsExistingWhitespaceWhenSurroundingsAlreadyHaveSpaces() {
        val result = format("Hello ", "  spaced input  ", "world")
        assertEquals("spaced input ", result)
    }

    @Test
    fun existingTerminalPunctuationPreventsExtraPeriod() {
        val result = format("Testing", "Something!", "")
        assertEquals(" Something!", result)
    }

    @Test
    fun addsPeriodWhenNextWordStartsWithCapital() {
        val result = format("Hello", "there", " Next")
        assertEquals(" there.", result)
    }

    private fun format(before: String, dictated: String, after: String): String {
        val context = SmartTextInsertionFormatter.contextFrom(before, after)
        return SmartTextInsertionFormatter.format(dictated, context)
    }
}
