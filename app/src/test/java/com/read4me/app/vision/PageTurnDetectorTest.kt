package com.read4me.app.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTurnDetectorTest {
    private val detector = PageTurnDetector(
        motionThreshold = 15f,
        stableThreshold = 3f,
        pageChangeThreshold = 8f,
        stableFramesRequired = 3,
    )

    @Test
    fun stableFramesDoNotTriggerPageTurn() {
        detector.accept(frame(30))

        repeat(10) {
            assertFalse(detector.accept(frame(31)).pageTurned)
        }
    }

    @Test
    fun motionThenDifferentStablePageTriggersOnce() {
        detector.accept(frame(20))
        detector.accept(frame(90))
        detector.accept(frame(150))

        assertFalse(detector.accept(frame(150)).pageTurned)
        assertFalse(detector.accept(frame(150)).pageTurned)
        assertTrue(detector.accept(frame(150)).pageTurned)
        assertFalse(detector.accept(frame(150)).pageTurned)
    }

    @Test
    fun temporaryOcclusionReturningToSamePageDoesNotTrigger() {
        detector.accept(frame(45))
        detector.accept(frame(180))
        detector.accept(frame(45))

        repeat(4) {
            assertFalse(detector.accept(frame(45)).pageTurned)
        }
    }

    @Test
    fun resetMakesTheFirstFrameAfterOrientationChangeTheNewBaseline() {
        detector.accept(frame(30))
        detector.accept(frame(90))
        detector.reset()

        assertFalse(detector.accept(frame(200)).pageTurned)
        repeat(6) { assertFalse(detector.accept(frame(200)).pageTurned) }
    }

    @Test
    fun resetAdvancesGenerationSoQueuedResultsCanBeRejected() {
        val beforeReset = detector.accept(frame(30))
        detector.reset()

        val afterReset = detector.accept(frame(30))

        assertTrue(afterReset.generation > beforeReset.generation)
    }

    private fun frame(value: Int) = ByteArray(120) { value.toByte() }
}
