package com.read4me.app.ui

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    var choosingManualSpread by remember { mutableStateOf(false) }
    var parentMode by remember { mutableStateOf(false) }
    var readingPhase by remember {
        mutableStateOf(if (orbReferences.isEmpty()) ChildReadingPhase.NO_BOOKS else ChildReadingPhase.LOOKING)
    }
    var phaseBeforeConfirmation by remember { mutableStateOf(ChildReadingPhase.LOOKING) }

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
            // The camera has a stable, full-screen viewport in every playback state.
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val landscape = maxWidth > maxHeight
                // Controls overlay the preview. The guide keeps its proportions and is
                // only a composition aid; recognition uses the complete camera viewport.
                BoxWithConstraints(
                    Modifier.fillMaxSize().padding(
                        start = if (landscape) 60.dp else 16.dp,
                        end = if (landscape) 60.dp else 16.dp,
                        top = if (landscape) 12.dp else 60.dp,
                        bottom = 64.dp,
                    ),
                ) {
                    val guide = centeredCaptureFrame(maxWidth.value, maxHeight.value, if (landscape) 1.5f else .75f)
                    Canvas(Modifier.align(Alignment.Center).size(guide.width.dp, guide.height.dp)) {
                        val color = if (isMoving) Honey else Color.White.copy(alpha = .9f)
                        val length = minOf(28.dp.toPx(), size.minDimension * .15f)
                        val inset = 3.dp.toPx()
                        listOf(
                            Triple(Offset(inset, inset), 1f, 1f),
                            Triple(Offset(size.width - inset, inset), -1f, 1f),
                            Triple(Offset(inset, size.height - inset), 1f, -1f),
                            Triple(Offset(size.width - inset, size.height - inset), -1f, -1f),
                        ).forEach { (corner, x, y) ->
                            listOf(corner + Offset(x * length, 0f), corner + Offset(0f, y * length)).forEach { end ->
                                drawLine(Ink.copy(alpha = .6f), corner, end, 5.dp.toPx(), StrokeCap.Round)
                                drawLine(color, corner, end, 2.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                }
                AppIconButton(
                    AppIcons.Back, "返回书架", onExit,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Ink.copy(alpha = .55f), CircleShape),
                    tint = Color.White,
                )
                AppIconButton(
                    AppIcons.More, "识别帮助与选页播放", { parentMode = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(Ink.copy(alpha = .55f), CircleShape),
                    tint = Color.White,
                )
                Surface(
                    color = Ink.copy(alpha = .78f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        .widthIn(max = 440.dp).fillMaxWidth(),
                ) {
                    Row(
                        Modifier.height(48.dp).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (readingPhase) {
                                ChildReadingPhase.NO_BOOKS -> "先为绘本添加书面照片"
                                ChildReadingPhase.LOOKING -> if (isMoving) "请放稳书面" else "对准完整书面，停稳即播放"
                                ChildReadingPhase.CONFIRMING -> "正在确认书面…"
                                ChildReadingPhase.PLAYING -> "第 ${currentSpread?.ordinal ?: "-"} 个书面 · 播放中"
                                ChildReadingPhase.PAUSED -> "已暂停 · 可以继续或翻页"
                                ChildReadingPhase.MOVED -> "请放回书面"
                                ChildReadingPhase.FINISHED -> "讲完啦，翻页继续"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        currentSpread?.let { spread ->
                            AppIconButton(
                                if (isPlaying) AppIcons.Pause else AppIcons.Play,
                                if (isPlaying) "暂停" else "播放",
                                {
                                    if (readingPhase != ChildReadingPhase.MOVED) {
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
                                        } else currentBook?.let { play(it, spread, inliers) }
                                    }
                                },
                                tint = if (readingPhase == ChildReadingPhase.MOVED) Color.White.copy(alpha = .35f) else Color.White,
                            )
                            AppIconButton(AppIcons.Restart, "从头听", {
                                if (readingPhase != ChildReadingPhase.MOVED) currentBook?.let { play(it, spread, inliers) }
                            }, tint = if (readingPhase == ChildReadingPhase.MOVED) Color.White.copy(alpha = .35f) else Color.White)
                        }
                    }
                }

                if (parentMode) {
                    Surface(
                        color = Paper,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    ) {
                        Column(Modifier.heightIn(max = maxHeight * .8f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text("识别帮助", style = MaterialTheme.typography.titleLarge)
                            Text("把书面完整放进画面，放稳并避开反光。双页建议横屏。", modifier = Modifier.padding(top = 8.dp))
                            TextButton(onClick = { choosingManualSpread = !choosingManualSpread }) {
                                Text(if (choosingManualSpread) "收起选页" else "没播对？选页播放")
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
                                TextButton(onClick = onExit) { Text("返回书架", color = Coral) }
                                Button(
                                    onClick = {
                                        parentMode = false
                                        choosingManualSpread = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Moss),
                                ) { Text("返回取景") }
                            }
                        }
                    }
                }
            }
        }
    }
}
