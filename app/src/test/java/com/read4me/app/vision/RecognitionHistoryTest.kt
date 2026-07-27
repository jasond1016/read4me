package com.read4me.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionHistoryTest {
    @Test fun summarizesFailuresCorrectionsAndConfirmedInliersPerSpread() {
        val events = listOf(
            event(RecognitionEvent.Outcome.CONFIRMED, 20),
            event(RecognitionEvent.Outcome.CONFIRMED, 30),
            event(RecognitionEvent.Outcome.LOW_INLIERS, 5),
            event(RecognitionEvent.Outcome.AMBIGUOUS, 18),
            event(RecognitionEvent.Outcome.LOW_INLIERS, 4),
            event(RecognitionEvent.Outcome.MANUAL_CORRECTION, 0),
        )
        val summary = RecognitionHistory.summarize(events).single()
        assertEquals(2, summary.confirmations)
        assertEquals(3, summary.failures)
        assertEquals(1, summary.manualCorrections)
        assertEquals(25, summary.averageConfirmedInliers)
        assertTrue(summary.needsNewReference)
    }

    @Test fun aSmallNumberOfFailuresDoesNotRecommendRecapture() {
        assertFalse(RecognitionHistory.summarize(listOf(event(RecognitionEvent.Outcome.LOW_INLIERS, 4))).single().needsNewReference)
    }

    @Test fun addingAReferenceStartsANewRepairWindow() {
        val summary = RecognitionHistory.summarize(listOf(
            event(RecognitionEvent.Outcome.CONFIRMED, 18),
            event(RecognitionEvent.Outcome.LOW_INLIERS, 4),
            event(RecognitionEvent.Outcome.LOW_INLIERS, 5),
            event(RecognitionEvent.Outcome.LOW_INLIERS, 6),
            event(RecognitionEvent.Outcome.REFERENCE_ADDED, 0),
            event(RecognitionEvent.Outcome.CONFIRMED, 24),
        )).single()

        assertEquals(2, summary.confirmations)
        assertEquals(0, summary.failures)
        assertEquals(21, summary.averageConfirmedInliers)
        assertFalse(summary.needsNewReference)
    }

    private fun event(outcome: RecognitionEvent.Outcome, inliers: Int) = RecognitionEvent(
        1L, "book", "spread", outcome, inliers, null, 10L, "LIBRARY",
    )
}
