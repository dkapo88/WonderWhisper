package com.slumdog88.dictationkeyboardai.offline

/**
 * Lightweight growable float buffer to accumulate PCM samples without boxing.
 */
class FloatArrayBuilder(initialCapacity: Int = 16_000) {
    private var buffer = FloatArray(initialCapacity.coerceAtLeast(1))
    var size: Int = 0
        private set

    fun append(value: Float) {
        ensureCapacity(size + 1)
        buffer[size] = value
        size += 1
    }

    fun appendAll(values: FloatArray) {
        ensureCapacity(size + values.size)
        System.arraycopy(values, 0, buffer, size, values.size)
        size += values.size
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity <= buffer.size) return
        var newSize = if (buffer.size > 0) buffer.size else 1
        while (newSize < minCapacity && newSize < Int.MAX_VALUE) {
            newSize = if (newSize <= Int.MAX_VALUE / 2) newSize * 2 else Int.MAX_VALUE
        }
        if (newSize < minCapacity) {
            newSize = minCapacity
        }
        buffer = buffer.copyOf(newSize)
    }

    fun toFloatArray(): FloatArray = buffer.copyOf(size)

    fun clear() {
        size = 0
    }
}
