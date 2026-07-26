package com.read4me.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** Keeps camera-resolution JPEG decoding and cache churn off the UI thread. */
object StoryImageLoader {
    private const val MAX_DIMENSION = 1_024
    private val cache = object : LruCache<String, Bitmap>(24 * 1_024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1_024
    }

    fun cached(file: File): Bitmap? = cache.get(key(file))

    fun load(file: File): Bitmap? {
        val key = key(file)
        cache.get(key)?.let { return it }
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun key(file: File) = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
}
