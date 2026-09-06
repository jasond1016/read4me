package com.read4me.app.ui

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.read4me.app.audio.AudioSegmentPlayer
import com.read4me.app.audio.AudioBookPlaybackService
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StorySpread
import com.read4me.app.vision.CameraFrameAnalyzer
import com.read4me.app.vision.LayeredSearchPlanner
import com.read4me.app.vision.OrbPageMatcher
import com.read4me.app.vision.PageTurnDetector
import com.read4me.app.vision.RecognitionEvent
import com.read4me.app.vision.RecognitionHistoryStore
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private enum class ChildReadingPhase {
    NO_BOOKS,
    LOOKING,
    CONFIRMING,
    PLAYING,
    PAUSED,
    MOVED,
    FINISHED,
}

@Composable
private fun ChildReadingStatus(
    phase: ChildReadingPhase,
    status: String,
    book: StoryBook?,
    spread: StorySpread?,
    progress: Float,
) {
    val pulse = rememberInfiniteTransition(label = "readingPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "readingPulseScale",
    )
    val accent = when (phase) {
        ChildReadingPhase.PLAYING -> Coral
        ChildReadingPhase.FINISHED -> Moss
        ChildReadingPhase.MOVED -> Honey
        ChildReadingPhase.PAUSED -> Honey
        ChildReadingPhase.CONFIRMING -> Color(0xFF6B7FA3)
        ChildReadingPhase.NO_BOOKS,
        ChildReadingPhase.LOOKING -> Ink.copy(alpha = 0.5f)
    }
    val statusIcon = when (phase) {
        ChildReadingPhase.PLAYING -> AppIcons.Play
        ChildReadingPhase.FINISHED -> AppIcons.Restart
        ChildReadingPhase.MOVED -> AppIcons.Reading
        ChildReadingPhase.PAUSED -> AppIcons.Pause
        ChildReadingPhase.CONFIRMING -> AppIcons.More
        ChildReadingPhase.NO_BOOKS -> AppIcons.Library
        ChildReadingPhase.LOOKING -> AppIcons.Fullscreen
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(width = 94.dp, height = 72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (spread?.imageFile != null) {
                StoryImage(
                    spread.imageFile,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (phase == ChildReadingPhase.MOVED) 0.55f else 1f },
                )
            } else {
                AppIcon(
                    statusIcon,
                    contentDescription = null,
                    tint = accent,
                    size = 32.dp,
                    modifier = Modifier.graphicsLayer {
                        val scale = if (phase == ChildReadingPhase.PLAYING) pulseScale else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
            if (spread?.imageFile != null) {
                Surface(
                    color = accent,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(
                            statusIcon,
                            contentDescription = null,
                            tint = Color.White,
                            size = 18.dp,
                            modifier = Modifier.graphicsLayer {
                                val scale = if (phase == ChildReadingPhase.PLAYING) pulseScale else 1f
                                scaleX = scale
                                scaleY = scale
                            },
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(status, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(
                when (phase) {
                    ChildReadingPhase.NO_BOOKS -> "先录完一本绘本，并为书面补拍照片。"
                    ChildReadingPhase.LOOKING -> "让整页进入取景框，手移开，保持光线均匀。"
                    ChildReadingPhase.CONFIRMING -> "已经看到绘本，请保持不动。"
                    ChildReadingPhase.PLAYING -> "正在播放这一页的录音。"
                    ChildReadingPhase.MOVED -> "把整页放回框里，停稳后会继续识别。"
                    ChildReadingPhase.PAUSED -> "点播放继续听，也可以翻到下一页。"
                    ChildReadingPhase.FINISHED -> "这一页听完了，翻到下一页继续。"
                }, style = MaterialTheme.typography.bodyMedium, color = Moss,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (book != null && spread != null) {
                Text(
                    if (phase == ChildReadingPhase.MOVED) {
                        "刚才是《${book.title}》 · 第 ${spread.ordinal} 个书面"
                    } else {
                        "《${book.title}》 · 第 ${spread.ordinal} 个书面"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.58f),
                    maxLines = 1,
                )
            }
        }
    }
    val showsProgress = when (phase) {
        ChildReadingPhase.PLAYING,
        ChildReadingPhase.PAUSED,
        ChildReadingPhase.MOVED,
        ChildReadingPhase.FINISHED -> true
        else -> false
    }
    if (spread != null && showsProgress) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(CircleShape),
            color = accent,
            trackColor = accent.copy(alpha = 0.16f),
        )
    }
}

@Composable
fun ChildReadingScreen(
    books: List<StoryBook>,
    recognitionHistory: RecognitionHistoryStore,
    onExit: () -> Unit,
) {
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
    val player = remember { AudioSegmentPlayer(context.applicationContext) }
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
    val recognitionGeneration = remember { AtomicLong(0L) }
    val recognitionArmed = remember { AtomicBoolean(true) }
    val lifecycleResumed = remember { AtomicBoolean(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
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
    var readingPhase by remember {
        mutableStateOf(if (orbReferences.isEmpty()) ChildReadingPhase.NO_BOOKS else ChildReadingPhase.LOOKING)
    }
    var phaseBeforeConfirmation by remember { mutableStateOf(ChildReadingPhase.LOOKING) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }

    KeepScreenOn()

    LaunchedEffect(Unit) {
        context.stopService(Intent(context, AudioBookPlaybackService::class.java))
    }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner, player, orbMatcher) {
        fun requireFreshRecognition() {
            recognitionGeneration.incrementAndGet()
            recognitionArmed.set(false)
            runCatching {
                analysisExecutor.execute {
                    orbMatcher.requireReconfirmation()
                    recognitionArmed.set(true)
                }
            }
        }
        fun pauseForInterruption(nextStatus: String) {
            if (isPlaying && player.pause()) {
                isPlaying = false
                pausedForPageChange = true
                readingPhase = ChildReadingPhase.MOVED
                status = nextStatus
                requireFreshRecognition()
            }
        }
        player.setInterruptionListener(
            onInterrupted = { pauseForInterruption("声音被其他应用打断，回来后会继续") },
            onFocusAvailable = {
                if (pausedForPageChange) {
                    status = "请把书放回框里，确认后继续"
                    requireFreshRecognition()
                }
            },
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    lifecycleResumed.set(false)
                    pauseForInterruption("阅读已暂停，回来后会先确认书面")
                }
                Lifecycle.Event.ON_RESUME -> {
                    lifecycleResumed.set(true)
                    if (pausedForPageChange) {
                        status = "请把书放回框里，确认后继续"
                        requireFreshRecognition()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleResumed.set(false)
            recognitionGeneration.incrementAndGet()
            recognitionArmed.set(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.clearInterruptionListener()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player.progress()?.let { playbackProgress = it }
            delay(250L)
        }
    }

    BackHandler {
        if (parentMode) {
            parentMode = false
            choosingManualSpread = false
        } else {
            onExit()
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
        readingPhase = ChildReadingPhase.PLAYING
        playbackProgress = 0f
        status = "听故事"
        val started = player.play(
            spread.effectiveSegments,
            onError = {
                isPlaying = false
                readingPhase = ChildReadingPhase.FINISHED
                status = "旁白文件不可用"
            },
            onFinished = {
                isPlaying = false
                readingPhase = ChildReadingPhase.FINISHED
                playbackProgress = 1f
                status = "讲完啦，请翻页"
            },
        )
        if (!started) {
            isPlaying = false
            readingPhase = ChildReadingPhase.FINISHED
            status = "旁白文件不可用"
        }
    }

    LaunchedEffect(isMoving) {
        if (isMoving) {
            delay(1_200L)
            if (isMoving && isPlaying && player.pause()) {
                isPlaying = false
                pausedForPageChange = true
                readingPhase = ChildReadingPhase.MOVED
                status = "放回刚才的位置"
            }
        }
    }

    DisposableEffect(controller, lifecycleOwner, orbMatcher) {
        controller.bindToLifecycle(lifecycleOwner)
        var lastFailureKey: String? = null
        var lastFailureAtMs = 0L
        val analyzer = CameraFrameAnalyzer(detector) { result, _, grayFrame ->
            val evaluatedContext = recognitionContext.get()
            val evaluatedGeneration = recognitionGeneration.get()
            val evaluatedArmed = recognitionArmed.get()
            val evaluationStarted = android.os.SystemClock.elapsedRealtime()
            val decision = if (result.isMoving) {
                orbMatcher.requireReconfirmation()
                null
            } else {
                orbMatcher.evaluate(grayFrame, evaluatedContext)
            }
            val latencyMs = android.os.SystemClock.elapsedRealtime() - evaluationStarted
            decision?.let { current ->
                val best = current.best
                val outcome = when (current.state) {
                    OrbPageMatcher.State.CONFIRMED -> RecognitionEvent.Outcome.CONFIRMED
                    OrbPageMatcher.State.LOW_INLIERS -> RecognitionEvent.Outcome.LOW_INLIERS
                    OrbPageMatcher.State.AMBIGUOUS -> RecognitionEvent.Outcome.AMBIGUOUS
                    else -> null
                }
                if (outcome != null && best != null) {
                    val now = System.currentTimeMillis()
                    val failureKey = "${best.reference.groupKey}:$outcome"
                    val shouldRecord = outcome == RecognitionEvent.Outcome.CONFIRMED ||
                        failureKey != lastFailureKey || now - lastFailureAtMs >= 3_000L
                    if (shouldRecord) {
                        runCatching { recognitionHistory.record(RecognitionEvent(
                            timestampMs = now,
                            bookId = best.reference.bookId,
                            spreadId = best.reference.spreadId,
                            outcome = outcome,
                            bestInliers = best.inliers,
                            secondInliers = current.second?.inliers,
                            latencyMs = latencyMs,
                            searchPath = current.searchPath.name,
                        )) }
                        if (outcome != RecognitionEvent.Outcome.CONFIRMED) {
                            lastFailureKey = failureKey
                            lastFailureAtMs = now
                        }
                    }
                }
            }
            mainExecutor.execute {
                isMoving = result.isMoving
                if (result.isMoving) {
                    if (!isPlaying && currentSpread == null) {
                        readingPhase = ChildReadingPhase.LOOKING
                        status = "把书放稳一点"
                    }
                } else if (result.pageTurned) {
                    if (isPlaying && player.pause()) {
                        isPlaying = false
                        pausedForPageChange = true
                    }
                    if (readingPhase != ChildReadingPhase.CONFIRMING) {
                        phaseBeforeConfirmation = readingPhase
                    }
                    readingPhase = ChildReadingPhase.CONFIRMING
                    status = "正在找这一页"
                }
                val currentDecision = decision?.takeIf {
                    recognitionContext.get() == evaluatedContext &&
                        evaluatedArmed && recognitionArmed.get() &&
                        recognitionGeneration.get() == evaluatedGeneration && lifecycleResumed.get()
                }
                if (currentDecision != null) diagnostic = recognitionDiagnostic(currentDecision)
                if (currentDecision != null && !isPlaying && !result.pageTurned && !pausedForPageChange &&
                    readingPhase != ChildReadingPhase.PAUSED && readingPhase != ChildReadingPhase.FINISHED
                ) {
                    status = when (currentDecision.state) {
                        OrbPageMatcher.State.NO_REFERENCES -> "没有可识别的参考照片"
                        OrbPageMatcher.State.TOO_FEW_FEATURES -> "画面细节太少，请把书放进白框"
                        OrbPageMatcher.State.LOW_INLIERS -> "还没对准，再放稳一点"
                        OrbPageMatcher.State.AMBIGUOUS -> "找到了相似页面，正在分辨"
                        OrbPageMatcher.State.CONFIRMING -> "正在确认书面……"
                        else -> status
                    }
                    readingPhase = when (currentDecision.state) {
                        OrbPageMatcher.State.NO_REFERENCES -> ChildReadingPhase.NO_BOOKS
                        OrbPageMatcher.State.CONFIRMING,
                        OrbPageMatcher.State.AMBIGUOUS -> ChildReadingPhase.CONFIRMING
                        else -> ChildReadingPhase.LOOKING
                    }
                }
                currentDecision?.confirmed?.let {
                    spreadsByKey[it.reference.groupKey]?.let { (book, spread) ->
                        val isCurrentSpread = currentBook?.id == book.id && currentSpread?.spreadId == spread.spreadId
                        when {
                            isCurrentSpread && pausedForPageChange && player.resume() -> {
                                pausedForPageChange = false
                                isPlaying = true
                                readingPhase = ChildReadingPhase.PLAYING
                                inliers = it.inliers
                                status = "继续听故事"
                            }
                            isCurrentSpread && readingPhase == ChildReadingPhase.CONFIRMING -> {
                                readingPhase = phaseBeforeConfirmation
                                status = when (phaseBeforeConfirmation) {
                                    ChildReadingPhase.FINISHED -> "讲完啦，请翻页"
                                    ChildReadingPhase.PAUSED -> "休息一下"
                                    else -> status
                                }
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

            Text(
                "返回书架",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
                    .background(Ink.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onExit)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    ChildReadingStatus(
                        phase = readingPhase,
                        status = status,
                        book = currentBook,
                        spread = currentSpread,
                        progress = playbackProgress,
                    )
                    currentSpread?.let { spread ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isPlaying) {
                                        if (player.pause()) {
                                            isPlaying = false
                                            pausedForPageChange = false
                                            readingPhase = ChildReadingPhase.PAUSED
                                            status = "休息一下"
                                        }
                                    } else if (readingPhase == ChildReadingPhase.PAUSED && player.resume()) {
                                        isPlaying = true
                                        readingPhase = ChildReadingPhase.PLAYING
                                        status = "继续听故事"
                                    } else {
                                        currentBook?.let { play(it, spread, inliers) }
                                    }
                                },
                                enabled = readingPhase != ChildReadingPhase.MOVED,
                                modifier = Modifier.weight(1f).height(64.dp),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                if (readingPhase != ChildReadingPhase.MOVED) {
                                    AppIcon(if (isPlaying) AppIcons.Pause else AppIcons.Play, null, size = 28.dp)
                                }
                                Text(
                                    when {
                                        isPlaying -> "暂停一下"
                                        readingPhase == ChildReadingPhase.PAUSED -> "继续听"
                                        readingPhase == ChildReadingPhase.MOVED -> "放回书面"
                                        else -> "再听一次"
                                    },
                                    modifier = if (readingPhase == ChildReadingPhase.MOVED) Modifier else Modifier.padding(start = 6.dp),
                                )
                            }
                            Button(
                                onClick = { currentBook?.let { play(it, spread, inliers) } },
                                enabled = readingPhase != ChildReadingPhase.MOVED,
                                modifier = Modifier.weight(1f).height(64.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Moss),
                            ) {
                                AppIcon(AppIcons.Restart, null)
                                Text("从头听", modifier = Modifier.padding(start = 6.dp))
                            }
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
                                                runCatching { recognitionHistory.record(RecognitionEvent(
                                                    timestampMs = System.currentTimeMillis(),
                                                    bookId = choiceBook.id,
                                                    spreadId = choiceSpread.spreadId,
                                                    outcome = RecognitionEvent.Outcome.MANUAL_CORRECTION,
                                                    bestInliers = 0,
                                                    secondInliers = null,
                                                    latencyMs = 0L,
                                                    searchPath = "MANUAL",
                                                )) }
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
