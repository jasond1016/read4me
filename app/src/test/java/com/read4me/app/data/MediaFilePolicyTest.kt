package com.read4me.app.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaFilePolicyTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun repairCanKeepAnotherMissingFileButCannotPublishANewMissingFile() {
        val missing = File(files.root, "missing.m4a")
        assertTrue(canRetainMediaFile(missing, setOf(missing.canonicalFile)))
        assertFalse(canRetainMediaFile(File(files.root, "new-missing.m4a"), setOf(missing.canonicalFile)))
        assertTrue(canRetainMediaFile(files.newFile("repaired.m4a"), emptySet()))
    }

    @Test fun directoryCannotStandInForPublishedMedia() {
        val directory = files.newFolder("photo.jpg")
        assertFalse(canRetainMediaFile(directory, setOf(directory.canonicalFile)))
    }
}
