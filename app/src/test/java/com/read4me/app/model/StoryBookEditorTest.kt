package com.read4me.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoryBookEditorTest {
    private val first = File("first.m4a")
    private val second = File("second.m4a")

    @Test fun aggregateClipCrossesFilesAndMapsOffsets() {
        val source = listOf(NarrationSegment(first, 100, 1_100), NarrationSegment(second, 500, 2_000))
        assertEquals(
            listOf(NarrationSegment(first, 900, 1_100), NarrationSegment(second, 500, 1_300)),
            NarrationTimeline.clip(source, 800, 1_800),
        )
        assertEquals(NarrationTimeline.Position(1, 700), NarrationTimeline.map(source, 1_200))
    }

    @Test fun spreadExposesSourceAndNonDestructivelyTrimmedEffectiveSegments() {
        val book = book(listOf(marker("one", listOf(NarrationSegment(first, 0, 1_000), NarrationSegment(second, 0, 1_000)))))
        val edited = StoryBookEditor.trimNarration(book, "one", 750, 1_500)
        assertEquals(book.markers[0].segments, edited.spreads[0].sourceSegments)
        assertEquals(listOf(NarrationSegment(first, 750, 1_000), NarrationSegment(second, 0, 500)), edited.spreads[0].effectiveSegments)
        assertEquals(750, edited.playableDurationMs)
    }

    @Test fun sharedBoundaryRequiresSameContiguousPhysicalFile() {
        assertTrue(StoryBookEditor.shareBoundary(marker("a", listOf(NarrationSegment(first, 0, 1_000))), marker("b", listOf(NarrationSegment(first, 1_000, 2_000)))))
        assertFalse(StoryBookEditor.shareBoundary(marker("a", listOf(NarrationSegment(first, 0, 1_000))), marker("b", listOf(NarrationSegment(second, 1_000, 2_000)))))
    }

    @Test fun movingTooShortSharedBoundaryIsRejectedWithoutThrowing() {
        val source = book(listOf(
            marker("a", listOf(NarrationSegment(first, 0, 400))),
            marker("b", listOf(NarrationSegment(first, 400, 800))),
        ))

        assertEquals(source, StoryBookEditor.moveBoundary(source, "a", "b", 400))
    }

    @Test fun mergeConcatenatesNarrationAndReorderPreservesIdentity() {
        val book = book(listOf(marker("a", listOf(NarrationSegment(first, 0, 800))), marker("b", listOf(NarrationSegment(second, 0, 900)))))
        val merged = StoryBookEditor.mergeWithNext(book, "a")
        assertEquals(listOf(NarrationSegment(first, 0, 800), NarrationSegment(second, 0, 900)), merged.markers.single().segments)
        val three = book.copy(markers = book.markers + marker("c", listOf(NarrationSegment(first, 2_000, 3_000))))
        assertEquals(listOf("c", "a", "b"), StoryBookEditor.reorder(three, "c", 0).markers.map { it.spreadId })
    }

    @Test fun insertionSplitsInsidePhysicalSegmentWithoutReencoding() {
        val book = book(listOf(marker("a", listOf(NarrationSegment(first, 100, 2_100)))))
        val inserted = StoryBookEditor.insertAfter(book, "a", 750, marker("b", emptyList()))
        assertEquals(listOf(NarrationSegment(first, 100, 850)), inserted.markers[0].segments)
        assertEquals(listOf(NarrationSegment(first, 850, 2_100)), inserted.markers[1].segments)
    }

    @Test fun reorderPreservesResumeCursor() {
        val source = book(listOf(marker("a", listOf(NarrationSegment(first, 0, 800))), marker("b", listOf(NarrationSegment(second, 0, 900))))).copy(resumeSpreadId = "a")
        assertEquals("a", StoryBookEditor.reorder(source, "a", 1).resumeSpreadId)
    }

    @Test fun deletingCursorPicksNextThenPreviousDeterministically() {
        val source = book(listOf(marker("a", listOf(NarrationSegment(first, 0, 800))), marker("b", listOf(NarrationSegment(second, 0, 900))), marker("c", listOf(NarrationSegment(first, 900, 1800))))).copy(resumeSpreadId = "b")
        assertEquals("c", StoryBookEditor.delete(source, "b").resumeSpreadId)
        assertEquals("b", StoryBookEditor.delete(source.copy(resumeSpreadId = "c"), "c").resumeSpreadId)
    }

    @Test fun mergeMapsRightCursorToLeft() {
        val source = book(listOf(marker("a", listOf(NarrationSegment(first, 0, 800))), marker("b", listOf(NarrationSegment(second, 0, 900))))).copy(resumeSpreadId = "b")
        assertEquals("a", StoryBookEditor.mergeWithNext(source, "a").resumeSpreadId)
    }

    @Test fun referencePrimaryDeleteAndReplace() {
        val one = SpreadReference(File("one.jpg"), referenceId = "one")
        val two = SpreadReference(File("two.jpg"), referenceId = "two")
        val source = book(listOf(marker("a", listOf(NarrationSegment(first, 0, 800))).copy(references = listOf(one))))
        val added = StoryBookEditor.addReference(source, "a", two)
        assertEquals("two", StoryBookEditor.setPrimaryReference(added, "a", "two").markers[0].references[0].referenceId)
        assertEquals(1, StoryBookEditor.deleteReference(added, "a", "one").markers[0].references.size)
        assertEquals(File("new.jpg"), StoryBookEditor.replaceReference(source, "a", File("new.jpg")).markers[0].imageFile)
    }

    @Test fun editSessionUndoRestoresPreviousBook() {
        val source = book(listOf(marker("a", listOf(NarrationSegment(first, 0, 800))), marker("b", listOf(NarrationSegment(second, 0, 900)))))
        val session = StoryEditSession(source)
        session.apply(StoryBookEditor.reorder(source, "b", 0)) {}
        assertEquals(source, session.undo {})
    }

    private fun marker(id: String, segments: List<NarrationSegment>) = SpreadMarker(0, MarkerSource.MANUAL, emptyList(), segments = segments, spreadId = id)
    private fun book(markers: List<SpreadMarker>) = StoryBook("id", "title", File("."), first, 0, markers)
}
