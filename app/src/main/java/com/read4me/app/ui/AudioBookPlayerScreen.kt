package com.read4me.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.read4me.app.audio.AudioBookPlan
import com.read4me.app.audio.AudioBookPlaybackService
import com.read4me.app.model.StoryBook
import kotlinx.coroutines.delay

@Composable
fun AudioBookPlayerScreen(book: StoryBook, onBack: () -> Unit) {
    val context = LocalContext.current
    val plan = remember(book) { AudioBookPlan.from(book) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var queueIndex by remember { mutableStateOf(0) }
    var itemPosition by remember { mutableLongStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences(AudioBookPlaybackService.PREFS, Context.MODE_PRIVATE) }
    var sleepMode by remember { mutableStateOf(prefs.getString(AudioBookPlaybackService.KEY_TIMER_MODE, null)) }

    LaunchedEffect(book.id) {
        context.startService(Intent(context, AudioBookPlaybackService::class.java).apply {
            action = AudioBookPlaybackService.ACTION_PLAY_BOOK
            putExtra(AudioBookPlaybackService.EXTRA_BOOK_ID, book.id)
        })
    }
    DisposableEffect(context) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, AudioBookPlaybackService::class.java))).buildAsync()
        var disposed = false
        future.addListener({
            runCatching { future.get() }.onSuccess {
                if (disposed) it.release() else controller = it
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            controller = null
            if (future.isDone) runCatching { future.get().release() } else future.cancel(true)
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                queueIndex = it.currentMediaItemIndex.coerceAtLeast(0)
                itemPosition = it.currentPosition.coerceAtLeast(0)
                playing = it.isPlaying
            }
            sleepMode = prefs.getString(AudioBookPlaybackService.KEY_TIMER_MODE, null)
            delay(500)
        }
    }

    BackHandler(onBack = onBack)

    val entry = plan.entries.getOrNull(queueIndex)
    val wholePosition = (entry?.bookOffsetMs ?: 0) + itemPosition
    val progress = if (plan.durationMs > 0) wholePosition.toFloat() / plan.durationMs else 0f
    fun timer(mode: String) {
        sleepMode = mode.takeUnless { it == AudioBookPlaybackService.SLEEP_CANCEL }
        context.startService(Intent(context, AudioBookPlaybackService::class.java).apply {
            action = AudioBookPlaybackService.ACTION_SLEEP
            putExtra(AudioBookPlaybackService.EXTRA_SLEEP_MODE, mode)
        })
    }

    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = androidx.compose.ui.graphics.Color.White, shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("← 返回", color = Moss, fontWeight = FontWeight.Bold) }
                    Text(book.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text("正在陪你读", color = WarmCoral, fontWeight = FontWeight.Bold)
                Text(book.title, style = MaterialTheme.typography.headlineLarge, color = Ink, maxLines = 2)
                Text("书面 ${(entry?.spreadIndex ?: 0) + 1} / ${plan.spreadCount}", style = MaterialTheme.typography.titleLarge, color = Moss)
                WarmCard(Modifier.fillMaxWidth()) {
                    StoryImage(
                        file = book.spreads.getOrNull(entry?.spreadIndex ?: 0)?.imageFile,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.32f).padding(10.dp),
                    )
                }
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = WarmCoral)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatAudioTime(wholePosition), color = Ink.copy(alpha = .65f))
                    Text(formatAudioTime(plan.durationMs), color = Ink.copy(alpha = .65f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { controller?.seekToPreviousMediaItem() }, shape = CircleShape, contentPadding = PaddingValues(16.dp)) { Text("上一面") }
                    Button(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }, shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Coral), modifier = Modifier.height(64.dp)) {
                        Text(if (playing) "暂停" else "播放", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { controller?.seekToNextMediaItem() }, shape = CircleShape, contentPadding = PaddingValues(16.dp)) { Text("下一面") }
                }
                OutlinedButton(onClick = { controller?.seekTo(0, 0); controller?.play() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text("从头播放")
                }
                Text("睡眠定时${sleepMode?.let { " · ${sleepLabel(it)}" } ?: ""}", style = MaterialTheme.typography.titleLarge, color = Ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("本面" to AudioBookPlaybackService.SLEEP_SPREAD, "整本" to AudioBookPlaybackService.SLEEP_BOOK, "10 分" to "10").forEach { (label, mode) ->
                        OutlinedButton(onClick = { timer(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("20 分" to "20", "30 分" to "30", "取消" to AudioBookPlaybackService.SLEEP_CANCEL).forEach { (label, mode) ->
                        OutlinedButton(onClick = { timer(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                }
            }
        }
    }
}

private fun sleepLabel(mode: String) = when (mode) {
    AudioBookPlaybackService.SLEEP_SPREAD -> "当前书面结束"
    AudioBookPlaybackService.SLEEP_BOOK -> "整本结束"
    else -> "$mode 分钟"
}

private fun formatAudioTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
