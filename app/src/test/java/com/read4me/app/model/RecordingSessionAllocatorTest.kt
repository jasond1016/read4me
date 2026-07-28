package com.read4me.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RecordingSessionAllocatorTest {
    private val first = File("first.m4a")
    private val second = File("second.m4a")

    @Test fun oneClipSpansPages() {
        val result = allocateRecordingSession(first, 3_000, listOf(SessionBoundary("a", 0), SessionBoundary("b", 1_200)))!!
        assertEquals(listOf(NarrationSegment(first, 0, 1_200)), result["a"])
        assertEquals(listOf(NarrationSegment(first, 1_200, 3_000)), result["b"])
    }

    @Test fun pauseMidSpreadThenSecondClipAppendsToSameSpread() {
        val old = allocateRecordingSession(first, 1_000, listOf(SessionBoundary("a", 0)))!!["a"].orEmpty()
        val resumed = allocateRecordingSession(second, 900, listOf(SessionBoundary("a", 0)))!!["a"].orEmpty()
        assertEquals(listOf(NarrationSegment(first, 0, 1_000), NarrationSegment(second, 0, 900)), old + resumed)
    }

    @Test fun invalidShortSessionDoesNotPublish() {
        assertNull(allocateRecordingSession(first, 799, listOf(SessionBoundary("a", 0))))
    }

    @Test fun pendingCaptureIsOmittedUntilPublished() {
        val durable = listOf(SessionBoundary("a", 0))
        assertEquals(durable, publishPendingMarker(durable, null, 4))
        assertEquals(durable + SessionBoundary("b", 900), publishPendingMarker(durable, PendingMarker(4, "b", 900), 4))
    }

    @Test fun lateCaptureCannotMutateNewSession() {
        val durable = listOf(SessionBoundary("a", 0))
        assertEquals(durable, publishPendingMarker(durable, PendingMarker(3, "old", 900), 4))
    }

    @Test fun unresolvedTailIsAllocatedToLastDurableBoundary() {
        val result = allocateRecordingSession(first, 2_000, listOf(SessionBoundary("a", 0)))!!
        assertEquals(listOf(NarrationSegment(first, 0, 2_000)), result["a"])
    }

    @Test fun returningToEarlierSpreadPreservesEverySegmentInOrder() {
        val result = allocateRecordingSession(
            first,
            3_000,
            listOf(SessionBoundary("a", 0), SessionBoundary("b", 1_000), SessionBoundary("a", 2_000)),
        )!!

        assertEquals(
            listOf(NarrationSegment(first, 0, 1_000), NarrationSegment(first, 2_000, 3_000)),
            result["a"],
        )
        assertEquals(listOf(NarrationSegment(first, 1_000, 2_000)), result["b"])
    }
}
