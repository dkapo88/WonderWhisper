package com.slumdog88.dictationkeyboardai.transcription.streaming

class ShortArrayBuffer(initialCapacity: Int = 4096) {
    private var data: ShortArray = ShortArray(initialCapacity)
    private var size: Int = 0

    fun append(samples: ShortArray) {
        ensureCapacity(size + samples.size)
        System.arraycopy(samples, 0, data, size, samples.size)
        size += samples.size
    }

    fun toShortArray(): ShortArray = data.copyOf(size)

    fun clear() {
        size = 0
    }

    fun length(): Int = size

    private fun ensureCapacity(capacity: Int) {
        if (capacity <= data.size) return
        var newSize = data.size
        while (newSize < capacity) {
            newSize = newSize * 2
        }
        data = data.copyOf(newSize)
    }
}
