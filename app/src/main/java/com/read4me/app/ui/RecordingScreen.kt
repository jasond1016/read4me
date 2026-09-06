package com.read4me.app.ui

import android.content.res.Configuration
import android.os.SystemClock
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.read4me.app.RecordingRecoveryViewModel
import com.read4me.app.audio.StoryAudioRecorder
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.MarkerSource
import com.read4me.app.model.SessionBoundary
import com.read4me.app.model.PendingMarker
import com.read4me.app.model.SpreadMarker
import com.read4me.app.model.SpreadReference
import com.read4me.app.model.RecordingMode
import com.read4me.app.model.StoryBook
import com.read4me.app.model.readiness
import com.read4me.app.model.StoryStatus
import com.read4me.app.model.allocateRecordingSession
import com.read4me.app.model.publishPendingMarker
import com.read4me.app.vision.CameraFrameAnalyzer
import com.read4me.app.vision.PageTurnDetector
import com.read4me.app.vision.VisualFingerprint
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
fun RecordingScreen(
    book: StoryBook,
    mode: RecordingMode,
    repository: StoryRepository,
    recovery: RecordingRecoveryViewModel,
    onCancel: () -> Unit,
    onFinished: (StoryBook) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember(mode) {
        if (mode == RecordingMode.CAMERA) Executors.newSingleThreadExecutor() else null
    }
    val controller = remember(mode) {
        if (mode == RecordingMode.CAMERA) LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        } else null
    }
    val detector = remember { PageTurnDetector() }
    val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    val captureState = remember { RecordingCaptureState(detector, orientation) }
    val recorder = remember { StoryAudioRecorder(context) }
    var draft by remember(book.id) { mutableStateOf(book) }
    val markers = remember(book.id) { mutableStateListOf<SpreadMarker>().apply { addAll(book.markers) } }
    val pendingCaptures = remember { mutableStateListOf<String>() }
    var phase by remember(book.id) {
        mutableStateOf(
            if (recovery.pendingCandidate?.id == book.id) RecordingPhase.SAVE_FAILED else RecordingPhase.READY,
        )
    }
    val isRecording = phase == RecordingPhase.RECORDING
    var initialCaptureReady by remember {
        mutableStateOf(markers.isNotEmpty() && (mode == RecordingMode.MANUAL || markers.last().references.firstOrNull()?.file?.isFile == true))
    }
    var sessionFile by remember { mutableStateOf<File?>(null) }
    val sessionBoundaries = remember { mutableStateListOf<SessionBoundary>() }
    val sessionMarkerIds = remember { mutableStateListOf<String>() }
    var sessionToken by remember { mutableLongStateOf(0L) }
    var pendingImage by remember { mutableStateOf<File?>(null) }
    var isMoving by remember { mutableStateOf(false) }
    var motionScore by remember { mutableFloatStateOf(0f) }
    var elapsedMs by remember { mutableLongStateOf(0) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var pageFeedbackUntil by remember { mutableLongStateOf(0L) }
    var quickCorrectionUntil by remember { mutableLongStateOf(0L) }
    var uiNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var pendingCorrectionSpreadId by remember { mutableStateOf<String?>(null) }
    var captureMessage by remember(book.id) {
        mutableStateOf(
            if (phase == RecordingPhase.SAVE_FAILED) "上次保存未完成，请重试"
            else if (mode == RecordingMode.MANUAL) "手动模式：翻页后请按“下一书面”"
            else "把完整书面放进取景框",
        )
    }
    var latestFingerprint by remember { mutableStateOf<ByteArray?>(null) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var captureFraming by remember(book.id) {
        mutableStateOf(if (markers.isEmpty()) CaptureFraming.SINGLE_PAGE else CaptureFraming.TWO_PAGE_SPREAD)
    }
    var ignorePageTurnsUntil by remember { mutableLongStateOf(0L) }

    fun beginOrientationTransition() {
        captureState.freeze(SystemClock.elapsedRealtime())
        latestFingerprint = null
        isMoving = false
        motionScore = 0f
    }

    fun markSensorDirectionStable() = captureState.markSensorDirectionStable()

    SideEffect {
        val firstComposition = !captureState.automaticGateInitialized.getAndSet(true)
        val orientationChanged = captureState.appliedOrientation.getAndSet(orientation) != orientation
        if (firstComposition) {
            val resetGeneration = detector.reset()
            captureState.automaticPageTurnsFrozen.set(false)
            captureState.sensorTransitionLatched.set(false)
            captureState.allowedDetectorGeneration.set(resetGeneration)
            captureState.stableDetectorGeneration.set(resetGeneration)
            captureState.settleNotBeforeMs.set(0L)
            captureState.postOrientationGuardUntilMs.set(0L)
            captureState.stableFrameCount.set(0)
            captureState.stableFrameWidth.set(0)
            captureState.stableFrameHeight.set(0)
        } else if (orientationChanged && mode == RecordingMode.CAMERA) {
            beginOrientationTransition()
        }
    }

    DisposableEffect(context, lifecycleOwner, mode) {
        if (mode == RecordingMode.CAMERA) {
            val listener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(angleDegrees: Int) {
                    val bucket = orientationBucketForAngle(angleDegrees) ?: return
                    captureState.onSensorAngle(angleDegrees, SystemClock.elapsedRealtime())
                    if (captureState.sensorStableAngle.get() < 0) {
                        captureState.sensorStableAngle.set(angleDegrees)
                        captureState.sensorStableBucket.set(bucket)
                    }
                    val configurationBucket = if (
                        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    ) {
                        ORIENTATION_BUCKET_LANDSCAPE
                    } else {
                        ORIENTATION_BUCKET_PORTRAIT
                    }
                    if (
                        shouldFreezeForOrientation(
                            angleDegrees = angleDegrees,
                            stableAngleDegrees = captureState.sensorStableAngle.get(),
                            stableBucket = captureState.sensorStableBucket.get().takeIf { it >= 0 },
                            configurationBucket = configurationBucket,
                        ) && captureState.sensorTransitionLatched.compareAndSet(false, true)
                    ) {
                        beginOrientationTransition()
                    }
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> if (listener.canDetectOrientation()) listener.enable()
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_DESTROY -> listener.disable()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                listener.canDetectOrientation()
            ) {
                listener.enable()
            }
            onDispose {
                listener.disable()
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        } else {
            onDispose { }
        }
    }

    fun switchCamera() {
        val camera = controller ?: return
        val targetFront = !isFrontCamera
        val resetGeneration = detector.reset()
        captureState.automaticPageTurnsFrozen.set(false)
        captureState.settleNotBeforeMs.set(0L)
        captureState.allowedDetectorGeneration.set(resetGeneration)
        captureState.stableDetectorGeneration.set(resetGeneration)
        captureState.stableFrameCount.set(0)
        markSensorDirectionStable()
        latestFingerprint = null
        isMoving = false
        motionScore = 0f
        ignorePageTurnsUntil = SystemClock.elapsedRealtime() + 1_200L
        runCatching {
            camera.cameraSelector =
                if (targetFront) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
        }.onSuccess {
            isFrontCamera = targetFront
            captureMessage =
                if (targetFront) "已切换到前置摄像头 · 录音继续"
                else "已切换到后置摄像头 · 录音继续"
        }.onFailure {
            ignorePageTurnsUntil = 0L
            captureMessage = if (targetFront) "这台设备没有可用的前置摄像头" else "无法切换到后置摄像头"
        }
    }

    KeepScreenOn(isRecording)

    fun persistCandidate(candidate: StoryBook, finishAfterSave: Boolean): Boolean {
        return runCatching { repository.save(candidate) }.fold(
            onSuccess = {
                draft = candidate
                markers.clear(); markers.addAll(candidate.markers)
                recovery.pendingCandidate = null
                recovery.finishAfterSave = false
                sessionBoundaries.clear(); sessionMarkerIds.clear(); sessionFile = null
                phase = RecordingPhase.PAUSED
                captureMessage = "已暂停并安全保存"
                if (finishAfterSave) onFinished(candidate)
                true
            },
            onFailure = {
                recovery.pendingCandidate = candidate
                recovery.finishAfterSave = finishAfterSave
                phase = RecordingPhase.SAVE_FAILED
                captureMessage = "保存失败，录音文件仍保留。请释放存储空间后重试"
                false
            },
        )
    }

    fun finalizeSession(): Boolean {
        if (phase != RecordingPhase.RECORDING) return phase == RecordingPhase.PAUSED
        phase = RecordingPhase.FINALIZING
        sessionToken++
        pendingImage?.delete(); pendingImage = null; pendingCaptures.clear()
        val file = sessionFile
        val stop = recorder.stop()
        val duration = stop.durationMs
        val durableBoundaries = sessionBoundaries.takeWhile { it.startMs < duration }
        val droppedSpreadIds = sessionBoundaries.drop(durableBoundaries.size).map(SessionBoundary::spreadId).toSet()
        markers.filter { it.spreadId in droppedSpreadIds && it.spreadId in sessionMarkerIds }
            .forEach { marker -> marker.references.forEach { it.file.delete() } }
        markers.removeAll { it.spreadId in droppedSpreadIds && it.spreadId in sessionMarkerIds }
        val allocation = file?.takeIf { it.isFile && it.length() > 0 }
            ?.takeIf { stop.successful }
            ?.let { allocateRecordingSession(it, duration, durableBoundaries) }
        if (allocation == null) {
            file?.delete()
            markers.filter { it.spreadId in sessionMarkerIds }.forEach { marker -> marker.references.forEach { it.file.delete() } }
            markers.removeAll { it.spreadId in sessionMarkerIds }
            captureMessage = "这次录音过短或被中断，未保存这次内容；之前的录音仍保留"
        } else {
            val updated = markers.map { marker ->
                marker.copy(segments = marker.segments + allocation[marker.spreadId].orEmpty())
            }
            markers.clear(); markers.addAll(updated)
            draft = draft.copy(
                markers = updated,
                status = StoryStatus.IN_PROGRESS,
                resumeSpreadId = durableBoundaries.last().spreadId,
            )
            return persistCandidate(draft, finishAfterSave = false)
        }
        sessionBoundaries.clear(); sessionMarkerIds.clear(); sessionFile = null
        phase = RecordingPhase.PAUSED
        return false
    }

    fun complete() {
        if (phase == RecordingPhase.RECORDING && !finalizeSession()) return
        if (phase != RecordingPhase.PAUSED && phase != RecordingPhase.READY) return
        if (draft.readiness().canPlay) {
            persistCandidate(
                draft.copy(status = StoryStatus.COMPLETE, resumeSpreadId = draft.markers.last().spreadId),
                finishAfterSave = true,
            )
        } else {
            captureMessage = "还没有完整的录音，请继续录制后再结束。"
        }
    }

    fun leaveRecording() {
        if (phase == RecordingPhase.SAVE_FAILED || phase == RecordingPhase.FINALIZING) return
        if (phase == RecordingPhase.RECORDING && !finalizeSession()) return
        if (markers.isEmpty()) repository.deleteDraft(draft)
        onCancel()
    }
    val currentFinalize by rememberUpdatedState(::finalizeSession)

    fun captureMarker(source: MarkerSource) {
        if (phase != RecordingPhase.RECORDING || pendingCaptures.isNotEmpty()) return
        val timestamp = if (source == MarkerSource.INITIAL) 0L else recorder.elapsedMs
        if (source != MarkerSource.INITIAL && timestamp - (sessionBoundaries.lastOrNull()?.startMs ?: 0L) < 1_200L) {
            return
        }
        val spreadId = UUID.randomUUID().toString()
        val token = sessionToken
        if (mode == RecordingMode.MANUAL) {
            markers += SpreadMarker(
                timestampMs = timestamp,
                source = source,
                references = emptyList(),
                spreadId = spreadId,
            )
            val published = publishPendingMarker(
                sessionBoundaries,
                PendingMarker(token, spreadId, timestamp),
                sessionToken,
            )
            sessionBoundaries.clear(); sessionBoundaries.addAll(published)
            sessionMarkerIds += spreadId
            initialCaptureReady = true
            captureMessage = if (source == MarkerSource.INITIAL) {
                "已开始第 1 个书面"
            } else {
                pageFeedbackUntil = SystemClock.elapsedRealtime() + 2_000L
                quickCorrectionUntil = SystemClock.elapsedRealtime() + 5_000L
                "已进入书面 ${markers.size}"
            }
            return
        }
        val imageFile = repository.imageFile(draft, spreadId)
        val captureFile = File(imageFile.parentFile, ".$spreadId-$token.pending.jpg")
        pendingImage = captureFile
        val pendingMarker = SpreadMarker(
            timestampMs = timestamp,
            source = source,
            references = listOf(SpreadReference(imageFile, latestFingerprint?.copyOf(), 2)),
            spreadId = spreadId,
        )
        pendingCaptures += spreadId
        runCatching { requireNotNull(controller).takePicture(
            ImageCapture.OutputFileOptions.Builder(captureFile)
                .setMetadata(
                    ImageCapture.Metadata().apply {
                        isReversedHorizontal = false
                    },
                )
                .build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (token != sessionToken || phase != RecordingPhase.RECORDING) {
                        captureFile.delete()
                        return
                    }
                    runCatching { repository.installInsertImage(draft, spreadId, captureFile) }.onFailure {
                        captureFile.delete(); pendingCaptures.remove(spreadId); pendingImage = null
                        if (source == MarkerSource.INITIAL) initialCaptureReady = false
                        captureMessage = "书面照片保存失败，录音仍在继续"
                        return
                    }
                    markers += pendingMarker
                    val published = publishPendingMarker(sessionBoundaries, PendingMarker(token, spreadId, timestamp), sessionToken)
                    sessionBoundaries.clear(); sessionBoundaries.addAll(published)
                    sessionMarkerIds += spreadId
                    pendingCaptures.remove(spreadId)
                    pendingImage = null
                    if (source == MarkerSource.INITIAL) {
                        initialCaptureReady = true
                        captureFraming = CaptureFraming.TWO_PAGE_SPREAD
                        beginOrientationTransition()
                    }
                    if (source != MarkerSource.INITIAL) {
                        val now = SystemClock.elapsedRealtime()
                        pageFeedbackUntil = now + 2_000L
                        quickCorrectionUntil = now + 5_000L
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    captureMessage = when (source) {
                        MarkerSource.INITIAL -> "首个书面已保存 · 接下来默认拍左右两页，可随时切换"
                        MarkerSource.AUTOMATIC -> "已自动进入书面 ${markers.size}"
                        MarkerSource.MANUAL -> "已进入书面 ${markers.size}"
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    captureFile.delete()
                    if (token != sessionToken) return
                    pendingCaptures.remove(spreadId)
                    pendingImage = null
                    if (source == MarkerSource.INITIAL) initialCaptureReady = false
                    captureMessage = if (source == MarkerSource.INITIAL) {
                        "首个书面保存失败，请重试首张书面"
                    } else {
                        "书面照片保存失败，录音仍在继续"
                    }
                }
            },
        ) }.onFailure {
            captureFile.delete()
            pendingCaptures.remove(spreadId)
            pendingImage = null
            captureMessage = "无法拍摄书面，请稍后重试；录音仍在继续"
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val active = AtomicBoolean(true)
        if (controller != null && analysisExecutor != null) {
            controller.bindToLifecycle(lifecycleOwner)
            val analyzer = CameraFrameAnalyzer(detector) { result, luma, grayFrame ->
                val analyzedOrientation = context.resources.configuration.orientation
                val fingerprint = VisualFingerprint.fromLuma(luma)
                mainExecutor.execute {
                    if (!active.get()) return@execute
                    val currentOrientation = context.resources.configuration.orientation
                    val nowMs = SystemClock.elapsedRealtime()
                    if (
                        analyzedOrientation != currentOrientation ||
                            captureState.appliedOrientation.get() != currentOrientation
                    ) {
                        captureState.stableFrameCount.set(0)
                        captureState.stableFrameWidth.set(0)
                        captureState.stableFrameHeight.set(0)
                        return@execute
                    }
                    if (captureState.automaticPageTurnsFrozen.get()) {
                        if (!canAccumulateStableFrame(nowMs, captureState.settleNotBeforeMs.get())) {
                            captureState.stableFrameCount.set(0)
                            captureState.stableFrameWidth.set(0)
                            captureState.stableFrameHeight.set(0)
                        } else {
                            val countBefore = captureState.stableFrameCount.get()
                            val sameStableStream =
                                result.generation == captureState.stableDetectorGeneration.get() &&
                                    !result.isMoving &&
                                    !result.pageTurned &&
                                    result.motionScore <= 5f &&
                                    (countBefore == 0 ||
                                        (captureState.stableFrameWidth.get() == grayFrame.width &&
                                            captureState.stableFrameHeight.get() == grayFrame.height))
                            if (sameStableStream) {
                                if (countBefore == 0) {
                                    captureState.stableFrameWidth.set(grayFrame.width)
                                    captureState.stableFrameHeight.set(grayFrame.height)
                                }
                                val count = captureState.stableFrameCount.incrementAndGet()
                                if (
                                    count >= 4 &&
                                    nowMs >= captureState.postOrientationGuardUntilMs.get()
                                ) {
                                    val resumedGeneration = detector.reset()
                                    captureState.stableDetectorGeneration.set(resumedGeneration)
                                    captureState.allowedDetectorGeneration.set(resumedGeneration)
                                    captureState.stableFrameCount.set(0)
                                    captureState.automaticPageTurnsFrozen.set(false)
                                    markSensorDirectionStable()
                                }
                            } else {
                                captureState.stableFrameCount.set(0)
                                captureState.stableFrameWidth.set(0)
                                captureState.stableFrameHeight.set(0)
                            }
                        }
                    }
                    latestFingerprint = fingerprint
                    motionScore = result.motionScore
                    isMoving = result.isMoving
                    if (
                        shouldCommitAutomaticPageTurn(
                            pageTurned = result.pageTurned,
                            nowMs = nowMs,
                            suppressUntilMs = ignorePageTurnsUntil,
                            settleNotBeforeMs = captureState.settleNotBeforeMs.get(),
                            postOrientationGuardUntilMs = captureState.postOrientationGuardUntilMs.get(),
                            analyzedOrientation = analyzedOrientation,
                            currentOrientation = currentOrientation,
                            appliedOrientation = captureState.appliedOrientation.get(),
                            automaticPageTurnsFrozen = captureState.automaticPageTurnsFrozen.get(),
                            detectorGeneration = result.generation,
                            allowedDetectorGeneration = captureState.allowedDetectorGeneration.get(),
                        ) &&
                            phase == RecordingPhase.RECORDING &&
                            initialCaptureReady
                    ) {
                        captureMarker(MarkerSource.AUTOMATIC)
                    }
                }
            }
            controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) currentFinalize()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            active.set(false)
            currentFinalize()
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller?.clearImageAnalysisAnalyzer()
            controller?.unbind()
            analysisExecutor?.shutdown()
            recorder.release()
        }
    }

    LaunchedEffect(controller, lifecycleOwner, phase) {
        if (phase != RecordingPhase.RECORDING) pendingCorrectionSpreadId = null
        if (controller != null) {
            if (phase == RecordingPhase.READY || phase == RecordingPhase.RECORDING) {
                runCatching { controller.bindToLifecycle(lifecycleOwner) }
            } else {
                controller.unbind()
            }
        }
    }

    BackHandler(onBack = ::leaveRecording)

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMs = recorder.elapsedMs
            amplitude = amplitudeLevel(recorder.amplitude)
            uiNow = SystemClock.elapsedRealtime()
            delay(160)
        }
    }

    fun resumeRecording() {
        val resetGeneration = detector.reset()
        captureState.automaticPageTurnsFrozen.set(false)
        captureState.settleNotBeforeMs.set(0L)
        captureState.allowedDetectorGeneration.set(resetGeneration)
        captureState.stableDetectorGeneration.set(resetGeneration)
        captureState.stableFrameCount.set(0)
        markSensorDirectionStable()
        val file = repository.recordingFile(draft)
        runCatching { recorder.start(file) }.onFailure {
            file.delete()
            captureMessage = "无法开始录音，请检查麦克风是否被占用及剩余存储空间，再重试"
            return
        }
        elapsedMs = 0L
        amplitude = 0f
        sessionFile = file
        phase = RecordingPhase.RECORDING
        if (markers.isEmpty()) {
            initialCaptureReady = false
            captureMessage = "正在建立第一个书面"
            captureMarker(MarkerSource.INITIAL)
        } else {
            initialCaptureReady = true
            val resumeId = draft.resumeSpreadId
                ?.takeIf { id -> markers.any { it.spreadId == id } }
                ?: markers.last().spreadId
            sessionBoundaries += SessionBoundary(resumeId, 0L)
            captureMessage = "继续当前书面"
        }
    }

    fun undoLastPage(expectedSpreadId: String) {
        if (
            sessionMarkerIds.lastOrNull() != expectedSpreadId ||
                sessionBoundaries.lastOrNull()?.spreadId != expectedSpreadId
        ) return
        val removedId = sessionMarkerIds.removeAt(sessionMarkerIds.lastIndex)
        sessionBoundaries.removeAll { it.spreadId == removedId }
        markers.firstOrNull { it.spreadId == removedId }?.references?.forEach { it.file.delete() }
        markers.removeAll { it.spreadId == removedId }
        pageFeedbackUntil = 0L
        quickCorrectionUntil = 0L
        captureMessage = "已更正上一次翻页 · 当前仍是书面 ${markers.size}"
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        if (mode == RecordingMode.MANUAL) {
            val canUndoManualPage = sessionBoundaries.size > 1 &&
                sessionMarkerIds.lastOrNull() == sessionBoundaries.lastOrNull()?.spreadId
            when (phase) {
                RecordingPhase.RECORDING -> ManualRecordingActive(
                    page = markers.size.coerceAtLeast(1),
                    elapsedMs = elapsedMs,
                    amplitude = amplitude,
                    pageJustChanged = uiNow < pageFeedbackUntil,
                    canUndo = canUndoManualPage,
                    onBack = ::leaveRecording,
                    onNextPage = {
                        val markerCount = markers.size
                        captureMarker(if (initialCaptureReady) MarkerSource.MANUAL else MarkerSource.INITIAL)
                        if (markers.size > markerCount) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onUndo = { pendingCorrectionSpreadId = sessionMarkerIds.lastOrNull() },
                    onPause = { finalizeSession() },
                    onComplete = ::complete,
                )
                RecordingPhase.FINALIZING -> ManualRecordingSaving()
                RecordingPhase.SAVE_FAILED -> ManualRecordingSaveFailed(
                    message = captureMessage,
                    onRetry = { recovery.pendingCandidate?.let { persistCandidate(it, recovery.finishAfterSave) } },
                )
                RecordingPhase.READY,
                RecordingPhase.PAUSED -> ManualRecordingPaused(
                    pageCount = markers.size,
                    durationMs = draft.playableDurationMs,
                    mode = mode,
                    message = captureMessage,
                    onResume = ::resumeRecording,
                    onComplete = ::complete,
                    onReturnToLibrary = ::leaveRecording,
                )
            }
        } else {
            val canCorrectPage = pendingCaptures.isEmpty() && sessionBoundaries.size > 1 &&
                sessionMarkerIds.lastOrNull() == sessionBoundaries.lastOrNull()?.spreadId
            when (phase) {
                RecordingPhase.FINALIZING -> ManualRecordingSaving()
                RecordingPhase.SAVE_FAILED -> ManualRecordingSaveFailed(
                    message = captureMessage,
                    onRetry = { recovery.pendingCandidate?.let { persistCandidate(it, recovery.finishAfterSave) } },
                )
                RecordingPhase.PAUSED -> ManualRecordingPaused(
                    mode = mode,
                    message = captureMessage,
                    pageCount = markers.size,
                    durationMs = draft.playableDurationMs,
                    onResume = ::resumeRecording,
                    onComplete = ::complete,
                    onReturnToLibrary = ::leaveRecording,
                )
                RecordingPhase.READY,
                RecordingPhase.RECORDING -> CameraRecordingWorkspace(
                    controller = requireNotNull(controller),
                    isRecording = isRecording,
                    isFrontCamera = isFrontCamera,
                    isMoving = isMoving,
                    elapsedMs = elapsedMs,
                    amplitude = amplitude,
                    page = markers.size.coerceAtLeast(1),
                    cameraReady = latestFingerprint != null,
                    initialCaptureReady = initialCaptureReady,
                    captureInProgress = pendingCaptures.isNotEmpty(),
                    pageJustChanged = uiNow < pageFeedbackUntil,
                    showQuickCorrection = canCorrectPage && uiNow < quickCorrectionUntil,
                    orientationGuardActive = isRecording && (captureState.automaticPageTurnsFrozen.get() || uiNow < captureState.postOrientationGuardUntilMs.get()),
                    canCorrectPage = canCorrectPage,
                    captureMessage = captureMessage,
                    captureFraming = captureFraming,
                    onCaptureFramingChange = {
                        if (captureFraming != it) {
                            captureFraming = it
                            beginOrientationTransition()
                        }
                    },
                    onBack = ::leaveRecording,
                    onSwitchCamera = ::switchCamera,
                    onStart = ::resumeRecording,
                    onMarkPage = { captureMarker(if (initialCaptureReady) MarkerSource.MANUAL else MarkerSource.INITIAL) },
                    onCorrectPage = { pendingCorrectionSpreadId = sessionMarkerIds.lastOrNull() },
                    onPause = { finalizeSession() },
                    onComplete = ::complete,
                )
            }
        }
    }
    pendingCorrectionSpreadId?.let { correctionSpreadId ->
        AlertDialog(
            onDismissRequest = { pendingCorrectionSpreadId = null },
            title = { Text("更正上一次翻页？") },
            text = {
                Text(
                    "当前将从书面 ${markers.size} 回到书面 ${(markers.size - 1).coerceAtLeast(1)}。录音不会被删除，但刚才建立的分界会移除，之后的声音会并回前一书面。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCorrectionSpreadId = null
                        undoLastPage(correctionSpreadId)
                    },
                ) { Text("确认更正", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { pendingCorrectionSpreadId = null }) { Text("取消") } },
        )
    }

}
