package com.read4me.app.model

import java.io.File

data class SessionBoundary(val spreadId: String, val startMs: Long)

/** Pure publication state: a capture is not durable, and therefore owns no boundary, until success. */
data class PendingMarker(val sessionToken: Long, val spreadId: String, val startMs: Long)

fun publishPendingMarker(
    boundaries: List<SessionBoundary>,
    pending: PendingMarker?,
    activeSessionToken: Long,
): List<SessionBoundary> = if (
    pending != null && pending.sessionToken == activeSessionToken &&
    (boundaries.isEmpty() && pending.startMs == 0L || boundaries.lastOrNull()?.startMs?.let { pending.startMs > it } == true)
) boundaries + SessionBoundary(pending.spreadId, pending.startMs) else boundaries

/** Publishes one physical recording across session-relative spread boundaries. */
fun allocateRecordingSession(
    file: File,
    durationMs: Long,
    boundaries: List<SessionBoundary>,
    minimumDurationMs: Long = 800L,
): Map<String, List<NarrationSegment>>? {
    if (durationMs < minimumDurationMs || boundaries.isEmpty() || boundaries.first().startMs != 0L) return null
    if (boundaries.zipWithNext().any { (a, b) -> b.startMs <= a.startMs } || boundaries.last().startMs >= durationMs) return null
    return boundaries.mapIndexed { index, boundary ->
        val end = boundaries.getOrNull(index + 1)?.startMs ?: durationMs
        boundary.spreadId to NarrationSegment(file, boundary.startMs, end)
    }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
}
