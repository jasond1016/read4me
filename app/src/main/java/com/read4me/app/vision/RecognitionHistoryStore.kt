package com.read4me.app.vision

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class RecognitionEvent(
    val timestampMs: Long,
    val bookId: String?,
    val spreadId: String?,
    val outcome: Outcome,
    val bestInliers: Int,
    val secondInliers: Int?,
    val latencyMs: Long,
    val searchPath: String,
) {
    enum class Outcome { CONFIRMED, LOW_INLIERS, AMBIGUOUS, MANUAL_CORRECTION, REFERENCE_ADDED }
}

data class RecognitionSummary(
    val bookId: String,
    val spreadId: String?,
    val confirmations: Int,
    val failures: Int,
    val manualCorrections: Int,
    val averageConfirmedInliers: Int,
) {
    val needsNewReference: Boolean get() = failures >= 3 || manualCorrections >= 2
}

object RecognitionHistory {
    fun summarize(events: List<RecognitionEvent>): List<RecognitionSummary> = events
        .filter { it.bookId != null }
        .groupBy { it.bookId!! to it.spreadId }
        .map { (identity, group) ->
            val latestReference = group.indexOfLast { it.outcome == RecognitionEvent.Outcome.REFERENCE_ADDED }
            val current = group.drop(latestReference + 1)
            val confirmed = current.filter { it.outcome == RecognitionEvent.Outcome.CONFIRMED }
            RecognitionSummary(
                bookId = identity.first,
                spreadId = identity.second,
                confirmations = confirmed.size,
                failures = current.count {
                    it.outcome == RecognitionEvent.Outcome.LOW_INLIERS || it.outcome == RecognitionEvent.Outcome.AMBIGUOUS
                },
                manualCorrections = current.count { it.outcome == RecognitionEvent.Outcome.MANUAL_CORRECTION },
                averageConfirmedInliers = confirmed.map(RecognitionEvent::bestInliers).average().takeIf { !it.isNaN() }?.toInt() ?: 0,
            )
        }
        .sortedWith(compareByDescending<RecognitionSummary> { it.needsNewReference }.thenByDescending { it.failures + it.manualCorrections })
}

/** Bounded local-only event log. It stores scores and identities, never camera frames. */
class RecognitionHistoryStore(context: Context, private val maxEvents: Int = 1_000) {
    private val file = File(context.filesDir, "recognition-history.json")
    private val events = load().toMutableList()

    @Synchronized
    fun record(event: RecognitionEvent) {
        events += event
        if (events.size > maxEvents) events.subList(0, events.size - maxEvents).clear()
        persist()
    }

    @Synchronized fun summaries(): List<RecognitionSummary> = RecognitionHistory.summarize(events.toList())

    @Synchronized
    fun clear() {
        events.clear()
        file.delete()
        File(file.parentFile, "${file.name}.pending").delete()
    }

    private fun load(): List<RecognitionEvent> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            RecognitionEvent(
                timestampMs = value.getLong("timestampMs"),
                bookId = value.optString("bookId").takeIf { it.isNotBlank() && it != "null" },
                spreadId = value.optString("spreadId").takeIf { it.isNotBlank() && it != "null" },
                outcome = RecognitionEvent.Outcome.valueOf(value.getString("outcome")),
                bestInliers = value.optInt("bestInliers"),
                secondInliers = if (value.isNull("secondInliers")) null else value.getInt("secondInliers"),
                latencyMs = value.optLong("latencyMs"),
                searchPath = value.optString("searchPath"),
            )
        }.takeLast(maxEvents)
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray().apply {
            events.forEach { event -> put(JSONObject().apply {
                put("timestampMs", event.timestampMs)
                put("bookId", event.bookId ?: JSONObject.NULL)
                put("spreadId", event.spreadId ?: JSONObject.NULL)
                put("outcome", event.outcome.name)
                put("bestInliers", event.bestInliers)
                put("secondInliers", event.secondInliers ?: JSONObject.NULL)
                put("latencyMs", event.latencyMs)
                put("searchPath", event.searchPath)
            }) }
        }
        val pending = File(file.parentFile, "${file.name}.pending")
        pending.writeText(array.toString())
        try {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
