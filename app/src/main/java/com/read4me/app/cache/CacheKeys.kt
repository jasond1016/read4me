package com.read4me.app.cache

import java.io.File
import java.security.MessageDigest

object CacheKeys {
    fun waveform(file: File, startMs: Long, endMs: Long, buckets: Int, formatVersion: Int): String = hash(
        "waveform|$formatVersion|${signature(file)}|$startMs|$endMs|$buckets",
    )

    fun thumbnail(file: File, maxDimension: Int, formatVersion: Int): String = hash(
        "thumbnail|$formatVersion|${signature(file)}|$maxDimension",
    )

    private fun signature(file: File) = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
