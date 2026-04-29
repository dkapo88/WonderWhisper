package com.slumdog88.dictationkeyboardai.offline

import kotlin.math.max
import kotlin.math.roundToInt

object OfflineAudioUtils {

    fun resample(samples: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (samples.isEmpty() || sourceRate <= 0 || sourceRate == targetRate) {
            return samples
        }

        val ratio = targetRate.toDouble() / sourceRate.toDouble()
        val outputSize = max(1, (samples.size * ratio).roundToInt())
        val output = FloatArray(outputSize)

        for (i in 0 until outputSize) {
            val srcPosition = i / ratio
            val index = srcPosition.toInt()
            val frac = srcPosition - index

            val sampleA = samples.getOrNull(index) ?: 0f
            val sampleB = samples.getOrNull(index + 1) ?: sampleA
            output[i] = ((1.0 - frac) * sampleA + frac * sampleB).toFloat()
        }

        return output
    }

    fun floatsToShorts(samples: FloatArray): ShortArray {
        val output = ShortArray(samples.size)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1f, 1f)
            output[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return output
    }

    /**
     * Trim leading and trailing silence from PCM float samples.
     * - Frame-based energy thresholding with dynamic threshold from noise floor.
     * - Adds small padding to avoid cutting phonemes.
     */
    fun trimSilence(
        samples: FloatArray,
        sampleRate: Int,
        frameMs: Int = 20,
        startPadMs: Int = 50,
        endPadMs: Int = 100
    ): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0) return samples

        val frameSize = (sampleRate * (frameMs / 1000.0)).toInt().coerceAtLeast(1)
        val nFrames = (samples.size + frameSize - 1) / frameSize

        // Compute per-frame energy (mean absolute value)
        val energies = FloatArray(nFrames)
        var idx = 0
        for (f in 0 until nFrames) {
            var sum = 0.0
            var count = 0
            val end = (idx + frameSize).coerceAtMost(samples.size)
            while (idx < end) {
                sum += kotlin.math.abs(samples[idx].toDouble())
                idx++
                count++
            }
            energies[f] = if (count > 0) (sum / count).toFloat() else 0f
        }

        if (energies.isEmpty()) return samples

        // Dynamic threshold: 20th percentile of energies, clamped to [0.002, 0.02]
        val sorted = energies.copyOf().apply { sort() }
        val p20 = sorted[(sorted.lastIndex * 0.2f).toInt().coerceIn(0, sorted.lastIndex)]
        val threshold = p20.coerceIn(0.002f, 0.02f)

        // Find first/last frame above threshold
        var firstFrame = 0
        while (firstFrame < nFrames && energies[firstFrame] < threshold) firstFrame++

        var lastFrame = nFrames - 1
        while (lastFrame >= 0 && energies[lastFrame] < threshold) lastFrame--

        if (firstFrame >= lastFrame) {
            // All silence
            return FloatArray(0)
        }

        val startPad = (sampleRate * (startPadMs / 1000.0)).toInt()
        val endPad = (sampleRate * (endPadMs / 1000.0)).toInt()

        val startIdx = (firstFrame * frameSize - startPad).coerceAtLeast(0)
        val endIdx = ((lastFrame + 1) * frameSize + endPad).coerceAtMost(samples.size)

        return samples.copyOfRange(startIdx, endIdx)
    }
}
