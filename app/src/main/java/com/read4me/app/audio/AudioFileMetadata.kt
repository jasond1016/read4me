package com.read4me.app.audio

import android.media.MediaMetadataRetriever
import java.io.File

/** Reads the finalized media timeline rather than estimating it from wall-clock recording time. */
fun audioDurationMs(file: File): Long? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } finally {
        retriever.release()
    }
}.getOrNull()?.takeIf { it > 0L }
