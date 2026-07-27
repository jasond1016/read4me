package com.read4me.app.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StoryBookEditorTest {
    @Test
    fun replacingReferencePreservesTimingAudioAndOtherMarkers() {
        val source = book()
        val replacement = File("replacement.jpg")
        val edited = StoryBookEditor.replaceReference(source, "two", replacement, byteArrayOf(1, 2))

        assertEquals(replacement, edited.markers[1].imageFile)
        assertEquals(listOf(0L, 1_000L, 2_000L), edited.markers.map { it.timestampMs })
        assertEquals(source.audioFile, edited.audioFile)
        assertEquals(source.markers[0], edited.markers[0])
    }

    @Test
    fun replacingMissingReferenceOrdinalDoesNothing() {
        val source = book()
        assertEquals(source, StoryBookEditor.replaceReference(source, "missing", File("replacement.jpg")))
    }

    @Test
    fun movingBoundaryKeepsBothAdjacentSpreadsAtLeastHalfSecondLong() {
        val edited = StoryBookEditor.moveBoundary(book(), "one", "two", timestampMs = 1_900L)

        assertEquals(1_500L, edited.markers[1].timestampMs)
    }

    @Test
    fun trimmingASequenceLeavesSilenceOutOfBothAdjacentSpreads() {
        val edited = StoryBookEditor.trimNarration(book(), "one", startMs = 200L, endMs = 800L)

        assertEquals(200L, edited.spreads[0].startMs)
        assertEquals(800L, edited.spreads[0].endMs)
        assertEquals(1_000L, edited.spreads[1].startMs)
    }

    @Test
    fun trimmingLastSequenceCanRemoveTrailingSilence() {
        val edited = StoryBookEditor.trimNarration(book(), "two", startMs = 1_100L, endMs = 1_700L)

        assertEquals(1_100L, edited.spreads[1].startMs)
        assertEquals(1_700L, edited.spreads[1].endMs)
    }

    @Test
    fun replacingNarrationResetsTrimsToTheNewRecording() {
        val trimmed = StoryBookEditor.trimNarration(book(), "one", startMs = 200L, endMs = 800L)
        val edited = StoryBookEditor.replaceNarration(trimmed, "one", File("override.m4a"), 1_200L)

        assertEquals(0L, edited.spreads[0].startMs)
        assertEquals(1_200L, edited.spreads[0].endMs)
    }

    @Test
    fun mergeRejectsOverrideAndOnlyMergesContiguousBaseRanges() {
        val source = book().copy(
            markers = book().markers.mapIndexed { index, marker ->
                if (index == 0) marker.copy(overrideAudioFile = File("override.m4a"), overrideDurationMs = 800L)
                else marker
            },
        )

        val merged = StoryBookEditor.mergeWithNext(source, "one")

        assertEquals(source, merged)
    }

    @Test fun reorderPreservesStableIdentityMediaAndAudioAllocation() {
        val source = book()
        val edited = StoryBookEditor.reorder(source, "three", 0)
        assertEquals(listOf("three", "one", "two"), edited.markers.map { it.spreadId })
        assertEquals(2_000L, edited.markers.first().recordingStartMs)
        assertEquals(source.markers.first { it.spreadId == "three" }, edited.markers.first())
    }

    @Test fun insertSplitsAllocationWithoutGapOrOverlapAndDeleteLeavesItUnassigned() {
        val source = book()
        val inserted = StoryBookEditor.insertAfter(source, "one", 500L,
            SpreadMarker(0L, File("new.jpg"), MarkerSource.MANUAL, spreadId = "new"))
        assertEquals(500L, inserted.markers[0].recordingEndMs)
        assertEquals(500L, inserted.markers[1].recordingStartMs)
        assertEquals(1_000L, inserted.markers[1].recordingEndMs)
        val deleted = StoryBookEditor.delete(inserted, "new")
        assertEquals(500L, deleted.markers[0].recordingEndMs)
        assertEquals(1_000L, deleted.markers[1].recordingStartMs)
    }

    @Test fun insertRejectsTooShortMissingAndDuplicateAnchorsButAllowsOverrideDonor() {
        val source = book()
        val marker = SpreadMarker(0L, File("new.jpg"), MarkerSource.MANUAL, spreadId = "new")
        assertEquals(source, StoryBookEditor.insertAfter(source, "missing", 500L, marker))
        assertEquals(source, StoryBookEditor.insertAfter(source, "one", 499L, marker))
        assertEquals(source, StoryBookEditor.insertAfter(source, "one", 501L, source.markers[1]))
        val overridden = source.copy(markers = source.markers.mapIndexed { i, item ->
            if (i == 0) item.copy(overrideAudioFile = File("override.m4a"), overrideDurationMs = 1_000L) else item
        })
        val inserted = StoryBookEditor.insertAfter(overridden, "one", 500L, marker)
        assertEquals(File("override.m4a"), inserted.markers[0].overrideAudioFile)
        assertEquals("new", inserted.markers[1].spreadId)
        assertEquals(null, inserted.markers[1].overrideAudioFile)
    }

    @Test fun trimmedBaseInsertNeverHidesAnchorAndResetsDisjointTrimToNewRange() {
        val source = book().copy(markers = book().markers.mapIndexed { i, marker ->
            if (i == 0) marker.copy(trimStartMs = 700L, trimEndMs = 900L) else marker
        })
        val inserted = StoryBookEditor.insertAfter(source, "one", 500L,
            SpreadMarker(0L, null, MarkerSource.MANUAL, spreadId = "suffix"))
        assertEquals(3 + 1, inserted.spreads.size)
        assertEquals(0L to 500L, inserted.spreads.first().startMs to inserted.spreads.first().endMs)
        assertEquals("suffix", inserted.markers[1].spreadId)
        assertEquals(null, inserted.markers[1].trimStartMs)
    }

    @Test fun overrideDonorKeepsOverrideRelativeTrimsAndSuffixUsesBaseAudio() {
        val override = File("override.m4a")
        val source = book().copy(markers = book().markers.mapIndexed { i, marker ->
            if (i == 0) marker.copy(overrideAudioFile = override, overrideDurationMs = 900L, trimStartMs = 100L, trimEndMs = 800L) else marker
        })
        val inserted = StoryBookEditor.insertAfter(source, "one", 500L,
            SpreadMarker(0L, null, MarkerSource.MANUAL, spreadId = "suffix"))
        assertEquals(100L to 800L, inserted.markers[0].trimStartMs to inserted.markers[0].trimEndMs)
        assertEquals(override, inserted.spreads[0].audioFile)
        assertEquals(source.audioFile, inserted.spreads[1].audioFile)
        assertEquals(500L to 1_000L, inserted.spreads[1].startMs to inserted.spreads[1].endMs)
    }

    @Test fun editingByIdAfterReorderTargetsRequestedSpread() {
        val reordered = StoryBookEditor.reorder(book(), "three", 0)
        val edited = StoryBookEditor.trimNarration(reordered, "one", 100L, 800L)
        assertEquals(100L, edited.markers.first { it.spreadId == "one" }.trimStartMs)
        assertEquals(null, edited.markers.first { it.spreadId == "three" }.trimStartMs)
    }

    @Test fun insertionAllocatesExactlyTheAnchorBaseRange() {
        val source = book()
        val inserted = StoryBookEditor.insertAfter(source, "two", 1_500L,
            SpreadMarker(0L, null, MarkerSource.MANUAL, spreadId = "new"))
        assertEquals(listOf(1_000L to 1_500L, 1_500L to 2_000L),
            inserted.markers.slice(1..2).map { it.recordingStartMs to it.recordingEndMs })
        assertEquals(MarkerSource.MANUAL, inserted.markers[2].source)
    }

    @Test fun soleSpreadCannotBeDeletedAndSessionUndoPersistsBeforePublishing() {
        val one = book().copy(markers = book().markers.take(1))
        assertEquals(one, StoryBookEditor.delete(one, "one"))
        val session = StoryEditSession(book())
        val reordered = StoryBookEditor.reorder(session.current, "three", 0)
        val saved = mutableListOf<StoryBook>()
        session.apply(reordered, saved::add)
        assertEquals(reordered, saved.last())
        assertEquals(book(), session.undo(saved::add))
    }

    private fun book() = StoryBook(
        id = "book",
        title = "Story",
        directory = File("."),
        audioFile = File("recording.m4a"),
        durationMs = 2_000L,
        markers = listOf(
            SpreadMarker(0L, null, MarkerSource.INITIAL, spreadId = "one", recordingStartMs = 0L, recordingEndMs = 1_000L),
            SpreadMarker(1_000L, null, MarkerSource.MANUAL, spreadId = "two", recordingStartMs = 1_000L, recordingEndMs = 2_000L),
            SpreadMarker(2_000L, null, MarkerSource.MANUAL, spreadId = "three", recordingStartMs = 2_000L, recordingEndMs = 2_500L),
        ),
    )
}
