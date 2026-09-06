package com.read4me.app.model

import java.io.File

interface MediaProbe {
    fun canReadPhoto(file: File): Boolean
    fun audioDurationMs(file: File): Long?
}

/** Cache by file identity and revision; failures are cached too, but never across a file change. */
class CachedMediaProbe(private val delegate: MediaProbe, private val capacity: Int = 256) : MediaProbe {
    private data class Key(val path: String, val bytes: Long, val modified: Long)
    private val photos = object : LinkedHashMap<Key, Boolean>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Boolean>?) = size > capacity
    }
    private val audio = object : LinkedHashMap<Key, Long?>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Long?>?) = size > capacity
    }
    private fun key(file: File) = Key(file.absolutePath, file.length(), file.lastModified())

    @Synchronized override fun canReadPhoto(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val key = key(file)
        return photos.getOrPut(key) { runCatching { delegate.canReadPhoto(file) }.getOrDefault(false) }
    }

    @Synchronized override fun audioDurationMs(file: File): Long? {
        if (!file.isFile || file.length() == 0L) return null
        val key = key(file)
        if (!audio.containsKey(key)) audio[key] = runCatching { delegate.audioDurationMs(file) }.getOrNull()?.takeIf { it > 0 }
        return audio[key]
    }
}
