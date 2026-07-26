package com.read4me.app.model

import java.io.File

object StoryBookEditor {
    fun moveBoundary(book: StoryBook, markerIndex: Int, timestampMs: Long): StoryBook {
        if (markerIndex <= 0 || markerIndex >= book.markers.size) return book
        val markers = book.markers.toMutableList()
        val minimum = markers[markerIndex - 1].timestampMs + 500L
        val maximum = (markers.getOrNull(markerIndex + 1)?.timestampMs ?: book.durationMs) - 500L
        if (maximum < minimum) return book
        markers[markerIndex] = markers[markerIndex].copy(
            timestampMs = timestampMs.coerceIn(minimum, maximum),
        )
        markers[markerIndex - 1] = markers[markerIndex - 1].copy(trimEndMs = null)
        markers[markerIndex] = markers[markerIndex].copy(trimStartMs = null)
        return book.copy(markers = markers)
    }

    fun trimNarration(book: StoryBook, ordinal: Int, startMs: Long, endMs: Long): StoryBook {
        if (ordinal <= 0 || ordinal > book.markers.size) return book
        val markers = book.markers.toMutableList()
        val index = ordinal - 1
        val marker = markers[index]
        val sourceStartMs = if (marker.overrideAudioFile != null) 0L else marker.timestampMs
        val sourceEndMs = marker.overrideDurationMs
            ?.takeIf { marker.overrideAudioFile != null }
            ?: (markers.getOrNull(index + 1)?.timestampMs ?: book.durationMs)
        if (sourceEndMs - sourceStartMs < 500L) return book
        val trimmedStartMs = startMs.coerceIn(sourceStartMs, sourceEndMs - 500L)
        val trimmedEndMs = endMs.coerceIn(trimmedStartMs + 500L, sourceEndMs)
        markers[index] = marker.copy(trimStartMs = trimmedStartMs, trimEndMs = trimmedEndMs)
        return book.copy(markers = markers)
    }

    fun mergeWithNext(book: StoryBook, ordinal: Int): StoryBook {
        if (ordinal <= 0 || ordinal >= book.markers.size) return book
        val markers = book.markers.toMutableList()
        val first = ordinal - 1
        markers[first] = markers[first].copy(
            overrideAudioFile = null,
            overrideDurationMs = null,
            trimStartMs = null,
            trimEndMs = null,
        )
        markers.removeAt(ordinal)
        return book.copy(markers = markers)
    }

    fun replaceNarration(book: StoryBook, ordinal: Int, audioFile: File, durationMs: Long): StoryBook {
        if (ordinal <= 0 || ordinal > book.markers.size || durationMs <= 0) return book
        val markers = book.markers.toMutableList()
        markers[ordinal - 1] = markers[ordinal - 1].copy(
            overrideAudioFile = audioFile,
            overrideDurationMs = durationMs,
            trimStartMs = null,
            trimEndMs = null,
        )
        return book.copy(markers = markers)
    }
}
