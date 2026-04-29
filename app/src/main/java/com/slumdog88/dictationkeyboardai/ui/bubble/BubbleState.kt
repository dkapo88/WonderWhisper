package com.slumdog88.dictationkeyboardai.ui.bubble

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import com.slumdog88.dictationkeyboardai.ui.theme.PastelBlue

/**
 * Represents the different states of the bubble overlay.
 */
sealed class BubbleState {
    /**
     * Bubble is idle, waiting for user interaction.
     */
    data object Idle : BubbleState()

    /**
     * Bubble is actively recording audio.
     * @param amplitude Current normalized amplitude (0-1)
     * @param amplitudeHistory Recent amplitude samples for ring visualization
     * @param durationMs Recording duration in milliseconds
     */
    data class Recording(
        val amplitude: Float = 0f,
        val amplitudeHistory: List<Float> = emptyList(),
        val durationMs: Long = 0L
    ) : BubbleState()

    /**
     * Bubble is processing the recording (transcription/AI).
     * @param statusMessage Status text to display
     */
    data class Processing(
        val statusMessage: String = "Processing..."
    ) : BubbleState()

    /**
     * Quick action menu is open.
     * @param previousState The state before menu was opened (to restore on close)
     * @param menuDirection Direction the menu expands (LEFT or RIGHT)
     * @param selectedAction Currently highlighted action (for accessibility)
     */
    data class MenuOpen(
        val previousState: BubbleState,
        val menuDirection: MenuDirection,
        val selectedAction: QuickAction? = null
    ) : BubbleState()
}

/**
 * Available quick actions in the bubble menu.
 */
enum class QuickAction {
    CANCEL_RECORDING,
    DISMISS_MENU,
    SELECT_ALL,
    REPROCESS,
    TOGGLE_AI,
    OPEN_SETTINGS
}

/**
 * Direction for the quick action menu arc.
 * Determines which side of the bubble the menu buttons appear.
 */
enum class MenuDirection {
    LEFT,   // Menu arc on left side (when bubble is near right screen edge)
    RIGHT   // Menu arc on right side (when bubble is near left screen edge)
}

/**
 * Configuration for bubble appearance and position.
 */
data class BubbleConfig(
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val position: IntOffset = IntOffset.Zero,
    val accentColor: Color = PastelBlue
)

/**
 * Circular buffer for storing amplitude history.
 */
class AmplitudeBuffer(private val maxSize: Int = 24) {
    private val buffer = ArrayDeque<Float>(maxSize)

    fun add(normalized: Float) {
        if (buffer.size >= maxSize) {
            buffer.removeFirst()
        }
        buffer.addLast(normalized)
    }

    fun getHistory(): List<Float> = buffer.toList()

    fun clear() = buffer.clear()

    companion object {
        /**
         * Normalize raw amplitude (0-32767) to 0-1 range with logarithmic scaling.
         */
        fun normalizeAmplitude(raw: Int): Float {
            val maxAmplitude = 32767f
            val normalized = (raw / maxAmplitude).coerceIn(0f, 1f)
            // Logarithmic scaling for better visual representation
            return kotlin.math.ln(1 + normalized * 9) / kotlin.math.ln(10f)
        }
    }
}
