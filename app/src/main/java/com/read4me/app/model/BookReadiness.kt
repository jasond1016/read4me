package com.read4me.app.model

import com.read4me.app.data.AndroidMediaProbe

/** Check usable media, including references whose files disappeared after an import. */
val StorySpread.hasUsablePhoto: Boolean
    get() = hasUsablePhoto(AndroidMediaProbe)

val StorySpread.hasUsableAudio: Boolean
    get() = hasUsableAudio(AndroidMediaProbe)

fun StorySpread.hasUsablePhoto(probe: MediaProbe): Boolean = references.any { probe.canReadPhoto(it.file) }

fun StorySpread.hasUsableAudio(probe: MediaProbe): Boolean = effectiveSegments.let { segments ->
    segments.isNotEmpty() && segments.all { segment ->
        val duration = probe.audioDurationMs(segment.file)
        duration != null && segment.startMs < duration && segment.endMs <= duration
    }
}

data class BookReadiness(
    val missingPhotoIds: List<String>,
    val missingAudioIds: List<String>,
    val canPlay: Boolean,
)

fun StoryBook.readiness(probe: MediaProbe = AndroidMediaProbe): BookReadiness {
    val pages = spreads
    val missingAudio = pages.filterNot { it.hasUsableAudio(probe) }.map { it.spreadId }
    return BookReadiness(
        missingPhotoIds = pages.filterNot { it.hasUsablePhoto(probe) }.map { it.spreadId },
        missingAudioIds = missingAudio,
        canPlay = pages.isNotEmpty() && missingAudio.isEmpty(),
    )
}
