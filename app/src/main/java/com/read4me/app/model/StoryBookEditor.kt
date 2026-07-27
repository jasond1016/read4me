package com.read4me.app.model

import java.io.File

object StoryBookEditor {
    private const val MIN_MS = 500L
    private fun index(book: StoryBook, id: String) = book.markers.indexOfFirst { it.spreadId == id }

    fun replaceReference(book: StoryBook, spreadId: String, imageFile: File, fingerprint: ByteArray? = null, fingerprintVersion: Int = 2) =
        update(book, spreadId) { it.copy(imageFile = imageFile, fingerprint = fingerprint?.copyOf(), fingerprintVersion = fingerprintVersion) }

    fun moveBoundary(book: StoryBook, leftId: String, rightId: String, timestampMs: Long): StoryBook {
        val left = index(book, leftId); val right = index(book, rightId)
        if (left < 0 || right != left + 1) return book
        val a = book.markers[left]; val b = book.markers[right]
        if (!shareBoundary(a, b)) return book
        val minimum = a.recordingStartMs + MIN_MS
        val maximum = b.recordingEndMs - MIN_MS
        if (maximum < minimum) return book
        val split = timestampMs.coerceIn(minimum, maximum)
        val out = book.markers.toMutableList()
        out[left] = a.copy(recordingEndMs = split, trimEndMs = null)
        out[right] = b.copy(timestampMs = split, recordingStartMs = split, trimStartMs = null)
        return book.copy(markers = out)
    }

    fun trimNarration(book: StoryBook, spreadId: String, startMs: Long, endMs: Long): StoryBook = update(book, spreadId) { marker ->
        val sourceStart = if (marker.overrideAudioFile != null) 0L else marker.recordingStartMs
        val sourceEnd = if (marker.overrideAudioFile != null) marker.overrideDurationMs ?: 0L else marker.recordingEndMs
        if (sourceEnd - sourceStart < MIN_MS) marker else {
            val start = startMs.coerceIn(sourceStart, sourceEnd - MIN_MS)
            marker.copy(trimStartMs = start, trimEndMs = endMs.coerceIn(start + MIN_MS, sourceEnd))
        }
    }

    fun reorder(book: StoryBook, spreadId: String, newIndex: Int): StoryBook {
        val old = index(book, spreadId); if (old < 0 || newIndex !in book.markers.indices || old == newIndex) return book
        val out = book.markers.toMutableList(); val marker = out.removeAt(old); out.add(newIndex, marker)
        return book.copy(markers = out)
    }
    fun delete(book: StoryBook, spreadId: String): StoryBook =
        if (book.markers.size <= 1 || index(book, spreadId) < 0) book else book.copy(markers = book.markers.filterNot { it.spreadId == spreadId })

    fun insertAfter(book: StoryBook, anchorId: String, splitMs: Long, newMarker: SpreadMarker): StoryBook {
        val i = index(book, anchorId); if (i < 0 || newMarker.spreadId == anchorId || book.markers.any { it.spreadId == newMarker.spreadId }) return book
        val anchor = book.markers[i]
        if (splitMs < anchor.recordingStartMs + MIN_MS || splitMs > anchor.recordingEndMs - MIN_MS) return book
        val out = book.markers.toMutableList()
        val baseTrim = if (anchor.overrideAudioFile == null) clampTrim(anchor, anchor.recordingStartMs, splitMs) else anchor.trimStartMs to anchor.trimEndMs
        out[i] = anchor.copy(recordingEndMs = splitMs, trimStartMs = baseTrim.first, trimEndMs = baseTrim.second)
        out.add(i + 1, newMarker.copy(timestampMs = splitMs, recordingStartMs = splitMs, recordingEndMs = anchor.recordingEndMs, overrideAudioFile = null, overrideDurationMs = null, trimStartMs = null, trimEndMs = null))
        return book.copy(markers = out)
    }

    fun mergeWithNext(book: StoryBook, spreadId: String): StoryBook {
        val i = index(book, spreadId); if (i !in 0 until book.markers.lastIndex) return book
        val a = book.markers[i]; val b = book.markers[i + 1]; if (!shareBoundary(a, b)) return book
        val out = book.markers.toMutableList(); out[i] = a.copy(recordingEndMs = b.recordingEndMs, trimStartMs = null, trimEndMs = null); out.removeAt(i + 1)
        return book.copy(markers = out)
    }
    fun replaceNarration(book: StoryBook, spreadId: String, audioFile: File, durationMs: Long) = if (durationMs <= 0) book else update(book, spreadId) { it.copy(overrideAudioFile = audioFile, overrideDurationMs = durationMs, trimStartMs = null, trimEndMs = null) }

    fun shareBoundary(a: SpreadMarker, b: SpreadMarker) = a.overrideAudioFile == null && b.overrideAudioFile == null && a.recordingEndMs == b.recordingStartMs
    private fun clampTrim(marker: SpreadMarker, start: Long, end: Long): Pair<Long?, Long?> {
        if (marker.trimStartMs == null && marker.trimEndMs == null) return null to null
        val intersectionStart = maxOf(marker.trimStartMs ?: marker.recordingStartMs, start)
        val intersectionEnd = minOf(marker.trimEndMs ?: marker.recordingEndMs, end)
        return if (intersectionEnd > intersectionStart) intersectionStart to intersectionEnd else start to end
    }
    private fun update(book: StoryBook, id: String, transform: (SpreadMarker) -> SpreadMarker): StoryBook { val i=index(book,id); if(i<0)return book; val out=book.markers.toMutableList(); out[i]=transform(out[i]); return book.copy(markers=out) }
}

/** One-level model history. Call save(next) before assigning [current]. */
class StoryEditSession(initial: StoryBook, initialUndo: StoryBook? = null) {
    var current: StoryBook = initial; private set
    private var previous: StoryBook? = initialUndo
    val canUndo get() = previous != null
    val undoSnapshot get() = previous
    fun apply(next: StoryBook, save: (StoryBook) -> Unit): StoryBook { if (next == current) return current; save(next); previous=current; current=next; return current }
    fun undo(save: (StoryBook) -> Unit): StoryBook { val old=previous ?: return current; save(old); previous=null; current=old; return current }
    fun clearUndo() { previous=null }
}
