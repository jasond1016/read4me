package com.read4me.app.data

import android.content.Context
import android.util.Base64
import com.read4me.app.audio.audioDurationMs
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.NarrationSegment
import com.read4me.app.model.PhotoQuality
import com.read4me.app.model.RecordingMode
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.SpreadReference
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StoryStatus
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

    init {
        recoverPendingManifests()
    }

    data class TrashedBook(val book: StoryBook, val deletedAtMs: Long)

    fun createDraft(title: String, recordingMode: RecordingMode = RecordingMode.CAMERA): StoryBook {
        val id = UUID.randomUUID().toString()
        val directory = File(booksDirectory, id).apply {
            mkdirs()
            File(this, "spreads").mkdirs()
            File(this, "recordings").mkdirs()
        }
        return StoryBook(
            id,
            title.ifBlank { "我们的故事" },
            directory,
            File(directory, "legacy.m4a"),
            0L,
            emptyList(),
            StoryStatus.IN_PROGRESS,
            recordingMode = recordingMode,
        ).also(::save)
    }

    fun imageFile(book: StoryBook, spreadId: String): File =
        File(File(book.directory, "spreads"), "$spreadId.jpg")

    fun save(book: StoryBook) {
        // Repair one page at a time even if another already-published file has disappeared.
        // New references still have to exist, and containment is always checked.
        val retainedFiles = load(book.directory)?.let { previous ->
            previous.markers.flatMap { marker -> marker.references.map { it.file } + marker.segments.map { it.file } }
                .map { it.canonicalFile }.toSet()
        }.orEmpty()
        validateFiles(book, verifyMediaDuration = false, retainedFiles = retainedFiles)
        val markers = JSONArray().apply {
            book.markers.forEach { marker ->
                put(JSONObject().apply {
                    put("id", marker.spreadId)
                    put("timestampMs", marker.timestampMs)
                    put("segments", JSONArray().apply {
                        marker.segments.forEach { segment ->
                            put(JSONObject().apply {
                                put("file", segment.file.relativeTo(book.directory).invariantSeparatorsPath)
                                put("startMs", segment.startMs)
                                put("endMs", segment.endMs)
                            })
                        }
                    })
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
                    put("trimStartMs", marker.trimStartMs ?: JSONObject.NULL)
                    put("trimEndMs", marker.trimEndMs ?: JSONObject.NULL)
                })
            }
        }
        val manifest = JSONObject().apply {
            put("version", 7)
            put("id", book.id)
            put("title", book.title)
            put("status", book.status.name)
            put("recordingMode", book.recordingMode.name)
            put("resumeSpreadId", book.resumeSpreadId ?: JSONObject.NULL)
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

    fun export(book: StoryBook, output: OutputStream) {
        validateFiles(book)
        BookArchive.export(book.directory, output)
    }

    fun exportLibrary(output: OutputStream) {
        val books = loadAll()
        books.forEach(::validateFiles)
        LibraryArchive.export(books.map(StoryBook::directory), output)
    }

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

    private fun validateFiles(book: StoryBook, verifyMediaDuration: Boolean = true, retainedFiles: Set<File> = emptySet()) {
        val root = book.directory.canonicalFile
        fun isContained(file: File): Boolean {
            val canonical = file.canonicalFile
            return canonical.path.startsWith(root.path + File.separator)
        }
        if (book.status == StoryStatus.COMPLETE) {
            require(book.markers.isNotEmpty()) { "Complete book has no playable spreads" }
            require(book.markers.all { it.segments.isNotEmpty() }) { "A complete spread has no narration" }
        } else {
            require(book.markers.all { it.segments.isNotEmpty() }) {
                "A published draft spread has no narration"
            }
        }
        require(book.markers.map(SpreadMarker::spreadId).distinct().size == book.markers.size) {
            "Spread IDs are not unique"
        }
        require(
            if (book.markers.isEmpty()) book.resumeSpreadId == null
            else book.resumeSpreadId != null && book.markers.any { it.spreadId == book.resumeSpreadId }
        ) { "Recording cursor does not identify a spread" }
        require(book.markers.all { marker -> marker.references.all { isContained(it.file) && canRetainMediaFile(it.file, retainedFiles) } }) {
            "A spread image is missing or unsafe"
        }
        require(book.markers.flatMap(SpreadMarker::segments).all {
            isContained(it.file) && canRetainMediaFile(it.file, retainedFiles) && it.startMs >= 0 && it.endMs > it.startMs
        }) {
            "A narration segment is missing, unsafe, or invalid"
        }
        if (verifyMediaDuration) {
            val mediaDurations = mutableMapOf<File, Long>()
            require(book.markers.flatMap(SpreadMarker::segments).all { segment ->
                val duration = mediaDurations.getOrPut(segment.file.canonicalFile) {
                    audioDurationMs(segment.file) ?: -1L
                }
                segment.endMs <= duration
            }) { "A narration segment exceeds its media duration" }
        }
        require(book.markers.all { marker ->
            val total = marker.segments.sumOf(NarrationSegment::durationMs)
            val start = marker.trimStartMs ?: 0L
            val end = marker.trimEndMs ?: total
            start >= 0L && end > start && end <= total
        }) { "A spread has an invalid or empty trim range" }
    }

    /** Completes a save whose manifest was fully written but whose final rename was interrupted. */
    private fun recoverPendingManifests() {
        booksDirectory.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            val pending = File(directory, "manifest.json.pending")
            if (!pending.isFile) return@forEach
            val target = File(directory, "manifest.json")
            val backup = File(directory, "manifest.json.recovery-backup")
            if (backup.isFile) {
                val targetIsValid = runCatching {
                    validateFiles(load(directory) ?: error("Current manifest is invalid"), verifyMediaDuration = false)
                }.isSuccess
                if (targetIsValid) backup.delete() else runCatching {
                    Files.move(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            val hadTarget = target.isFile
            var backupCreatedThisAttempt = false
            runCatching {
                val candidate = JSONObject(pending.readText())
                require(candidate.optInt("version") == 7)
                require(candidate.getString("id") == directory.name)
                require(candidate.has("markers") && candidate.has("status"))
                if (target.isFile) {
                    Files.move(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    backupCreatedThisAttempt = true
                }
                Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                validateFiles(load(directory) ?: error("Pending manifest is invalid"), verifyMediaDuration = false)
                backup.delete()
            }.onFailure {
                if (backupCreatedThisAttempt && backup.isFile) {
                    runCatching {
                        Files.move(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                } else if (!hadTarget) {
                    target.delete()
                }
            }
        }
    }

    fun deleteDraft(book: StoryBook) {
        require(book.status == StoryStatus.IN_PROGRESS)
        book.directory.deleteRecursively()
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
        require(version in 1..7) { "Unsupported manifest version $version" }
        val markerJson = json.getJSONArray("markers")
        val spreadsDirectory = File(directory, "spreads")
        val markers = buildList {
            for (index in 0 until markerJson.length()) {
                val item = markerJson.getJSONObject(index)
                val image = item.optString("image").takeIf { it.isNotBlank() && it != "null" }
                val timestamp = item.getLong("timestampMs")
                val nextTimestamp = markerJson.optJSONObject(index + 1)?.optLong("timestampMs")
                    ?: json.optLong("durationMs", timestamp)
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
                val legacyOverride = item.optString("overrideAudio")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { File(directory, it) }
                val legacyOverrideDuration = if (item.isNull("overrideDurationMs")) null else item.getLong("overrideDurationMs")
                val recordingStart = if (version in 5..6) item.getLong("recordingStartMs") else timestamp
                val recordingEnd = if (version in 5..6) item.getLong("recordingEndMs") else nextTimestamp
                val segments = if (version >= 7) {
                    val array = item.getJSONArray("segments")
                    buildList {
                        for (segmentIndex in 0 until array.length()) {
                            val segment = array.getJSONObject(segmentIndex)
                            add(NarrationSegment(File(directory, segment.getString("file")), segment.getLong("startMs"), segment.getLong("endMs")))
                        }
                    }
                } else if (legacyOverride != null && (legacyOverrideDuration ?: 0) > 0) {
                    listOf(NarrationSegment(legacyOverride, 0, legacyOverrideDuration!!))
                } else if (recordingEnd > recordingStart) {
                    listOf(NarrationSegment(File(directory, json.getString("audio")), recordingStart, recordingEnd))
                } else emptyList()
                add(
                    SpreadMarker(
                        timestampMs = timestamp,
                        source = MarkerSource.valueOf(item.getString("source")),
                        references = references,
                        segments = segments,
                        trimStartMs = if (item.isNull("trimStartMs")) null else item.getLong("trimStartMs") - if (version < 7 && legacyOverride == null) recordingStart else 0,
                        trimEndMs = if (item.isNull("trimEndMs")) null else item.getLong("trimEndMs") - if (version < 7 && legacyOverride == null) recordingStart else 0,
                        spreadId = if (version >= 5) item.getString("id") else deterministicId,
                        recordingStartMs = recordingStart,
                        recordingEndMs = recordingEnd,
                    ),
                )
            }
        }
        StoryBook(
            id = json.getString("id"),
            title = json.getString("title"),
            directory = directory,
            audioFile = json.optString("audio").takeIf(String::isNotBlank)?.let { File(directory, it) } ?: File(directory, "legacy.m4a"),
            durationMs = json.optLong("durationMs", markers.sumOf { marker -> marker.segments.sumOf(NarrationSegment::durationMs) }),
            markers = markers,
            status = if (version >= 7) StoryStatus.valueOf(json.getString("status")) else StoryStatus.COMPLETE,
            resumeSpreadId = if (version >= 7) json.optString("resumeSpreadId").takeIf { it.isNotBlank() && it != "null" }
                else markers.lastOrNull()?.spreadId,
            recordingMode = json.optString("recordingMode")
                .takeIf { it.isNotBlank() }
                ?.let(RecordingMode::valueOf)
                ?: if (markers.isNotEmpty() && markers.all { it.references.isEmpty() }) RecordingMode.MANUAL
                else RecordingMode.CAMERA,
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
        File(File(book.directory, "recordings").apply { mkdirs() }, "${spreadId}-${UUID.randomUUID()}.m4a")

    fun recordingFile(book: StoryBook): File =
        File(File(book.directory, "recordings").apply { mkdirs() }, "${UUID.randomUUID()}.m4a")

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
