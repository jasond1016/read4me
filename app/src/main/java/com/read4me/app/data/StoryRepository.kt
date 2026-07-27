package com.read4me.app.data

import android.content.Context
import android.util.Base64
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.StoryBook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    fun imageFile(draft: Draft, spreadId: String): File =
        File(File(draft.directory, "spreads"), "$spreadId.jpg")

    fun save(book: StoryBook) {
        val markers = JSONArray().apply {
            book.markers.forEach { marker ->
                put(JSONObject().apply {
                    put("id", marker.spreadId)
                    put("timestampMs", marker.timestampMs)
                    put("recordingStartMs", marker.recordingStartMs)
                    put("recordingEndMs", marker.recordingEndMs)
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
            put("version", 5)
            put("id", book.id)
            put("title", book.title)
            put("audio", book.audioFile.name)
            put("durationMs", book.durationMs)
            put("markers", markers)
        }
        val target = File(book.directory, "manifest.json")
        val pending = File(book.directory, "manifest.json.pending")
        pending.writeText(manifest.toString(2))
        try {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun loadAll(): List<StoryBook> = booksDirectory.listFiles()
        .orEmpty()
        .mapNotNull(::load)
        .sortedByDescending { it.directory.lastModified() }

    fun export(book: StoryBook, output: OutputStream) = BookArchive.export(book.directory, output)

    fun import(input: InputStream): StoryBook {
        val temporary = File(booksDirectory, ".import-${UUID.randomUUID()}")
        val id = UUID.randomUUID().toString()
        val target = File(booksDirectory, id)
        try {
            check(temporary.mkdirs()) { "Could not prepare import" }
            BookArchive.extract(input, temporary)
            val imported = load(temporary) ?: error("Book manifest is invalid")
            validateFiles(imported)
            val manifestFile = File(temporary, "manifest.json")
            val manifest = JSONObject(manifestFile.readText()).put("id", id)
            manifestFile.writeText(manifest.toString(2))
            check(!target.exists())
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temporary.toPath(), target.toPath())
            }
            return load(target) ?: error("Imported book could not be loaded")
        } catch (failure: Exception) {
            temporary.deleteRecursively()
            target.deleteRecursively()
            throw failure
        }
    }

    private fun validateFiles(book: StoryBook) {
        require(book.audioFile.isFile) { "Recording is missing" }
        require(book.markers.isNotEmpty()) { "Book has no spreads" }
        require(book.markers.all { it.imageFile?.isFile == true }) { "A spread image is missing" }
        require(book.markers.all { it.overrideAudioFile == null || it.overrideAudioFile.isFile }) {
            "An override recording is missing"
        }
    }

    fun deleteDraft(draft: Draft) {
        draft.directory.deleteRecursively()
    }

    /** Installs a replacement at a new immutable path so a failed manifest save leaves the old image intact. */
    fun replaceReferenceImage(book: StoryBook, spreadId: String, pendingFile: File): File {
        val marker = book.markers.firstOrNull { it.spreadId == spreadId }
        require(marker != null && pendingFile.isFile)
        val spreads = File(book.directory, "spreads").apply { mkdirs() }
        val target = File(spreads, "$spreadId-${UUID.randomUUID()}.jpg")
        try {
            Files.move(
                pendingFile.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(pendingFile.toPath(), target.toPath())
        }
        return target
    }

    /** Installs a new spread image. Existing stable files are never overwritten. */
    fun installInsertImage(book: StoryBook, spreadId: String, pendingFile: File): File {
        require(pendingFile.isFile)
        val target = File(File(book.directory, "spreads").apply { mkdirs() }, "$spreadId.jpg")
        require(!target.exists())
        try {
            Files.move(pendingFile.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(pendingFile.toPath(), target.toPath())
        }
        return target
    }

    private fun load(directory: File): StoryBook? = runCatching {
        val json = JSONObject(File(directory, "manifest.json").readText())
        val version = json.optInt("version", 1)
        require(version in 1..5) { "Unsupported manifest version $version" }
        val markerJson = json.getJSONArray("markers")
        val spreadsDirectory = File(directory, "spreads")
        val markers = buildList {
            for (index in 0 until markerJson.length()) {
                val item = markerJson.getJSONObject(index)
                val image = item.optString("image").takeIf { it.isNotBlank() && it != "null" }
                val timestamp = item.getLong("timestampMs")
                val nextTimestamp = markerJson.optJSONObject(index + 1)?.optLong("timestampMs")
                    ?: json.getLong("durationMs")
                val deterministicId = UUID.nameUUIDFromBytes(
                    "${json.getString("id")}:$index:$timestamp".toByteArray(Charsets.UTF_8),
                ).toString()
                add(
                    SpreadMarker(
                        timestampMs = timestamp,
                        imageFile = image?.let { File(spreadsDirectory, it) }?.takeIf(File::exists),
                        source = MarkerSource.valueOf(item.getString("source")),
                        fingerprint = item.optString("fingerprint")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?.let { Base64.decode(it, Base64.NO_WRAP) },
                        fingerprintVersion = item.optInt("fingerprintVersion", 1),
                        overrideAudioFile = item.optString("overrideAudio")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?.let { File(directory, it) },
                        overrideDurationMs = if (item.isNull("overrideDurationMs")) {
                            null
                        } else {
                            item.getLong("overrideDurationMs")
                        },
                        trimStartMs = if (item.isNull("trimStartMs")) null else item.getLong("trimStartMs"),
                        trimEndMs = if (item.isNull("trimEndMs")) null else item.getLong("trimEndMs"),
                        spreadId = if (version >= 5) item.getString("id") else deterministicId,
                        recordingStartMs = if (version >= 5) item.getLong("recordingStartMs") else timestamp,
                        recordingEndMs = if (version >= 5) item.getLong("recordingEndMs") else nextTimestamp,
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

    fun overrideAudioFile(book: StoryBook, spreadId: String): File =
        File(File(book.directory, "overrides").apply { mkdirs() }, "$spreadId.m4a")

}
