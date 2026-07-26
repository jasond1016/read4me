package com.read4me.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.read4me.app.audio.StoryAudioRecorder
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StoryBookEditor
import com.read4me.app.model.StorySpread
import com.read4me.app.vision.CameraFrameAnalyzer
import com.read4me.app.vision.OrbPageMatcher
import com.read4me.app.vision.PageTurnDetector
import com.read4me.app.vision.VisualFingerprint
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ln

@Composable
fun LibraryScreen(
    books: List<StoryBook>,
    onCreateBook: () -> Unit,
    onChildMode: () -> Unit,
    onOpenBook: (StoryBook) -> Unit,
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
                    BookCard(book = book, onClick = { onOpenBook(book) })
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
private fun BookCard(book: StoryBook, onClick: () -> Unit) {
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
            }
            Text("›", style = MaterialTheme.typography.headlineLarge, color = Coral)
        }
    }
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
    var isRecording by remember { mutableStateOf(false) }
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
        val imageFile = repository.imageFile(draft, markers.size + 1)
        markers += SpreadMarker(
            timestampMs = timestamp,
            imageFile = imageFile,
            source = source,
            fingerprint = latestFingerprint?.copyOf(),
            fingerprintVersion = 2,
        )
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(imageFile).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    captureMessage = if (source == MarkerSource.AUTOMATIC) "已自动记下新书面" else "已记下书面 ${markers.size}"
                }

                override fun onError(exception: ImageCaptureException) {
                    captureMessage = "书面照片保存失败，录音仍在继续"
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
                if (result.pageTurned && isRecording) captureMarker(MarkerSource.AUTOMATIC)
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
                                onClick = { captureMarker(MarkerSource.MANUAL) },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                            ) { Text("标记翻页") }
                            Button(
                                onClick = {
                                    val duration = recorder.stop()
                                    isRecording = false
                                    val book = StoryBook(
                                        id = draft.id,
                                        title = draft.title,
                                        directory = draft.directory,
                                        audioFile = draft.audioFile,
                                        durationMs = duration,
                                        markers = markers.toList(),
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
        books.flatMap { book -> book.spreads.map { spread -> "${book.id}:${spread.ordinal}" to (book to spread) } }.toMap()
    }
    val orbReferences = remember(books) {
        books.flatMap { book ->
            book.spreads.mapNotNull { spread ->
                spread.imageFile?.takeIf(File::exists)?.let {
                    OrbPageMatcher.Reference(book.id, spread.ordinal, it)
                }
            }
        }
    }
    val orbMatcher = remember(orbReferences) { OrbPageMatcher(orbReferences) }
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
            val decision = if (result.isMoving) {
                orbMatcher.requireReconfirmation()
                null
            } else {
                orbMatcher.evaluate(grayFrame)
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
                if (decision != null) diagnostic = recognitionDiagnostic(decision)
                if (decision != null && !isPlaying && !result.pageTurned) {
                    status = when (decision.state) {
                        OrbPageMatcher.State.NO_REFERENCES -> "没有可识别的参考照片"
                        OrbPageMatcher.State.TOO_FEW_FEATURES -> "画面细节太少，请把书放进白框"
                        OrbPageMatcher.State.LOW_INLIERS -> "还没对准，再放稳一点"
                        OrbPageMatcher.State.AMBIGUOUS -> "找到了相似页面，正在分辨"
                        OrbPageMatcher.State.CONFIRMING -> "正在确认书面……"
                        else -> status
                    }
                }
                decision?.confirmed?.let {
                    spreadsByKey[it.reference.key]?.let { (book, spread) ->
                        val isCurrentSpread = currentBook?.id == book.id && currentSpread?.ordinal == spread.ordinal
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
    ordinal: Int,
    repository: StoryRepository,
    onCancel: () -> Unit,
    onFinished: (StoryBook) -> Unit,
) {
    BackHandler(onBack = onCancel)
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
    val pendingFile = remember(book.id, ordinal) {
        File(File(book.directory, "spreads"), "%03d.pending.jpg".format(ordinal))
    }
    var latestFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("把完整书面放进白框后拍照") }
    val fingerprintAnalyzer = remember {
        CameraFrameAnalyzer(PageTurnDetector()) { _, luma, _ ->
            val fingerprint = VisualFingerprint.fromLuma(luma)
            mainExecutor.execute { latestFingerprint = fingerprint }
        }
    }

    DisposableEffect(controller, lifecycleOwner, fingerprintAnalyzer) {
        pendingFile.delete()
        controller.bindToLifecycle(lifecycleOwner)
        controller.setImageAnalysisAnalyzer(analysisExecutor, fingerprintAnalyzer)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.shutdown()
            pendingFile.delete()
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
                    Text("重拍书面 $ordinal", style = MaterialTheme.typography.headlineLarge)
                    Text(message, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                    Button(
                        enabled = !isCapturing,
                        onClick = {
                            isCapturing = true
                            message = "正在保存新照片……"
                            pendingFile.delete()
                            controller.takePicture(
                                ImageCapture.OutputFileOptions.Builder(pendingFile).build(),
                                mainExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        runCatching {
                                            val target = repository.replaceReferenceImage(book, ordinal, pendingFile)
                                            val updated = StoryBookEditor.replaceReference(
                                                book,
                                                ordinal,
                                                target,
                                                latestFingerprint,
                                            )
                                            repository.save(updated)
                                            updated
                                        }.onSuccess(onFinished).onFailure {
                                            isCapturing = false
                                            message = "替换失败，原照片已保留，请重试"
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        pendingFile.delete()
                                        isCapturing = false
                                        message = "拍照失败，原照片已保留，请重试"
                                    }
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(if (isCapturing) "保存中" else "拍下并替换") }
                    TextButton(enabled = !isCapturing, onClick = onCancel) { Text("取消") }
                }
            }
        }
    }
}

@Composable
fun RerecordScreen(
    book: StoryBook,
    ordinal: Int,
    repository: StoryRepository,
    onCancel: () -> Unit,
    onFinished: (StoryBook) -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { StoryAudioRecorder(context) }
    val spread = book.spreads.getOrNull(ordinal - 1)
    val targetFile = remember(book.id, ordinal) { repository.overrideAudioFile(book, ordinal) }
    val pendingFile = remember(targetFile) { File(targetFile.parentFile, "${targetFile.name}.pending") }
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
                        val updated = StoryBookEditor.replaceNarration(book, ordinal, targetFile, duration)
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
    onRerecord: (StoryBook, Int) -> Unit,
    onRecapture: (StoryBook, Int) -> Unit,
    onBack: () -> Unit,
) {
    val player = remember { AudioSegmentPlayer() }
    var editableBook by remember(book.id) { mutableStateOf(book) }
    var playingOrdinal by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    fun setBoundary(markerIndex: Int, timestampMs: Long) {
        player.stop()
        playingOrdinal = null
        editableBook = StoryBookEditor.moveBoundary(editableBook, markerIndex, timestampMs)
        repository.save(editableBook)
    }

    fun trimNarration(ordinal: Int, startMs: Long, endMs: Long) {
        player.stop()
        playingOrdinal = null
        editableBook = StoryBookEditor.trimNarration(editableBook, ordinal, startMs, endMs)
        repository.save(editableBook)
    }

    fun mergeWithNext(ordinal: Int) {
        player.stop()
        playingOrdinal = null
        editableBook = StoryBookEditor.mergeWithNext(editableBook, ordinal)
        repository.save(editableBook)
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
            }
            items(editableBook.spreads, key = { it.ordinal }) { spread ->
                val marker = editableBook.markers[spread.ordinal - 1]
                val sourceStartMs = if (marker.overrideAudioFile != null) 0L else marker.timestampMs
                val sourceEndMs = marker.overrideDurationMs
                    ?.takeIf { marker.overrideAudioFile != null }
                    ?: (editableBook.markers.getOrNull(spread.ordinal)?.timestampMs ?: editableBook.durationMs)
                val hasSharedBoundary = spread.ordinal < editableBook.spreads.size &&
                    marker.overrideAudioFile == null &&
                    editableBook.markers[spread.ordinal].overrideAudioFile == null
                SpreadReviewCard(
                    spread = spread,
                    playing = playingOrdinal == spread.ordinal,
                    trimRange = sourceStartMs.toFloat()..sourceEndMs.toFloat(),
                    boundaryValueMs = editableBook.markers.getOrNull(spread.ordinal)
                        ?.timestampMs
                        ?.takeIf { hasSharedBoundary },
                    boundaryRange = if (hasSharedBoundary) {
                        val minimum = editableBook.markers[spread.ordinal - 1].timestampMs + 500L
                        val maximum = editableBook.markers
                            .getOrNull(spread.ordinal + 1)
                            ?.timestampMs
                            ?.minus(500L)
                            ?: (editableBook.durationMs - 500L)
                        minimum.toFloat()..maximum.toFloat()
                    } else {
                        null
                    },
                    onTrimChanged = { startMs, endMs ->
                        trimNarration(spread.ordinal, startMs, endMs)
                    },
                    onBoundaryChanged = { setBoundary(spread.ordinal, it) },
                    onMergeNext = if (spread.ordinal < editableBook.spreads.size) {
                        { mergeWithNext(spread.ordinal) }
                    } else null,
                    onRerecord = { onRerecord(editableBook, spread.ordinal) },
                    onRecapture = { onRecapture(editableBook, spread.ordinal) },
                    onPlay = {
                        if (playingOrdinal == spread.ordinal) {
                            player.stop()
                            playingOrdinal = null
                        } else {
                            playingOrdinal = spread.ordinal
                            player.play(spread.audioFile, spread.startMs, spread.endMs) {
                                playingOrdinal = null
                            }
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
    trimRange: ClosedFloatingPointRange<Float>,
    boundaryValueMs: Long?,
    boundaryRange: ClosedFloatingPointRange<Float>?,
    onTrimChanged: (Long, Long) -> Unit,
    onBoundaryChanged: (Long) -> Unit,
    onMergeNext: (() -> Unit)?,
    onRerecord: () -> Unit,
    onRecapture: () -> Unit,
    onPlay: () -> Unit,
) {
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
                OutlinedButton(
                    onClick = onPlay,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.padding(top = 7.dp),
                ) { Text(if (playing) "停止" else "▶ 试听") }
                TextButton(
                    onClick = onRecapture,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) { Text("重拍书面") }
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
}

@Composable
private fun StoryImage(file: File?, modifier: Modifier = Modifier) {
    val bitmap = remember(file?.absolutePath, file?.lastModified()) {
        file?.takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
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
    val tenths = (safe % 1000) / 100
    return "%02d:%02d.%d".format(totalSeconds / 60, totalSeconds % 60, tenths)
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
    return "$bestText$secondText · $state"
}
