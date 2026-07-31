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

/** An immutable physical slice. Times are offsets in [file], not logical book time. */
data class NarrationSegment(val file: File, val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs > startMs) }
    val durationMs: Long get() = endMs - startMs
}

interface OrderedNarration {
    val sourceSegments: List<NarrationSegment>
    val effectiveSegments: List<NarrationSegment>
    val durationMs: Long get() = effectiveSegments.sumOf(NarrationSegment::durationMs)
}

/** Pure aggregate-timeline operations; clipping never changes a media file. */
object NarrationTimeline {
    data class Position(val segmentIndex: Int, val sourceTimeMs: Long)

    fun duration(segments: List<NarrationSegment>) = segments.sumOf(NarrationSegment::durationMs)

    fun map(segments: List<NarrationSegment>, offsetMs: Long): Position? {
        if (segments.isEmpty() || offsetMs !in 0..duration(segments)) return null
        var remaining = offsetMs
        segments.forEachIndexed { index, segment ->
            if (remaining <= segment.durationMs) return Position(index, segment.startMs + remaining)
            remaining -= segment.durationMs
        }
        return null
    }

    fun clip(segments: List<NarrationSegment>, startOffsetMs: Long, endOffsetMs: Long): List<NarrationSegment> {
        val total = duration(segments)
        val start = startOffsetMs.coerceIn(0, total)
        val end = endOffsetMs.coerceIn(start, total)
        if (end <= start) return emptyList()
        var cursor = 0L
        return buildList {
            segments.forEach { segment ->
                val localStart = (start - cursor).coerceIn(0, segment.durationMs)
                val localEnd = (end - cursor).coerceIn(0, segment.durationMs)
                if (localEnd > localStart) add(segment.copy(startMs = segment.startMs + localStart, endMs = segment.startMs + localEnd))
                cursor += segment.durationMs
            }
        }
    }
}

data class SpreadMarker(
    val timestampMs: Long,
    val source: MarkerSource,
    val references: List<SpreadReference>,
    val segments: List<NarrationSegment> = emptyList(),
    /** Aggregate offsets into [segments]. */
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    val spreadId: String = UUID.randomUUID().toString(),
    // Transitional constructor fields retained for source compatibility; v7 persistence ignores them.
    val overrideAudioFile: File? = null,
    val overrideDurationMs: Long? = null,
    val recordingStartMs: Long = timestampMs,
    val recordingEndMs: Long = timestampMs,
) {
    val imageFile get() = references.firstOrNull()?.file
    val fingerprint get() = references.firstOrNull()?.fingerprint
    val fingerprintVersion get() = references.firstOrNull()?.fingerprintVersion ?: 1
}

enum class MarkerSource { INITIAL, AUTOMATIC, MANUAL }
enum class StoryStatus { IN_PROGRESS, COMPLETE }
enum class RecordingMode { CAMERA, MANUAL }

data class StoryBook(
    val id: String,
    val title: String,
    val directory: File,
    /** Retained only so v6 callers/tests can construct a book; new manifests derive media from segments. */
    val audioFile: File,
    val durationMs: Long,
    val markers: List<SpreadMarker>,
    val status: StoryStatus = StoryStatus.COMPLETE,
    /** Stable recording/review cursor. It is deliberately independent of display order. */
    val resumeSpreadId: String? = markers.lastOrNull()?.spreadId,
    /** Recording input policy is persistent and must not be inferred from later reference-photo edits. */
    val recordingMode: RecordingMode = RecordingMode.CAMERA,
) {
    val resumeMarker get() = markers.firstOrNull { it.spreadId == resumeSpreadId }
    val playableDurationMs get() = spreads.sumOf(StorySpread::durationMs)
    val spreads get() = markers.mapIndexed { index, marker ->
        val source = marker.segments
        val total = NarrationTimeline.duration(source)
        val start = marker.trimStartMs?.coerceIn(0, total) ?: 0
        val end = marker.trimEndMs?.coerceIn(start, total) ?: total
        StorySpread(marker.spreadId, index + 1, marker.references, source, start, end, marker.source)
    }
}

data class StorySpread(
    val spreadId: String,
    val ordinal: Int,
    val references: List<SpreadReference>,
    override val sourceSegments: List<NarrationSegment>,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val source: MarkerSource,
) : OrderedNarration {
    override val effectiveSegments get() = NarrationTimeline.clip(sourceSegments, trimStartMs, trimEndMs)
    override val durationMs get() = effectiveSegments.sumOf(NarrationSegment::durationMs)
    val imageFile get() = references.firstOrNull()?.file
    val fingerprint get() = references.firstOrNull()?.fingerprint
    val fingerprintVersion get() = references.firstOrNull()?.fingerprintVersion ?: 1
    // Compatibility for UI being migrated: only meaningful for a single physical segment.
    val audioFile get() = effectiveSegments.firstOrNull()?.file ?: File("")
    val startMs get() = if (effectiveSegments.size == 1) effectiveSegments[0].startMs else 0L
    val endMs get() = if (effectiveSegments.size == 1) effectiveSegments[0].endMs else durationMs
}
