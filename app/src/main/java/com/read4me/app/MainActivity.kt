package com.read4me.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.read4me.app.model.readiness
import com.read4me.app.model.hasUsablePhoto
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.read4me.app.model.RecordingMode
import com.read4me.app.ui.LibraryScreen
import com.read4me.app.ui.InsertSpreadScreen
import com.read4me.app.ui.Read4MeTheme
import com.read4me.app.ui.RecordingScreen
import com.read4me.app.ui.RerecordScreen
import com.read4me.app.ui.RecaptureScreen
import com.read4me.app.ui.ReferenceCapturePurpose
import com.read4me.app.ui.ReferenceVerificationScreen
import com.read4me.app.ui.ReviewScreen
import com.read4me.app.ui.BookOverviewScreen
import com.read4me.app.ui.SpreadPreviewScreen
import com.read4me.app.ui.SetupScreen
import com.read4me.app.ui.StoryImageLoader
import com.read4me.app.ui.ChildReadingScreen
import com.read4me.app.ui.AudioBookPlayerScreen
import com.read4me.app.ui.CompletionSummaryScreen
import com.read4me.app.ui.WarmShellTab
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = StoryRepository(this)
        val recognitionHistory = RecognitionHistoryStore(this)
        setContent {
            Read4MeTheme {
                val recordingRecovery: RecordingRecoveryViewModel = viewModel()
                val destinationSaver = remember(repository) {
                    androidx.compose.runtime.saveable.mapSaver(
                        save = { route: Destination -> route.checkpoint() },
                        restore = { state -> restoreDestination(state.mapValues { it.value as String }) { id -> repository.loadAll().firstOrNull { it.id == id } } },
                    )
                }
                var destination: Destination by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = destinationSaver) {
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
                var libraryInitialTab by remember { mutableStateOf(WarmShellTab.LIBRARY) }
                val scope = rememberCoroutineScope()
                val libraryState = rememberSaveableStateHolder()
                val pageListState = rememberSaveableStateHolder()

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
                                (permissionTarget as? Destination.Recording)?.mode == RecordingMode.MANUAL || permissionTarget is Destination.Rerecord
                            ) {
                                "需要麦克风权限才能录音，已有内容不会受影响。"
                            } else if (pendingRecording != null || permissionTarget is Destination.Recording) {
                                "需要摄像头和麦克风权限才能自动记录翻页，也可以选择只录声音。"
                            } else {
                                "需要摄像头权限才能拍摄或识别绘本。已有录音仍可正常播放。"
                            }
                        pendingRecording = null
                    }
                }

                when (val current = destination) {
                    Destination.Library -> libraryState.SaveableStateProvider("library") { LibraryScreen(
                        books = books,
                        trashedBooks = trashedBooks,
                        recognitionSummaries = recognitionSummaries,
                        initialShellTab = libraryInitialTab,
                        onCreateBook = {
                            permissionMessage = null
                            destination = Destination.Setup
                        },
                        onChildMode = {
                            if (hasCameraPermission()) {
                                destination = Destination.ChildReading()
                            } else {
                                permissionTarget = Destination.ChildReading()
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA),
                                )
                            }
                        },
                        onOpenBook = {
                            libraryInitialTab = WarmShellTab.LIBRARY
                            destination = Destination.BookDetails(it)
                        },
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
                    ) }

                    is Destination.ChildReading -> ChildReadingScreen(
                        books = books,
                        recognitionHistory = recognitionHistory,
                        onExit = {
                            recognitionSummaries = recognitionHistory.summaries()
                            destination = current.returnBookId?.let { bookId ->
                                repository.loadAll().firstOrNull { it.id == bookId }?.let { Destination.BookDetails(it) }
                            } ?: Destination.Library
                        },
                    )

                    is Destination.BookDetails -> BookOverviewScreen(
                        book = current.book,
                        onBack = {
                            libraryInitialTab = WarmShellTab.LIBRARY
                            books = repository.loadAll()
                            destination = Destination.Library
                        },
                        onStartReading = {
                            val target = Destination.ChildReading(current.book.id)
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        onContinueRecording = {
                            val mode = current.book.recordingMode
                            val target = Destination.Recording(current.book, mode, returnToDetails = true)
                            recordingRecovery.recordingMode = mode
                            val granted = if (mode == RecordingMode.MANUAL) hasAudioPermission() else hasCapturePermissions()
                            if (granted) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(
                                    if (mode == RecordingMode.MANUAL) arrayOf(Manifest.permission.RECORD_AUDIO)
                                    else arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                                )
                            }
                        },
                        onPlayBook = {
                            destination = Destination.AudioBook(
                                current.book,
                                Destination.AudioBookReturn.DETAILS,
                            )
                        },
                        onBrowseSpreads = {
                            destination = Destination.AudioBook(
                                current.book,
                                Destination.AudioBookReturn.DETAILS,
                                initiallyShowPageList = true,
                                playWhenReady = false,
                                pageListBackToCaller = true,
                            )
                        },
                        onOpenSpread = { spreadId ->
                            val spread = current.book.spreads.firstOrNull { it.spreadId == spreadId }
                            if (spread != null && !spread.hasUsablePhoto) {
                                val target = Destination.Recapture(
                                    current.book,
                                    spreadId,
                                    undo = null,
                                    undoImage = null,
                                    returnToPreview = true,
                                )
                                if (hasCameraPermission()) destination = target
                                else {
                                    permissionTarget = target
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                }
                            } else destination = Destination.SpreadPreview(current.book, spreadId)
                        },
                        onOrganize = {
                            destination = Destination.Review(
                                current.book,
                                organizeDraft = current.book,
                                returnToDetails = true,
                            )
                        },
                        onBatchRecapture = {
                            val missingSpreadIds = current.book.readiness().missingPhotoIds
                            val target = Destination.BatchRecapture(
                                current.book,
                                missingSpreadIds,
                                completed = 0,
                                total = missingSpreadIds.size,
                                returnToDetails = true,
                            )
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        onCreateBook = { destination = Destination.Setup },
                        onOpenMe = {
                            libraryInitialTab = WarmShellTab.ME
                            destination = Destination.Library
                        },
                    )

                    is Destination.SpreadPreview -> SpreadPreviewScreen(
                        book = current.book,
                        initialSpreadId = current.spreadId,
                        repository = repository,
                        onBack = {
                            books = repository.loadAll()
                            destination = books.firstOrNull { it.id == current.book.id }
                                ?.let(Destination::BookDetails) ?: Destination.Library
                        },
                        onEditNarration = { updatedBook, spreadId ->
                            destination = Destination.Review(
                                updatedBook,
                                focusedSpreadId = spreadId,
                                returnToPreviewSpreadId = spreadId,
                            )
                        },
                        onCaptureReference = { updatedBook, spreadId, purpose ->
                            val target = Destination.Recapture(
                                updatedBook,
                                spreadId,
                                undo = null,
                                undoImage = null,
                                returnToPreview = true,
                                capturePurpose = purpose,
                            )
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
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
                            destination = if (current.returnToDetails) {
                                books.firstOrNull { it.id == current.book.id }?.let { Destination.BookDetails(it) }
                                    ?: Destination.Library
                            } else Destination.Library
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
                        onReview = { destination = Destination.BookDetails(current.book) },
                        onLibrary = { books = repository.loadAll(); destination = Destination.Library },
                        onRepairPhotos = {
                            val missing = current.book.readiness().missingPhotoIds
                            if (missing.isNotEmpty()) {
                                val target = Destination.BatchRecapture(current.book, missing, 0, missing.size, returnToDetails = true)
                                if (hasCameraPermission()) destination = target
                                else { permissionTarget = target; permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
                            }
                        },
                        onRepairAudio = { spreadId ->
                            val target = Destination.Rerecord(current.book, spreadId, returnToDetails = true)
                            if (hasAudioPermission()) destination = target
                            else { permissionTarget = target; permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                        },
                    )

                    is Destination.Review -> ReviewScreen(
                        book = current.book,
                        repository = repository,
                        initialSelectedSpreadId = current.focusedSpreadId,
                        returnActionLabel = if (current.pageListReturnTo != null || current.returnToPreviewSpreadId != null) "完成" else null,
                        onRerecord = { updatedBook, spreadId ->
                            val target = Destination.Rerecord(
                                updatedBook,
                                spreadId,
                                returnToDetails = current.returnToDetails,
                                returnToPreviewSpreadId = current.returnToPreviewSpreadId,
                            )
                            if (hasAudioPermission()) {
                                destination = target
                            } else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        onRecapture = { updatedBook, spreadId, undo, undoImage ->
                            val target = Destination.Recapture(
                                updatedBook,
                                spreadId,
                                undo,
                                undoImage,
                                returnToDetails = current.returnToDetails,
                                returnToPreview = current.returnToPreviewSpreadId != null,
                            )
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
                                returnToDetails = current.returnToDetails,
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
                                returnToDetails = current.returnToDetails,
                            )
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        onPlayBook = {
                            destination = Destination.AudioBook(
                                it,
                                if (current.returnToDetails) Destination.AudioBookReturn.DETAILS
                                else Destination.AudioBookReturn.REVIEW,
                            )
                        },
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
                                    pageListBackToCaller = current.pageListBackToCaller,
                                )
                            } else if (current.returnToPreviewSpreadId != null) {
                                val refreshed = repository.loadAll().firstOrNull { it.id == current.book.id } ?: current.book
                                Destination.SpreadPreview(refreshed, current.returnToPreviewSpreadId)
                            } else if (current.returnToDetails) {
                                val refreshed = repository.loadAll().firstOrNull { it.id == current.book.id } ?: current.book
                                Destination.BookDetails(refreshed)
                            } else Destination.Library
                        },
                    )

                    is Destination.AudioBook -> AudioBookPlayerScreen(
                        pageListState = pageListState,
                        book = current.book,
                        initiallyShowPageList = current.initiallyShowPageList,
                        playWhenReady = current.playWhenReady,
                        pageListBackToCaller = current.pageListBackToCaller,
                        onRepairSpread = { spreadId, audio ->
                            val returnTo = current.copy(initiallyShowPageList = true, playWhenReady = false)
                            val target = if (audio) Destination.Rerecord(current.book, spreadId, returnToPlayer = returnTo)
                            else Destination.Recapture(current.book, spreadId, null, null, returnToPlayer = returnTo)
                            if (if (audio) hasAudioPermission() else hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(if (audio) Manifest.permission.RECORD_AUDIO else Manifest.permission.CAMERA))
                            }
                        },
                        onEditSpread = { spreadId, wasPlaying ->
                            val refreshed = repository.loadAll().firstOrNull { it.id == current.book.id } ?: current.book
                            destination = Destination.Review(
                                refreshed,
                                focusedSpreadId = spreadId,
                                pageListReturnTo = current.returnTo,
                                resumePageListPlayback = wasPlaying,
                                pageListBackToCaller = current.pageListBackToCaller,
                            )
                        },
                        onBack = {
                            destination = when (current.returnTo) {
                                Destination.AudioBookReturn.LIBRARY -> Destination.Library
                                Destination.AudioBookReturn.DETAILS -> Destination.BookDetails(current.book)
                                Destination.AudioBookReturn.REVIEW -> Destination.Review(current.book)
                                Destination.AudioBookReturn.COMPLETION_SUMMARY -> Destination.CompletionSummary(current.book)
                            }
                        },
                    )

                    is Destination.Rerecord -> RerecordScreen(
                        book = current.book,
                        spreadId = current.spreadId,
                        repository = repository,
                        onCancel = {
                            destination = current.returnToPlayer?.copy(book = current.book) ?: current.returnToPreviewSpreadId?.let {
                                Destination.SpreadPreview(current.book, it)
                            } ?: Destination.Review(
                                current.book,
                                focusedSpreadId = current.spreadId,
                                returnToDetails = current.returnToDetails,
                            )
                        },
                        onFinished = { updated ->
                            books = repository.loadAll()
                            destination = current.returnToPlayer?.copy(book = updated) ?: current.returnToPreviewSpreadId?.let {
                                Destination.SpreadPreview(updated, it)
                            } ?: Destination.Review(
                                updated,
                                focusedSpreadId = current.spreadId,
                                returnToDetails = current.returnToDetails,
                            )
                        },
                    )

                    is Destination.Recapture -> RecaptureScreen(
                        book = current.book,
                        spreadId = current.spreadId,
                        repository = repository,
                        capturePurpose = current.capturePurpose,
                        onCancel = {
                            destination = current.returnToPlayer ?: if (current.returnToLibrary) Destination.Library
                            else if (current.returnToPreview) Destination.SpreadPreview(current.book, current.spreadId)
                            else {
                                Destination.Review(
                                    current.book,
                                    current.undo,
                                    current.undoImage,
                                    returnToDetails = current.returnToDetails,
                                )
                            }
                        },
                        onFinished = { updated, previous, image ->
                            books = repository.loadAll()
                            destination = Destination.VerifyReference(
                                updated,
                                current.spreadId,
                                previous,
                                image,
                                current.returnToLibrary,
                                returnToDetails = current.returnToDetails,
                                returnToPreview = current.returnToPreview,
                                returnToPlayer = current.returnToPlayer,
                            )
                        },
                    )

                    is Destination.BatchRecapture -> {
                        val spreadId = current.remainingSpreadIds.firstOrNull()
                        if (spreadId == null) {
                            destination = if (current.returnToDetails) Destination.BookDetails(current.book)
                            else Destination.Review(current.book)
                        } else {
                            key(current.book.id, spreadId) {
                                RecaptureScreen(
                                    book = current.book,
                                    spreadId = spreadId,
                                    repository = repository,
                                    progressLabel = "${current.completed + 1}/${current.total}",
                                    onSkip = {
                                        val remaining = current.remainingSpreadIds.drop(1)
                                        destination = if (remaining.isEmpty()) {
                                            if (current.returnToDetails) Destination.BookDetails(current.book)
                                            else Destination.Review(current.book)
                                        }
                                        else current.copy(
                                            remainingSpreadIds = remaining,
                                            completed = current.completed + 1,
                                        )
                                    },
                                    onCancel = {
                                        books = repository.loadAll()
                                        destination = if (current.returnToDetails) Destination.BookDetails(current.book)
                                        else Destination.Review(current.book)
                                    },
                                    onFinished = { updated, _, _ ->
                                        books = repository.loadAll()
                                        val remaining = current.remainingSpreadIds.drop(1)
                                        destination = if (remaining.isEmpty()) {
                                            Destination.VerifyReference(
                                                updated,
                                                spreadId,
                                                null,
                                                null,
                                                returnToLibrary = false,
                                                returnToDetails = current.returnToDetails,
                                                returnToPreview = false,
                                            )
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
                            destination = current.returnToPlayer?.copy(book = current.book) ?: if (current.returnToLibrary) Destination.Library else {
                                if (current.returnToPreview) Destination.SpreadPreview(current.book, current.spreadId)
                                else if (current.returnToDetails) Destination.BookDetails(current.book)
                                else Destination.Review(current.book, current.undo, current.undoImage)
                            }
                        },
                        onFinished = {
                            recognitionSummaries = recognitionHistory.summaries()
                            books = repository.loadAll()
                            destination = current.returnToPlayer?.copy(book = current.book) ?: if (current.returnToLibrary) Destination.Library else {
                                if (current.returnToPreview) Destination.SpreadPreview(current.book, current.spreadId)
                                else if (current.returnToDetails) Destination.BookDetails(current.book)
                                else Destination.Review(current.book, current.undo, current.undoImage)
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
                                returnToDetails = current.returnToDetails,
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
                                returnToDetails = current.returnToDetails,
                            )
                        },
                    )
                }
                if (permissionMessage != null && destination != Destination.Setup) AlertDialog(
                    onDismissRequest = { permissionMessage = null; permissionTarget = null },
                    title = { Text("还需要开启权限") },
                    text = { Text(requireNotNull(permissionMessage)) },
                    confirmButton = { TextButton(onClick = {
                        permissionMessage = null
                        permissionTarget = null
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }) { Text("去设置") } },
                    dismissButton = { TextButton(onClick = { permissionMessage = null; permissionTarget = null }) { Text("稍后再说") } },
                )
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
