package com.read4me.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun suggestsPaddedNonDestructiveBoundariesAroundSustainedSound() {
        val suggestion = WaveformMath.suggestSilenceTrim(
            floatArrayOf(0f, 0f, .8f, .9f, .7f, .6f, .8f, .9f, 0f, 0f),
            sourceStartMs = 0L,
            sourceEndMs = 10_000L,
            paddingMs = 100L,
        )

        assertEquals(1_900L, suggestion?.startMs)
        assertEquals(8_100L, suggestion?.endMs)
    }

    @Test fun ignoresAnIsolatedClickAtTheStart() {
        val suggestion = WaveformMath.suggestSilenceTrim(
            floatArrayOf(1f, 0f, 0f, .8f, .9f, .7f, 0f, 0f),
            sourceStartMs = 0L,
            sourceEndMs = 8_000L,
            paddingMs = 0L,
        )

        assertEquals(3_000L, suggestion?.startMs)
        assertEquals(6_000L, suggestion?.endMs)
    }

    @Test fun doesNotSuggestTrimmingContinuousNarrationOrTotalSilence() {
        assertNull(WaveformMath.suggestSilenceTrim(FloatArray(10) { 1f }, 0L, 10_000L))
        assertNull(WaveformMath.suggestSilenceTrim(FloatArray(10), 0L, 10_000L))
    }
}
