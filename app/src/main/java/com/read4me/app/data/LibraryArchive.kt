package com.read4me.app.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Context-free container for a complete active library. Trash and derived caches are excluded. */
object LibraryArchive {
    const val MAX_ENTRIES = 10_000
    const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024

    fun export(bookDirectories: List<File>, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("library.json"))
            zip.write("{\"version\":1,\"books\":${bookDirectories.size}}".toByteArray())
            zip.closeEntry()
            bookDirectories.forEach { directory ->
                require(File(directory, "manifest.json").isFile) { "Book manifest is missing" }
                directory.walkTopDown().filter(File::isFile).forEach { file ->
                    val relative = file.relativeTo(directory).invariantSeparatorsPath
                    require(BookArchive.isSafePath(relative)) { "Unsafe book path" }
                    zip.putNextEntry(ZipEntry("books/${directory.name}/$relative"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Extracts into an empty caller-owned directory and returns the extracted book directories. */
    fun extract(input: InputStream, destination: File): List<File> {
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
                require(BookArchive.isSafePath(name) && seen.add(name)) { "Archive contains an unsafe path" }
                val target = File(root, name).canonicalFile
                require(target.path.startsWith(root.path + File.separator)) { "Archive path escapes the library" }
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
        val library = File(root, "library.json")
        require(library.isFile && Regex("\"version\"\\s*:\\s*1(?:\\D|$)").containsMatchIn(library.readText())) {
            "Library manifest is missing or unsupported"
        }
        val books = File(root, "books").listFiles().orEmpty().filter(File::isDirectory)
        require(books.all { File(it, "manifest.json").isFile }) { "A book manifest is missing" }
        return books
    }
}
