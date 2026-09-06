package com.read4me.app.model

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BookReadinessTest {
    @get:Rule val files = TemporaryFolder()

    private val probe = CachedMediaProbe(object : MediaProbe {
        override fun canReadPhoto(file: File) = file.readText() == "photo"
        override fun audioDurationMs(file: File) = file.readText().toLongOrNull()
    })
    private fun media(name: String, content: String = "1000") = files.newFile(name).apply { writeText(content) }

    private fun book(markers: List<SpreadMarker>) = StoryBook(
        "book", "Test", files.root, File(files.root, "unused.m4a"), 0, markers,
    )

    @Test fun emptyBookIsNotPlayable() {
        assertFalse(book(emptyList()).readiness(probe).canPlay)
    }

    @Test fun emptyAndCorruptMediaRequireRepair() {
        for (content in listOf("", "corrupt")) {
            val photo = media("photo-${content.length}.jpg", content)
            val audio = media("audio-${content.length}.m4a", content)
            val result = book(listOf(SpreadMarker(0, MarkerSource.MANUAL, listOf(SpreadReference(photo)),
                listOf(NarrationSegment(audio, 0, 1000)), spreadId = "one"))).readiness(probe)
            assertFalse(result.canPlay)
            assertEquals(listOf("one"), result.missingPhotoIds)
        }
    }

    @Test fun segmentBeyondPhysicalDurationRequiresRepair() {
        val result = book(listOf(SpreadMarker(0, MarkerSource.MANUAL, emptyList(),
            listOf(NarrationSegment(media("short.m4a", "999"), 0, 1000)), spreadId = "one"))).readiness(probe)
        assertFalse(result.canPlay)
    }

    @Test fun cacheInvalidatesWhenFileIsReplacedOrDeleted() {
        val file = media("fixed.m4a", "broken")
        assertNull(probe.audioDurationMs(file))
        file.writeText("1000")
        assertEquals(1000L, probe.audioDurationMs(file))
        file.delete()
        assertNull(probe.audioDurationMs(file))
    }

    @Test fun audioOnlyBookCanPlayAndOffersPhotosAsOptionalRepair() {
        val audio = media("voice.m4a")
        val result = book(listOf(SpreadMarker(0, MarkerSource.INITIAL, emptyList(),
            listOf(NarrationSegment(audio, 0, 1000)), spreadId = "one"))).readiness(probe)
        assertTrue(result.canPlay)
        assertEquals(listOf("one"), result.missingPhotoIds)
        assertTrue(result.missingAudioIds.isEmpty())
    }

    @Test fun missingPrimaryPhotoDoesNotHideUsableAlternative() {
        val result = book(listOf(SpreadMarker(0, MarkerSource.INITIAL,
            listOf(SpreadReference(File(files.root, "missing.jpg")), SpreadReference(media("other.jpg", "photo"))),
            spreadId = "one"))).readiness(probe)
        assertTrue(result.missingPhotoIds.isEmpty())
        assertEquals(listOf("one"), result.missingAudioIds)
    }

    @Test fun oneMissingAudioSegmentRequiresRepairEvenWhenOtherSegmentExists() {
        val result = book(listOf(SpreadMarker(0, MarkerSource.INITIAL,
            listOf(SpreadReference(File(files.root, "missing.jpg"))),
            listOf(NarrationSegment(media("ok.m4a"), 0, 1000),
                NarrationSegment(File(files.root, "missing.m4a"), 0, 1000)), spreadId = "one"))).readiness(probe)
        assertFalse(result.canPlay)
        assertEquals(listOf("one"), result.missingAudioIds)
        assertEquals(listOf("one"), result.missingPhotoIds)
    }
}
