package com.read4me.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class LibraryArchiveTest {
    @Test fun exportsAndExtractsMultipleBooksWithoutTrash() = withTempDirectory { root ->
        val first = book(root, "one", "first")
        val second = book(root, "two", "second")
        val bytes = ByteArrayOutputStream().also { LibraryArchive.export(listOf(first, second), it) }.toByteArray()
        val extracted = File(root, "extracted").apply { mkdir() }

        val books = LibraryArchive.extract(ByteArrayInputStream(bytes), extracted)

        assertEquals(setOf("one", "two"), books.map(File::getName).toSet())
        assertEquals("first", File(extracted, "books/one/recording.m4a").readText())
        assertFalse(File(extracted, "trash").exists())
    }

    @Test fun rejectsTraversalAndMissingLibraryManifest() = withTempDirectory { root ->
        listOf(
            zip("library.json" to "{\"version\":1}", "books/../evil" to "bad"),
            zip("books/one/manifest.json" to "{}"),
        ).forEachIndexed { index, bytes ->
            val destination = File(root, "bad-$index").apply { mkdir() }
            assertTrue(runCatching { LibraryArchive.extract(ByteArrayInputStream(bytes), destination) }.isFailure)
        }
        assertFalse(File(root, "evil").exists())
    }

    private fun book(root: File, id: String, audio: String) = File(root, id).apply {
        mkdirs()
        File(this, "manifest.json").writeText("{}")
        File(this, "recording.m4a").writeText(audio)
    }

    private fun zip(vararg entries: Pair<String, String>) = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
        } }
    }.toByteArray()

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = createTempDirectory("read4me-library-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
