package com.read4me.app.data

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.read4me.app.model.CachedMediaProbe
import com.read4me.app.model.MediaProbe
import java.io.File

/** Small image decode and audio-container inspection; this is not a full-stream integrity scan. */
val AndroidMediaProbe: MediaProbe = CachedMediaProbe(object : MediaProbe {
    override fun canReadPhoto(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        bitmap.recycle()
        return true
    }

    override fun audioDurationMs(file: File): Long? {
        val metadata = MediaMetadataRetriever()
        return try {
            metadata.setDataSource(file.absolutePath)
            if (metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") null
            else metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            metadata.release()
        }
    }
})
