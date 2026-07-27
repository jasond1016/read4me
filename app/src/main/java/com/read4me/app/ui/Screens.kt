package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.read4me.app.audio.AudioSegmentPlayer
import com.read4me.app.audio.AudioWaveformExtractor
import com.read4me.app.audio.StoryAudioRecorder
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.SpreadReference
import com.read4me.app.model.PhotoQuality
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StoryBookEditor
import com.read4me.app.model.StoryEditSession
import com.read4me.app.model.StorySpread
import com.read4me.app.vision.CameraFrameAnalyzer
import com.read4me.app.vision.LayeredSearchPlanner
import com.read4me.app.vision.OrbPageMatcher
import com.read4me.app.vision.PageTurnDetector
import com.read4me.app.vision.PhotoQualityAnalyzer
import com.read4me.app.vision.VisualFingerprint
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ln

@Composable
fun LibraryScreen(
    books: List<StoryBook>,
    trashedBooks: List<StoryRepository.TrashedBook>,
    onCreateBook: () -> Unit,
    onChildMode: () -> Unit,
    onOpenBook: (StoryBook) -> Unit,
    archiveMessage: String?,
    onImport: () -> Unit,
    onExport: (StoryBook) -> Unit,
    onRename: (StoryBook, String) -> Unit,
    onMoveToTrash: (StoryBook) -> Unit,
    onRestore: (StoryRepository.TrashedBook) -> Unit,
    onPermanentlyDelete: (StoryRepository.TrashedBook) -> Unit,
    onImportLibrary: () -> Unit,
    onExportLibrary: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Paper) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text("留声绘本", style = MaterialTheme.typography.displayLarge, color = Ink)
                Text(
                    "把第一次陪读，留在每次翻页里。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink.copy(alpha = 0.68f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
            }

            item {
                Button(
                    onClick = onCreateBook,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("＋ 录一本新书", fontWeight = FontWeight.Bold)
                }
            }

            item {
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("导入 / 恢复绘本")
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onExportLibrary, modifier = Modifier.weight(1f)) {
                        Text("导出整库")
                    }
                    OutlinedButton(onClick = onImportLibrary, modifier = Modifier.weight(1f)) {
                        Text("恢复整库")
                    }
                }
                archiveMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }

            if (books.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = onChildMode,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("打开孩子阅读模式", color = Moss, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (books.isEmpty()) {
                item { EmptyLibraryCard() }
            } else {
                item {
                    Text(
                        "我们家的故事",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(books, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        onClick = { onOpenBook(book) },
                        onExport = { onExport(book) },
                        onRename = { onRename(book, it) },
                        onMoveToTrash = { onMoveToTrash(book) },
                    )
                }
            }

            if (trashedBooks.isNotEmpty()) {
                item {
                    Text("回收站", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                    Text("保留 30 天后自动永久删除", style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = .58f))
                }
                items(trashedBooks, key = { it.book.id }) { trashed ->
                    TrashBookCard(
                        trashed = trashed,
                        onRestore = { onRestore(trashed) },
                        onPermanentlyDelete = { onPermanentlyDelete(trashed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(26.dp)) {
            Text("一本书，一段家的声音", style = MaterialTheme.typography.headlineMedium)
            Text(
                "固定好手机，把书放进画面，然后像平常一样读。翻页时，App 会替你记住每个书面。",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepPebble("1", "放好书")
                StepPebble("2", "读一遍")
                StepPebble("3", "留下来")
            }
        }
    }
}

@Composable
private fun StepPebble(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(44.dp).background(Honey, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontWeight = FontWeight.Bold, color = Ink)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BookCard(
    book: StoryBook,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onRename: (String) -> Unit,
    onMoveToTrash: () -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var title by remember(book.title) { mutableStateOf(book.title) }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryImage(
                file = book.spreads.firstOrNull()?.imageFile,
                modifier = Modifier.size(width = 104.dp, height = 78.dp).clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${book.spreads.size} 个书面 · ${formatDuration(book.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                TextButton(onClick = onExport, contentPadding = PaddingValues(0.dp)) {
                    Text("导出备份", style = MaterialTheme.typography.labelMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showRename = true }, contentPadding = PaddingValues(0.dp)) { Text("重命名") }
                    TextButton(onClick = { showDelete = true }, contentPadding = PaddingValues(0.dp)) { Text("删除", color = Coral) }
                }
            }
            Text("›", style = MaterialTheme.typography.headlineLarge, color = Coral)
        }
    }
    if (showRename) AlertDialog(
        onDismissRequest = { showRename = false },
        title = { Text("重命名绘本") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("绘本名称") })
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { showRename = false; onRename(title) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } },
    )
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("移到回收站？") },
        text = { Text("录音和照片会保留 30 天，可以从回收站恢复。") },
        confirmButton = { TextButton(onClick = { showDelete = false; onMoveToTrash() }) { Text("移到回收站", color = Coral) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun TrashBookCard(
    trashed: StoryRepository.TrashedBook,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit,
) {
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SoftWhite)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(trashed.book.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "删除于 ${SimpleDateFormat("M月d日", Locale.CHINA).format(Date(trashed.deletedAtMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = .58f),
                )
            }
            TextButton(onClick = onRestore) { Text("恢复") }
            TextButton(onClick = { confirmPermanentDelete = true }) { Text("永久删除", color = Coral) }
        }
    }
    if (confirmPermanentDelete) AlertDialog(
        onDismissRequest = { confirmPermanentDelete = false },
        title = { Text("永久删除这本绘本？") },
        text = { Text("所有录音和照片都会被删除，且无法恢复。") },
        confirmButton = {
            TextButton(onClick = { confirmPermanentDelete = false; onPermanentlyDelete() }) { Text("永久删除", color = Coral) }
        },
        dismissButton = { TextButton(onClick = { confirmPermanentDelete = false }) { Text("取消") } },
    )
}

@Composable
fun SetupScreen(
    message: String?,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
) {
    var title by remember {
        mutableStateOf("我们的故事 · ${SimpleDateFormat("M月d日", Locale.CHINA).format(Date())}")
    }
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(bottom = 18.dp),
            ) {
                Text("‹ 返回", color = Moss)
            }
            Text("准备第一次陪读", style = MaterialTheme.typography.headlineLarge)
            Text(
                "手机固定后，让后置摄像头看到完整的左右书面。录制过程中可以自然说话、停顿和翻页。",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 12.dp),
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("这次故事的名字") },
                singleLine = true,
                keyboardActions = KeyboardActions(onDone = { onStart(title) }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )

            PreparationNote("后置摄像头", "画质更清晰，设备屏幕仍朝上可见")
            PreparationNote("请勿打扰", "避免来电和通知打断珍贵的录音")
            PreparationNote("两侧光线", "减少铜版纸反光和设备阴影")

            Spacer(Modifier.weight(1f))
            if (message != null) {
                Text(message, color = Coral, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = { onStart(title) },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("打开摄像头")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("暂不录制")
            }
        }
    }
}

@Composable
private fun PreparationNote(title: String, detail: String) {
    Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).background(Moss, CircleShape))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.64f))
        }
    }
}

@Composable
private fun BookGuideFrame(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(1.32f)
            .border(
                width = 3.dp,
                color = if (active) Honey else Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
            ),
    )
}

@Composable
fun RecordingScreen(
    title: String,
    repository: StoryRepository,
    onCancel: () -> Unit,
    onFinished: (StoryBook) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }
    val detector = remember { PageTurnDetector() }
    val recorder = remember { StoryAudioRecorder(context) }
    val draft = remember { repository.createDraft(title) }
    val markers = remember { mutableStateListOf<SpreadMarker>() }
    val pendingCaptures = remember { mutableStateListOf<String>() }
    var isRecording by remember { mutableStateOf(false) }
    var initialCaptureReady by remember { mutableStateOf(false) }
    var isMoving by remember { mutableStateOf(false) }
    var motionScore by remember { mutableFloatStateOf(0f) }
    var elapsedMs by remember { mutableLongStateOf(0) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var captureMessage by remember { mutableStateOf("把完整书面放进取景框") }
    var latestFingerprint by remember { mutableStateOf<ByteArray?>(null) }

    fun captureMarker(source: MarkerSource) {
        if (!isRecording) return
        val timestamp = if (source == MarkerSource.INITIAL) 0L else recorder.elapsedMs
        if (source != MarkerSource.INITIAL && timestamp - (markers.lastOrNull()?.timestampMs ?: 0L) < 1_200L) {
            return
        }
        val spreadId = UUID.randomUUID().toString()
        val imageFile = repository.imageFile(draft, spreadId)
        markers += SpreadMarker(
            timestampMs = timestamp,
            source = source,
            references = listOf(SpreadReference(imageFile, latestFingerprint?.copyOf(), 2)),
            spreadId = spreadId,
        )
        pendingCaptures += spreadId
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(imageFile).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    pendingCaptures.remove(spreadId)
                    if (source == MarkerSource.INITIAL) initialCaptureReady = true
                    captureMessage = if (source == MarkerSource.AUTOMATIC) "已自动记下新书面" else "已记下书面 ${markers.size}"
                }

                override fun onError(exception: ImageCaptureException) {
                    pendingCaptures.remove(spreadId)
                    markers.removeAll { it.spreadId == spreadId }
                    imageFile.delete()
                    if (source == MarkerSource.INITIAL) initialCaptureReady = false
                    captureMessage = if (source == MarkerSource.INITIAL) {
                        "首个书面保存失败，请重试首张书面"
                    } else {
                        "书面照片保存失败，录音仍在继续"
                    }
                }
            },
        )
    }

    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        val analyzer = CameraFrameAnalyzer(detector) { result, luma, _ ->
            val fingerprint = VisualFingerprint.fromLuma(luma)
            mainExecutor.execute {
                latestFingerprint = fingerprint
                motionScore = result.motionScore
                isMoving = result.isMoving
                if (result.pageTurned && isRecording && initialCaptureReady) captureMarker(MarkerSource.AUTOMATIC)
            }
        }
        controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.shutdown()
            recorder.release()
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMs = recorder.elapsedMs
            amplitude = amplitudeLevel(recorder.amplitude)
            delay(160)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.32f)) {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            this.controller = controller
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                BookGuideFrame(
                    active = isMoving,
                    modifier = Modifier.align(Alignment.Center),
                )
                Surface(
                    color = Ink.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                ) {
                    Text(
                        if (isRecording) "● ${formatDuration(elapsedMs)} · ${markers.size} 个书面" else "校准画面",
                        color = if (isRecording) Color(0xFFFFB2A4) else Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }

            Surface(color = Paper, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Text(captureMessage, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (isMoving) "检测到翻页动作，等待画面稳定……" else if (isRecording) "正常讲故事；需要时可手动补一个标记。" else "确认书本完整清晰，再开始录音。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    if (isRecording) {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 16.dp).height(8.dp).clip(CircleShape).background(Color(0xFFE2D8C9)),
                        ) {
                            Box(Modifier.fillMaxWidth(amplitude.coerceIn(0.04f, 1f)).fillMaxHeight().background(Coral, CircleShape))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                enabled = pendingCaptures.isEmpty(),
                                onClick = {
                                    captureMarker(if (initialCaptureReady) MarkerSource.MANUAL else MarkerSource.INITIAL)
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                            ) { Text(if (initialCaptureReady) "标记翻页" else "重试首张书面") }
                            Button(
                                enabled = initialCaptureReady && pendingCaptures.isEmpty() &&
                                    elapsedMs > (markers.lastOrNull()?.timestampMs ?: Long.MAX_VALUE),
                                onClick = {
                                    val duration = recorder.stop()
                                    isRecording = false
                                    val book = StoryBook(
                                        id = draft.id,
                                        title = draft.title,
                                        directory = draft.directory,
                                        audioFile = draft.audioFile,
                                        durationMs = duration,
                                        markers = allocateRecordingRanges(markers, duration),
                                    )
                                    repository.save(book)
                                    onFinished(book)
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Moss),
                            ) { Text("完成陪读") }
                        }
                    } else {
                        Button(
                            onClick = {
                                detector.reset()
                                recorder.start(draft.audioFile)
                                isRecording = true
                                initialCaptureReady = false
                                captureMessage = "正在保存第一个书面"
                                captureMarker(MarkerSource.INITIAL)
                            },
                            enabled = latestFingerprint != null,
                            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(58.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("● 开始陪读") }
                        OutlinedButton(
                            onClick = {
                                repository.deleteDraft(draft)
                                onCancel()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("取消") }
                    }
                    Text(
                        "运动值 ${motionScore.toInt()} · 自动标记会在新书面稳定后发生",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.42f),
                        modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
fun ChildReadingScreen(books: List<StoryBook>, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }
    val detector = remember { PageTurnDetector() }
    val player = remember { AudioSegmentPlayer() }
    val spreadsByKey = remember(books) {
        books.flatMap { book -> book.spreads.map { spread -> "${book.id}:${spread.spreadId}" to (book to spread) } }.toMap()
    }
    val orbReferences = remember(books) {
        books.flatMap { book ->
            book.spreads.flatMap { spread ->
                spread.references.filter { it.file.exists() }.map { reference ->
                    OrbPageMatcher.Reference(book.id, spread.ordinal, spread.spreadId, reference.file, reference.referenceId)
                }
            }
        }
    }
    val orbMatcher = remember(orbReferences) { OrbPageMatcher(orbReferences) }
    val recognitionContext = remember { AtomicReference<LayeredSearchPlanner.Context?>(null) }
    var currentBook by remember { mutableStateOf<StoryBook?>(null) }
    var currentSpread by remember { mutableStateOf<StorySpread?>(null) }
    var status by remember {
        mutableStateOf(
            if (orbReferences.isEmpty()) "这些绘本没有可用的参考照片" else "把任意已录绘本放进取景框",
        )
    }
    var isPlaying by remember { mutableStateOf(false) }
    var inliers by remember { mutableStateOf(0) }
    var isMoving by remember { mutableStateOf(false) }
    var pausedForPageChange by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf("等待第一组稳定画面") }
    var choosingManualSpread by remember { mutableStateOf(false) }
    var manualCorrection by remember { mutableStateOf(false) }
    var parentMode by remember { mutableStateOf(false) }

    BackHandler {
        if (parentMode) {
            parentMode = false
            choosingManualSpread = false
        } else {
            parentMode = true
        }
    }

    fun play(book: StoryBook, spread: StorySpread, geometricInliers: Int) {
        player.stop()
        pausedForPageChange = false
        currentBook = book
        currentSpread = spread
        recognitionContext.set(LayeredSearchPlanner.Context(book.id, spread.ordinal))
        inliers = geometricInliers
        isPlaying = true
        status = "正在讲第 ${spread.ordinal} 个书面"
        player.play(spread.audioFile, spread.startMs, spread.endMs) {
            isPlaying = false
            status = "这一页讲完了，等你翻页"
        }
    }

    LaunchedEffect(isMoving) {
        if (isMoving) {
            delay(1_200L)
            if (isMoving && isPlaying && player.pause()) {
                isPlaying = false
                pausedForPageChange = true
                status = "画面持续移动，音频已暂停；放回书面后会继续"
            }
        }
    }

    DisposableEffect(controller, lifecycleOwner, orbMatcher) {
        controller.bindToLifecycle(lifecycleOwner)
        val analyzer = CameraFrameAnalyzer(detector) { result, _, grayFrame ->
            val evaluatedContext = recognitionContext.get()
            val decision = if (result.isMoving) {
                orbMatcher.requireReconfirmation()
                null
            } else {
                orbMatcher.evaluate(grayFrame, evaluatedContext)
            }
            mainExecutor.execute {
                isMoving = result.isMoving
                if (result.isMoving) {
                    if (!isPlaying) status = "画面在移动，等放稳后继续识别……"
                } else if (result.pageTurned) {
                    if (isPlaying && player.pause()) {
                        isPlaying = false
                        pausedForPageChange = true
                    }
                    status = "看到你翻页了，正在确认新书面……"
                }
                val currentDecision = decision?.takeIf { recognitionContext.get() == evaluatedContext }
                if (currentDecision != null) diagnostic = recognitionDiagnostic(currentDecision)
                if (currentDecision != null && !isPlaying && !result.pageTurned) {
                    status = when (currentDecision.state) {
                        OrbPageMatcher.State.NO_REFERENCES -> "没有可识别的参考照片"
                        OrbPageMatcher.State.TOO_FEW_FEATURES -> "画面细节太少，请把书放进白框"
                        OrbPageMatcher.State.LOW_INLIERS -> "还没对准，再放稳一点"
                        OrbPageMatcher.State.AMBIGUOUS -> "找到了相似页面，正在分辨"
                        OrbPageMatcher.State.CONFIRMING -> "正在确认书面……"
                        else -> status
                    }
                }
                currentDecision?.confirmed?.let {
                    spreadsByKey[it.reference.groupKey]?.let { (book, spread) ->
                        val isCurrentSpread = currentBook?.id == book.id && currentSpread?.spreadId == spread.spreadId
                        when {
                            isCurrentSpread && pausedForPageChange && player.resume() -> {
                                pausedForPageChange = false
                                isPlaying = true
                                inliers = it.inliers
                                status = "画面找回来了，继续讲第 ${spread.ordinal} 个书面"
                            }
                            !isCurrentSpread -> {
                                manualCorrection = false
                                play(book, spread, it.inliers)
                            }
                        }
                    }
                }
            }
        }
        controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        onDispose {
            player.stop()
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.execute { orbMatcher.close() }
            analysisExecutor.shutdown()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1.32f),
            )

            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1.32f),
            ) {
                BookGuideFrame(active = isMoving, modifier = Modifier.align(Alignment.Center))
            }

            Text(
                "家长",
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .background(Ink.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { parentMode = true })
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Surface(
                color = Paper.copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(14.dp).background(if (isPlaying) Coral else Honey, CircleShape),
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    currentBook?.let {
                        Text(
                            "《${it.title}》 · 第 ${currentSpread?.ordinal ?: "-"} 个书面",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.58f),
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    currentSpread?.let { spread ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isPlaying) {
                                        player.stop()
                                        isPlaying = false
                                        status = "暂停了，点继续从头听这一页"
                                    } else {
                                        currentBook?.let { play(it, spread, inliers) }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text(if (isPlaying) "暂停" else "从头继续") }
                            Button(
                                onClick = { currentBook?.let { play(it, spread, inliers) } },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Moss),
                            ) { Text("重新播放") }
                        }
                    }
                }
            }

            if (parentMode) {
                Surface(
                    color = Paper,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("家长诊断", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "绘本：${currentBook?.title ?: "未识别"}　书面：${currentSpread?.ordinal ?: "-"}　几何内点：$inliers",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            diagnostic,
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink.copy(alpha = 0.62f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (manualCorrection) {
                            Text("当前识别已手动纠正", color = Coral, style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { choosingManualSpread = !choosingManualSpread }) {
                            Text(if (choosingManualSpread) "收起纠正选项" else "手动纠正识别")
                        }
                        if (choosingManualSpread) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                books.forEach { choiceBook ->
                                    choiceBook.spreads.forEach { choiceSpread ->
                                        TextButton(
                                            onClick = {
                                                manualCorrection = true
                                                choosingManualSpread = false
                                                play(choiceBook, choiceSpread, 0)
                                                status = "正在讲《${choiceBook.title}》第 ${choiceSpread.ordinal} 个书面"
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) { Text("${choiceBook.title} ${choiceSpread.ordinal}", maxLines = 1) }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onExit) { Text("退出阅读模式", color = Coral) }
                            Button(
                                onClick = {
                                    parentMode = false
                                    choosingManualSpread = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Moss),
                            ) { Text("关闭家长面板") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecaptureScreen(
    book: StoryBook,
    spreadId: String,
    repository: StoryRepository,
    onCancel: () -> Unit,
    onFinished: (StoryBook, StoryBook, File) -> Unit,
) {
    val marker = book.markers.firstOrNull { it.spreadId == spreadId }
    val ordinal = book.markers.indexOfFirst { it.spreadId == spreadId } + 1
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var activeToken by remember { mutableStateOf<String?>(UUID.randomUUID().toString()) }
    var latestFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var capturedFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("把完整书面放进白框后拍照") }
    BackHandler(enabled = !isCapturing, onBack = onCancel)
    val fingerprintAnalyzer = remember {
        CameraFrameAnalyzer(PageTurnDetector()) { _, luma, _ ->
            val fingerprint = VisualFingerprint.fromLuma(luma)
            mainExecutor.execute { if (activeToken != null) latestFingerprint = fingerprint }
        }
    }

    DisposableEffect(controller, lifecycleOwner, fingerprintAnalyzer) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.setImageAnalysisAnalyzer(analysisExecutor, fingerprintAnalyzer)
        onDispose {
            activeToken = null
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.shutdown()
            pendingFile?.delete()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1.32f),
            )
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1.32f)) {
                BookGuideFrame(active = isCapturing, modifier = Modifier.align(Alignment.Center))
            }
            Surface(
                color = Paper.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("添加书面 $ordinal 的参考照片", style = MaterialTheme.typography.headlineLarge)
                    Text(message, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                    Button(
                        enabled = !isCapturing,
                        onClick = {
                            if (marker == null) return@Button
                            isCapturing = true
                            message = "正在保存新照片……"
                            val token = UUID.randomUUID().toString()
                            activeToken = token
                            capturedFingerprint = latestFingerprint?.copyOf()
                            val captureFile = File(File(book.directory, "spreads"), ".$spreadId-$token.pending.jpg")
                            pendingFile?.delete()
                            pendingFile = captureFile
                            controller.takePicture(
                                ImageCapture.OutputFileOptions.Builder(captureFile).build(),
                                mainExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        if (activeToken != token) { captureFile.delete(); return }
                                        message = "正在检查照片质量……"
                                        analysisExecutor.execute {
                                            val quality = PhotoQualityAnalyzer.analyze(captureFile)
                                            mainExecutor.execute {
                                                if (activeToken != token) {
                                                    captureFile.delete()
                                                    return@execute
                                                }
                                                var installed: File? = null
                                                runCatching {
                                                    val target = repository.replaceReferenceImage(book, spreadId, captureFile)
                                                    installed = target
                                                    val updated = StoryBookEditor.addReference(book, spreadId,
                                                        SpreadReference(target, capturedFingerprint, 2, quality))
                                                    repository.save(updated)
                                                    updated
                                                }.onSuccess { updated -> onFinished(updated, book, requireNotNull(installed)) }.onFailure {
                                                    installed?.delete()
                                                    isCapturing = false
                                                    message = "添加失败，原照片已保留，请重试"
                                                }
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        captureFile.delete()
                                        if (activeToken != token) return
                                        isCapturing = false
                                        message = "拍照失败，原照片已保留，请重试"
                                    }
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(if (isCapturing) "分析并保存中" else "拍下并添加") }
                    TextButton(enabled = !isCapturing, onClick = onCancel) { Text("取消") }
                }
            }
        }
    }
}

@Composable
fun InsertSpreadScreen(
    book: StoryBook,
    anchorSpreadId: String,
    repository: StoryRepository,
    onCancel: () -> Unit,
    onFinished: (StoryBook, StoryBook, File) -> Unit,
) {
    val anchor = book.markers.firstOrNull { it.spreadId == anchorSpreadId }
    if (anchor == null || anchor.recordingEndMs - anchor.recordingStartMs < 1_000L) {
        LaunchedEffect(Unit) { onCancel() }
        return
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var activeToken by remember { mutableStateOf<String?>(UUID.randomUUID().toString()) }
    val player = remember { AudioSegmentPlayer() }
    var latestFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var capturedFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var photoReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("把完整书面放进白框后拍照") }
    var split by remember { mutableFloatStateOf(((anchor.recordingStartMs + anchor.recordingEndMs) / 2L).toFloat()) }
    var waveform by remember { mutableStateOf<FloatArray?>(null) }
    val analyzer = remember {
        CameraFrameAnalyzer(PageTurnDetector()) { _, luma, _ ->
            val value = VisualFingerprint.fromLuma(luma)
            mainExecutor.execute { if (activeToken != null) latestFingerprint = value }
        }
    }
    fun cancel() {
        player.stop()
        pendingFile?.delete()
        onCancel()
    }
    BackHandler(enabled = !busy, onBack = ::cancel)
    DisposableEffect(controller, lifecycleOwner, analyzer) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        onDispose {
            activeToken = null
            player.stop()
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.shutdown()
            pendingFile?.delete()
        }
    }
    LaunchedEffect(photoReady) {
        if (photoReady && waveform == null) waveform = withContext(Dispatchers.IO) {
            runCatching { AudioWaveformExtractor().extract(book.audioFile, anchor.recordingStartMs, anchor.recordingEndMs) }.getOrNull()
        }
    }
    Surface(Modifier.fillMaxSize(), color = if (photoReady) Paper else Ink) {
        if (!photoReady) Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } },
                modifier = Modifier.fillMaxSize(),
            )
            BookGuideFrame(active = latestFingerprint != null, modifier = Modifier.align(Alignment.Center))
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Paper, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("插入下一书面", style = MaterialTheme.typography.headlineLarge)
                    Text(message, modifier = Modifier.padding(vertical = 10.dp))
                    Button(enabled = !busy && latestFingerprint != null, onClick = {
                        busy = true; pendingFile?.delete(); capturedFingerprint = latestFingerprint?.copyOf()
                        val token = UUID.randomUUID().toString()
                        activeToken = token
                        val captureFile = File(File(book.directory, "spreads"), ".insert-$anchorSpreadId-$token.pending.jpg")
                        pendingFile = captureFile
                        controller.takePicture(ImageCapture.OutputFileOptions.Builder(captureFile).build(), mainExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { if (activeToken == token) { busy = false; photoReady = true } else captureFile.delete() }
                                override fun onError(exception: ImageCaptureException) { captureFile.delete(); if (activeToken == token) { busy = false; message = "拍照失败，请重试" } }
                            })
                    }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(if (busy) "拍摄中" else "拍下书面") }
                    TextButton(enabled = !busy, onClick = ::cancel) { Text("取消") }
                }
            }
        } else Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("分配原录音", style = MaterialTheme.typography.headlineLarge)
            Text("选择新书面开始的位置：${formatBoundary(split.toLong())}", modifier = Modifier.padding(top = 8.dp))
            Canvas(Modifier.fillMaxWidth().padding(top = 18.dp).height(84.dp).background(Color.White, RoundedCornerShape(12.dp))) {
                val samples = waveform ?: return@Canvas
                val step = size.width / samples.size.coerceAtLeast(1)
                samples.forEachIndexed { index, value ->
                    val half = value * size.height * .45f
                    drawLine(Moss, Offset(index * step, size.height / 2 - half), Offset(index * step, size.height / 2 + half), 2f)
                }
            }
            Slider(value = split, onValueChange = { split = it }, valueRange =
                (anchor.recordingStartMs + 500L).toFloat()..(anchor.recordingEndMs - 500L).toFloat())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { player.play(book.audioFile, (split.toLong() - 2_000L).coerceAtLeast(anchor.recordingStartMs), split.toLong()) }, modifier = Modifier.weight(1f)) { Text("试听左侧结尾") }
                OutlinedButton(onClick = { player.play(book.audioFile, split.toLong(), (split.toLong() + 2_000L).coerceAtMost(anchor.recordingEndMs)) }, modifier = Modifier.weight(1f)) { Text("试听右侧开头") }
            }
            Spacer(Modifier.weight(1f))
            Button(enabled = !busy, onClick = {
                busy = true
                var installed: File? = null
                runCatching {
                    val newId = UUID.randomUUID().toString()
                    installed = repository.installInsertImage(book, newId, requireNotNull(pendingFile))
                    val updated = StoryBookEditor.insertAfter(book, anchorSpreadId, split.toLong(), SpreadMarker(
                        timestampMs = split.toLong(), source = MarkerSource.MANUAL,
                        references = listOf(SpreadReference(requireNotNull(installed), capturedFingerprint?.copyOf(), 2)),
                        spreadId = newId,
                    ))
                    check(updated !== book)
                    repository.save(updated)
                    updated
                }.onSuccess { onFinished(it, book, installed!!) }.onFailure {
                    installed?.delete(); busy = false; message = "插入失败，绘本没有改变"
                }
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(if (busy) "保存中" else "确认插入") }
            TextButton(enabled = !busy, onClick = ::cancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            if (message.startsWith("插入失败")) Text(message, color = Coral)
        }
    }
}

@Composable
fun RerecordScreen(
    book: StoryBook,
    spreadId: String,
    repository: StoryRepository,
    onCancel: () -> Unit,
    onFinished: (StoryBook) -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { StoryAudioRecorder(context) }
    val spread = book.spreads.firstOrNull { it.spreadId == spreadId }
    val ordinal = spread?.ordinal ?: 0
    val targetFile = remember(book.id, spreadId) { repository.overrideAudioFile(book, spreadId) }
    val pendingFile = remember(targetFile) { File(targetFile.parentFile, "${targetFile.name}.${UUID.randomUUID()}.pending") }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var amplitude by remember { mutableFloatStateOf(0f) }

    fun cancel() {
        recorder.release()
        pendingFile.delete()
        onCancel()
    }

    BackHandler(onBack = ::cancel)
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            pendingFile.delete()
        }
    }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMs = recorder.elapsedMs
            amplitude = amplitudeLevel(recorder.amplitude)
            delay(120)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("单独重录", style = MaterialTheme.typography.headlineLarge)
            Text(
                "${book.title} · 书面 $ordinal",
                style = MaterialTheme.typography.titleLarge,
                color = Moss,
                modifier = Modifier.padding(top = 8.dp),
            )
            StoryImage(
                file = spread?.imageFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .aspectRatio(1.32f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Text(
                if (isRecording) "● ${formatDuration(elapsedMs)}" else "只重录这一书面，不会改变其他录音",
                style = MaterialTheme.typography.titleLarge,
                color = if (isRecording) Coral else Ink,
                modifier = Modifier.padding(top = 24.dp),
            )
            if (isRecording) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 18.dp).height(10.dp).clip(CircleShape).background(Color(0xFFE2D8C9)),
                ) {
                    Box(Modifier.fillMaxWidth(amplitude.coerceIn(0.04f, 1f)).fillMaxHeight().background(Coral, CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (!isRecording) {
                        pendingFile.delete()
                        recorder.start(pendingFile)
                        isRecording = true
                    } else {
                        val duration = recorder.stop()
                        isRecording = false
                        targetFile.parentFile?.mkdirs()
                        pendingFile.copyTo(targetFile, overwrite = true)
                        pendingFile.delete()
                        val updated = StoryBookEditor.replaceNarration(book, spreadId, targetFile, duration)
                        repository.save(updated)
                        onFinished(updated)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Moss else Coral),
            ) { Text(if (isRecording) "完成并使用这段录音" else "● 开始重录") }
            if (!isRecording) {
                OutlinedButton(
                    onClick = ::cancel,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("取消") }
            }
        }
    }
}

@Composable
fun ReviewScreen(
    book: StoryBook,
    repository: StoryRepository,
    onRerecord: (StoryBook, String) -> Unit,
    onRecapture: (StoryBook, String, StoryBook?, File?) -> Unit,
    onInsert: (StoryBook, String, StoryBook?, File?) -> Unit,
    initialUndo: StoryBook? = null,
    initialUndoImage: File? = null,
    onBack: () -> Unit,
) {
    val player = remember { AudioSegmentPlayer() }
    val editSession = remember(book.id, book.markers.map { it.spreadId }) { StoryEditSession(book, initialUndo) }
    var editableBook by remember(book.id) { mutableStateOf(book) }
    var undoImage by remember(book.id) { mutableStateOf(initialUndoImage) }
    var playingSpreadId by remember { mutableStateOf<String?>(null) }
    var previewPositionMs by remember { mutableStateOf<Long?>(null) }
    val waveformCache = remember(book.id) { mutableStateMapOf<WaveformCacheKey, FloatArray>() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    LaunchedEffect(editableBook) {
        editableBook.spreads.forEach { spread ->
            val key = waveformCacheKey(editableBook, spread)
            if (waveformCache[key] == null) {
                waveformCache[key] = withContext(Dispatchers.IO) {
                    runCatching {
                        AudioWaveformExtractor().extract(spread.audioFile, key.startMs, key.endMs)
                    }.getOrElse { FloatArray(0) }
                }
            }
        }
    }

    fun setBoundary(leftId: String, rightId: String, timestampMs: Long) {
        player.stop()
        playingSpreadId = null
        previewPositionMs = null
        editableBook = editSession.apply(StoryBookEditor.moveBoundary(editableBook, leftId, rightId, timestampMs), repository::save)
    }

    fun trimNarration(spreadId: String, startMs: Long, endMs: Long) {
        player.stop()
        playingSpreadId = null
        previewPositionMs = null
        editableBook = editSession.apply(StoryBookEditor.trimNarration(editableBook, spreadId, startMs, endMs), repository::save)
    }

    fun mergeWithNext(spreadId: String) {
        player.stop()
        playingSpreadId = null
        previewPositionMs = null
        editableBook = editSession.apply(StoryBookEditor.mergeWithNext(editableBook, spreadId), repository::save)
    }

    Surface(Modifier.fillMaxSize(), color = Paper) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("陪读已经留下来了", style = MaterialTheme.typography.headlineLarge)
                Text(
                    editableBook.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Moss,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "${editableBook.spreads.size} 个书面 · ${formatDuration(editableBook.durationMs)}。逐个试听，拖动两个手柄修剪每页首尾。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink.copy(alpha = 0.68f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                Button(enabled = editSession.canUndo, onClick = {
                    player.stop()
                    playingSpreadId = null
                    editableBook = editSession.undo(repository::save)
                    undoImage?.takeIf { image ->
                        editableBook.markers.none { marker -> marker.references.any { it.file == image } }
                    }?.delete()
                    undoImage = null
                }) { Text("撤销上一步") }
            }
            items(editableBook.spreads, key = { it.spreadId }) { spread ->
                val markerIndex = editableBook.markers.indexOfFirst { it.spreadId == spread.spreadId }
                val marker = editableBook.markers[markerIndex]
                val sourceStartMs = if (marker.overrideAudioFile != null) 0L else marker.recordingStartMs
                val sourceEndMs = marker.overrideDurationMs
                    ?.takeIf { marker.overrideAudioFile != null }
                    ?: marker.recordingEndMs
                val waveformKey = waveformCacheKey(editableBook, spread)
                val nextMarker = editableBook.markers.getOrNull(markerIndex + 1)
                val hasSharedBoundary = nextMarker != null && StoryBookEditor.shareBoundary(marker, nextMarker)
                SpreadReviewCard(
                    spread = spread,
                    playing = playingSpreadId == spread.spreadId,
                    previewPositionMs = previewPositionMs?.takeIf { playingSpreadId == spread.spreadId },
                    waveform = waveformCache[waveformKey],
                    trimRange = sourceStartMs.toFloat()..sourceEndMs.toFloat(),
                    boundaryValueMs = nextMarker
                        ?.recordingStartMs
                        ?.takeIf { hasSharedBoundary },
                    boundaryRange = if (hasSharedBoundary) {
                        val minimum = marker.recordingStartMs + 500L
                        val maximum = requireNotNull(nextMarker).recordingEndMs - 500L
                        minimum.toFloat()..maximum.toFloat()
                    } else {
                        null
                    },
                    onTrimChanged = { startMs, endMs ->
                        trimNarration(spread.spreadId, startMs, endMs)
                    },
                    onBoundaryChanged = { nextMarker?.let { next -> setBoundary(spread.spreadId, next.spreadId, it) } },
                    onMergeNext = if (hasSharedBoundary) {
                        { mergeWithNext(spread.spreadId) }
                    } else null,
                    onRerecord = { onRerecord(editableBook, spread.spreadId) },
                    onRecapture = {
                        onRecapture(editableBook, spread.spreadId, editSession.undoSnapshot, undoImage)
                    },
                    onDeleteReference = { referenceId -> editableBook = editSession.apply(StoryBookEditor.deleteReference(editableBook, spread.spreadId, referenceId), repository::save) },
                    onSetPrimary = { referenceId -> editableBook = editSession.apply(StoryBookEditor.setPrimaryReference(editableBook, spread.spreadId, referenceId), repository::save) },
                    onMoveUp = if (spread.ordinal > 1) {{ editableBook = editSession.apply(StoryBookEditor.reorder(editableBook, spread.spreadId, spread.ordinal - 2), repository::save) }} else null,
                    onMoveDown = if (spread.ordinal < editableBook.spreads.size) {{ editableBook = editSession.apply(StoryBookEditor.reorder(editableBook, spread.spreadId, spread.ordinal), repository::save) }} else null,
                    onDelete = if (editableBook.markers.size > 1) {{ editableBook = editSession.apply(StoryBookEditor.delete(editableBook, spread.spreadId), repository::save) }} else null,
                    onInsert = if (marker.recordingEndMs - marker.recordingStartMs >= 1_000L) {
                        { onInsert(editableBook, spread.spreadId, editSession.undoSnapshot, undoImage) }
                    } else null,
                    onPlay = {
                        if (playingSpreadId == spread.spreadId) {
                            player.stop()
                            playingSpreadId = null
                            previewPositionMs = null
                        } else {
                            playingSpreadId = spread.spreadId
                            previewPositionMs = null
                            player.play(spread.audioFile, spread.startMs, spread.endMs) {
                                playingSpreadId = null
                                previewPositionMs = null
                            }
                        }
                    },
                    onPreview = { startMs, endMs ->
                        player.stop()
                        playingSpreadId = spread.spreadId
                        previewPositionMs = startMs
                        player.play(spread.audioFile, startMs, endMs) {
                            playingSpreadId = null
                            previewPositionMs = null
                        }
                    },
                )
            }
            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Moss),
                ) { Text("回到家庭书架") }
                Text(
                    "修剪只改变播放范围，不会删除原始录音；随时可以重新调整。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 12.dp, bottom = 30.dp),
                )
            }
        }
    }
}

@Composable
private fun SpreadReviewCard(
    spread: StorySpread,
    playing: Boolean,
    previewPositionMs: Long?,
    waveform: FloatArray?,
    trimRange: ClosedFloatingPointRange<Float>,
    boundaryValueMs: Long?,
    boundaryRange: ClosedFloatingPointRange<Float>?,
    onTrimChanged: (Long, Long) -> Unit,
    onBoundaryChanged: (Long) -> Unit,
    onMergeNext: (() -> Unit)?,
    onRerecord: () -> Unit,
    onRecapture: () -> Unit,
    onDeleteReference: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onInsert: (() -> Unit)?,
    onPlay: () -> Unit,
    onPreview: (Long, Long) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var trimValue by remember(spread.startMs, spread.endMs) {
        mutableStateOf(spread.startMs.toFloat()..spread.endMs.toFloat())
    }
    var boundaryValue by remember(boundaryValueMs) {
        mutableFloatStateOf((boundaryValueMs ?: spread.endMs).toFloat())
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryImage(
                file = spread.imageFile,
                modifier = Modifier.size(width = 118.dp, height = 88.dp).clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("书面 ${spread.ordinal}", style = MaterialTheme.typography.titleLarge)
                    if (spread.source == MarkerSource.AUTOMATIC) {
                        Text(
                            " 自动",
                            color = Moss,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
                Text(
                    "${formatDuration(spread.startMs)} — ${formatDuration(spread.endMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.58f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text("${spread.references.size} 张参考照片 · ${qualityLabel(spread.references.firstOrNull()?.quality)}",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = .68f))
                OutlinedButton(
                    onClick = onPlay,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.padding(top = 7.dp),
                ) { Text(if (playing) "停止" else "▶ 试听") }
                TextButton(
                    onClick = onRecapture,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) { Text("添加参考照片") }
            }
        }
        if (spread.references.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                spread.references.forEachIndexed { index, reference ->
                    Column(Modifier.padding(end = 8.dp)) {
                        StoryImage(
                            file = reference.file,
                            modifier = Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Text("照片 ${index + 1}${if (index == 0) "（主图）" else ""}")
                        Text(qualityLabel(reference.quality), style = MaterialTheme.typography.bodyMedium)
                        if (index > 0) TextButton(onClick = { onSetPrimary(reference.referenceId) }) { Text("设为主图") }
                        if (spread.references.size > 1) {
                            TextButton(onClick = { onDeleteReference(reference.referenceId) }) { Text("删除") }
                        }
                    }
                }
            }
        }
        if (trimRange.endInclusive - trimRange.start >= 500f) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                Text(
                    "修剪本书面 · ${formatBoundary(trimValue.start.toLong())} — ${formatBoundary(trimValue.endInclusive.toLong())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.62f),
                )
                Text(
                    "拖动左右手柄；手柄外的空白不会播放，也不会归到相邻书面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.48f),
                )
                RangeSlider(
                    value = trimValue,
                    onValueChange = {
                        if (it.endInclusive - it.start >= 500f) trimValue = it
                    },
                    onValueChangeFinished = {
                        onTrimChanged(trimValue.start.toLong(), trimValue.endInclusive.toLong())
                    },
                    valueRange = trimRange,
                    modifier = Modifier.fillMaxWidth(),
                )
                val peaks = waveform
                if (peaks == null) {
                    Text("正在读取波形…", style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = .45f))
                } else if (peaks.isNotEmpty()) {
                    val waveformColor = Moss
                    val playheadColor = Color(0xFFC85A3C)
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Paper)
                            .pointerInput(peaks, trimRange) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val position = (trimRange.start + fraction *
                                        (trimRange.endInclusive - trimRange.start)).toLong()
                                        .coerceAtMost(trimRange.endInclusive.toLong() - 1L)
                                    onPreview(position, minOf(position + 2_000L, trimRange.endInclusive.toLong()))
                                }
                            },
                    ) {
                        val center = size.height / 2f
                        peaks.forEachIndexed { index, peak ->
                            val x = (index + .5f) * size.width / peaks.size
                            val half = maxOf(1f, peak * center * .88f)
                            drawLine(waveformColor, Offset(x, center - half), Offset(x, center + half), 1.5f)
                        }
                        previewPositionMs?.let {
                            val fraction = ((it - trimRange.start) /
                                (trimRange.endInclusive - trimRange.start)).coerceIn(0f, 1f)
                            val x = fraction * size.width
                            drawLine(playheadColor, Offset(x, 0f), Offset(x, size.height), 3f)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                val start = trimValue.start.toLong()
                                onPreview(start, minOf(start + 2_000L, trimValue.endInclusive.toLong()))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text("试听开头") }
                        TextButton(
                            onClick = {
                                val end = trimValue.endInclusive.toLong()
                                onPreview(maxOf(trimValue.start.toLong(), end - 2_000L), end)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text("试听结尾") }
                    }
                }
                if (trimValue.start > trimRange.start || trimValue.endInclusive < trimRange.endInclusive) {
                    TextButton(
                        onClick = {
                            trimValue = trimRange
                            onTrimChanged(trimRange.start.toLong(), trimRange.endInclusive.toLong())
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("恢复完整范围") }
                }
            }
        }
        if (boundaryRange != null && boundaryRange.endInclusive > boundaryRange.start) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                Text(
                    "调整与下一书面的分界 · ${formatBoundary(boundaryValue.toLong())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.62f),
                )
                Text(
                    "左边属于书面 ${spread.ordinal}，右边属于书面 ${spread.ordinal + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.48f),
                )
                Slider(
                    value = boundaryValue.coerceIn(boundaryRange.start, boundaryRange.endInclusive),
                    onValueChange = { boundaryValue = it },
                    onValueChangeFinished = { onBoundaryChanged(boundaryValue.toLong()) },
                    valueRange = boundaryRange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            onMoveUp?.let { TextButton(onClick = it) { Text("上移") } }
            onMoveDown?.let { TextButton(onClick = it) { Text("下移") } }
            onDelete?.let { TextButton(onClick = { confirmDelete = true }) { Text("删除") } }
            onInsert?.let { TextButton(onClick = it) { Text("插入下一书面", maxLines = 1) } }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRerecord,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("单独重录", maxLines = 1) }
            onMergeNext?.let {
                OutlinedButton(
                    onClick = it,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("合并下一书面", maxLines = 1) }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除这个书面？") },
        text = { Text("删除后需使用“撤销上一步”才能恢复。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

private data class WaveformCacheKey(
    val path: String,
    val modifiedMs: Long,
    val sizeBytes: Long,
    val startMs: Long,
    val endMs: Long,
)

private fun waveformCacheKey(book: StoryBook, spread: StorySpread): WaveformCacheKey {
    val marker = book.markers.first { it.spreadId == spread.spreadId }
    val sourceStartMs = if (marker.overrideAudioFile != null) 0L else marker.recordingStartMs
    val sourceEndMs = marker.overrideDurationMs
        ?.takeIf { marker.overrideAudioFile != null }
        ?: marker.recordingEndMs
    return WaveformCacheKey(
        path = spread.audioFile.absolutePath,
        modifiedMs = spread.audioFile.lastModified(),
        sizeBytes = spread.audioFile.length(),
        startMs = sourceStartMs,
        endMs = sourceEndMs,
    )
}

private fun allocateRecordingRanges(markers: List<SpreadMarker>, durationMs: Long): List<SpreadMarker> {
    require(markers.isNotEmpty() && durationMs > markers.last().timestampMs)
    return markers.mapIndexed { index, marker ->
        val end = markers.getOrNull(index + 1)?.timestampMs ?: durationMs
        require(end > marker.timestampMs)
        marker.copy(recordingStartMs = marker.timestampMs, recordingEndMs = end)
    }
}

@Composable
private fun StoryImage(file: File?, modifier: Modifier = Modifier) {
    val imageKey = file?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    var bitmap by remember(imageKey) {
        mutableStateOf(file?.let(StoryImageLoader::cached))
    }
    LaunchedEffect(imageKey) {
        if (bitmap == null && file != null) {
            bitmap = withContext(Dispatchers.IO) { StoryImageLoader.load(file) }
        }
    }
    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = "绘本书面",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Color(0xFFE4D8C8)), contentAlignment = Alignment.Center) {
            Text("书", style = MaterialTheme.typography.headlineLarge, color = Moss)
        }
    }
}

private fun amplitudeLevel(value: Int): Float {
    if (value <= 0) return 0f
    return (ln(value.toFloat()) / ln(32767f)).coerceIn(0f, 1f)
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatBoundary(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0)
    val totalSeconds = safe / 1000
    val millis = safe % 1000
    return "%02d:%02d.%03d".format(totalSeconds / 60, totalSeconds % 60, millis)
}

private fun recognitionDiagnostic(decision: OrbPageMatcher.Decision): String {
    val best = decision.best
    val second = decision.second
    val bestText = best?.let { "最佳 ${it.reference.spreadOrdinal}：${it.inliers} 内点" }
        ?: "没有候选"
    val secondText = second?.let { "，次佳 ${it.reference.spreadOrdinal}：${it.inliers} 内点" }.orEmpty()
    val state = when (decision.state) {
        OrbPageMatcher.State.LOW_INLIERS -> "几何匹配不足"
        OrbPageMatcher.State.AMBIGUOUS -> "候选太接近"
        OrbPageMatcher.State.CONFIRMING -> "确认 ${decision.confirmationCount}/${decision.confirmationsRequired}"
        OrbPageMatcher.State.CONFIRMED, OrbPageMatcher.State.STABLE -> "已确认"
        OrbPageMatcher.State.NO_REFERENCES -> "没有参考照片"
        OrbPageMatcher.State.TOO_FEW_FEATURES -> "画面特征太少"
    }
    val path = when (decision.searchPath) {
        OrbPageMatcher.SearchPath.NONE -> "未搜索"
        OrbPageMatcher.SearchPath.ADJACENT -> "邻页快查"
        OrbPageMatcher.SearchPath.LIBRARY -> "全库查找"
    }
    val search = "索引 ${decision.indexedReferences} · 几何 ${decision.geometricallyVerified} · $path"
    return "$bestText$secondText · $state · $search"
}

private fun qualityLabel(quality: PhotoQuality?): String = when (quality?.status) {
    null -> "尚未检查"
    PhotoQuality.Status.GOOD -> "质量良好"
    PhotoQuality.Status.UNAVAILABLE -> "无法检查"
    PhotoQuality.Status.ISSUES -> "建议重拍：" + quality.issues.joinToString("、") {
        when (it) {
            PhotoQuality.Issue.BLURRY -> "模糊"
            PhotoQuality.Issue.TOO_FEW_DETAILS -> "细节过少"
            PhotoQuality.Issue.TOO_DARK -> "过暗"
            PhotoQuality.Issue.OVEREXPOSED -> "过曝"
        }
    }
}
