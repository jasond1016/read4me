package com.read4me.app.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StoryBookEditorTest {
    @Test
    fun replacingReferencePreservesTimingAudioAndOtherMarkers() {
        val source = book()
        val replacement = File("replacement.jpg")
        val edited = StoryBookEditor.replaceReference(source, 2, replacement, byteArrayOf(1, 2))

        assertEquals(replacement, edited.markers[1].imageFile)
        assertEquals(listOf(0L, 1_000L, 2_000L), edited.markers.map { it.timestampMs })
        assertEquals(source.audioFile, edited.audioFile)
        assertEquals(source.markers[0], edited.markers[0])
    }

    @Test
    fun replacingMissingReferenceOrdinalDoesNothing() {
        val source = book()
        assertEquals(source, StoryBookEditor.replaceReference(source, 99, File("replacement.jpg")))
    }

    @Test
    fun movingBoundaryKeepsBothAdjacentSpreadsAtLeastHalfSecondLong() {
        val edited = StoryBookEditor.moveBoundary(book(), markerIndex = 1, timestampMs = 1_900L)

        assertEquals(1_500L, edited.markers[1].timestampMs)
    }

    @Test
    fun trimmingASequenceLeavesSilenceOutOfBothAdjacentSpreads() {
        val edited = StoryBookEditor.trimNarration(book(), ordinal = 1, startMs = 200L, endMs = 800L)

        assertEquals(200L, edited.spreads[0].startMs)
        assertEquals(800L, edited.spreads[0].endMs)
        assertEquals(1_000L, edited.spreads[1].startMs)
    }

    @Test
    fun trimmingLastSequenceCanRemoveTrailingSilence() {
        val edited = StoryBookEditor.trimNarration(book(), ordinal = 2, startMs = 1_100L, endMs = 1_700L)

        assertEquals(1_100L, edited.spreads[1].startMs)
        assertEquals(1_700L, edited.spreads[1].endMs)
    }

    @Test
    fun replacingNarrationResetsTrimsToTheNewRecording() {
        val trimmed = StoryBookEditor.trimNarration(book(), ordinal = 1, startMs = 200L, endMs = 800L)
        val edited = StoryBookEditor.replaceNarration(trimmed, 1, File("override.m4a"), 1_200L)

        assertEquals(0L, edited.spreads[0].startMs)
        assertEquals(1_200L, edited.spreads[0].endMs)
    }

    @Test
    fun mergingRemovesSharedBoundaryAndClearsOverrideThatCannotSpanBothSpreads() {
        val source = book().copy(
            markers = book().markers.mapIndexed { index, marker ->
                if (index == 0) marker.copy(overrideAudioFile = File("override.m4a"), overrideDurationMs = 800L)
                else marker
            },
        )

        val merged = StoryBookEditor.mergeWithNext(source, ordinal = 1)

        assertEquals(listOf(0L, 2_000L), merged.markers.map { it.timestampMs })
        assertEquals(null, merged.markers.first().overrideAudioFile)
    }

    private fun book() = StoryBook(
        id = "book",
        title = "Story",
        directory = File("."),
        audioFile = File("recording.m4a"),
        durationMs = 2_000L,
        markers = listOf(
            SpreadMarker(0L, null, MarkerSource.INITIAL),
            SpreadMarker(1_000L, null, MarkerSource.MANUAL),
            SpreadMarker(2_000L, null, MarkerSource.MANUAL),
        ),
    )
}
