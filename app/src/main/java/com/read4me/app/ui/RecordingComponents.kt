package com.read4me.app.ui

import android.content.res.Configuration
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.read4me.app.R
import com.read4me.app.model.RecordingMode

@Composable
internal fun CameraRecordingWorkspace(
    controller: LifecycleCameraController,
    isRecording: Boolean,
    isFrontCamera: Boolean,
    isMoving: Boolean,
    elapsedMs: Long,
    amplitude: Float,
    page: Int,
    cameraReady: Boolean,
    initialCaptureReady: Boolean,
    captureInProgress: Boolean,
    pageJustChanged: Boolean,
    showQuickCorrection: Boolean,
    orientationGuardActive: Boolean,
    canCorrectPage: Boolean,
    captureMessage: String,
    captureFraming: CaptureFraming,
    onCaptureFramingChange: (CaptureFraming) -> Unit,
    onBack: () -> Unit,
    onSwitchCamera: () -> Unit,
    onStart: () -> Unit,
    onMarkPage: () -> Unit,
    onCorrectPage: () -> Unit,
    onPause: () -> Unit,
    onComplete: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val compactRail = isLandscape && maxHeight < 600.dp
        val startRailWidth = (maxWidth * .22f).coerceIn(130.dp, 190.dp)
        val endRailWidth = (maxWidth * .30f).coerceIn(180.dp, 300.dp)
        BookCaptureScaffold(
            controller = controller,
            framing = captureFraming,
            active = isMoving,
            onBack = onBack,
            backContentDescription = "暂停并返回",
            onFramingChange = onCaptureFramingChange,
            portraitBottomInset = 220.dp,
            landscapeStartRailWidth = startRailWidth,
            landscapeEndRailWidth = endRailWidth,
            topStatus = if (isRecording) "● ${formatDuration(elapsedMs)} · 当前书面 $page" else "校准书面位置",
            portraitTopEnd = @Composable {
                if (!captureInProgress) {
                    Surface(color = Ink.copy(alpha = .76f), shape = CircleShape) {
                        AppIconButton(
                            AppIcons.CameraSwitch,
                            if (isFrontCamera) "切换到后置摄像头" else "切换到前置摄像头",
                            onSwitchCamera,
                            Modifier.size(48.dp),
                            tint = Color.White,
                            iconSize = 27.dp,
                        )
                    }
                }
            },
            landscapeStartExtra = @Composable {
                if (!captureInProgress) {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = Ink.copy(alpha = .76f), shape = CircleShape) {
                        AppIconButton(
                            AppIcons.CameraSwitch,
                            if (isFrontCamera) "切换到后置摄像头" else "切换到前置摄像头",
                            onSwitchCamera,
                            Modifier.size(48.dp),
                            tint = Color.White,
                            iconSize = 27.dp,
                        )
                    }
                }
            },
            portraitControls = @Composable {
            CameraRecordingControls(
                isRecording = isRecording,
                isMoving = isMoving,
                amplitude = amplitude,
                elapsedMs = elapsedMs,
                page = page,
                cameraReady = cameraReady,
                initialCaptureReady = initialCaptureReady,
                captureInProgress = captureInProgress,
                pageJustChanged = pageJustChanged,
                showQuickCorrection = showQuickCorrection,
                orientationGuardActive = orientationGuardActive,
                canCorrectPage = canCorrectPage,
                captureMessage = captureMessage,
                onStart = onStart,
                onMarkPage = onMarkPage,
                onCorrectPage = onCorrectPage,
                onPause = onPause,
                onComplete = onComplete,
                compact = false,
                rail = false,
            )
            },
            landscapeControls = @Composable {
                CameraRecordingControls(
                    isRecording = isRecording,
                    isMoving = isMoving,
                    amplitude = amplitude,
                    elapsedMs = elapsedMs,
                    page = page,
                    cameraReady = cameraReady,
                    initialCaptureReady = initialCaptureReady,
                    captureInProgress = captureInProgress,
                    pageJustChanged = pageJustChanged,
                    showQuickCorrection = showQuickCorrection,
                    orientationGuardActive = orientationGuardActive,
                    canCorrectPage = canCorrectPage,
                    captureMessage = captureMessage,
                    onStart = onStart,
                    onMarkPage = onMarkPage,
                    onCorrectPage = onCorrectPage,
                    onPause = onPause,
                    onComplete = onComplete,
                    compact = compactRail,
                    rail = true,
                )
            },
        )
    }
}

@Composable
private fun CameraRecordingControls(
    isRecording: Boolean,
    isMoving: Boolean,
    amplitude: Float,
    elapsedMs: Long,
    page: Int,
    cameraReady: Boolean,
    initialCaptureReady: Boolean,
    captureInProgress: Boolean,
    pageJustChanged: Boolean,
    showQuickCorrection: Boolean,
    orientationGuardActive: Boolean,
    canCorrectPage: Boolean,
    captureMessage: String,
    onStart: () -> Unit,
    onMarkPage: () -> Unit,
    onCorrectPage: () -> Unit,
    onPause: () -> Unit,
    onComplete: () -> Unit,
    compact: Boolean,
    rail: Boolean,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val statusText = when {
        captureInProgress -> "正在保存新书面……"
        orientationGuardActive -> "正在适配方向 · 自动翻页稍后恢复"
        pageJustChanged -> "✓ 已进入书面 $page"
        isMoving -> "检测到翻页 · 请等待画面稳定"
        isRecording -> "书面 $page · 等待翻页"
        else -> "让书面完整清晰地进入取景框"
    }
    val showError = captureMessage.contains("失败") || captureMessage.contains("无法") ||
        captureMessage.contains("不可用") || captureMessage.contains("没有可用")
    val railTextColor = if (rail) Color.White else WarmBrown
    Surface(
        modifier,
        color = if (rail) Ink.copy(alpha = .78f) else Paper.copy(alpha = .97f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 9.dp else 12.dp,
            ),
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.Center),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (pageJustChanged && !rail) WarmMoss else railTextColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isRecording && canCorrectPage) {
                        Box {
                            AppIconButton(
                                AppIcons.More,
                                "更多录制操作",
                                { menuExpanded = true },
                                Modifier.size(42.dp),
                                tint = WarmBrown,
                            )
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("更正上一次翻页", color = Coral)
                                            Text("录音会归回前一书面", style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    onClick = { menuExpanded = false; onCorrectPage() },
                                )
                            }
                        }
                    }
                }
                if (isRecording) {
                    if (!compact) {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 7.dp).height(5.dp).clip(CircleShape)
                                .background(Color(0xFFE2D8C9)),
                        ) {
                            Box(Modifier.fillMaxWidth(amplitude.coerceIn(.04f, 1f)).fillMaxHeight().background(Coral, CircleShape))
                        }
                    }
                    Button(
                        onClick = onMarkPage,
                        enabled = !captureInProgress && !pageJustChanged,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmCoral,
                            disabledContainerColor = if (pageJustChanged) WarmMoss else WarmCoral.copy(alpha = .45f),
                            disabledContentColor = Color.White,
                        ),
                    ) {
                        Text(
                            when {
                                captureInProgress -> "正在保存新书面……"
                                pageJustChanged -> "✓ 已进入书面 $page"
                                initialCaptureReady -> "漏记翻页？点这里补记"
                                else -> "重试保存第一个书面"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (showQuickCorrection) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("刚才的翻页标记不对？", color = WarmBrown.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onCorrectPage) { Text("更正", color = Coral, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = if (showQuickCorrection) 0.dp else 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onPause,
                            enabled = !captureInProgress,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(15.dp),
                        ) { Text("暂停", color = railTextColor) }
                        Button(
                            onClick = onComplete,
                            enabled = initialCaptureReady && !captureInProgress && elapsedMs >= 800L,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarmMoss),
                        ) { Text("结束录制") }
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = cameraReady,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarmCoral),
                    ) { Text("开始录音", fontWeight = FontWeight.Bold) }
                }
                if (!compact && showError) {
                    Text(
                        captureMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Coral,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

internal enum class RecordingPhase { READY, RECORDING, FINALIZING, PAUSED, SAVE_FAILED }

@Composable
internal fun ManualRecordingActive(
    page: Int,
    elapsedMs: Long,
    amplitude: Float,
    pageJustChanged: Boolean,
    canUndo: Boolean,
    onBack: () -> Unit,
    onNextPage: () -> Unit,
    onUndo: () -> Unit,
    onPause: () -> Unit,
    onComplete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(WarmPaper)) {
        Surface(color = Ink) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(AppIcons.Back, "保存并返回书架", onBack, Modifier.size(48.dp), tint = Color.White)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("手动翻页录制", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("不使用摄像头", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onComplete) { Text("结束录制", color = Color.White, fontWeight = FontWeight.Bold) }
                if (canUndo) Box {
                    AppIconButton(AppIcons.More, "更多录制操作", { menuExpanded = true }, tint = Color.White)
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("更正上一次翻页", color = Coral)
                                    Text("会移除刚才的书面分界", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onUndo()
                            },
                        )
                    }
                }
            }
        }
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().weight(1f)
                .align(Alignment.CenterHorizontally).padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(color = Coral.copy(alpha = .12f), shape = CircleShape) {
                Text("●  正在录制", color = Coral, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Text("书面 $page", color = WarmBrown, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            Text("本次录制 ${formatDuration(elapsedMs)}", color = WarmBrown.copy(alpha = .62f), modifier = Modifier.padding(top = 5.dp))
            Box(
                Modifier.fillMaxWidth().padding(top = 24.dp).height(10.dp).clip(CircleShape).background(Color(0xFFE2D8C9)),
            ) {
                Box(
                    Modifier.fillMaxWidth(amplitude.coerceIn(.04f, 1f)).fillMaxHeight()
                        .background(Coral, CircleShape),
                )
            }
            Text(
                if (pageJustChanged) "✓ 已进入书面 $page" else "正在录制书面 $page",
                color = if (pageJustChanged) WarmMoss else WarmBrown.copy(alpha = .58f),
                fontWeight = if (pageJustChanged) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(top = 15.dp),
            )
        }
        Surface(color = Color.White, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 18.dp)) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("读完当前书面并翻页后", color = WarmBrown.copy(alpha = .68f))
                    Button(
                        onClick = onNextPage,
                        enabled = !pageJustChanged,
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(68.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pageJustChanged) WarmMoss else WarmCoral,
                            disabledContainerColor = WarmMoss,
                            disabledContentColor = Color.White,
                        ),
                    ) {
                        Text(
                            if (pageJustChanged) "✓ 已进入书面 $page" else "进入书面 ${page + 1}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(onClick = onPause, modifier = Modifier.padding(top = 5.dp).height(48.dp)) {
                        AppIcon(AppIcons.Pause, null, tint = WarmBrown, size = 21.dp)
                        Text("暂停", color = WarmBrown, modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManualRecordingSaving() {
    Box(Modifier.fillMaxSize().background(WarmPaper).statusBarsPadding().navigationBarsPadding().padding(32.dp)) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("正在安全保存录音……", style = MaterialTheme.typography.headlineSmall, color = WarmBrown, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).height(7.dp).clip(CircleShape),
                color = WarmMoss,
                trackColor = WarmMoss.copy(alpha = .16f),
            )
            Text("保存完成前请不要退出", color = WarmBrown.copy(alpha = .58f), modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
internal fun ManualRecordingPaused(
    pageCount: Int,
    durationMs: Long,
    mode: RecordingMode,
    message: String,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onReturnToLibrary: () -> Unit,
) {
    val hasRecording = durationMs > 0L
    val needsAttention = message.contains("未保存") || message.contains("还没有")
    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            WarmTopBar(if (hasRecording) "录制已暂停" else "准备录音", onReturnToLibrary)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).widthIn(max = 560.dp)
                    .fillMaxWidth().align(Alignment.CenterHorizontally).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WarmIllustration(R.drawable.illustration_recording_pause, "休息一下，稍后继续", Modifier.fillMaxWidth().height(160.dp), ContentScale.Fit)
                Text(if (hasRecording) "声音已保存，放心休息" else "准备好了就开始读吧", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (hasRecording) Text("$pageCount 个书面 · ${formatDuration(durationMs)}", color = Moss)
                Text(
                    if (hasRecording) "继续录制会接在当前书面后面；返回书架也可以稍后继续。"
                    else if (mode == RecordingMode.MANUAL) "正常朗读，每次翻页后点“下一书面”。只录声音，不开启摄像头。"
                    else "对准绘本后开始录音，翻页会自动记录。",
                    color = Moss,
                )
                if (needsAttention) Text(message, color = MaterialTheme.colorScheme.error)
            }
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WarmPrimaryButton(if (hasRecording) "继续录制" else "开始录音", onResume, Modifier.fillMaxWidth(), AppIcons.Play)
                if (hasRecording) OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("录完了，去试听") }
                TextButton(onClick = onReturnToLibrary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (hasRecording) "返回书架" else "暂不录制") }
            }
        }
    }
}

@Composable
internal fun ManualRecordingSaveFailed(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(WarmPaper).statusBarsPadding().navigationBarsPadding().padding(28.dp)) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("录音尚未保存", style = MaterialTheme.typography.headlineSmall, color = Coral, fontWeight = FontWeight.Bold)
            Text(message, color = WarmBrown.copy(alpha = .7f), modifier = Modifier.padding(top = 14.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = RoundedCornerShape(18.dp),
            ) { Text("重试保存") }
            Text("保存成功前不能离开，以免这次录音丢失。", color = Coral, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
