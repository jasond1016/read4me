package com.read4me.app.model

import java.io.File
import java.util.UUID

data class PhotoQuality(
    val status: Status,
    val laplacianVariance: Double? = null,
    val orbKeypoints: Int? = null,
    val meanBrightness: Double? = null,
    val darkPixelRatio: Double? = null,
    val overexposedPixelRatio: Double? = null,
    val issues: List<Issue> = emptyList(),
) {
    enum class Status { GOOD, ISSUES, UNAVAILABLE }
    enum class Issue { BLURRY, TOO_FEW_DETAILS, TOO_DARK, OVEREXPOSED }
}

data class SpreadReference(
    val file: File,
    val fingerprint: ByteArray? = null,
    val fingerprintVersion: Int = 1,
    val quality: PhotoQuality? = null,
    val referenceId: String = UUID.randomUUID().toString(),
)

data class SpreadMarker(
    /** Legacy capture timestamp; v5 playback never infers an end from display order. */
    val timestampMs: Long,
    val source: MarkerSource,
    val references: List<SpreadReference>,
    val overrideAudioFile: File? = null,
    val overrideDurationMs: Long? = null,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    val spreadId: String = UUID.randomUUID().toString(),
    val recordingStartMs: Long = timestampMs,
    val recordingEndMs: Long = timestampMs,
) {
    val imageFile: File? get() = references.firstOrNull()?.file
    val fingerprint: ByteArray? get() = references.firstOrNull()?.fingerprint
    val fingerprintVersion: Int get() = references.firstOrNull()?.fingerprintVersion ?: 1
}

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
                references = marker.references,
                audioFile = marker.overrideAudioFile ?: audioFile,
                startMs = validStart,
                endMs = validEnd,
                source = marker.source,
            )
        }
}

data class StorySpread(
    val spreadId: String,
    val ordinal: Int,
    val references: List<SpreadReference>,
    val audioFile: File,
    val startMs: Long,
    val endMs: Long,
    val source: MarkerSource,
) {
    val durationMs: Long get() = endMs - startMs
    val imageFile: File? get() = references.firstOrNull()?.file
    val fingerprint: ByteArray? get() = references.firstOrNull()?.fingerprint
    val fingerprintVersion: Int get() = references.firstOrNull()?.fingerprintVersion ?: 1
}
