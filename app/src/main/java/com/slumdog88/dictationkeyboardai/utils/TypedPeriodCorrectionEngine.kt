package com.slumdog88.dictationkeyboardai.utils

/**
 * Pure logic for period-triggered typed autocorrection.
 */
object TypedPeriodCorrectionEngine {
    const val MIN_SEGMENT_LENGTH = 3
    private val sentenceEnders = setOf('.', '?', '!')

    data class EditorSnapshot(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    data class SegmentTarget(
        val start: Int,
        val end: Int,
        val rawSegment: String,
        val trimmedSegment: String,
        val leadingWhitespace: String
    )

    /**
     * Computes the sentence segment ending at [cursorAfterPeriod], bounded to current line only.
     */
    fun computeTargetSegment(
        snapshot: EditorSnapshot,
        cursorAfterPeriod: Int
    ): SegmentTarget? {
        val text = snapshot.text
        if (text.isEmpty()) return null

        val cursor = cursorAfterPeriod.coerceIn(0, text.length)
        if (cursor <= 0) return null

        val periodIndex = cursor - 1
        if (periodIndex !in text.indices) return null
        val typedTerminator = text[periodIndex]
        if (!sentenceEnders.contains(typedTerminator)) return null

        // Guardrail: skip likely decimal input such as "3."
        val beforePeriodIndex = periodIndex - 1
        if (typedTerminator == '.' && beforePeriodIndex in text.indices && text[beforePeriodIndex].isDigit()) return null

        val lineStart = text.lastIndexOf('\n', periodIndex).let { idx ->
            if (idx == -1) 0 else idx + 1
        }

        // Find previous sentence ender on the same line, excluding the just-typed terminator.
        val previousPeriodSearchFrom = periodIndex - 1
        var previousPeriod = -1
        if (previousPeriodSearchFrom >= lineStart) {
            for (i in previousPeriodSearchFrom downTo lineStart) {
                if (sentenceEnders.contains(text[i])) {
                    previousPeriod = i
                    break
                }
            }
        }

        val start = if (previousPeriod >= lineStart) previousPeriod + 1 else lineStart
        val end = cursor
        if (end <= start) return null

        val rawSegment = text.substring(start, end)
        val trimmedSegment = rawSegment.trim()
        if (trimmedSegment.length < MIN_SEGMENT_LENGTH) return null
        if (!trimmedSegment.any { it.isLetter() }) return null

        return SegmentTarget(
            start = start,
            end = end,
            rawSegment = rawSegment,
            trimmedSegment = trimmedSegment,
            leadingWhitespace = rawSegment.takeWhile { it.isWhitespace() }
        )
    }

    /**
     * Returns an inclusive [IntRange] for exact replacement, or null when ambiguous/unmatched.
     */
    fun findExactReplacementRange(
        currentText: String,
        originalRawSegment: String,
        originalStartHint: Int
    ): IntRange? {
        if (originalRawSegment.isEmpty()) return null
        val len = originalRawSegment.length

        if (originalStartHint >= 0 && originalStartHint + len <= currentText.length) {
            val hinted = currentText.substring(originalStartHint, originalStartHint + len)
            if (hinted == originalRawSegment) {
                return originalStartHint..(originalStartHint + len - 1)
            }
        }

        val matches = mutableListOf<Int>()
        var index = currentText.indexOf(originalRawSegment)
        while (index >= 0) {
            matches.add(index)
            index = currentText.indexOf(originalRawSegment, startIndex = index + 1)
        }

        return if (matches.size == 1) {
            val start = matches.first()
            start..(start + len - 1)
        } else {
            null
        }
    }

    /**
     * Builds deterministic whole-field context with bounded size.
     */
    fun buildContextPayload(fullText: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (fullText.length <= maxChars) return fullText

        val marker = "\n...[truncated]...\n"
        if (maxChars <= marker.length + 2) {
            return fullText.take(maxChars)
        }

        val budget = maxChars - marker.length
        val headLen = budget / 2
        val tailLen = budget - headLen
        return buildString(maxChars) {
            append(fullText.take(headLen))
            append(marker)
            append(fullText.takeLast(tailLen))
        }
    }
}
