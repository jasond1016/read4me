package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.read4me.app.audio.AudioSegmentPlayer
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StoryBookEditor
import com.read4me.app.model.StorySpread

enum class ReferenceCapturePurpose { ADD_REFERENCE, REPLACE_DISPLAY_IMAGE }

@Composable
fun SpreadPreviewScreen(
    book: StoryBook,
    initialSpreadId: String,
    repository: StoryRepository,
    onBack: () -> Unit,
    onEditNarration: (StoryBook, String) -> Unit,
    onCaptureReference: (StoryBook, String, ReferenceCapturePurpose) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { AudioSegmentPlayer(context.applicationContext) }
    var currentBook by remember(book.id) { mutableStateOf(book) }
    var currentId by remember(book.id) {
        mutableStateOf(initialSpreadId.takeIf { id -> book.spreads.any { it.spreadId == id } }
            ?: book.spreads.firstOrNull()?.spreadId.orEmpty())
    }
    var playing by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var manageReferences by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    val spreads = currentBook.spreads
    val index = spreads.indexOfFirst { it.spreadId == currentId }.coerceAtLeast(0)
    val spread = spreads.getOrNull(index)

    fun stop() {
        player.stop()
        playing = false
    }
    fun select(id: String) {
        stop()
        currentId = id
        menuExpanded = false
    }
    fun update(next: StoryBook) {
        stop()
        repository.save(next)
        currentBook = next
    }
    fun leave() {
        stop()
        onBack()
    }

    DisposableEffect(Unit) { onDispose(player::stop) }
    BackHandler {
        when {
            fullscreen -> fullscreen = false
            manageReferences -> manageReferences = false
            menuExpanded -> menuExpanded = false
            else -> leave()
        }
    }

    if (fullscreen && spread != null) {
        FullscreenPreview(spread, index, spreads.size) { fullscreen = false }
        return
    }

    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        Column(Modifier.fillMaxSize()) {
            PreviewTopBar(
                title = currentBook.title,
                page = spread?.ordinal,
                onBack = ::leave,
                expanded = menuExpanded,
                onExpand = { menuExpanded = true },
                onDismiss = { menuExpanded = false },
                onEditNarration = {
                    menuExpanded = false
                    stop()
                    spread?.let { onEditNarration(currentBook, it.spreadId) }
                },
                onReplaceDisplayImage = {
                    menuExpanded = false
                    stop()
                    spread?.let { onCaptureReference(currentBook, it.spreadId, ReferenceCapturePurpose.REPLACE_DISPLAY_IMAGE) }
                },
                onAddReference = {
                    menuExpanded = false
                    stop()
                    spread?.let { onCaptureReference(currentBook, it.spreadId, ReferenceCapturePurpose.ADD_REFERENCE) }
                },
                onManageReferences = {
                    menuExpanded = false
                    manageReferences = true
                },
            )
            if (spread == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("这本书还没有可查看的书面", color = WarmBrown.copy(alpha = .65f))
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
                    val expandedLayout = maxWidth >= 700.dp
                    if (expandedLayout) {
                        Row(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            SpreadRail(spreads, currentId, ::select, Modifier.width(180.dp).fillMaxHeight())
                            PreviewContent(
                                spread, index, spreads.size, playing,
                                previous = { spreads.getOrNull(index - 1)?.let { select(it.spreadId) } },
                                next = { spreads.getOrNull(index + 1)?.let { select(it.spreadId) } },
                                toggle = {
                                    if (playing) stop()
                                    else {
                                        val started = player.play(
                                            spread.effectiveSegments,
                                            onError = { playing = false },
                                            onFinished = { playing = false },
                                        )
                                        playing = started
                                    }
                                },
                                fullscreen = { fullscreen = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        PreviewContent(
                            spread, index, spreads.size, playing,
                            previous = { spreads.getOrNull(index - 1)?.let { select(it.spreadId) } },
                            next = { spreads.getOrNull(index + 1)?.let { select(it.spreadId) } },
                            toggle = {
                                if (playing) stop()
                                else playing = player.play(
                                    spread.effectiveSegments,
                                    onError = { playing = false },
                                    onFinished = { playing = false },
                                )
                            },
                            fullscreen = { fullscreen = true },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }
    }

    if (manageReferences && spread != null) {
        ReferenceManagerDialog(
            spread = spread,
            onDismiss = { manageReferences = false },
            onAdd = {
                manageReferences = false
                onCaptureReference(currentBook, spread.spreadId, ReferenceCapturePurpose.ADD_REFERENCE)
            },
            onPrimary = { update(StoryBookEditor.setPrimaryReference(currentBook, spread.spreadId, it)) },
            onDelete = { update(StoryBookEditor.deleteReference(currentBook, spread.spreadId, it)) },
        )
    }
}

@Composable
private fun PreviewTopBar(
    title: String,
    page: Int?,
    onBack: () -> Unit,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onEditNarration: () -> Unit,
    onReplaceDisplayImage: () -> Unit,
    onAddReference: () -> Unit,
    onManageReferences: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(AppIcons.Back, "返回书籍详情", onBack, Modifier.size(52.dp), tint = WarmBrown)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = WarmBrown)
                if (page != null) Text("第 $page 页", style = MaterialTheme.typography.bodySmall, color = WarmBrown.copy(alpha = .62f))
            }
            Box {
                AppIconButton(AppIcons.More, "更多书面操作", onExpand, tint = WarmBrown)
                DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
                    DropdownMenuItem(text = { Text("编辑本页旁白") }, onClick = onEditNarration)
                    DropdownMenuItem(text = { Text("更换展示图片") }, onClick = onReplaceDisplayImage)
                    DropdownMenuItem(text = { Text("添加识别参考图") }, onClick = onAddReference)
                    DropdownMenuItem(text = { Text("管理书面图片") }, onClick = onManageReferences)
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    spread: StorySpread,
    index: Int,
    count: Int,
    playing: Boolean,
    previous: () -> Unit,
    next: () -> Unit,
    toggle: () -> Unit,
    fullscreen: () -> Unit,
    modifier: Modifier,
) {
    val playable = spread.effectiveSegments.isNotEmpty() && spread.effectiveSegments.all { it.file.isFile }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = SoftWhite,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 5.dp,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Box(Modifier.fillMaxSize().clickable(onClick = fullscreen)) {
                StoryImage(spread.imageFile, Modifier.fillMaxSize(), ContentScale.Fit)
                Surface(
                    color = SoftWhite.copy(alpha = .9f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(46.dp),
                ) { Box(contentAlignment = Alignment.Center) { AppIcon(AppIcons.Fullscreen, "全屏查看", tint = WarmBrown) } }
            }
        }
        Text("书面 ${index + 1}/$count  ·  ${formatDuration(spread.durationMs)}", color = WarmBrown.copy(alpha = .68f), modifier = Modifier.padding(top = 14.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(enabled = index > 0, onClick = previous) {
                AppIcon(AppIcons.Back, null)
                Text("上一书面")
            }
            Button(
                enabled = playable,
                onClick = toggle,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = WarmCoral),
                modifier = Modifier.size(64.dp),
            ) { AppIcon(if (playing) AppIcons.Pause else AppIcons.Play, if (playing) "暂停" else "播放", tint = Color.White, size = 30.dp) }
            TextButton(enabled = index < count - 1, onClick = next) {
                Text("下一书面")
                AppIcon(AppIcons.Forward, null)
            }
        }
    }
}

@Composable
private fun SpreadRail(spreads: List<StorySpread>, selectedId: String, select: (String) -> Unit, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(spreads, key = { it.spreadId }) { spread ->
            Surface(
                color = if (spread.spreadId == selectedId) WarmCoral.copy(alpha = .13f) else Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { select(spread.spreadId) },
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StoryImage(spread.imageFile, Modifier.size(72.dp, 54.dp), ContentScale.Crop)
                    Text("第 ${spread.ordinal} 页", color = WarmBrown, modifier = Modifier.padding(start = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun FullscreenPreview(spread: StorySpread, index: Int, count: Int, onExit: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize().clickable(onClick = onExit)) {
            StoryImage(spread.imageFile, Modifier.fillMaxSize(), ContentScale.Fit)
            AppIconButton(AppIcons.Back, "退出全屏", onExit, Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart), tint = Color.White)
            Text(
                "${index + 1}/$count",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
            )
        }
    }
}

@Composable
private fun ReferenceManagerDialog(
    spread: StorySpread,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onPrimary: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理书面图片") },
        text = {
            Column {
                Text("第一张是阅读时显示的主图；其他照片只用于辅助识别。", color = WarmBrown.copy(alpha = .68f))
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(spread.references, key = { it.referenceId }) { reference ->
                        val primary = reference.referenceId == spread.references.firstOrNull()?.referenceId
                        Surface(color = if (primary) WarmMoss.copy(alpha = .12f) else WarmPaper, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StoryImage(reference.file, Modifier.size(90.dp, 68.dp), ContentScale.Crop)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(if (primary) "展示主图" else "识别参考图", fontWeight = FontWeight.Bold, color = WarmBrown)
                                    if (!primary) TextButton(onClick = { onPrimary(reference.referenceId) }) { Text("设为展示主图") }
                                }
                                if (spread.references.size > 1) TextButton(onClick = { onDelete(reference.referenceId) }) { Text("删除") }
                            }
                        }
                    }
                }
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("添加识别参考图") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
