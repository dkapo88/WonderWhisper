package com.slumdog88.dictationkeyboardai.utils

/**
 * Applies context-aware spacing, casing, and punctuation when inserting dictated text into an
 * existing buffer. The formatter intentionally infers more detail than the single-character spec by
 * tracking the nearest non-whitespace characters so that common editor situations such as
 * "period + space" are handled correctly.
 */
object SmartTextInsertionFormatter {
    private val sentenceTerminators = setOf('.', '!', '?')

    data class InsertionContext(
        val immediateBefore: Char?,
        val immediateAfter: Char?,
        val previousNonWhitespace: Char?,
        val nextNonWhitespace: Char?,
        val hasTextBefore: Boolean,
        val hasTextAfter: Boolean
    ) {
        companion object {
            val EMPTY = InsertionContext(null, null, null, null, false, false)

            fun from(before: CharSequence?, after: CharSequence?): InsertionContext {
                val beforeStr = before?.toString() ?: ""
                val afterStr = after?.toString() ?: ""
                return InsertionContext(
                    immediateBefore = beforeStr.lastOrNull(),
                    immediateAfter = afterStr.firstOrNull(),
                    previousNonWhitespace = beforeStr.lastOrNull { !it.isWhitespace() },
                    nextNonWhitespace = afterStr.firstOrNull { !it.isWhitespace() },
                    hasTextBefore = beforeStr.isNotEmpty(),
                    hasTextAfter = afterStr.isNotEmpty()
                )
            }
        }
    }

    fun defaultContext() = InsertionContext.EMPTY

    fun contextFrom(before: CharSequence?, after: CharSequence?) =
        InsertionContext.from(before, after)

    fun format(dictatedText: String, context: InsertionContext): String {
        val trimmed = dictatedText.trim()
        if (trimmed.isEmpty()) return ""

        val alphaInfo = analyzeAlphabeticCharacters(trimmed)
        var workingText = trimmed

        val atSentenceBoundary = isSentenceBoundaryBefore(context)
        var decapitalized = false

        workingText = when {
            atSentenceBoundary -> capitalizeFirstAlphabetic(workingText)
            shouldDecapitalize(alphaInfo, context, workingText) -> {
                decapitalized = true
                decapitalizeFirstAlphabetic(workingText)
            }
            else -> workingText
        }

        val builder = StringBuilder()
        if (shouldInsertSpaceBefore(context.immediateBefore)) {
            builder.append(' ')
        }

        builder.append(workingText)

        if (shouldAppendPeriod(workingText, alphaInfo, context, decapitalized, atSentenceBoundary)) {
            builder.append('.')
        }

        if (shouldInsertSpaceAfter(context.immediateAfter)) {
            builder.append(' ')
        }

        return builder.toString()
    }

    private fun analyzeAlphabeticCharacters(text: String): AlphabeticInfo? {
        var firstAlphaIndex = -1
        var firstAlphaUppercase = false
        var hasAdditionalUppercase = false

        text.forEachIndexed { index, c ->
            if (c.isLetter()) {
                if (firstAlphaIndex == -1) {
                    firstAlphaIndex = index
                    firstAlphaUppercase = c.isUpperCase()
                } else if (c.isUpperCase()) {
                    hasAdditionalUppercase = true
                }
            }
        }

        if (firstAlphaIndex == -1) return null
        return AlphabeticInfo(
            firstIsUppercase = firstAlphaUppercase,
            hasAdditionalUppercase = hasAdditionalUppercase
        )
    }

    private fun capitalizeFirstAlphabetic(text: String): String {
        val chars = text.toCharArray()
        for (i in chars.indices) {
            if (chars[i].isLetter()) {
                chars[i] = chars[i].uppercaseChar()
                return String(chars)
            }
        }
        return text
    }

    private fun decapitalizeFirstAlphabetic(text: String): String {
        val chars = text.toCharArray()
        for (i in chars.indices) {
            if (chars[i].isLetter()) {
                chars[i] = chars[i].lowercaseChar()
                return String(chars)
            }
        }
        return text
    }

    private fun isSentenceBoundaryBefore(context: InsertionContext): Boolean {
        return !context.hasTextBefore ||
            context.previousNonWhitespace == null ||
            sentenceTerminators.contains(context.previousNonWhitespace)
    }

    private fun shouldDecapitalize(
        alphaInfo: AlphabeticInfo?,
        context: InsertionContext,
        text: String
    ): Boolean {
        if (alphaInfo == null || !alphaInfo.firstIsUppercase) return false
        if (!context.hasTextBefore) return false
        if (context.previousNonWhitespace != null && sentenceTerminators.contains(context.previousNonWhitespace)) {
            return false
        }
        if (!text.any { it.isWhitespace() }) return false
        if (alphaInfo.hasAdditionalUppercase) return false
        val firstWord = extractFirstWord(text)
        if (firstWord == "I") return false
        return true
    }

    private fun shouldAppendPeriod(
        workingText: String,
        alphaInfo: AlphabeticInfo?,
        context: InsertionContext,
        decapitalized: Boolean,
        atSentenceBoundary: Boolean
    ): Boolean {
        if (alphaInfo == null || !alphaInfo.firstIsUppercase) return false
        if (decapitalized) return false
        if (hasTerminalPunctuation(workingText)) return false

        val uppercaseNextWord = context.nextNonWhitespace?.let { it.isLetter() && it.isUpperCase() } == true
        if (uppercaseNextWord) return true

        val hasFollowingContent = context.hasTextAfter && context.nextNonWhitespace != null
        if (hasFollowingContent) return false

        return atSentenceBoundary || !context.hasTextAfter
    }

    private fun hasTerminalPunctuation(text: String): Boolean {
        val lastNonWhitespaceIndex = text.indexOfLast { !it.isWhitespace() }
        if (lastNonWhitespaceIndex == -1) return false
        return sentenceTerminators.contains(text[lastNonWhitespaceIndex])
    }

    private fun shouldInsertSpaceBefore(beforeChar: Char?): Boolean {
        return beforeChar != null && !beforeChar.isWhitespace()
    }

    private fun shouldInsertSpaceAfter(afterChar: Char?): Boolean {
        return afterChar != null && !afterChar.isWhitespace()
    }

    private fun extractFirstWord(text: String): String {
        val builder = StringBuilder()
        for (c in text) {
            if (c.isLetter()) {
                builder.append(c)
            } else {
                break
            }
        }
        return builder.toString()
    }

    private data class AlphabeticInfo(
        val firstIsUppercase: Boolean,
        val hasAdditionalUppercase: Boolean
    )
}
