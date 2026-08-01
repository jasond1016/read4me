package com.read4me.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.read4me.app.data.StoryRepository
import com.read4me.app.audio.PersistentWaveformCache
import com.read4me.app.model.StoryBook
import com.read4me.app.model.RecordingMode
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
import com.read4me.app.ui.AudioBookPlayerScreen
import com.read4me.app.ui.CompletionSummaryScreen
import com.read4me.app.vision.RecognitionHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingRecoveryViewModel : ViewModel() {
    var pendingCandidate by mutableStateOf<StoryBook?>(null)
    var finishAfterSave by mutableStateOf(false)
    var recordingMode by mutableStateOf(RecordingMode.CAMERA)
}

class MainActivity : ComponentActivity() {
    private sealed interface Destination {
        enum class AudioBookReturn { LIBRARY, REVIEW, COMPLETION_SUMMARY }

        data object Library : Destination
        data object Setup : Destination
        data object ChildReading : Destination
        data class Recording(val book: StoryBook, val mode: RecordingMode = RecordingMode.CAMERA) : Destination
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
        ) : Destination
        data class AudioBook(
            val book: StoryBook,
            val returnTo: AudioBookReturn,
            val initiallyShowPageList: Boolean = false,
            val playWhenReady: Boolean = true,
        ) : Destination
        data class CompletionSummary(val book: StoryBook) : Destination
        data class Rerecord(val book: StoryBook, val spreadId: String) : Destination
        data class Recapture(
            val book: StoryBook,
            val spreadId: String,
            val undo: StoryBook?,
            val undoImage: java.io.File?,
            val returnToLibrary: Boolean = false,
        ) : Destination
        data class BatchRecapture(
            val book: StoryBook,
            val remainingSpreadIds: List<String>,
            val completed: Int,
            val total: Int,
        ) : Destination
        data class VerifyReference(
            val book: StoryBook,
            val spreadId: String,
            val undo: StoryBook?,
            val undoImage: java.io.File?,
            val returnToLibrary: Boolean,
        ) : Destination
        data class InsertSpread(
            val baseBook: StoryBook,
            val draftBook: StoryBook,
            val anchorSpreadId: String,
            val organizeSelected: String?,
            val organizeImages: List<java.io.File>,
            val editorUndo: StoryBook?,
            val editorUndoImages: List<java.io.File>,
        ) : Destination
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = StoryRepository(this)
        val recognitionHistory = RecognitionHistoryStore(this)
        setContent {
            Read4MeTheme {
                val recordingRecovery: RecordingRecoveryViewModel = viewModel()
                var destination: Destination by remember {
                    mutableStateOf(
                        recordingRecovery.pendingCandidate?.let {
                            Destination.Recording(it, recordingRecovery.recordingMode)
                        } ?: Destination.Library,
                    )
                }
                var books by remember { mutableStateOf(repository.loadAll()) }
                var trashedBooks by remember { mutableStateOf(repository.loadTrash()) }
                var recognitionSummaries by remember { mutableStateOf(recognitionHistory.summaries()) }
                var permissionTarget: Destination? by remember { mutableStateOf(null) }
                var pendingRecording by remember { mutableStateOf<Pair<String, RecordingMode>?>(null) }
                var permissionMessage by remember { mutableStateOf<String?>(null) }
                var archiveMessage by remember { mutableStateOf<String?>(null) }
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
                        val pending = pendingRecording
                        if (pending != null) {
                            val draft = repository.createDraft(pending.first, pending.second)
                            books = repository.loadAll()
                            recordingRecovery.recordingMode = pending.second
                            destination = Destination.Recording(draft, pending.second)
                        } else {
                            destination = permissionTarget ?: Destination.Library
                        }
                        pendingRecording = null
                        permissionTarget = null
                    } else {
                        permissionMessage =
                            if (pendingRecording?.second == RecordingMode.MANUAL ||
                                (permissionTarget as? Destination.Recording)?.mode == RecordingMode.MANUAL
                            ) {
                                "手动翻页录制只需要麦克风权限。"
                            } else {
                                "需要摄像头和麦克风权限，才能记录书面与陪读声音。"
                            }
                        pendingRecording = null
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
                        onPlayBook = { destination = Destination.AudioBook(it, Destination.AudioBookReturn.LIBRARY) },
                        onContinueBook = { book ->
                            val mode = book.recordingMode
                            val target = Destination.Recording(book, mode)
                            recordingRecovery.recordingMode = mode
                            val granted = if (mode == RecordingMode.MANUAL) hasAudioPermission() else hasCapturePermissions()
                            if (granted) destination = target else {
                                permissionTarget = target
                                permissionLauncher.launch(
                                    if (mode == RecordingMode.MANUAL) arrayOf(Manifest.permission.RECORD_AUDIO)
                                    else arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                                )
                            }
                        },
                        archiveMessage = archiveMessage,
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        onExport = { book ->
                            scope.launch {
                                archiveMessage = runCatching {
                                    val shareFile = withContext(Dispatchers.IO) {
                                        val directory = java.io.File(cacheDir, "read4me-shares").apply { mkdirs() }
                                        directory.listFiles()?.forEach { it.delete() }
                                        val safeTitle = book.title.replace(Regex("[\\/:*?\"<>|]"), "_").take(40)
                                        java.io.File(directory, "${safeTitle.ifBlank { "book" }}.read4me").also { file ->
                                            file.outputStream().use { repository.export(book, it) }
                                        }
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        this@MainActivity,
                                        "$packageName.fileprovider",
                                        shareFile,
                                    )
                                    startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_TITLE, shareFile.name)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                            "分享给家人",
                                        ),
                                    )
                                    "已准备分享 ${book.title}"
                                }.getOrElse { "分享失败：${it.message ?: "无法创建绘本文件"}" }
                            }
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
                        onStart = { title, mode ->
                            recordingRecovery.recordingMode = mode
                            val granted = if (mode == RecordingMode.MANUAL) hasAudioPermission() else hasCapturePermissions()
                            if (granted) {
                                val draft = repository.createDraft(title, mode)
                                books = repository.loadAll()
                                destination = Destination.Recording(draft, mode)
                            } else {
                                pendingRecording = title to mode
                                permissionLauncher.launch(
                                    if (mode == RecordingMode.MANUAL) arrayOf(Manifest.permission.RECORD_AUDIO)
                                    else arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                                )
                            }
                        },
                    )

                    is Destination.Recording -> RecordingScreen(
                        book = current.book,
                        mode = current.mode,
                        repository = repository,
                        recovery = recordingRecovery,
                        onCancel = {
                            books = repository.loadAll()
                            trashedBooks = repository.loadTrash()
                            destination = Destination.Library
                        },
                        onFinished = { book ->
                            books = repository.loadAll()
                            trashedBooks = repository.loadTrash()
                            destination = Destination.CompletionSummary(book)
                        },
                    )

                    is Destination.CompletionSummary -> CompletionSummaryScreen(
                        book = current.book,
                        onPlay = {
                            destination = Destination.AudioBook(
                                current.book,
                                Destination.AudioBookReturn.COMPLETION_SUMMARY,
                            )
                        },
                        onReview = { destination = Destination.Review(current.book) },
                    )

                    is Destination.Review -> ReviewScreen(
                        book = current.book,
                        repository = repository,
                        initialSelectedSpreadId = current.focusedSpreadId,
                        returnActionLabel = if (current.pageListReturnTo != null) "完成" else null,
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
                        onBatchRecapture = { updatedBook, missingSpreadIds ->
                            val target = Destination.BatchRecapture(
                                updatedBook,
                                missingSpreadIds,
                                completed = 0,
                                total = missingSpreadIds.size,
                            )
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        onInsert = { baseBook, draftBook, anchorSpreadId, organizeSelected, organizeImages, editorUndo, editorUndoImages ->
                            val target = Destination.InsertSpread(
                                baseBook,
                                draftBook,
                                anchorSpreadId,
                                organizeSelected,
                                organizeImages,
                                editorUndo,
                                editorUndoImages,
                            )
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        onPlayBook = { destination = Destination.AudioBook(it, Destination.AudioBookReturn.REVIEW) },
                        initialUndo = current.initialUndo,
                        initialUndoImage = current.undoImage,
                        initialUndoImages = current.undoImages,
                        initialOrganizeDraft = current.organizeDraft,
                        initialOrganizeSelected = current.organizeSelected,
                        initialOrganizeCurrent = current.organizeCurrent,
                        initialOrganizeImages = current.organizeImages,
                        onBack = {
                            books = repository.loadAll()
                            destination = if (current.pageListReturnTo != null) {
                                val refreshed = repository.loadAll().firstOrNull { it.id == current.book.id } ?: current.book
                                Destination.AudioBook(
                                    refreshed,
                                    current.pageListReturnTo,
                                    initiallyShowPageList = true,
                                    playWhenReady = current.resumePageListPlayback,
                                )
                            } else Destination.Library
                        },
                    )

                    is Destination.AudioBook -> AudioBookPlayerScreen(
                        book = current.book,
                        initiallyShowPageList = current.initiallyShowPageList,
                        playWhenReady = current.playWhenReady,
                        onEditSpread = { spreadId, wasPlaying ->
                            val refreshed = repository.loadAll().firstOrNull { it.id == current.book.id } ?: current.book
                            destination = Destination.Review(
                                refreshed,
                                focusedSpreadId = spreadId,
                                pageListReturnTo = current.returnTo,
                                resumePageListPlayback = wasPlaying,
                            )
                        },
                        onBack = {
                            destination = when (current.returnTo) {
                                Destination.AudioBookReturn.LIBRARY -> Destination.Library
                                Destination.AudioBookReturn.REVIEW -> Destination.Review(current.book)
                                Destination.AudioBookReturn.COMPLETION_SUMMARY -> Destination.CompletionSummary(current.book)
                            }
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
                            destination = Destination.VerifyReference(
                                updated, current.spreadId, previous, image, current.returnToLibrary,
                            )
                        },
                    )

                    is Destination.BatchRecapture -> {
                        val spreadId = current.remainingSpreadIds.firstOrNull()
                        if (spreadId == null) {
                            destination = Destination.Review(current.book)
                        } else {
                            key(current.book.id, spreadId) {
                                RecaptureScreen(
                                    book = current.book,
                                    spreadId = spreadId,
                                    repository = repository,
                                    progressLabel = "${current.completed + 1}/${current.total}",
                                    onSkip = {
                                        val remaining = current.remainingSpreadIds.drop(1)
                                        destination = if (remaining.isEmpty()) Destination.Review(current.book)
                                        else current.copy(
                                            remainingSpreadIds = remaining,
                                            completed = current.completed + 1,
                                        )
                                    },
                                    onCancel = {
                                        books = repository.loadAll()
                                        destination = Destination.Review(current.book)
                                    },
                                    onFinished = { updated, _, _ ->
                                        books = repository.loadAll()
                                        val remaining = current.remainingSpreadIds.drop(1)
                                        destination = if (remaining.isEmpty()) {
                                            Destination.VerifyReference(updated, spreadId, null, null, false)
                                        } else {
                                            current.copy(
                                                book = updated,
                                                remainingSpreadIds = remaining,
                                                completed = current.completed + 1,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

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
                        book = current.draftBook,
                        anchorSpreadId = current.anchorSpreadId,
                        repository = repository,
                        onCancel = {
                            destination = Destination.Review(
                                current.baseBook,
                                initialUndo = current.editorUndo,
                                undoImages = current.editorUndoImages,
                                organizeDraft = current.draftBook,
                                organizeSelected = current.organizeSelected,
                                organizeCurrent = current.anchorSpreadId,
                                organizeImages = current.organizeImages,
                            )
                        },
                        onFinished = { updated, previous, image ->
                            destination = Destination.Review(
                                current.baseBook,
                                initialUndo = current.editorUndo,
                                undoImages = current.editorUndoImages,
                                organizeDraft = updated,
                                organizeSelected = current.organizeSelected,
                                organizeCurrent = current.anchorSpreadId,
                                organizeImages = current.organizeImages + image,
                            )
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
