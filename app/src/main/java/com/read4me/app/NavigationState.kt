package com.read4me.app

import com.read4me.app.model.StoryBook
import com.read4me.app.model.RecordingMode
import com.read4me.app.ui.ReferenceCapturePurpose

/** Save stable identifiers only. Media and committed edits are reloaded from disk. */
internal fun Destination.checkpoint(): Map<String, String> = buildMap {
    fun book(value: StoryBook) { put("book", value.id) }
    fun details(value: Boolean) { put("details", value.toString()) }
    fun player(value: Destination.AudioBook?) {
        value?.checkpoint()?.forEach { (key, item) -> put("player.$key", item) }
    }
    put("route", when (this@checkpoint) {
        Destination.Library -> "Library"
        Destination.Setup -> "Setup"
        is Destination.ChildReading -> "ChildReading"
        is Destination.BookDetails -> "BookDetails"
        is Destination.CompletionSummary -> "CompletionSummary"
        is Destination.SpreadPreview -> "SpreadPreview"
        is Destination.Recording -> "Recording"
        is Destination.AudioBook -> "AudioBook"
        is Destination.Review -> "Review"
        is Destination.Rerecord -> "Rerecord"
        is Destination.Recapture -> "Recapture"
        is Destination.BatchRecapture -> "BatchRecapture"
        is Destination.VerifyReference, is Destination.InsertSpread -> "Review"
    })
    when (val route = this@checkpoint) {
        Destination.Library, Destination.Setup -> Unit
        is Destination.ChildReading -> route.returnBookId?.let { put("returnBook", it) }
        is Destination.BookDetails -> book(route.book)
        is Destination.CompletionSummary -> book(route.book)
        is Destination.SpreadPreview -> { book(route.book); put("spread", route.spreadId) }
        is Destination.Recording -> { book(route.book); put("mode", route.mode.name); details(route.returnToDetails) }
        is Destination.AudioBook -> {
            book(route.book)
            put("return", route.returnTo.name)
            put("pages", route.initiallyShowPageList.toString())
            put("caller", route.pageListBackToCaller.toString())
        }
        is Destination.Review -> {
            book(route.book); details(route.returnToDetails)
            (route.focusedSpreadId ?: route.organizeCurrent)?.let { put("spread", it) }
            route.pageListReturnTo?.let { put("return", it.name) }
            put("caller", route.pageListBackToCaller.toString())
            route.returnToPreviewSpreadId?.let { put("preview", it) }
        }
        is Destination.Rerecord -> {
            book(route.book); put("spread", route.spreadId); details(route.returnToDetails)
            route.returnToPreviewSpreadId?.let { put("preview", it) }; player(route.returnToPlayer)
        }
        is Destination.Recapture -> {
            book(route.book); put("spread", route.spreadId); details(route.returnToDetails)
            put("library", route.returnToLibrary.toString()); put("preview", route.returnToPreview.toString())
            put("purpose", route.capturePurpose.name); player(route.returnToPlayer)
        }
        is Destination.BatchRecapture -> {
            book(route.book); details(route.returnToDetails)
            put("remaining", route.remainingSpreadIds.joinToString(","))
            put("completed", route.completed.toString()); put("total", route.total.toString())
        }
        is Destination.VerifyReference -> {
            // The installed reference is already durable; reopen its review instead of applying it twice.
            book(route.book); put("route", "Review"); put("spread", route.spreadId)
            details(route.returnToDetails)
        }
        is Destination.InsertSpread -> {
            book(route.baseBook); put("route", "Review"); put("spread", route.anchorSpreadId)
            details(route.returnToDetails)
        }
    }
}

internal fun restoreDestination(state: Map<String, String>, findBook: (String) -> StoryBook?): Destination {
    fun flag(key: String) = state[key] == "true"
    val kind = state["route"]
    if (kind == "Setup") return Destination.Setup
    if (kind == "ChildReading") return Destination.ChildReading(state["returnBook"]?.takeIf { findBook(it) != null })
    val book = state["book"]?.let(findBook) ?: return Destination.Library
    val spreadId = state["spread"]?.takeIf { id -> book.markers.any { it.spreadId == id } }
    val back = Destination.AudioBookReturn.entries.firstOrNull { it.name == state["return"] }
    val playerState = state.filterKeys { it.startsWith("player.") }.mapKeys { it.key.removePrefix("player.") }
    val player = if (playerState.isEmpty()) null else restoreDestination(playerState, findBook) as? Destination.AudioBook
    return when (kind) {
        "BookDetails" -> Destination.BookDetails(book)
        "CompletionSummary" -> Destination.CompletionSummary(book)
        "Recording" -> Destination.Recording(book, RecordingMode.entries.firstOrNull { it.name == state["mode"] } ?: book.recordingMode, flag("details"))
        "SpreadPreview" -> spreadId?.let { Destination.SpreadPreview(book, it) } ?: Destination.BookDetails(book)
        "AudioBook" -> Destination.AudioBook(book, back ?: Destination.AudioBookReturn.LIBRARY, flag("pages"), playWhenReady = false, pageListBackToCaller = flag("caller"))
        "Review" -> Destination.Review(book, focusedSpreadId = spreadId, pageListReturnTo = back, returnToDetails = flag("details"), pageListBackToCaller = flag("caller"), returnToPreviewSpreadId = state["preview"])
        "Rerecord" -> spreadId?.let { Destination.Rerecord(book, it, flag("details"), state["preview"], player) } ?: Destination.BookDetails(book)
        "Recapture" -> spreadId?.let { Destination.Recapture(book, it, null, null, flag("library"), flag("details"), flag("preview"), ReferenceCapturePurpose.entries.firstOrNull { purpose -> purpose.name == state["purpose"] } ?: ReferenceCapturePurpose.ADD_REFERENCE, player) } ?: Destination.BookDetails(book)
        "BatchRecapture" -> {
            val remaining = state["remaining"].orEmpty().split(',').filter { id -> book.markers.any { it.spreadId == id } }
            if (remaining.isEmpty()) Destination.BookDetails(book)
            else Destination.BatchRecapture(book, remaining, state["completed"]?.toIntOrNull() ?: 0, state["total"]?.toIntOrNull() ?: remaining.size, flag("details"))
        }
        else -> Destination.Library
    }
}

internal sealed interface Destination {
    enum class AudioBookReturn { LIBRARY, DETAILS, REVIEW, COMPLETION_SUMMARY }

    data object Library : Destination
    data object Setup : Destination
    data class ChildReading(val returnBookId: String? = null) : Destination
    data class BookDetails(val book: StoryBook) : Destination
    data class SpreadPreview(val book: StoryBook, val spreadId: String) : Destination
    data class Recording(
        val book: StoryBook,
        val mode: RecordingMode = RecordingMode.CAMERA,
        val returnToDetails: Boolean = false,
    ) : Destination
    data class Review(
        val book: StoryBook,
        val initialUndo: StoryBook? = null,
        val undoImage: java.io.File? = null,
        val undoImages: List<java.io.File> = emptyList(),
        val organizeDraft: StoryBook? = null,
        val organizeSelected: String? = null,
        val organizeCurrent: String? = null,
        val organizeImages: List<java.io.File> = emptyList(),
        val focusedSpreadId: String? = null,
        val pageListReturnTo: AudioBookReturn? = null,
        val resumePageListPlayback: Boolean = false,
        val returnToDetails: Boolean = false,
        val pageListBackToCaller: Boolean = false,
        val returnToPreviewSpreadId: String? = null,
    ) : Destination
    data class AudioBook(
        val book: StoryBook,
        val returnTo: AudioBookReturn,
        val initiallyShowPageList: Boolean = false,
        val playWhenReady: Boolean = true,
        val pageListBackToCaller: Boolean = false,
    ) : Destination
    data class CompletionSummary(val book: StoryBook) : Destination
    data class Rerecord(
        val book: StoryBook,
        val spreadId: String,
        val returnToDetails: Boolean = false,
        val returnToPreviewSpreadId: String? = null,
        val returnToPlayer: AudioBook? = null,
    ) : Destination
    data class Recapture(
        val book: StoryBook,
        val spreadId: String,
        val undo: StoryBook?,
        val undoImage: java.io.File?,
        val returnToLibrary: Boolean = false,
        val returnToDetails: Boolean = false,
        val returnToPreview: Boolean = false,
        val capturePurpose: ReferenceCapturePurpose = ReferenceCapturePurpose.ADD_REFERENCE,
        val returnToPlayer: AudioBook? = null,
    ) : Destination
    data class BatchRecapture(
        val book: StoryBook,
        val remainingSpreadIds: List<String>,
        val completed: Int,
        val total: Int,
        val returnToDetails: Boolean = false,
    ) : Destination
    data class VerifyReference(
        val book: StoryBook,
        val spreadId: String,
        val undo: StoryBook?,
        val undoImage: java.io.File?,
        val returnToLibrary: Boolean,
        val returnToDetails: Boolean = false,
        val returnToPreview: Boolean = false,
        val returnToPlayer: AudioBook? = null,
    ) : Destination
    data class InsertSpread(
        val baseBook: StoryBook,
        val draftBook: StoryBook,
        val anchorSpreadId: String,
        val organizeSelected: String?,
        val organizeImages: List<java.io.File>,
        val editorUndo: StoryBook?,
        val editorUndoImages: List<java.io.File>,
        val returnToDetails: Boolean = false,
    ) : Destination
}
