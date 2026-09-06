package com.read4me.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.foundation.horizontalScroll
import com.read4me.app.data.AndroidMediaProbe
import com.read4me.app.model.hasUsableAudio
import com.read4me.app.model.hasUsablePhoto
import com.read4me.app.model.readiness
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.read4me.app.audio.AudioBookPlan
import com.read4me.app.audio.AudioBookPlaybackService
import com.read4me.app.model.StoryBook
import kotlinx.coroutines.delay

@Composable
fun AudioBookPlayerScreen(
    book: StoryBook,
    pageListState: SaveableStateHolder,
    initiallyShowPageList: Boolean = false,
    playWhenReady: Boolean = true,
    pageListBackToCaller: Boolean = false,
    onEditSpread: (String, Boolean) -> Unit = { _, _ -> },
    onRepairSpread: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val plan = remember(book) { AudioBookPlan.from(book) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var queueIndex by remember { mutableStateOf(0) }
    var itemPosition by remember { mutableLongStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var expanded by remember { mutableStateOf(false) }
    var showPages by rememberSaveable(book.id) { mutableStateOf(initiallyShowPageList) }
    var showImage by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences(AudioBookPlaybackService.PREFS, Context.MODE_PRIVATE) }
    var sleepMode by remember { mutableStateOf(prefs.getString(AudioBookPlaybackService.KEY_TIMER_MODE, null)) }

    LaunchedEffect(book.id, playWhenReady) {
        context.startService(Intent(context, AudioBookPlaybackService::class.java).apply {
            action = AudioBookPlaybackService.ACTION_PLAY_BOOK
            putExtra(AudioBookPlaybackService.EXTRA_BOOK_ID, book.id)
            putExtra(AudioBookPlaybackService.EXTRA_PLAY_WHEN_READY, playWhenReady)
        })
    }
    DisposableEffect(context) {
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, AudioBookPlaybackService::class.java)),
        ).buildAsync()
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
                speed = it.playbackParameters.speed
            }
            sleepMode = prefs.getString(AudioBookPlaybackService.KEY_TIMER_MODE, null)
            delay(300)
        }
    }
    BackHandler {
        when {
            showImage -> showImage = false
            showPages && pageListBackToCaller -> onBack()
            showPages -> showPages = false
            else -> onBack()
        }
    }

    val entry = plan.entries.getOrNull(queueIndex)
    val spreadIndex = entry?.spreadIndex ?: 0
    val spread = book.spreads.getOrNull(spreadIndex)
    val wholePosition = ((entry?.bookOffsetMs ?: 0) + itemPosition).coerceIn(0, plan.durationMs)
    var seekPreview by remember(book.id) { mutableStateOf<Float?>(null) }
    val progress = if (plan.durationMs > 0) wholePosition.toFloat() / plan.durationMs else 0f

    fun seekBook(positionMs: Long) {
        val target = positionMs.coerceIn(0, plan.durationMs)
        val targetEntry = plan.entries.lastOrNull { it.bookOffsetMs <= target } ?: plan.entries.firstOrNull() ?: return
        controller?.seekTo(targetEntry.queueIndex, (target - targetEntry.bookOffsetMs).coerceIn(0, targetEntry.durationMs))
    }
    fun timer(mode: String) {
        sleepMode = mode.takeUnless { it == AudioBookPlaybackService.SLEEP_CANCEL }
        context.startService(Intent(context, AudioBookPlaybackService::class.java).apply {
            action = AudioBookPlaybackService.ACTION_SLEEP
            putExtra(AudioBookPlaybackService.EXTRA_SLEEP_MODE, mode)
        })
    }

    if (showPages) {
        pageListState.SaveableStateProvider(book.id) {
            PageListScreen(
                book = book,
                selected = spreadIndex,
                playing = playing,
                onBack = {
                    if (pageListBackToCaller) onBack() else showPages = false
                },
                onRepair = { spreadId, audio ->
                    controller?.pause()
                    onRepairSpread(spreadId, audio)
                },
                onEdit = { spreadId ->
                    val wasPlaying = controller?.isPlaying == true
                    controller?.pause()
                    onEditSpread(spreadId, wasPlaying)
                },
            ) { index ->
                controller?.let { player ->
                    if (index == spreadIndex) {
                        if (player.isPlaying) player.pause() else player.play()
                    } else {
                        plan.entries.firstOrNull { it.spreadIndex == index }?.let {
                            player.seekTo(it.queueIndex, 0)
                            player.play()
                        }
                    }
                }
            }
        }
        return
    }
    if (showImage) {
        FullscreenSpread(
            spreadImage = spread?.imageFile,
            page = spreadIndex + 1,
            pageCount = plan.spreadCount,
            position = wholePosition,
            duration = plan.durationMs,
            playing = playing,
            onExit = { showImage = false },
            onSeek = ::seekBook,
            onToggle = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
        )
        return
    }

    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerTopBar(book.title, onBack) { expanded = !expanded }
            Column(
                Modifier.weight(1f).widthIn(max = 760.dp).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = Honey.copy(alpha = .13f), shape = CircleShape) {
                    Text("●  家人的声音", color = Ink.copy(.72f), modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = SoftWhite,
                    shadowElevation = 8.dp,
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.8f).clickable { showImage = true }) {
                        StoryImage(spread?.imageFile, Modifier.fillMaxSize())
                        Surface(
                            color = SoftWhite.copy(alpha = .9f), shape = CircleShape,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(48.dp),
                        ) { Box(contentAlignment = Alignment.Center) { AppIcon(AppIcons.Fullscreen, "全屏查看", tint = Ink) } }
                    }
                }
                Text(
                    "书面 ${spreadIndex + 1} / ${plan.spreadCount}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Moss,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Slider(
                    value = seekPreview ?: progress.coerceIn(0f, 1f),
                    onValueChange = { seekPreview = it },
                    onValueChangeFinished = {
                        seekPreview?.let { seekBook((it * plan.durationMs).toLong()) }
                        seekPreview = null
                    },
                    enabled = plan.durationMs > 0,
                    colors = SliderDefaults.colors(thumbColor = Moss, activeTrackColor = Honey, inactiveTrackColor = WarmLine),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatAudioTime(seekPreview?.let { (it * plan.durationMs).toLong() } ?: wholePosition), color = Moss)
                    Text(formatAudioTime(plan.durationMs), color = Moss)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundPlayerButton(AppIcons.Replay, "后退 15 秒", 52, { seekBook(wholePosition - 15_000) }, "15")
                    RoundPlayerButton(AppIcons.Previous, "上一书面", 52, { controller?.seekToPreviousMediaItem() })
                    Button(
                        onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                        modifier = Modifier.size(68.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Color.White),
                    ) { AppIcon(if (playing) AppIcons.Pause else AppIcons.Play, if (playing) "暂停" else "播放", tint = Color.White, size = 36.dp) }
                    RoundPlayerButton(AppIcons.Next, "下一书面", 52, { controller?.seekToNextMediaItem() })
                    RoundPlayerButton(AppIcons.ForwardMedia, "前进 15 秒", 52, { seekBook(wholePosition + 15_000) }, "15")
                }

                PlayerPanel(
                    expanded = expanded,
                    speed = speed,
                    sleepMode = sleepMode,
                    onToggle = { expanded = !expanded },
                    onRestart = { seekBook(0); controller?.play() },
                    onPages = { showPages = true },
                    onTimer = { expanded = true },
                    onSpeed = {
                        val next = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> .75f; else -> 1f }
                        controller?.setPlaybackSpeed(next)
                    },
                    setTimer = ::timer,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }

}

@Composable
private fun PlayerTopBar(title: String, onBack: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(AppIcons.Back, "返回", onBack, Modifier.size(52.dp), tint = Ink)
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        AppIconButton(AppIcons.More, "更多播放控制", onMore, Modifier.size(52.dp), tint = Ink.copy(.7f))
    }
}

@Composable
private fun RoundPlayerButton(icon: Int, description: String, size: Int, onClick: () -> Unit, badge: String? = null) {
    Surface(
        modifier = Modifier.size(size.dp).clickable(onClick = onClick),
        shape = CircleShape, color = SoftWhite, shadowElevation = 3.dp,
    ) { Box(contentAlignment = Alignment.Center) {
        AppIcon(icon, description, tint = Ink, size = if (badge == null) 24.dp else 34.dp)
        badge?.let {
            Text(
                it,
                color = Ink,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(SoftWhite, CircleShape).padding(horizontal = 2.dp, vertical = 1.dp),
            )
        }
    } }
}

@Composable
private fun PlayerPanel(
    expanded: Boolean,
    speed: Float,
    sleepMode: String?,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onPages: () -> Unit,
    onTimer: () -> Unit,
    onSpeed: () -> Unit,
    setTimer: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(26.dp), color = SoftWhite, shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelAction(AppIcons.List, "逐页试听", onPages, Modifier.weight(1f))
                PanelAction(if (expanded) AppIcons.ExpandLess else AppIcons.More, if (expanded) "收起设置" else "播放设置", onToggle, Modifier.weight(1f))
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelAction(AppIcons.Restart, "从头播放", onRestart, Modifier.weight(1f))
                    PanelAction(AppIcons.Timer, sleepMode?.let(::sleepLabel) ?: "定时关闭", onTimer, Modifier.weight(1f))
                    PanelAction(AppIcons.Speed, "${speedLabel(speed)}x 倍速", onSpeed, Modifier.weight(1f))
                }
                Text("睡眠定时", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp))
                listOf(
                    listOf("本面播放完" to AudioBookPlaybackService.SLEEP_SPREAD, "整本播放完" to AudioBookPlaybackService.SLEEP_BOOK, "10 分钟" to "10"),
                    listOf("20 分钟" to "20", "30 分钟" to "30", "取消" to AudioBookPlaybackService.SLEEP_CANCEL),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, mode) ->
                            OutlinedButton(onClick = { setTimer(mode) }, modifier = Modifier.weight(1f), shape = CircleShape,
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true)) { Text(label, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelAction(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = WarmPaper) {
        Column(Modifier.padding(vertical = 11.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(icon, null, tint = Ink)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PageListScreen(
    book: StoryBook,
    selected: Int,
    playing: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onRepair: (String, Boolean) -> Unit,
    onSelect: (Int) -> Unit,
) {
    var repairsOnly by rememberSaveable(book.id) { mutableStateOf(false) }
    val repairs = book.spreads.filter { !it.hasUsableAudio || !it.hasUsablePhoto }
    val visible = if (repairsOnly) repairs else book.spreads
    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            WarmTopBar("逐页试听与修复", onBack)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !repairsOnly, onClick = { repairsOnly = false }, label = { Text("全部 ${book.spreads.size}") })
                FilterChip(selected = repairsOnly, onClick = { repairsOnly = true }, label = { Text("待处理 ${repairs.size}") })
            }
            Text("点书面试听；照片可以稍后补拍。", color = Moss, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visible.isEmpty()) item {
                    Text(if (repairsOnly) "照片和录音都齐了，放心听吧。" else "还没有书面，先录下第一段声音吧。", modifier = Modifier.padding(20.dp), color = Moss)
                }
                items(visible, key = { it.spreadId }) { spread ->
                    val index = spread.ordinal - 1
                    val playable = spread.hasUsableAudio
                    val needsPhoto = !spread.hasUsablePhoto
                    var menu by remember(spread.spreadId) { mutableStateOf(false) }
                    WarmCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth().clickable(enabled = playable) { onSelect(index) }.heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                                StoryImage(spread.references.firstOrNull { AndroidMediaProbe.canReadPhoto(it.file) }?.file, Modifier.size(width = 72.dp, height = 64.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text("书面 ${spread.ordinal}", fontWeight = FontWeight.Bold)
                                    Text(when {
                                        !playable -> "需要补录声音"
                                        index == selected && playing -> "正在试听 · ${formatAudioTime(spread.durationMs)}"
                                        else -> formatAudioTime(spread.durationMs)
                                    }, color = Moss, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (playable) AppIcon(if (index == selected && playing) AppIcons.Pause else AppIcons.Play, if (index == selected && playing) "暂停" else "试听", tint = Moss)
                                Box {
                                    AppIconButton(AppIcons.More, "书面 ${spread.ordinal} 更多操作", { menu = true }, Modifier.size(48.dp), tint = Ink)
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("调整录音或重录") }, onClick = { menu = false; onEdit(spread.spreadId) })
                                    }
                                }
                            }
                            if (!playable || needsPhoto) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (!playable) "补录后即可播放" else "不影响整本播放", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Moss)
                                TextButton(onClick = { onRepair(spread.spreadId, !playable) }) { Text(if (!playable) "补录声音" else "补拍照片") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenSpread(
    spreadImage: java.io.File?, page: Int, pageCount: Int, position: Long, duration: Long,
    playing: Boolean, onExit: () -> Unit, onSeek: (Long) -> Unit, onToggle: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            StoryImage(spreadImage, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            Surface(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), color = Color.Black.copy(.55f), shape = CircleShape) {
                AppIconButton(AppIcons.Back, "退出全屏", onExit, Modifier.size(48.dp), tint = Color.White)
            }
            Surface(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp), color = Color.Black.copy(.55f), shape = CircleShape) {
                Text("$page / $pageCount", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(.68f)).navigationBarsPadding().padding(18.dp)) {
                Slider(value = if (duration > 0) position.toFloat() / duration else 0f, onValueChange = { onSeek((it * duration).toLong()) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatAudioTime(position), color = Color.White); Text(formatAudioTime(duration), color = Color.White) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundPlayerButton(AppIcons.Replay, "后退 15 秒", 52, { onSeek(position - 15_000) }, "15")
                    Button(onClick = onToggle, modifier = Modifier.size(68.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Honey)) { AppIcon(if (playing) AppIcons.Pause else AppIcons.Play, if (playing) "暂停" else "播放", tint = Color.White, size = 36.dp) }
                    RoundPlayerButton(AppIcons.ForwardMedia, "前进 15 秒", 52, { onSeek(position + 15_000) }, "15")
                }
            }
        }
    }
}

private fun sleepLabel(mode: String) = when (mode) {
    AudioBookPlaybackService.SLEEP_SPREAD -> "本面结束"
    AudioBookPlaybackService.SLEEP_BOOK -> "整本结束"
    else -> "$mode 分钟"
}

private fun speedLabel(speed: Float): String = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

private fun formatAudioTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
