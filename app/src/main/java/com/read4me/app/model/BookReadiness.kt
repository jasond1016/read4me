package com.read4me.app.model

/** Check usable media, including references whose files disappeared after an import. */
val StorySpread.hasUsablePhoto: Boolean
    get() = references.any { it.file.isFile }

val StorySpread.hasUsableAudio: Boolean
    get() = effectiveSegments.isNotEmpty() && effectiveSegments.all { it.file.isFile }

data class BookReadiness(
    val missingPhotoIds: List<String>,
    val missingAudioIds: List<String>,
    val canPlay: Boolean,
)

fun StoryBook.readiness(): BookReadiness {
    val pages = spreads
    val missingAudio = pages.filterNot { it.hasUsableAudio }.map { it.spreadId }
    return BookReadiness(
        missingPhotoIds = pages.filterNot { it.hasUsablePhoto }.map { it.spreadId },
        missingAudioIds = missingAudio,
        canPlay = pages.isNotEmpty() && missingAudio.isEmpty(),
    )
}
