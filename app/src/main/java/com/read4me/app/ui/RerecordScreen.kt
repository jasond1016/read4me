package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.read4me.app.audio.StoryAudioRecorder
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StoryBookEditor
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

@Composable
fun RerecordScreen(
    book: StoryBook,
    spreadId: String,
    repository: StoryRepository,
    onCancel: () -> Unit,
    onFinished: (StoryBook) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember { StoryAudioRecorder(context) }
    val spread = book.spreads.firstOrNull { it.spreadId == spreadId }
    val ordinal = spread?.ordinal ?: 0
    val targetFile = remember(book.id, spreadId) { repository.overrideAudioFile(book, spreadId) }
    val pendingFile = remember(targetFile) { File(targetFile.parentFile, "${targetFile.name}.${UUID.randomUUID()}.pending") }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDuration by remember { mutableLongStateOf(0L) }

    KeepScreenOn(isRecording)

    fun cancel() {
        recorder.release()
        pendingFile.delete()
        onCancel()
    }

    BackHandler(onBack = ::cancel)
    fun stopForReview() {
        if (!isRecording) return
        val stop = recorder.stop()
        isRecording = false
        pendingDuration = if (stop.successful && stop.durationMs >= 800L) stop.durationMs else 0L
        if (pendingDuration == 0L) {
            pendingFile.delete()
            message = "录音不足 0.8 秒或已中断，原旁白未更改，请重新录制"
        } else {
            message = "录音已暂停，确认后替换这一书面的旁白"
        }
    }
    val currentStop by rememberUpdatedState(::stopForReview)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) currentStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.Center).verticalScroll(rememberScrollState()),
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
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val wasRecording = isRecording
                        if (isRecording) stopForReview()
                        if (pendingDuration > 0L) {
                            runCatching {
                                targetFile.parentFile?.mkdirs()
                                pendingFile.copyTo(targetFile, overwrite = true)
                                val updated = StoryBookEditor.replaceNarration(book, spreadId, targetFile, pendingDuration)
                                repository.save(updated)
                                updated
                            }.onSuccess {
                                pendingFile.delete()
                                onFinished(it)
                            }.onFailure {
                                message = "保存失败，原旁白未更改。请释放空间后点击重试保存"
                            }
                        } else if (!wasRecording) {
                            message = null
                            pendingFile.delete()
                            runCatching { recorder.start(pendingFile) }.onSuccess {
                                elapsedMs = 0L
                                isRecording = true
                            }.onFailure {
                                pendingFile.delete()
                                message = "无法开始录音，请检查麦克风及剩余存储空间后重试"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Moss else Coral),
                ) { Text(if (isRecording) "完成并使用这段录音" else if (pendingDuration > 0L) "确认保存录音" else "● 开始重录") }
                message?.let { Text(it, color = Moss, modifier = Modifier.padding(top = 12.dp)) }
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
}
