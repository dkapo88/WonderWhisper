package com.slumdog88.dictationkeyboardai.offline

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.max

class OfflinePcmRecorder(
    private val sampleRate: Int = 16_000
) {
    private var audioRecord: AudioRecord? = null
    private var readJob: Job? = null
    private var builder = FloatArrayBuilder()

    fun start() {
        builder = FloatArrayBuilder()
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuffer, sampleRate * 2)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord could not be initialized")
        }
        audioRecord = record
        record.startRecording()

        val shortBuffer = ShortArray(bufferSize / 2)
        readJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    val read = record.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue
                    for (i in 0 until read) {
                        builder.append(shortBuffer[i] / 32768f)
                    }
                }
            } catch (_: CancellationException) {
            } catch (t: Throwable) {
                Log.e("OfflinePcmRecorder", "Error while reading audio samples", t)
            }
        }
    }

    fun stop(): FloatArray {
        val record = audioRecord ?: return FloatArray(0)
        runBlocking {
            readJob?.cancelAndJoin()
        }
        try {
            record.stop()
        } catch (_: Exception) {
        }
        record.release()
        audioRecord = null
        readJob = null
        return builder.toFloatArray()
    }
}
