package com.read4me.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.read4me.app.audio.AudioSegmentPlayer
import com.read4me.app.audio.PersistentWaveformCache
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.NarrationTimeline
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.SpreadReference
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StoryBookEditor
import com.read4me.app.vision.CameraFrameAnalyzer
import com.read4me.app.vision.LayeredSearchPlanner
import com.read4me.app.vision.OrbPageMatcher
import com.read4me.app.vision.PageTurnDetector
import com.read4me.app.vision.PhotoQualityAnalyzer
import com.read4me.app.vision.RecognitionEvent
import com.read4me.app.vision.RecognitionHistoryStore
import com.read4me.app.vision.VisualFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun RecaptureScreen(
    book: StoryBook,
    spreadId: String,
    repository: StoryRepository,
    capturePurpose: ReferenceCapturePurpose = ReferenceCapturePurpose.ADD_REFERENCE,
    progressLabel: String? = null,
    onSkip: (() -> Unit)? = null,
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
    var captureFraming by remember(spreadId) {
        mutableStateOf(if (ordinal == 1) CaptureFraming.SINGLE_PAGE else CaptureFraming.TWO_PAGE_SPREAD)
    }
    var message by remember { mutableStateOf("按实际内容选择单页或左右两页，再让书面完整进入框内") }
    val captureButtonLabel = when {
        isCapturing -> "分析并保存中"
        progressLabel != null -> "拍下并添加 · $progressLabel"
        capturePurpose == ReferenceCapturePurpose.REPLACE_DISPLAY_IMAGE -> "拍下并设为展示图"
        else -> "拍下并添加"
    }
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

    fun capturePhoto() {
        if (marker == null) return
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
                                val reference = SpreadReference(target, capturedFingerprint, 2, quality)
                                val withReference = StoryBookEditor.addReference(book, spreadId, reference)
                                val updated = if (capturePurpose == ReferenceCapturePurpose.REPLACE_DISPLAY_IMAGE) {
                                    StoryBookEditor.setPrimaryReference(withReference, spreadId, reference.referenceId)
                                } else withReference
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
    }

    val showStatus = isCapturing || message.contains("失败")
    Surface(Modifier.fillMaxSize(), color = Ink) {
        BookCaptureScaffold(
            controller = controller,
            framing = captureFraming,
            active = isCapturing,
            onBack = onCancel,
            backContentDescription = "取消拍摄",
            onFramingChange = { captureFraming = it },
            portraitBottomInset = 152.dp,
            landscapeStartRailWidth = 180.dp,
            landscapeEndRailWidth = 180.dp,
            portraitControls = {
                if (showStatus) {
                    CaptureStatusChip(message)
                    Spacer(Modifier.height(8.dp))
                }
                CaptureShutter(captureButtonLabel, "拍下并保存照片", !isCapturing, ::capturePhoto, circular = false)
                if (onSkip != null) {
                    TextButton(enabled = !isCapturing, onClick = onSkip) { Text("跳过", color = Color.White) }
                }
            },
            landscapeControls = {
                if (showStatus) {
                    CaptureStatusChip(message)
                    Spacer(Modifier.height(8.dp))
                }
                CaptureShutter(captureButtonLabel, "拍下并保存照片", !isCapturing, ::capturePhoto, circular = true)
                if (onSkip != null) {
                    TextButton(enabled = !isCapturing, onClick = onSkip) { Text("跳过", color = Color.White) }
                }
            },
        )
    }
}

@Composable
fun ReferenceVerificationScreen(
    book: StoryBook,
    spreadId: String,
    recognitionHistory: RecognitionHistoryStore,
    onCancel: () -> Unit,
    onFinished: () -> Unit,
) {
    val target = book.spreads.firstOrNull { it.spreadId == spreadId }
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
    val references = remember(book) {
        book.spreads.flatMap { spread ->
            spread.references.filter { it.file.exists() }.map { reference ->
                OrbPageMatcher.Reference(book.id, spread.ordinal, spread.spreadId, reference.file, reference.referenceId)
            }
        }
    }
    val matcher = remember(references) { OrbPageMatcher(references) }
    val verificationContext = remember(target) {
        target?.let { LayeredSearchPlanner.Context(book.id, it.ordinal) }
    }
    val verifiedOnce = remember { AtomicBoolean(false) }
    var verified by remember { mutableStateOf(false) }
    var inliers by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("把刚补拍的书面放回白框，保持不动") }

    BackHandler(onBack = onCancel)

    DisposableEffect(controller, lifecycleOwner, matcher) {
        controller.bindToLifecycle(lifecycleOwner)
        val analyzer = CameraFrameAnalyzer(PageTurnDetector()) { result, _, grayFrame ->
            if (verifiedOnce.get()) return@CameraFrameAnalyzer
            if (result.isMoving) {
                matcher.requireReconfirmation()
                mainExecutor.execute { message = "画面在移动，请把书放稳" }
                return@CameraFrameAnalyzer
            }
            val started = android.os.SystemClock.elapsedRealtime()
            val decision = matcher.evaluate(grayFrame, verificationContext)
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            mainExecutor.execute {
                if (verifiedOnce.get()) return@execute
                val confirmed = decision.confirmed
                when {
                    confirmed?.reference?.spreadId == spreadId && verifiedOnce.compareAndSet(false, true) -> {
                        verified = true
                        inliers = confirmed.inliers
                        message = "验证通过，这个书面现在可以被识别"
                        runCatching {
                            recognitionHistory.record(RecognitionEvent(
                                timestampMs = System.currentTimeMillis(),
                                bookId = book.id,
                                spreadId = spreadId,
                                outcome = RecognitionEvent.Outcome.REFERENCE_ADDED,
                                bestInliers = 0,
                                secondInliers = null,
                                latencyMs = 0,
                                searchPath = "REFERENCE_REPAIR_VERIFIED",
                            ))
                            recognitionHistory.record(RecognitionEvent(
                                timestampMs = System.currentTimeMillis(),
                                bookId = book.id,
                                spreadId = spreadId,
                                outcome = RecognitionEvent.Outcome.CONFIRMED,
                                bestInliers = confirmed.inliers,
                                secondInliers = decision.second?.inliers,
                                latencyMs = elapsed,
                                searchPath = "REFERENCE_VERIFY",
                            ))
                        }
                    }
                    confirmed != null -> {
                        message = "识别成了书面 ${confirmed.reference.spreadOrdinal}，请确认放入的是书面 ${target?.ordinal ?: "-"}"
                        matcher.requireReconfirmation()
                    }
                    decision.state == OrbPageMatcher.State.LOW_INLIERS -> message = "还没认出来，试着减少反光并放稳一点"
                    decision.state == OrbPageMatcher.State.AMBIGUOUS -> message = "和其他书面太相似，请换一个稍微不同的角度"
                    decision.state == OrbPageMatcher.State.TOO_FEW_FEATURES -> message = "画面细节太少，请让完整书面进入白框"
                    decision.state == OrbPageMatcher.State.CONFIRMING -> message = "找到了，继续保持不动"
                }
            }
        }
        controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.execute { matcher.close() }
            analysisExecutor.shutdown()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { viewContext -> PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                } },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1.32f),
            )
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1.32f)) {
                BookGuideFrame(active = verified, modifier = Modifier.align(Alignment.Center))
            }
            Surface(
                color = Paper.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (verified) "✓ 补拍验证通过" else "立即验证书面 ${target?.ordinal ?: "-"}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (verified) Moss else Ink,
                    )
                    Text(message, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))
                    if (verified) {
                        Text("几何匹配内点：$inliers", style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = .58f))
                        Button(
                            onClick = onFinished,
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Moss),
                        ) { Text("完成修复") }
                    } else {
                        TextButton(onClick = onCancel) { Text("暂不验证，保留新照片") }
                    }
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
    val anchorSpread = book.spreads.firstOrNull { it.spreadId == anchorSpreadId }
    val anchorDuration = anchorSpread?.durationMs ?: 0L
    if (anchor == null || anchorSpread == null || anchorDuration < 1_000L) {
        LaunchedEffect(Unit) { onCancel() }
        return
    }
    val context = LocalContext.current
    val persistentWaveforms = remember(context.cacheDir) {
        PersistentWaveformCache(File(context.cacheDir, "read4me/waveforms"))
    }
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
    val player = remember { AudioSegmentPlayer(context.applicationContext) }
    var latestFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var capturedFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var photoReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var captureFraming by remember(anchorSpreadId) { mutableStateOf(CaptureFraming.TWO_PAGE_SPREAD) }
    var message by remember { mutableStateOf("选择拍摄范围，让要保留的书面完整进入框内") }
    var split by remember { mutableFloatStateOf((anchorDuration / 2L).toFloat()) }
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
            runCatching {
                compositeWaveform(persistentWaveforms, anchorSpread.effectiveSegments)
            }.getOrNull()
        }
    }

    fun capturePhoto() {
        busy = true
        pendingFile?.delete()
        capturedFingerprint = latestFingerprint?.copyOf()
        val token = UUID.randomUUID().toString()
        activeToken = token
        val captureFile = File(File(book.directory, "spreads"), ".insert-$anchorSpreadId-$token.pending.jpg")
        pendingFile = captureFile
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(captureFile).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (activeToken == token) {
                        busy = false
                        photoReady = true
                    } else captureFile.delete()
                }

                override fun onError(exception: ImageCaptureException) {
                    captureFile.delete()
                    if (activeToken == token) {
                        busy = false
                        message = "拍照失败，请重试"
                    }
                }
            },
        )
    }

    val showStatus = busy || message.contains("失败")
    Surface(Modifier.fillMaxSize(), color = if (photoReady) Paper else Ink) {
        if (!photoReady) {
            BookCaptureScaffold(
                controller = controller,
                framing = captureFraming,
                active = busy,
                onBack = ::cancel,
                backContentDescription = "取消插入",
                onFramingChange = { captureFraming = it },
                portraitBottomInset = 112.dp,
                landscapeStartRailWidth = 180.dp,
                landscapeEndRailWidth = 180.dp,
                portraitControls = {
                if (showStatus) {
                    CaptureStatusChip(if (busy) "拍摄中……" else message)
                    Spacer(Modifier.height(8.dp))
                }
                CaptureShutter("拍下书面", "拍下并保存插入书面", !busy && latestFingerprint != null, ::capturePhoto, circular = false)
            },
            landscapeControls = {
                if (showStatus) {
                    CaptureStatusChip(if (busy) "拍摄中……" else message)
                    Spacer(Modifier.height(8.dp))
                }
                CaptureShutter("拍下书面", "拍下并保存插入书面", !busy && latestFingerprint != null, ::capturePhoto, circular = true)
            },
            )
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
                500f..(anchorDuration - 500L).toFloat())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    player.play(
                        NarrationTimeline.clip(anchorSpread.effectiveSegments, (split.toLong() - 2_000L).coerceAtLeast(0), split.toLong()),
                        onError = { message = "旁白文件不可用" },
                    )
                }, modifier = Modifier.weight(1f)) { Text("试听左侧结尾") }
                OutlinedButton(onClick = {
                    player.play(
                        NarrationTimeline.clip(anchorSpread.effectiveSegments, split.toLong(), (split.toLong() + 2_000L).coerceAtMost(anchorDuration)),
                        onError = { message = "旁白文件不可用" },
                    )
                }, modifier = Modifier.weight(1f)) { Text("试听右侧开头") }
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
