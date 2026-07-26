package com.read4me.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformMathTest {
    @Test fun bucketsAbsolutePeaksAndNormalizes() {
        val result = WaveformMath.peaks(floatArrayOf(-1f, .5f, 2f, -4f), 2)
        assertArrayEquals(floatArrayOf(.25f, 1f), result, .0001f)
    }

    @Test fun silenceHasRequestedBoundedSize() {
        val result = WaveformMath.peaks(FloatArray(10), 3)
        assertEquals(3, result.size)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), result, 0f)
    }
}
