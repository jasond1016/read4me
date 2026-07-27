package com.read4me.app.data

import android.content.Context
import android.util.Base64
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.PhotoQuality
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.SpreadReference
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
    private val trashDirectory = File(context.filesDir, "trash").apply { mkdirs() }

    data class TrashedBook(val book: StoryBook, val deletedAtMs: Long)

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
                    put("image", marker.references.firstOrNull()?.file?.name ?: JSONObject.NULL)
                    put("source", marker.source.name)
                    put(
                        "fingerprint",
                        marker.references.firstOrNull()?.fingerprint
                            ?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL,
                    )
                    put("fingerprintVersion", marker.references.firstOrNull()?.fingerprintVersion ?: 1)
                    put("references", JSONArray().apply {
                        marker.references.forEach { reference -> put(referenceJson(book, reference)) }
                    })
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
            put("version", 6)
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
        .filter { !it.name.startsWith('.') }
        .mapNotNull(::load)
        .sortedByDescending { it.directory.lastModified() }

    fun rename(book: StoryBook, title: String): StoryBook {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "Book title cannot be empty" }
        return book.copy(title = normalized).also(::save)
    }

    fun moveToTrash(book: StoryBook, nowMs: Long = System.currentTimeMillis()) {
        require(book.directory.parentFile?.canonicalFile == booksDirectory.canonicalFile)
        val target = File(trashDirectory, book.id)
        require(!target.exists()) { "Book is already in the recycle bin" }
        moveDirectory(book.directory, target)
        runCatching {
            File(target, ".trash.json").writeText(JSONObject().put("deletedAtMs", nowMs).toString())
        }.onFailure {
            File(target, ".trash.json").delete()
            moveDirectory(target, book.directory)
            throw it
        }
    }

    fun loadTrash(nowMs: Long = System.currentTimeMillis()): List<TrashedBook> {
        purgeExpiredTrash(nowMs)
        return trashDirectory.listFiles().orEmpty().mapNotNull { directory ->
            val book = load(directory) ?: return@mapNotNull null
            val deletedAt = runCatching {
                JSONObject(File(directory, ".trash.json").readText()).getLong("deletedAtMs")
            }.getOrDefault(directory.lastModified())
            TrashedBook(book, deletedAt)
        }.sortedByDescending(TrashedBook::deletedAtMs)
    }

    fun restore(trashed: TrashedBook): StoryBook {
        val source = trashed.book.directory
        require(source.parentFile?.canonicalFile == trashDirectory.canonicalFile)
        var id = trashed.book.id
        var target = File(booksDirectory, id)
        val manifest = File(source, "manifest.json")
        val originalManifest = manifest.readText()
        if (target.exists()) {
            id = UUID.randomUUID().toString()
            target = File(booksDirectory, id)
            manifest.writeText(JSONObject(manifest.readText()).put("id", id).toString(2))
        }
        try {
            moveDirectory(source, target)
        } catch (failure: Exception) {
            if (source.exists()) manifest.writeText(originalManifest)
            throw failure
        }
        File(target, ".trash.json").delete()
        return load(target) ?: error("Restored book could not be loaded")
    }

    fun permanentlyDelete(trashed: TrashedBook) {
        require(trashed.book.directory.parentFile?.canonicalFile == trashDirectory.canonicalFile)
        require(trashed.book.directory.deleteRecursively()) { "Could not permanently delete book" }
    }

    fun purgeExpiredTrash(nowMs: Long = System.currentTimeMillis(), retentionMs: Long = 30L * 24 * 60 * 60 * 1000) {
        trashDirectory.listFiles().orEmpty().forEach { directory ->
            val deletedAt = runCatching {
                JSONObject(File(directory, ".trash.json").readText()).getLong("deletedAtMs")
            }.getOrDefault(directory.lastModified())
            if (nowMs - deletedAt >= retentionMs) directory.deleteRecursively()
        }
    }

    fun export(book: StoryBook, output: OutputStream) = BookArchive.export(book.directory, output)

    fun exportLibrary(output: OutputStream) = LibraryArchive.export(
        loadAll().map(StoryBook::directory),
        output,
    )

    fun importLibrary(input: InputStream): List<StoryBook> {
        val staging = File(booksDirectory.parentFile, ".library-import-${UUID.randomUUID()}")
        val published = mutableListOf<File>()
        try {
            check(staging.mkdirs()) { "Could not prepare library import" }
            val extracted = LibraryArchive.extract(input, staging)
            extracted.forEach { validateFiles(load(it) ?: error("Book manifest is invalid")) }
            extracted.forEach { source ->
                val id = UUID.randomUUID().toString()
                val manifest = File(source, "manifest.json")
                manifest.writeText(JSONObject(manifest.readText()).put("id", id).toString(2))
                val target = File(booksDirectory, id)
                moveDirectory(source, target)
                published += target
            }
            return published.map { load(it) ?: error("Imported book could not be loaded") }
        } catch (failure: Exception) {
            published.forEach(File::deleteRecursively)
            throw failure
        } finally {
            staging.deleteRecursively()
        }
    }

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
        val root = book.directory.canonicalFile
        fun isContained(file: File): Boolean {
            val canonical = file.canonicalFile
            return canonical.path.startsWith(root.path + File.separator)
        }
        require(isContained(book.audioFile) && book.audioFile.isFile) { "Recording is missing or unsafe" }
        require(book.markers.isNotEmpty()) { "Book has no spreads" }
        require(book.markers.all { it.references.isNotEmpty() }) { "A spread has no reference images" }
        require(book.markers.all { marker -> marker.references.all { isContained(it.file) && it.file.isFile } }) {
            "A spread image is missing or unsafe"
        }
        require(book.markers.all {
            it.overrideAudioFile == null || isContained(it.overrideAudioFile) && it.overrideAudioFile.isFile
        }) {
            "An override recording is missing or unsafe"
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
        require(version in 1..6) { "Unsupported manifest version $version" }
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
                val legacyFingerprint = item.optString("fingerprint")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { Base64.decode(it, Base64.NO_WRAP) }
                val references = if (version >= 6) {
                    val array = item.getJSONArray("references")
                    buildList {
                        for (referenceIndex in 0 until array.length()) {
                            val reference = array.getJSONObject(referenceIndex)
                            add(readReference(directory, reference))
                        }
                    }
                } else image?.let {
                    listOf(SpreadReference(
                        file = File(spreadsDirectory, it),
                        fingerprint = legacyFingerprint,
                        fingerprintVersion = item.optInt("fingerprintVersion", 1),
                        referenceId = UUID.nameUUIDFromBytes("$deterministicId:$it".toByteArray()).toString(),
                    ))
                }.orEmpty()
                add(
                    SpreadMarker(
                        timestampMs = timestamp,
                        source = MarkerSource.valueOf(item.getString("source")),
                        references = references,
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

    private fun referenceJson(book: StoryBook, reference: SpreadReference) = JSONObject().apply {
        put("id", reference.referenceId)
        put("file", reference.file.relativeTo(book.directory).invariantSeparatorsPath)
        put("fingerprint", reference.fingerprint?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
        put("fingerprintVersion", reference.fingerprintVersion)
        reference.quality?.let { quality ->
            put("quality", JSONObject().apply {
                put("status", quality.status.name)
                put("laplacianVariance", quality.laplacianVariance ?: JSONObject.NULL)
                put("orbKeypoints", quality.orbKeypoints ?: JSONObject.NULL)
                put("meanBrightness", quality.meanBrightness ?: JSONObject.NULL)
                put("darkPixelRatio", quality.darkPixelRatio ?: JSONObject.NULL)
                put("overexposedPixelRatio", quality.overexposedPixelRatio ?: JSONObject.NULL)
                put("issues", JSONArray(quality.issues.map { it.name }))
            })
        }
    }

    private fun readReference(directory: File, json: JSONObject): SpreadReference {
        val quality = json.optJSONObject("quality")?.let { value ->
            PhotoQuality(
                status = PhotoQuality.Status.valueOf(value.getString("status")),
                laplacianVariance = value.optDoubleOrNull("laplacianVariance"),
                orbKeypoints = if (value.isNull("orbKeypoints")) null else value.getInt("orbKeypoints"),
                meanBrightness = value.optDoubleOrNull("meanBrightness"),
                darkPixelRatio = value.optDoubleOrNull("darkPixelRatio"),
                overexposedPixelRatio = value.optDoubleOrNull("overexposedPixelRatio"),
                issues = value.optJSONArray("issues")?.let { array ->
                    (0 until array.length()).map { PhotoQuality.Issue.valueOf(array.getString(it)) }
                }.orEmpty(),
            )
        }
        return SpreadReference(
            file = File(directory, json.getString("file")),
            fingerprint = json.optString("fingerprint").takeIf { it.isNotBlank() && it != "null" }
                ?.let { Base64.decode(it, Base64.NO_WRAP) },
            fingerprintVersion = json.optInt("fingerprintVersion", 1),
            quality = quality,
            referenceId = json.getString("id"),
        )
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (isNull(name) || !has(name)) null else getDouble(name)

    fun overrideAudioFile(book: StoryBook, spreadId: String): File =
        File(File(book.directory, "overrides").apply { mkdirs() }, "$spreadId.m4a")

    private fun moveDirectory(source: File, target: File) {
        require(source.exists() && !target.exists())
        target.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(source.toPath(), target.toPath())
        }
    }

}
