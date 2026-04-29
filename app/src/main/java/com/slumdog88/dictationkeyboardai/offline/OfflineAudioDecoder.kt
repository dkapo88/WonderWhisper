package com.slumdog88.dictationkeyboardai.offline

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OfflineAudioDecoder {
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val TIMEOUT_US = 10000L

    suspend fun decodeToMono16kFloatArray(audioFile: File): FloatArray =
        withContext(Dispatchers.IO) {
            require(audioFile.exists()) { "Audio file not found: ${audioFile.absolutePath}" }

            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(audioFile.absolutePath)
                val trackIndex = selectAudioTrack(extractor)
                require(trackIndex >= 0) { "No audio track found in ${audioFile.name}" }

                extractor.selectTrack(trackIndex)
                val inputFormat = extractor.getTrackFormat(trackIndex)

                val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                    ?: throw IllegalStateException("Audio track missing MIME type")
                val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(inputFormat, null, null, 0)
                codec.start()

                val pcmBuilder = FloatArrayBuilder()
                val bufferInfo = MediaCodec.BufferInfo()

                var sawInputEOS = false
                var sawOutputEOS = false

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    sawInputEOS = true
                                } else {
                                    val presentationTimeUs = extractor.sampleTime
                                    codec.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        sampleSize,
                                        presentationTimeUs,
                                        0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outputBufferIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                val chunk = ByteArray(bufferInfo.size)
                                outputBuffer.get(chunk)
                                outputBuffer.clear()
                                val chunkFloats = chunkToFloats(chunk, channelCount)
                                pcmBuilder.appendAll(chunkFloats)
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEOS = true
                            }
                        }

                        outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // Ignored – we continue using the format info captured earlier.
                        }

                        outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet.
                        }
                    }
                }

                val floatArray = pcmBuilder.toFloatArray()
                if (sourceSampleRate == TARGET_SAMPLE_RATE) {
                    floatArray
                } else {
                    OfflineAudioUtils.resample(floatArray, sourceSampleRate, TARGET_SAMPLE_RATE)
                }
            } finally {
                try {
                    codec?.stop()
                } catch (ignored: Exception) {
                }
                try {
                    codec?.release()
                } catch (ignored: Exception) {
                }
                extractor.release()
            }
        }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return index
            }
        }
        return -1
    }

    private fun chunkToFloats(data: ByteArray, channels: Int): FloatArray {
        if (channels <= 0) {
            return FloatArray(0)
        }
        val shortBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalSamples = shortBuffer.remaining()
        if (totalSamples == 0) return FloatArray(0)

        val shortArray = ShortArray(totalSamples)
        shortBuffer.get(shortArray)

        val frames = totalSamples / channels
        val output = FloatArray(frames)
        var index = 0
        for (frame in 0 until frames) {
            var acc = 0f
            for (channel in 0 until channels) {
                val sample = shortArray[index++]
                acc += sample / 32768f
            }
            output[frame] = acc / channels
        }
        return output
    }

}
