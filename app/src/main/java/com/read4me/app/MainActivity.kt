package com.read4me.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.read4me.app.data.StoryRepository
import com.read4me.app.audio.PersistentWaveformCache
import com.read4me.app.model.StoryBook
import com.read4me.app.ui.LibraryScreen
import com.read4me.app.ui.InsertSpreadScreen
import com.read4me.app.ui.Read4MeTheme
import com.read4me.app.ui.RecordingScreen
import com.read4me.app.ui.RerecordScreen
import com.read4me.app.ui.RecaptureScreen
import com.read4me.app.ui.ReferenceVerificationScreen
import com.read4me.app.ui.ReviewScreen
import com.read4me.app.ui.SetupScreen
import com.read4me.app.ui.StoryImageLoader
import com.read4me.app.ui.ChildReadingScreen
import com.read4me.app.vision.RecognitionHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private sealed interface Destination {
        data object Library : Destination
        data object Setup : Destination
        data object ChildReading : Destination
        data class Recording(val title: String) : Destination
        data class Review(val book: StoryBook, val initialUndo: StoryBook? = null, val undoImage: java.io.File? = null) : Destination
        data class Rerecord(val book: StoryBook, val spreadId: String) : Destination
        data class Recapture(
            val book: StoryBook,
            val spreadId: String,
            val undo: StoryBook?,
            val undoImage: java.io.File?,
            val returnToLibrary: Boolean = false,
        ) : Destination
        data class VerifyReference(
            val book: StoryBook,
            val spreadId: String,
            val undo: StoryBook?,
            val undoImage: java.io.File?,
            val returnToLibrary: Boolean,
        ) : Destination
        data class InsertSpread(val book: StoryBook, val anchorSpreadId: String, val undo: StoryBook?, val undoImage: java.io.File?) : Destination
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val repository = StoryRepository(this)
        val recognitionHistory = RecognitionHistoryStore(this)
        setContent {
            Read4MeTheme {
                var destination: Destination by remember { mutableStateOf(Destination.Library) }
                var books by remember { mutableStateOf(repository.loadAll()) }
                var trashedBooks by remember { mutableStateOf(repository.loadTrash()) }
                var recognitionSummaries by remember { mutableStateOf(recognitionHistory.summaries()) }
                var permissionTarget: Destination? by remember { mutableStateOf(null) }
                var permissionMessage by remember { mutableStateOf<String?>(null) }
                var archiveMessage by remember { mutableStateOf<String?>(null) }
                var exportBook by remember { mutableStateOf<StoryBook?>(null) }
                val scope = rememberCoroutineScope()

                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch {
                        archiveMessage = runCatching {
                            val refreshed = withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.use(repository::import)
                                    ?: error("Cannot open the selected file")
                                repository.loadAll()
                            }
                            books = refreshed
                            "绘本已导入"
                        }.getOrElse { "导入失败：${it.message ?: "备份文件无效"}" }
                    }
                }
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip"),
                ) { uri ->
                    val book = exportBook
                    exportBook = null
                    if (uri != null && book != null) scope.launch {
                        archiveMessage = runCatching {
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.use { repository.export(book, it) }
                                    ?: error("Cannot create the backup")
                            }
                            "绘本备份已导出"
                        }.getOrElse { "导出失败：${it.message ?: "无法写入文件"}" }
                    }
                }
                val libraryImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch {
                        archiveMessage = runCatching {
                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.use(repository::importLibrary)
                                    ?: error("Cannot open the selected file")
                            }
                            books = repository.loadAll()
                            trashedBooks = repository.loadTrash()
                            "整库备份已恢复"
                        }.getOrElse { "整库恢复失败：${it.message ?: "备份文件无效"}" }
                    }
                }
                val libraryExportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip"),
                ) { uri ->
                    if (uri != null) scope.launch {
                        archiveMessage = runCatching {
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.use(repository::exportLibrary)
                                    ?: error("Cannot create the backup")
                            }
                            "整库备份已导出"
                        }.getOrElse { "整库导出失败：${it.message ?: "无法写入文件"}" }
                    }
                }

                val permissionLauncher = rememberLauncher { granted ->
                    if (granted) {
                        permissionMessage = null
                        destination = permissionTarget ?: Destination.Library
                        permissionTarget = null
                    } else {
                        permissionMessage = "需要摄像头和麦克风权限，才能记录书面与陪读声音。"
                    }
                }

                when (val current = destination) {
                    Destination.Library -> LibraryScreen(
                        books = books,
                        trashedBooks = trashedBooks,
                        recognitionSummaries = recognitionSummaries,
                        onCreateBook = {
                            permissionMessage = null
                            destination = Destination.Setup
                        },
                        onChildMode = {
                            if (hasCameraPermission()) {
                                destination = Destination.ChildReading
                            } else {
                                permissionTarget = Destination.ChildReading
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA),
                                )
                            }
                        },
                        onOpenBook = { destination = Destination.Review(it) },
                        archiveMessage = archiveMessage,
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        onExport = { book ->
                            exportBook = book
                            val safeTitle = book.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
                            exportLauncher.launch("${safeTitle.ifBlank { "book" }}.read4me")
                        },
                        onRename = { book, title ->
                            archiveMessage = runCatching {
                                repository.rename(book, title)
                                books = repository.loadAll()
                                "绘本已重命名"
                            }.getOrElse { "重命名失败：${it.message ?: "名称无效"}" }
                        },
                        onMoveToTrash = { book ->
                            archiveMessage = runCatching {
                                repository.moveToTrash(book)
                                books = repository.loadAll()
                                trashedBooks = repository.loadTrash()
                                "绘本已移到回收站"
                            }.getOrElse { "删除失败：${it.message ?: "无法移动绘本"}" }
                        },
                        onRestore = { trashed ->
                            archiveMessage = runCatching {
                                repository.restore(trashed)
                                books = repository.loadAll()
                                trashedBooks = repository.loadTrash()
                                "绘本已恢复"
                            }.getOrElse { "恢复失败：${it.message ?: "无法恢复绘本"}" }
                        },
                        onPermanentlyDelete = { trashed ->
                            archiveMessage = runCatching {
                                repository.permanentlyDelete(trashed)
                                trashedBooks = repository.loadTrash()
                                "绘本已永久删除"
                            }.getOrElse { "永久删除失败：${it.message ?: "无法删除绘本"}" }
                        },
                        onImportLibrary = {
                            libraryImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        onExportLibrary = { libraryExportLauncher.launch("read4me-library.read4me-library") },
                        onClearRecognitionHistory = {
                            recognitionHistory.clear()
                            recognitionSummaries = emptyList()
                            archiveMessage = "识别记录已清除"
                        },
                        onClearMediaCache = {
                            PersistentWaveformCache(java.io.File(cacheDir, "read4me/waveforms")).clear()
                            StoryImageLoader.clearDisk(this@MainActivity)
                            archiveMessage = "波形和缩略图缓存已清除"
                        },
                        onRepairRecognition = { book, spreadId ->
                            val target = Destination.Recapture(book, spreadId, null, null, returnToLibrary = true)
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                    )

                    Destination.ChildReading -> ChildReadingScreen(
                        books = books,
                        recognitionHistory = recognitionHistory,
                        onExit = {
                            recognitionSummaries = recognitionHistory.summaries()
                            destination = Destination.Library
                        },
                    )

                    Destination.Setup -> SetupScreen(
                        message = permissionMessage,
                        onBack = { destination = Destination.Library },
                        onStart = { title ->
                            if (hasCapturePermissions()) {
                                destination = Destination.Recording(title)
                            } else {
                                permissionTarget = Destination.Recording(title)
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                                )
                            }
                        },
                    )

                    is Destination.Recording -> RecordingScreen(
                        title = current.title,
                        repository = repository,
                        onCancel = {
                            destination = Destination.Library
                        },
                        onFinished = { book ->
                            books = repository.loadAll()
                            destination = Destination.Review(book)
                        },
                    )

                    is Destination.Review -> ReviewScreen(
                        book = current.book,
                        repository = repository,
                        onRerecord = { updatedBook, spreadId ->
                            val target = Destination.Rerecord(updatedBook, spreadId)
                            if (hasAudioPermission()) {
                                destination = target
                            } else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        onRecapture = { updatedBook, spreadId, undo, undoImage ->
                            val target = Destination.Recapture(updatedBook, spreadId, undo, undoImage)
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        onInsert = { updatedBook, anchorSpreadId, undo, undoImage ->
                            val target = Destination.InsertSpread(updatedBook, anchorSpreadId, undo, undoImage)
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        initialUndo = current.initialUndo,
                        initialUndoImage = current.undoImage,
                        onBack = {
                            books = repository.loadAll()
                            destination = Destination.Library
                        },
                    )

                    is Destination.Rerecord -> RerecordScreen(
                        book = current.book,
                        spreadId = current.spreadId,
                        repository = repository,
                        onCancel = { destination = Destination.Review(current.book) },
                        onFinished = { updated ->
                            books = repository.loadAll()
                            destination = Destination.Review(updated)
                        },
                    )

                    is Destination.Recapture -> RecaptureScreen(
                        book = current.book,
                        spreadId = current.spreadId,
                        repository = repository,
                        onCancel = {
                            destination = if (current.returnToLibrary) Destination.Library else {
                                Destination.Review(current.book, current.undo, current.undoImage)
                            }
                        },
                        onFinished = { updated, previous, image ->
                            books = repository.loadAll()
                            recognitionHistory.record(com.read4me.app.vision.RecognitionEvent(
                                timestampMs = System.currentTimeMillis(),
                                bookId = updated.id,
                                spreadId = current.spreadId,
                                outcome = com.read4me.app.vision.RecognitionEvent.Outcome.REFERENCE_ADDED,
                                bestInliers = 0,
                                secondInliers = null,
                                latencyMs = 0,
                                searchPath = "REFERENCE_REPAIR",
                            ))
                            destination = Destination.VerifyReference(
                                updated, current.spreadId, previous, image, current.returnToLibrary,
                            )
                        },
                    )

                    is Destination.VerifyReference -> ReferenceVerificationScreen(
                        book = current.book,
                        spreadId = current.spreadId,
                        recognitionHistory = recognitionHistory,
                        onCancel = {
                            recognitionSummaries = recognitionHistory.summaries()
                            books = repository.loadAll()
                            destination = if (current.returnToLibrary) Destination.Library else {
                                Destination.Review(current.book, current.undo, current.undoImage)
                            }
                        },
                        onFinished = {
                            recognitionSummaries = recognitionHistory.summaries()
                            books = repository.loadAll()
                            destination = if (current.returnToLibrary) Destination.Library else {
                                Destination.Review(current.book, current.undo, current.undoImage)
                            }
                        },
                    )

                    is Destination.InsertSpread -> InsertSpreadScreen(
                        book = current.book,
                        anchorSpreadId = current.anchorSpreadId,
                        repository = repository,
                        onCancel = { destination = Destination.Review(current.book, current.undo, current.undoImage) },
                        onFinished = { updated, previous, image ->
                            books = repository.loadAll()
                            destination = Destination.Review(updated, previous, image)
                        },
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun rememberLauncher(onResult: (Boolean) -> Unit) =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results -> onResult(results.values.all { it }) }

    private fun hasCapturePermissions(): Boolean =
        hasCameraPermission() &&
            hasAudioPermission()

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
