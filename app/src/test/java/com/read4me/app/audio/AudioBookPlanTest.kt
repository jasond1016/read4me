package com.read4me.app.audio

import com.read4me.app.model.MarkerSource
import com.read4me.app.model.NarrationSegment
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.StoryBook
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AudioBookPlanTest {
    private val file = File("audio.m4a")

    @Test fun queueUsesEveryEffectivePhysicalSegmentInEncounterOrder() {
        val plan = AudioBookPlan.from(book())
        assertEquals(listOf("same", "same", "last"), plan.entries.map { it.spreadId })
        assertEquals(listOf(0L, 100L, 300L), plan.entries.map { it.bookOffsetMs })
        assertEquals(600L, plan.durationMs)
    }

    @Test fun aggregateResumeMapsToClippedItemLocalPosition() {
        val plan = AudioBookPlan.from(book())
        assertEquals(AudioBookPlan.Resume(1, 50), plan.resume("same", 150, 0))
        assertEquals(AudioBookPlan.Resume(0, 0), plan.resume("missing", 999))
    }

    @Test fun logicalNavigationSkipsInternalSegmentsAndPreservesRepeatedIds() {
        val plan = AudioBookPlan.from(book())
        assertEquals(2, plan.nextSpreadItem(0))
        assertEquals(2, plan.nextSpreadItem(1))
        assertEquals(0, plan.previousSpreadItem(2))
        assertEquals(0, plan.previousSpreadItem(1))
    }

    private fun book(): StoryBook {
        val markers = listOf(
            SpreadMarker(0, MarkerSource.INITIAL, emptyList(), listOf(
                NarrationSegment(file, 10, 110), NarrationSegment(file, 200, 400)), spreadId = "same"),
            SpreadMarker(1, MarkerSource.MANUAL, emptyList(), listOf(
                NarrationSegment(file, 500, 800)), spreadId = "last"),
        )
        return StoryBook("book", "Title", File("book"), file, 999, markers)
    }
}
