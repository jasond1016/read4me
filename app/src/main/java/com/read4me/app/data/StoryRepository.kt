package com.read4me.app.data

import android.content.Context
import android.util.Base64
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.StoryBook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class StoryRepository(context: Context) {
    private val booksDirectory = File(context.filesDir, "books").apply { mkdirs() }

    data class Draft(
        val id: String,
        val title: String,
        val directory: File,
        val audioFile: File,
    )

    fun createDraft(title: String): Draft {
        val id = UUID.randomUUID().toString()
        val directory = File(booksDirectory, id).apply {
            mkdirs()
            File(this, "spreads").mkdirs()
        }
        return Draft(
            id = id,
            title = title.ifBlank { "我们的故事" },
            directory = directory,
            audioFile = File(directory, "recording.m4a"),
        )
    }

    fun imageFile(draft: Draft, ordinal: Int): File =
        File(File(draft.directory, "spreads"), "%03d.jpg".format(ordinal))

    fun save(book: StoryBook) {
        val markers = JSONArray().apply {
            book.markers.forEach { marker ->
                put(JSONObject().apply {
                    put("timestampMs", marker.timestampMs)
                    put("image", marker.imageFile?.name ?: JSONObject.NULL)
                    put("source", marker.source.name)
                    put(
                        "fingerprint",
                        marker.fingerprint?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL,
                    )
                    put("fingerprintVersion", marker.fingerprintVersion)
                    put(
                        "overrideAudio",
                        marker.overrideAudioFile?.relativeTo(book.directory)?.invariantSeparatorsPath
                            ?: JSONObject.NULL,
                    )
                    put("overrideDurationMs", marker.overrideDurationMs ?: JSONObject.NULL)
                    put("trimStartMs", marker.trimStartMs ?: JSONObject.NULL)
                    put("trimEndMs", marker.trimEndMs ?: JSONObject.NULL)
                })
            }
        }
        val manifest = JSONObject().apply {
            put("version", 4)
            put("id", book.id)
            put("title", book.title)
            put("audio", book.audioFile.name)
            put("durationMs", book.durationMs)
            put("markers", markers)
        }
        File(book.directory, "manifest.json").writeText(manifest.toString(2))
    }

    fun loadAll(): List<StoryBook> = booksDirectory.listFiles()
        .orEmpty()
        .mapNotNull(::load)
        .sortedByDescending { it.directory.lastModified() }

    fun deleteDraft(draft: Draft) {
        draft.directory.deleteRecursively()
    }

    private fun load(directory: File): StoryBook? = runCatching {
        val json = JSONObject(File(directory, "manifest.json").readText())
        val markerJson = json.getJSONArray("markers")
        val spreadsDirectory = File(directory, "spreads")
        val markers = buildList {
            for (index in 0 until markerJson.length()) {
                val item = markerJson.getJSONObject(index)
                val image = item.optString("image").takeIf { it.isNotBlank() && it != "null" }
                add(
                    SpreadMarker(
                        timestampMs = item.getLong("timestampMs"),
                        imageFile = image?.let { File(spreadsDirectory, it) }?.takeIf(File::exists),
                        source = MarkerSource.valueOf(item.getString("source")),
                        fingerprint = item.optString("fingerprint")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?.let { Base64.decode(it, Base64.NO_WRAP) },
                        fingerprintVersion = item.optInt("fingerprintVersion", 1),
                        overrideAudioFile = item.optString("overrideAudio")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?.let { File(directory, it) }
                            ?.takeIf(File::exists),
                        overrideDurationMs = if (item.isNull("overrideDurationMs")) {
                            null
                        } else {
                            item.getLong("overrideDurationMs")
                        },
                        trimStartMs = if (item.isNull("trimStartMs")) null else item.getLong("trimStartMs"),
                        trimEndMs = if (item.isNull("trimEndMs")) null else item.getLong("trimEndMs"),
                    ),
                )
            }
        }
        StoryBook(
            id = json.getString("id"),
            title = json.getString("title"),
            directory = directory,
            audioFile = File(directory, json.getString("audio")),
            durationMs = json.getLong("durationMs"),
            markers = markers,
        )
    }.getOrNull()

    fun overrideAudioFile(book: StoryBook, ordinal: Int): File =
        File(File(book.directory, "overrides").apply { mkdirs() }, "spread-%03d.m4a".format(ordinal))
}
