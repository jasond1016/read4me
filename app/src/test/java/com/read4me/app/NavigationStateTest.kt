package com.read4me.app

import com.read4me.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NavigationStateTest {
    private val book = StoryBook("one", "Saved", File("one"), File("unused"), 0L,
        listOf(SpreadMarker(0, MarkerSource.MANUAL, emptyList(), spreadId = "page")))
    private fun restore(route: Destination, latest: StoryBook? = book) =
        restoreDestination(route.checkpoint()) { if (it == latest?.id) latest else null }

    @Test fun playbackRestoresLatestBookWithoutStartingAudio() {
        val latest = book.copy(title = "Renamed")
        val restored = restore(Destination.AudioBook(book, Destination.AudioBookReturn.DETAILS, true), latest) as Destination.AudioBook
        assertEquals("Renamed", restored.book.title)
        assertFalse(restored.playWhenReady)
        assertTrue(restored.initiallyShowPageList)
        assertEquals(Destination.AudioBookReturn.DETAILS, restored.returnTo)
    }

    @Test fun repairRestoresSpreadAndPlayerReturn() {
        val player = Destination.AudioBook(book, Destination.AudioBookReturn.REVIEW, true)
        val restored = restore(Destination.Rerecord(book, "page", returnToPlayer = player)) as Destination.Rerecord
        assertEquals("page", restored.spreadId)
        assertEquals(Destination.AudioBookReturn.REVIEW, restored.returnToPlayer?.returnTo)
        assertEquals(false, restored.returnToPlayer?.playWhenReady)
    }

    @Test fun deletedBookAndDeletedSpreadHaveSafeDestinations() {
        assertEquals(Destination.Library, restore(Destination.BookDetails(book), null))
        assertTrue(restore(Destination.Rerecord(book, "removed")) is Destination.BookDetails)
    }

    @Test fun batchRepairKeepsProgressAndDropsDeletedPages() {
        val restored = restore(Destination.BatchRecapture(book, listOf("gone", "page"), 2, 4, true)) as Destination.BatchRecapture
        assertEquals(listOf("page"), restored.remainingSpreadIds)
        assertEquals(2, restored.completed)
        assertEquals(4, restored.total)
        assertTrue(restored.returnToDetails)
    }

    @Test fun recordingKeepsInputPolicy() {
        val restored = restore(Destination.Recording(book, RecordingMode.MANUAL, true)) as Destination.Recording
        assertEquals(RecordingMode.MANUAL, restored.mode)
        assertTrue(restored.returnToDetails)
    }

    @Test fun reviewDoesNotReviveUncommittedBookCopy() {
        val restored = restore(Destination.Review(book, organizeDraft = book.copy(title = "Unsaved"), focusedSpreadId = "page")) as Destination.Review
        assertNull(restored.organizeDraft)
        assertEquals("Saved", restored.book.title)
        assertEquals("page", restored.focusedSpreadId)
    }
}
