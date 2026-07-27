package com.read4me.app.audio

import kotlin.math.abs

/** Codec-independent peak bucketing, kept separate so the signal math is JVM-testable. */
object WaveformMath {
    data class TrimSuggestion(val startMs: Long, val endMs: Long) {
        fun removedFromStart(sourceStartMs: Long): Long = (startMs - sourceStartMs).coerceAtLeast(0L)
        fun removedFromEnd(sourceEndMs: Long): Long = (sourceEndMs - endMs).coerceAtLeast(0L)
    }

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

    /** Returns conservative, padded boundaries. The caller decides whether to apply them. */
    fun suggestSilenceTrim(
        peaks: FloatArray,
        sourceStartMs: Long,
        sourceEndMs: Long,
        minimumRemovableMs: Long = 300L,
        paddingMs: Long = 120L,
    ): TrimSuggestion? {
        if (peaks.size < 4 || sourceEndMs - sourceStartMs < 500L || peaks.maxOrNull() == 0f) return null
        val sorted = peaks.sorted()
        val noiseFloor = sorted[(sorted.lastIndex * 0.2f).toInt()]
        val activeThreshold = maxOf(0.08f, noiseFloor * 2.5f).coerceAtMost(0.22f)

        fun sustainedStart(): Int? = (0 until peaks.lastIndex).firstOrNull { index ->
            peaks[index] >= activeThreshold && peaks[index + 1] >= activeThreshold
        }
        fun sustainedEnd(): Int? = (peaks.lastIndex downTo 1).firstOrNull { index ->
            peaks[index] >= activeThreshold && peaks[index - 1] >= activeThreshold
        }

        val firstActive = sustainedStart() ?: return null
        val lastActive = sustainedEnd() ?: return null
        if (lastActive <= firstActive) return null
        val durationMs = sourceEndMs - sourceStartMs
        val paddedStart = (sourceStartMs + durationMs * firstActive / peaks.size - paddingMs)
            .coerceAtLeast(sourceStartMs)
        val paddedEnd = (sourceStartMs + durationMs * (lastActive + 1L) / peaks.size + paddingMs)
            .coerceAtMost(sourceEndMs)
        val suggestedStart = paddedStart.takeIf { it - sourceStartMs >= minimumRemovableMs } ?: sourceStartMs
        val suggestedEnd = paddedEnd.takeIf { sourceEndMs - it >= minimumRemovableMs } ?: sourceEndMs
        if (suggestedStart == sourceStartMs && suggestedEnd == sourceEndMs) return null
        if (suggestedEnd - suggestedStart < 500L) return null
        return TrimSuggestion(suggestedStart, suggestedEnd)
    }
}
