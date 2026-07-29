package com.read4me.app.model

import java.io.File

object StoryBookEditor {
    private const val MIN_MS = 500L
    private fun index(book: StoryBook, id: String) = book.markers.indexOfFirst { it.spreadId == id }
    private fun source(book: StoryBook, marker: SpreadMarker) = book.spreads.first { it.spreadId == marker.spreadId }.sourceSegments

    fun replaceReference(book: StoryBook, spreadId: String, imageFile: File, fingerprint: ByteArray? = null, fingerprintVersion: Int = 2) = update(book, spreadId) { it.copy(references = listOf(SpreadReference(imageFile, fingerprint?.copyOf(), fingerprintVersion))) }
    fun addReference(book: StoryBook, spreadId: String, reference: SpreadReference) = update(book, spreadId) { it.copy(references = it.references + reference) }
    fun deleteReference(book: StoryBook, spreadId: String, referenceId: String) = update(book, spreadId) { marker -> if (marker.references.size <= 1) marker else marker.copy(references = marker.references.filterNot { it.referenceId == referenceId }) }
    fun setPrimaryReference(book: StoryBook, spreadId: String, referenceId: String) = update(book, spreadId) { marker -> marker.references.firstOrNull { it.referenceId == referenceId }?.let { marker.copy(references = listOf(it) + marker.references.filterNot { r -> r.referenceId == referenceId }) } ?: marker }
    fun updateReferenceQuality(book: StoryBook, spreadId: String, referenceId: String, quality: PhotoQuality) = update(book, spreadId) { marker -> marker.copy(references = marker.references.map { if (it.referenceId == referenceId) it.copy(quality = quality) else it }) }

    fun trimNarration(book: StoryBook, spreadId: String, startMs: Long, endMs: Long) = update(book, spreadId) { marker ->
        val total = NarrationTimeline.duration(source(book, marker))
        if (total < MIN_MS) marker else { val start = startMs.coerceIn(0, total - MIN_MS); marker.copy(trimStartMs = start, trimEndMs = endMs.coerceIn(start + MIN_MS, total)) }
    }

    fun shareBoundary(a: SpreadMarker, b: SpreadMarker): Boolean {
        val left = a.segments.lastOrNull() ?: return false
        val right = b.segments.firstOrNull() ?: return false
        return left.file.canonicalFile == right.file.canonicalFile && left.endMs == right.startMs
    }

    fun moveBoundary(book: StoryBook, leftId: String, rightId: String, timestampMs: Long): StoryBook {
        val i = index(book, leftId); if (i < 0 || index(book, rightId) != i + 1) return book
        val a = book.markers[i]; val b = book.markers[i + 1]; if (!shareBoundary(a, b)) return book
        val left = a.segments.last(); val right = b.segments.first()
        val minimum = left.startMs + MIN_MS
        val maximum = right.endMs - MIN_MS
        if (maximum < minimum) return book
        val split = timestampMs.coerceIn(minimum, maximum)
        val out = book.markers.toMutableList()
        out[i] = a.copy(segments = a.segments.dropLast(1) + left.copy(endMs = split), trimStartMs = null, trimEndMs = null)
        out[i + 1] = b.copy(segments = listOf(right.copy(startMs = split)) + b.segments.drop(1), trimStartMs = null, trimEndMs = null)
        return book.copy(markers = out)
    }

    fun reorder(book: StoryBook, spreadId: String, newIndex: Int): StoryBook { val old=index(book,spreadId); if(old<0||newIndex !in book.markers.indices||old==newIndex)return book; val out=book.markers.toMutableList(); out.add(newIndex,out.removeAt(old)); return book.copy(markers=out) }
    fun delete(book: StoryBook, spreadId: String): StoryBook {
        val index = index(book, spreadId)
        if (book.markers.size <= 1 || index < 0) return book
        val remaining = book.markers.filterNot { it.spreadId == spreadId }
        val cursor = if (book.resumeSpreadId == spreadId) remaining[minOf(index, remaining.lastIndex)].spreadId else book.resumeSpreadId
        return book.copy(markers = remaining, resumeSpreadId = cursor)
    }

    /** [splitMs] is an aggregate source offset. Splits inside a physical segment are safe. */
    fun insertAfter(book: StoryBook, anchorId: String, splitMs: Long, newMarker: SpreadMarker): StoryBook {
        val i=index(book,anchorId); if(i<0||book.markers.any{it.spreadId==newMarker.spreadId})return book
        val anchor=book.markers[i]; val segments=book.spreads[i].effectiveSegments; val total=NarrationTimeline.duration(segments)
        if(splitMs<MIN_MS||splitMs>total-MIN_MS)return book
        val left=NarrationTimeline.clip(segments,0,splitMs); val right=NarrationTimeline.clip(segments,splitMs,total)
        val out=book.markers.toMutableList(); out[i]=anchor.copy(segments=left,trimStartMs=null,trimEndMs=null,overrideAudioFile=null,overrideDurationMs=null)
        out.add(i+1,newMarker.copy(segments=right,trimStartMs=null,trimEndMs=null,overrideAudioFile=null,overrideDurationMs=null)); return book.copy(markers=out)
    }
    fun mergeWithNext(book: StoryBook, spreadId: String): StoryBook { val i=index(book,spreadId); if(i !in 0 until book.markers.lastIndex)return book; val out=book.markers.toMutableList(); val a=out[i]; val b=out[i+1]; out[i]=a.copy(segments=book.spreads[i].effectiveSegments+book.spreads[i+1].effectiveSegments,trimStartMs=null,trimEndMs=null,overrideAudioFile=null,overrideDurationMs=null); out.removeAt(i+1); return book.copy(markers=out, resumeSpreadId=if(book.resumeSpreadId==b.spreadId)a.spreadId else book.resumeSpreadId) }
    fun replaceNarration(book: StoryBook, spreadId: String, audioFile: File, durationMs: Long) = if(durationMs<=0)book else update(book,spreadId){it.copy(segments=listOf(NarrationSegment(audioFile,0,durationMs)),trimStartMs=null,trimEndMs=null,overrideAudioFile=null,overrideDurationMs=null)}
    private fun update(book:StoryBook,id:String,transform:(SpreadMarker)->SpreadMarker):StoryBook{val i=index(book,id);if(i<0)return book;val out=book.markers.toMutableList();out[i]=transform(out[i]);return book.copy(markers=out)}
}

class StoryEditSession(initial: StoryBook, initialUndo: StoryBook? = null) {
    var current=initial; private set
    private var previous=initialUndo
    val canUndo get()=previous!=null
    val undoSnapshot get()=previous
    fun apply(next:StoryBook,save:(StoryBook)->Unit):StoryBook{if(next==current)return current;save(next);previous=current;current=next;return current}
    fun undo(save:(StoryBook)->Unit):StoryBook{val old=previous?:return current;save(old);previous=null;current=old;return current}
    fun clearUndo(){previous=null}
}
