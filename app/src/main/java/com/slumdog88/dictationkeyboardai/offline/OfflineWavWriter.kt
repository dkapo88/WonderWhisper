package com.slumdog88.dictationkeyboardai.offline

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OfflineWavWriter {
    fun write16BitMono(file: File, samples: FloatArray, sampleRate: Int) {
        val pcm = OfflineAudioUtils.floatsToShorts(samples)
        val dataSize = pcm.size * 2
        val totalSize = 44 + dataSize
        val byteRate = sampleRate * 2 // mono, 16-bit
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize - 8)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // Audio format = PCM
        buffer.putShort(1) // Channels
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(2) // Block align
        buffer.putShort(16) // Bits per sample

        // data chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (sample in pcm) {
            buffer.putShort(sample)
        }

        buffer.flip()
        FileOutputStream(file).use { fos ->
            fos.channel.write(buffer)
        }
    }
}
