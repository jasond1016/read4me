package com.read4me.app.model

import java.io.File
import java.util.UUID

data class SpreadMarker(
    /** Legacy capture timestamp; v5 playback never infers an end from display order. */
    val timestampMs: Long,
    val imageFile: File?,
    val source: MarkerSource,
    val fingerprint: ByteArray? = null,
    val fingerprintVersion: Int = 1,
    val overrideAudioFile: File? = null,
    val overrideDurationMs: Long? = null,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    val spreadId: String = UUID.randomUUID().toString(),
    val recordingStartMs: Long = timestampMs,
    val recordingEndMs: Long = timestampMs,
)

enum class MarkerSource { INITIAL, AUTOMATIC, MANUAL }

data class StoryBook(
    val id: String,
    val title: String,
    val directory: File,
    val audioFile: File,
    val durationMs: Long,
    /** Display order. Recording allocation is held by each marker, not list position. */
    val markers: List<SpreadMarker>,
) {
    val spreads: List<StorySpread>
        get() = markers.mapIndexed { index, marker ->
            val overridden = marker.overrideAudioFile != null
            val sourceStart = if (overridden) 0L else marker.recordingStartMs
            val sourceEnd = if (overridden) marker.overrideDurationMs ?: 0L else marker.recordingEndMs
            val start = marker.trimStartMs?.coerceIn(sourceStart, sourceEnd) ?: sourceStart
            val end = marker.trimEndMs?.coerceIn(sourceStart, sourceEnd) ?: sourceEnd
            val validStart = if (end > start) start else sourceStart
            val validEnd = if (end > start) end else sourceEnd
            StorySpread(
                spreadId = marker.spreadId,
                ordinal = index + 1,
                imageFile = marker.imageFile,
                audioFile = marker.overrideAudioFile ?: audioFile,
                startMs = validStart,
                endMs = validEnd,
                source = marker.source,
                fingerprint = marker.fingerprint,
                fingerprintVersion = marker.fingerprintVersion,
            )
        }
}

data class StorySpread(
    val spreadId: String,
    val ordinal: Int,
    val imageFile: File?,
    val audioFile: File,
    val startMs: Long,
    val endMs: Long,
    val source: MarkerSource,
    val fingerprint: ByteArray?,
    val fingerprintVersion: Int,
) { val durationMs: Long get() = endMs - startMs }
