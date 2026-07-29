package com.read4me.app.audio

import com.read4me.app.model.StoryBook
import java.io.File

/** Context-free audiobook queue. Indices, rather than IDs, preserve repeated spread encounters. */
data class AudioBookPlan(val bookId: String, val title: String, val spreadCount: Int, val entries: List<Entry>) {
    data class Entry(
        val queueIndex: Int,
        val spreadIndex: Int,
        val spreadId: String,
        val segmentIndex: Int,
        val file: File,
        val sourceStartMs: Long,
        val sourceEndMs: Long,
        val spreadOffsetMs: Long,
        val bookOffsetMs: Long,
    ) {
        val durationMs get() = sourceEndMs - sourceStartMs
    }

    data class Resume(val queueIndex: Int, val itemPositionMs: Long)
    val durationMs get() = entries.sumOf(Entry::durationMs)

    fun resume(spreadId: String?, aggregateOffsetMs: Long, spreadIndexHint: Int? = null): Resume {
        if (entries.isEmpty()) return Resume(0, 0)
        val candidates = entries.filter { it.spreadId == spreadId }
        if (candidates.isEmpty()) return Resume(0, 0)
        val spreadIndex = candidates.firstOrNull { it.spreadIndex == spreadIndexHint }?.spreadIndex
            ?: candidates.firstOrNull()?.spreadIndex
            ?: candidates.first().spreadIndex
        val spread = entries.filter { it.spreadIndex == spreadIndex }
        val total = spread.sumOf(Entry::durationMs)
        var remaining = aggregateOffsetMs.coerceIn(0, total)
        spread.forEach { entry ->
            if (remaining < entry.durationMs || entry == spread.last()) {
                return Resume(entry.queueIndex, remaining.coerceAtMost(entry.durationMs))
            }
            remaining -= entry.durationMs
        }
        return Resume(spread.first().queueIndex, 0)
    }

    fun nextSpreadItem(queueIndex: Int): Int {
        val spread = entries.getOrNull(queueIndex)?.spreadIndex ?: return 0
        return entries.firstOrNull { it.spreadIndex > spread }?.queueIndex ?: queueIndex
    }

    fun previousSpreadItem(queueIndex: Int): Int {
        val spread = entries.getOrNull(queueIndex)?.spreadIndex ?: return 0
        val previous = entries.filter { it.spreadIndex < spread }.maxOfOrNull(Entry::spreadIndex)
        return entries.firstOrNull { it.spreadIndex == previous }?.queueIndex
            ?: entries.firstOrNull { it.spreadIndex == spread }?.queueIndex
            ?: queueIndex
    }

    companion object {
        fun from(book: StoryBook): AudioBookPlan {
            var bookOffset = 0L
            val entries = buildList {
                book.spreads.forEachIndexed { spreadIndex, spread ->
                    var spreadOffset = 0L
                    spread.effectiveSegments.forEachIndexed { segmentIndex, segment ->
                        add(Entry(size, spreadIndex, spread.spreadId, segmentIndex, segment.file,
                            segment.startMs, segment.endMs, spreadOffset, bookOffset))
                        spreadOffset += segment.durationMs
                        bookOffset += segment.durationMs
                    }
                }
            }
            return AudioBookPlan(book.id, book.title, book.spreads.size, entries)
        }
    }
}
