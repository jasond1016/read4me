package com.read4me.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRecognizerTest {
    @Test
    fun fingerprintIgnoresGlobalBrightnessChange() {
        val first = VisualFingerprint.fromLuma(pattern(offset = 0))
        val brighter = VisualFingerprint.fromLuma(pattern(offset = 35))

        assertTrue(VisualFingerprint.similarity(first, brighter) > 0.98f)
    }

    @Test
    fun fingerprintToleratesSmallBookPositionShift() {
        val original = VisualFingerprint.fromLuma(texturedPage(shiftColumns = 0))
        val shifted = VisualFingerprint.fromLuma(texturedPage(shiftColumns = 2))

        assertTrue(VisualFingerprint.similarity(original, shifted) > 0.9f)
    }

    @Test
    fun requiresRepeatedConfidentFramesBeforeConfirmingPage() {
        val pageOne = VisualFingerprint.fromLuma(pattern(0))
        val pageTwo = VisualFingerprint.fromLuma(invertedPattern())
        val recognizer = PageRecognizer(
            candidates = listOf(
                PageRecognizer.Candidate("book", 1, pageOne),
                PageRecognizer.Candidate("book", 2, pageTwo),
            ),
            minimumSimilarity = 0.8f,
            confirmationsRequired = 3,
        )

        assertNull(recognizer.accept(pageTwo))
        assertNull(recognizer.accept(pageTwo))
        assertEquals(2, recognizer.accept(pageTwo)?.candidate?.spreadOrdinal)
        assertNull(recognizer.accept(pageTwo))
    }

    @Test
    fun reportsAmbiguousCandidatesInsteadOfSilentlyFailing() {
        val page = VisualFingerprint.fromLuma(pattern(0))
        val recognizer = PageRecognizer(
            candidates = listOf(
                PageRecognizer.Candidate("book", 1, page),
                PageRecognizer.Candidate("book", 2, page.copyOf()),
            ),
        )

        val decision = recognizer.evaluate(page)

        assertEquals(PageRecognizer.State.AMBIGUOUS, decision.state)
        assertTrue(decision.best != null)
        assertTrue(decision.second != null)
    }

    @Test
    fun confirmedPageGivesSmallPreferenceToAdjacentSpread() {
        val cover = VisualFingerprint.fromLuma(texturedPage(0))
        val page = VisualFingerprint.fromLuma(texturedPage(2))
        val duplicatePage = page.copyOf()
        duplicatePage[0] = (duplicatePage[0] + 2).toByte()
        val recognizer = PageRecognizer(
            candidates = listOf(
                PageRecognizer.Candidate("book", 1, cover),
                PageRecognizer.Candidate("book", 2, page),
                PageRecognizer.Candidate("other", 8, duplicatePage),
            ),
            minimumMargin = 0.01f,
            confirmationsRequired = 1,
        )
        recognizer.accept(cover)

        val decision = recognizer.evaluate(page)

        assertEquals("book", decision.best?.candidate?.bookId)
        assertEquals(2, decision.best?.candidate?.spreadOrdinal)
    }

    private fun pattern(offset: Int): ByteArray = ByteArray(VisualFingerprint.COLUMNS * VisualFingerprint.ROWS) { index ->
        ((index % VisualFingerprint.COLUMNS) * 5 + (index / VisualFingerprint.COLUMNS) * 2 + offset)
            .coerceIn(0, 255)
            .toByte()
    }

    private fun invertedPattern(): ByteArray = ByteArray(VisualFingerprint.COLUMNS * VisualFingerprint.ROWS) { index ->
        (255 - ((index % VisualFingerprint.COLUMNS) * 5 + (index / VisualFingerprint.COLUMNS) * 2))
            .coerceIn(0, 255)
            .toByte()
    }

    private fun texturedPage(shiftColumns: Int): ByteArray =
        ByteArray(VisualFingerprint.COLUMNS * VisualFingerprint.ROWS) { index ->
            val row = index / VisualFingerprint.COLUMNS
            val column = (index % VisualFingerprint.COLUMNS - shiftColumns).coerceAtLeast(0)
            ((column * 37 + row * 61 + (column / 4) * 29) % 210 + 20).toByte()
        }
}
