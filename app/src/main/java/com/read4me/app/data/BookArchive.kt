package com.read4me.app.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Context-free implementation of the .read4me container format. */
object BookArchive {
    const val MAX_ENTRIES = 2_000
    const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024

    fun export(directory: File, output: OutputStream) {
        require(File(directory, "manifest.json").isFile) { "Book manifest is missing" }
        ZipOutputStream(output.buffered()).use { zip ->
            directory.walkTopDown().filter(File::isFile).filterNot {
                it.name == "manifest.json.pending" || it.name == "manifest.json.recovery-backup"
            }.forEach { file ->
                val name = file.relativeTo(directory).invariantSeparatorsPath
                require(isSafePath(name)) { "Unsafe book path" }
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Extracts into an empty caller-owned temporary directory. */
    fun extract(input: InputStream, destination: File) {
        require(destination.listFiles().isNullOrEmpty()) { "Import directory is not empty" }
        val root = destination.canonicalFile
        var entries = 0
        var expanded = 0L
        val seen = hashSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ENTRIES) { "Archive contains too many files" }
                val name = entry.name.replace('\\', '/')
                require(isSafePath(name) && seen.add(name)) { "Archive contains an unsafe path" }
                val target = File(root, name).canonicalFile
                require(target.path.startsWith(root.path + File.separator)) { "Archive path escapes the book" }
                if (entry.isDirectory) {
                    require(target.mkdirs() || target.isDirectory)
                } else {
                    val parent = requireNotNull(target.parentFile)
                    require(parent.mkdirs() || parent.isDirectory)
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            expanded += count
                            require(expanded <= MAX_EXPANDED_BYTES) { "Archive is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require(File(root, "manifest.json").isFile) { "Book manifest is missing" }
    }

    fun isSafePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        return path.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." }
    }
}
