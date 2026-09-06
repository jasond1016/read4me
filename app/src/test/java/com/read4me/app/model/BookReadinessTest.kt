package com.read4me.app.model

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BookReadinessTest {
    @get:Rule val files = TemporaryFolder()

    private fun book(markers: List<SpreadMarker>) = StoryBook(
        "book", "Test", files.root, File(files.root, "unused.m4a"), 0, markers,
    )

    @Test fun emptyBookIsNotPlayable() {
        assertFalse(book(emptyList()).readiness().canPlay)
    }

    @Test fun audioOnlyBookCanPlayAndOffersPhotosAsOptionalRepair() {
        val audio = files.newFile("voice.m4a")
        val result = book(listOf(SpreadMarker(0, MarkerSource.INITIAL, emptyList(),
            listOf(NarrationSegment(audio, 0, 1000)), spreadId = "one"))).readiness()
        assertTrue(result.canPlay)
        assertEquals(listOf("one"), result.missingPhotoIds)
        assertTrue(result.missingAudioIds.isEmpty())
    }

    @Test fun missingPrimaryPhotoDoesNotHideUsableAlternative() {
        val result = book(listOf(SpreadMarker(0, MarkerSource.INITIAL,
            listOf(SpreadReference(File(files.root, "missing.jpg")), SpreadReference(files.newFile("other.jpg"))),
            spreadId = "one"))).readiness()
        assertTrue(result.missingPhotoIds.isEmpty())
        assertEquals(listOf("one"), result.missingAudioIds)
    }

    @Test fun oneMissingAudioSegmentRequiresRepairEvenWhenOtherSegmentExists() {
        val result = book(listOf(SpreadMarker(0, MarkerSource.INITIAL,
            listOf(SpreadReference(File(files.root, "missing.jpg"))),
            listOf(NarrationSegment(files.newFile("ok.m4a"), 0, 1000),
                NarrationSegment(File(files.root, "missing.m4a"), 0, 1000)), spreadId = "one"))).readiness()
        assertFalse(result.canPlay)
        assertEquals(listOf("one"), result.missingAudioIds)
        assertEquals(listOf("one"), result.missingPhotoIds)
    }
}
