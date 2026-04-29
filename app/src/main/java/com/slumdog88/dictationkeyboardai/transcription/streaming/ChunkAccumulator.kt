package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.util.Log
import com.slumdog88.dictationkeyboardai.offline.OfflineWavWriter
import com.slumdog88.dictationkeyboardai.utils.AudioFileManager
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.max

data class ChunkResult(
    val id: UUID,
    val file: File,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val frameCount: Int
)

class ChunkAccumulator(
    private val audioFileManager: AudioFileManager,
    private val sampleRate: Int,
    private val preRollDurationMs: Int = 200
) {
    private val buffer = ShortArrayBuffer()
    private val preRollFrames: ArrayDeque<ShortArray> = ArrayDeque()
    private val preRollFrameCounts: ArrayDeque<Int> = ArrayDeque()
    private var collecting = false
    private var chunkStartTime: Long = 0L
    private var lastFrameEndTime: Long = 0L
    private var framesCollected: Int = 0
    private var preRollSamples: Int = 0
    private val preRollSampleLimit: Int = max(
        sampleRate * preRollDurationMs / 1000,
        sampleRate / 20
    )

    fun onFrame(frame: AudioFrame, events: List<VadEvent>): ChunkResult? {
        var shouldAppendFrame = collecting
        var result: ChunkResult? = null

        for (event in events) {
            when (event) {
                VadEvent.SpeechStarted -> {
                    val adjustedStart = max(0L, frame.timestampMs - currentPreRollDurationMs())
                    startChunk(adjustedStart)
                    appendPreRollToChunk()
                    shouldAppendFrame = true
                }
                VadEvent.SpeechEnded -> {
                    shouldAppendFrame = false
                    result = finalizeChunk(frame.timestampMs)
                }
            }
        }

        if (collecting && shouldAppendFrame) {
            buffer.append(frame.samples)
            framesCollected += 1
            lastFrameEndTime = frame.timestampMs + frame.durationMs
        }

        if (!collecting) {
            addToPreRoll(frame.samples)
        }

        return result
    }

    fun flushPendingChunk(): ChunkResult? {
        if (!collecting) return null
        val endTimestamp = if (lastFrameEndTime > 0L) lastFrameEndTime else chunkStartTime
        return finalizeChunk(endTimestamp)
    }

    fun reset() {
        collecting = false
        buffer.clear()
        framesCollected = 0
        chunkStartTime = 0L
        lastFrameEndTime = 0L
        clearPreRoll()
    }

    private fun startChunk(timestampMs: Long) {
        collecting = true
        buffer.clear()
        framesCollected = 0
        chunkStartTime = timestampMs
        lastFrameEndTime = timestampMs
        Log.d("ChunkAccumulator", "Started chunk at $timestampMs ms")
    }

    private fun finalizeChunk(endTimestampMs: Long): ChunkResult? {
        if (!collecting) return null

        val samples = buffer.toShortArray()
        collecting = false
        buffer.clear()

        if (samples.isEmpty()) {
            framesCollected = 0
            return null
        }

        val durationMs = if (lastFrameEndTime > chunkStartTime) {
            lastFrameEndTime - chunkStartTime
        } else {
            samples.size * 1000L / sampleRate
        }

        return try {
            val floatSamples = FloatArray(samples.size) { idx ->
                samples[idx] / 32768f
            }
            val file = audioFileManager.createTempPcmAudioFile()
            OfflineWavWriter.write16BitMono(file, floatSamples, sampleRate)
            ChunkResult(
                id = UUID.randomUUID(),
                file = file,
                startTimeMs = chunkStartTime,
                endTimeMs = endTimestampMs,
                durationMs = durationMs,
                frameCount = framesCollected
            )
        } catch (e: Exception) {
            Log.e("ChunkAccumulator", "Failed to finalize chunk", e)
            null
        } finally {
            framesCollected = 0
        }
    }

    private fun addToPreRoll(samples: ShortArray) {
        val copy = samples.copyOf()
        preRollFrames.addLast(copy)
        preRollFrameCounts.addLast(1)
        preRollSamples += copy.size
        while (preRollSamples > preRollSampleLimit && preRollFrames.isNotEmpty()) {
            val removed = preRollFrames.removeFirst()
            preRollSamples -= removed.size
            preRollFrameCounts.removeFirst()
        }
    }

    private fun appendPreRollToChunk() {
        if (preRollFrames.isEmpty()) return
        while (preRollFrames.isNotEmpty()) {
            val samples = preRollFrames.removeFirst()
            buffer.append(samples)
            framesCollected += preRollFrameCounts.removeFirst()
        }
        preRollSamples = 0
    }

    private fun clearPreRoll() {
        preRollFrames.clear()
        preRollFrameCounts.clear()
        preRollSamples = 0
    }

    private fun currentPreRollDurationMs(): Long {
        if (preRollSamples <= 0) return 0L
        return preRollSamples * 1000L / sampleRate
    }
}
