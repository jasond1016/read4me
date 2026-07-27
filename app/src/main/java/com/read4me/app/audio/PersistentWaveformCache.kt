package com.read4me.app.audio

import com.read4me.app.cache.CacheKeys
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Disposable, versioned waveform cache. Source identity and playback range are part of the key. */
class PersistentWaveformCache(private val directory: File) {
    companion object {
        private const val MAGIC = 0x52344D57
        private const val FORMAT_VERSION = 1
        private const val MAX_FILES = 2_000
    }

    fun loadOrExtract(
        source: File,
        startMs: Long,
        endMs: Long,
        bucketCount: Int = 150,
        extract: () -> FloatArray = { AudioWaveformExtractor().extract(source, startMs, endMs, bucketCount) },
    ): FloatArray {
        require(bucketCount > 0)
        directory.mkdirs()
        val key = CacheKeys.waveform(source, startMs, endMs, bucketCount, FORMAT_VERSION)
        val target = File(directory, "$key.waveform")
        read(target, bucketCount)?.let { return it }
        val values = extract()
        require(values.size == bucketCount)
        write(target, values)
        prune()
        return values
    }

    fun clear() = directory.deleteRecursively()

    private fun read(file: File, expectedCount: Int): FloatArray? = runCatching {
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == FORMAT_VERSION)
            val count = input.readInt()
            require(count == expectedCount && count in 1..10_000)
            FloatArray(count) { input.readFloat() }
        }
    }.onFailure { file.delete() }.getOrNull()

    private fun write(target: File, values: FloatArray) {
        val pending = File(directory, "${target.name}.${UUID.randomUUID()}.pending")
        DataOutputStream(pending.outputStream().buffered()).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(values.size)
            values.forEach(output::writeFloat)
        }
        try {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun prune() {
        val files = directory.listFiles { file -> file.extension == "waveform" }.orEmpty()
        if (files.size > MAX_FILES) files.sortedBy(File::lastModified).take(files.size - MAX_FILES).forEach(File::delete)
    }
}
