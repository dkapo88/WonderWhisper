package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class AudioCapturePipeline(
    private val config: AudioCaptureConfig,
    private val scope: CoroutineScope
) {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var frameChannel: Channel<AudioFrame>? = null

    fun start(): ReceiveChannel<AudioFrame> {
        if (captureJob != null) {
            throw IllegalStateException("Audio capture already started")
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.encoding
        )
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Unable to determine minimum buffer size")
        }

        val bufferSizeInBytes = max(minBuffer, config.bufferSizeBytes)
        val record = tryCreateRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufferSizeInBytes)
            ?: tryCreateRecorder(MediaRecorder.AudioSource.MIC, bufferSizeInBytes)
        if (record == null) {
            throw IllegalStateException("AudioRecord could not be initialized")
        }

        audioRecord = record
        val channel = Channel<AudioFrame>(capacity = Channel.BUFFERED)
        frameChannel = channel

        val frameSize = config.frameSizeSamples
        val readBuffer = ShortArray(bufferSizeInBytes / 2)
        val frameBuffer = ShortArray(frameSize)
        var frameBufferFill = 0
        var framesEmitted: Long = 0
        val frameDurationMs = config.frameDurationMs
        val captureStart = SystemClock.elapsedRealtime()

        captureJob = scope.launch(Dispatchers.IO) {
            try {
                record.startRecording()
                Log.d("AudioCapturePipeline", "AudioRecord started")

                while (isActive) {
                    val read = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue

                    var offset = 0
                    while (offset < read) {
                        val remaining = read - offset
                        val needed = frameSize - frameBufferFill
                        val toCopy = min(remaining, needed)
                        System.arraycopy(readBuffer, offset, frameBuffer, frameBufferFill, toCopy)
                        frameBufferFill += toCopy
                        offset += toCopy

                        if (frameBufferFill == frameSize) {
                            val samples = ShortArray(frameSize)
                            System.arraycopy(frameBuffer, 0, samples, 0, frameSize)
                            frameBufferFill = 0
                            val timestamp = captureStart + framesEmitted * frameDurationMs
                            framesEmitted++
                            val frame = AudioFrame(samples, timestamp, frameDurationMs)
                            channel.trySend(frame).isSuccess
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("AudioCapturePipeline", "Capture loop error", t)
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {
                }
                record.release()
                audioRecord = null
                channel.close()
                Log.d("AudioCapturePipeline", "AudioRecord stopped and resources released")
            }
        }

        return channel
    }

    suspend fun stop() {
        frameChannel?.close()
        frameChannel = null
        captureJob?.cancelAndJoin()
        captureJob = null
        audioRecord?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        audioRecord = null
    }

    private fun tryCreateRecorder(audioSource: Int, bufferSizeInBytes: Int): AudioRecord? {
        return try {
            val recorder = AudioRecord(
                audioSource,
                config.sampleRate,
                config.channelConfig,
                config.encoding,
                bufferSizeInBytes
            )
            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                recorder
            } else {
                recorder.release()
                null
            }
        } catch (t: Throwable) {
            Log.w("AudioCapturePipeline", "Failed to init AudioRecord with source=$audioSource", t)
            null
        }
    }
}
