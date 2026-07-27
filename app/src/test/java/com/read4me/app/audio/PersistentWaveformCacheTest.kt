package com.read4me.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class PersistentWaveformCacheTest {
    @Test fun persistsValuesAndInvalidatesWhenSourceMetadataOrRangeChanges() = withTempDirectory { root ->
        val source = File(root, "audio.m4a").apply { writeText("first") }
        val cache = PersistentWaveformCache(File(root, "cache"))
        var extracts = 0
        fun load(start: Long) = cache.loadOrExtract(source, start, 1_000L, 3) {
            extracts++; floatArrayOf(extracts.toFloat(), .5f, 1f)
        }

        assertArrayEquals(floatArrayOf(1f, .5f, 1f), load(0L), 0f)
        assertArrayEquals(floatArrayOf(1f, .5f, 1f), load(0L), 0f)
        assertEquals(1, extracts)
        assertNotEquals(load(100L)[0], load(0L)[0])
        source.appendText("changed")
        load(0L)
        assertEquals(3, extracts)
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = createTempDirectory("read4me-waveform-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
