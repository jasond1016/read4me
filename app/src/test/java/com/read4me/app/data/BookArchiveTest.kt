package com.read4me.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class BookArchiveTest {
    @Test fun rejectsTraversalAndAbsolutePaths() {
        listOf("../evil", "a/../evil", "/absolute", "C:/absolute", "a\\..\\evil").forEach {
            assertFalse(it, BookArchive.isSafePath(it))
        }
        assertTrue(BookArchive.isSafePath("spreads/001.jpg"))
    }

    @Test fun extractionRejectsZipSlip() = withTempDirectory { target ->
        val bytes = zipOf("../evil" to "bad")
        val failure = runCatching { BookArchive.extract(ByteArrayInputStream(bytes), target) }
        assertTrue(failure.isFailure)
        assertFalse(File(target.parentFile, "evil").exists())
    }

    @Test fun extractionRequiresManifest() = withTempDirectory { target ->
        val failure = runCatching {
            BookArchive.extract(ByteArrayInputStream(zipOf("recording.m4a" to "audio")), target)
        }
        assertTrue(failure.isFailure)
    }

    @Test fun exportHasRoundTripStructure() = withTempDirectory { source ->
        File(source, "manifest.json").writeText("{}")
        File(source, "recording.m4a").writeText("audio")
        File(source, "spreads").mkdir()
        File(source, "spreads/001.jpg").writeText("image")
        val output = ByteArrayOutputStream()
        BookArchive.export(source, output)
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) names += zip.nextEntry?.name ?: break
        }
        assertEquals(setOf("manifest.json", "recording.m4a", "spreads/001.jpg"), names)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
        } }
    }.toByteArray()

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = createTempDirectory("read4me-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
