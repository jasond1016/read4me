package com.read4me.app.model

import java.io.File

data class SpreadMarker(
    val timestampMs: Long,
    val imageFile: File?,
    val source: MarkerSource,
    val fingerprint: ByteArray? = null,
    val fingerprintVersion: Int = 1,
    val overrideAudioFile: File? = null,
    val overrideDurationMs: Long? = null,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
)

enum class MarkerSource {
    INITIAL,
    AUTOMATIC,
    MANUAL,
}

data class StoryBook(
    val id: String,
    val title: String,
    val directory: File,
    val audioFile: File,
    val durationMs: Long,
    val markers: List<SpreadMarker>,
) {
    val spreads: List<StorySpread>
        get() = markers.mapIndexed { index, marker ->
            val sourceStartMs = if (marker.overrideAudioFile != null) 0L else marker.timestampMs
            val sourceEndMs = marker.overrideDurationMs
                ?.takeIf { marker.overrideAudioFile != null }
                ?: (markers.getOrNull(index + 1)?.timestampMs ?: durationMs)
            StorySpread(
                ordinal = index + 1,
                imageFile = marker.imageFile,
                audioFile = marker.overrideAudioFile ?: audioFile,
                startMs = marker.trimStartMs?.coerceIn(sourceStartMs, sourceEndMs) ?: sourceStartMs,
                endMs = marker.trimEndMs?.coerceIn(sourceStartMs, sourceEndMs) ?: sourceEndMs,
                source = marker.source,
                fingerprint = marker.fingerprint,
                fingerprintVersion = marker.fingerprintVersion,
            )
        }.filter { it.endMs > it.startMs }
}

data class StorySpread(
    val ordinal: Int,
    val imageFile: File?,
    val audioFile: File,
    val startMs: Long,
    val endMs: Long,
    val source: MarkerSource,
    val fingerprint: ByteArray?,
    val fingerprintVersion: Int,
) {
    val durationMs: Long get() = endMs - startMs
}
