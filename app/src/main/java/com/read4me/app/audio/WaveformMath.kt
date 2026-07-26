package com.read4me.app.audio

import kotlin.math.abs

/** Codec-independent peak bucketing, kept separate so the signal math is JVM-testable. */
object WaveformMath {
    fun peaks(samples: FloatArray, bucketCount: Int): FloatArray {
        require(bucketCount > 0)
        if (samples.isEmpty()) return FloatArray(bucketCount)
        val result = FloatArray(bucketCount)
        samples.forEachIndexed { index, sample ->
            val bucket = ((index.toLong() * bucketCount) / samples.size).toInt().coerceAtMost(bucketCount - 1)
            result[bucket] = maxOf(result[bucket], abs(sample))
        }
        return normalize(result)
    }

    fun normalize(peaks: FloatArray): FloatArray {
        val maximum = peaks.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        if (maximum <= 0f) return FloatArray(peaks.size)
        return FloatArray(peaks.size) { peaks[it].coerceAtLeast(0f) / maximum }
    }
}
