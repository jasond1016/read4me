package com.read4me.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.Context
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.read4me.app.cache.CacheKeys
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Keeps camera-resolution JPEG decoding and cache churn off the UI thread. */
object StoryImageLoader {
    private const val MAX_DIMENSION = 1_024
    // Version 2 normalizes CameraX's EXIF orientation before caching thumbnails.
    private const val FORMAT_VERSION = 2
    private const val MAX_DISK_FILES = 1_000
    private val cache = object : LruCache<String, Bitmap>(24 * 1_024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1_024
    }

    fun cached(file: File): Bitmap? = cache.get(key(file))

    fun load(context: Context, file: File): Bitmap? {
        val key = key(file)
        cache.get(key)?.let { return it }
        if (!file.isFile) return null
        val diskDirectory = File(context.cacheDir, "read4me/thumbnails").apply { mkdirs() }
        val diskFile = File(diskDirectory, "${CacheKeys.thumbnail(file, MAX_DIMENSION, FORMAT_VERSION)}.jpg")
        BitmapFactory.decodeFile(diskFile.absolutePath)?.let {
            cache.put(key, it)
            return it
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        val bitmap = orientForDisplay(file, decoded)
        val pending = File(diskDirectory, "${diskFile.name}.${UUID.randomUUID()}.pending")
        runCatching {
            pending.outputStream().buffered().use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)) { "Could not encode thumbnail" }
            }
            try {
                Files.move(pending.toPath(), diskFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(pending.toPath(), diskFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val files = diskDirectory.listFiles { candidate -> candidate.extension == "jpg" }.orEmpty()
            if (files.size > MAX_DISK_FILES) {
                files.sortedBy(File::lastModified).take(files.size - MAX_DISK_FILES).forEach(File::delete)
            }
        }.onFailure { pending.delete() }
        cache.put(key, bitmap)
        return bitmap
    }

    fun clearDisk(context: Context) {
        File(context.cacheDir, "read4me/thumbnails").deleteRecursively()
        cache.evictAll()
    }

    private fun key(file: File) = "${file.absolutePath}:${file.lastModified()}:${file.length()}"

    private fun orientForDisplay(file: File, source: Bitmap): Bitmap {
        val exif = runCatching { ExifInterface(file) }.getOrNull() ?: return source
        val rotation = exif.rotationDegrees
        val flipped = exif.isFlipped
        if (rotation == 0 && !flipped) return source
        val matrix = Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrElse { source }.also { oriented ->
            if (oriented !== source) source.recycle()
        }
    }
}
