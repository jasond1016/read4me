package com.read4me.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformAllocationTest {
    @Test fun exactCountWithMoreSegmentsThanBuckets() {
        val result = allocateWaveformBuckets(List(10) { 1L }, 3)
        assertEquals(3, result.sum())
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 0, 0, 0, 0), result)
    }

    @Test fun unequalDurationsUseLargestRemainder() {
        assertEquals(listOf(2, 5), allocateWaveformBuckets(listOf(1, 3), 7))
    }

    @Test fun zeroDurationClipsReceiveNoBuckets() {
        assertEquals(listOf(0, 4, 0), allocateWaveformBuckets(listOf(0, 10, 0), 4))
    }
}
