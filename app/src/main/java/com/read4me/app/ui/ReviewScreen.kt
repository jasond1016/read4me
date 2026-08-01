package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.read4me.app.audio.AudioSegmentPlayer
import com.read4me.app.audio.PersistentWaveformCache
import com.read4me.app.audio.WaveformMath
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.NarrationTimeline
import com.read4me.app.model.StoryBook
import com.read4me.app.model.StoryBookEditor
import com.read4me.app.model.StoryEditSession
import com.read4me.app.model.StorySpread
import com.read4me.app.model.StoryStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReviewScreen(
    book: StoryBook,
    repository: StoryRepository,
    onRerecord: (StoryBook, String) -> Unit,
    onRecapture: (StoryBook, String, StoryBook?, File?) -> Unit,
    onBatchRecapture: (StoryBook, List<String>) -> Unit,
    onInsert: (StoryBook, StoryBook, String, String?, List<File>, StoryBook?, List<File>) -> Unit,
    onPlayBook: (StoryBook) -> Unit,
    initialUndo: StoryBook? = null,
    initialUndoImage: File? = null,
    initialUndoImages: List<File> = emptyList(),
    initialOrganizeDraft: StoryBook? = null,
    initialOrganizeSelected: String? = null,
    initialOrganizeCurrent: String? = null,
    initialOrganizeImages: List<File> = emptyList(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { AudioSegmentPlayer(context.applicationContext) }
    val diskCache = remember {
        PersistentWaveformCache(File(context.cacheDir, "read4me/waveforms"))
    }
    val session =
        remember(book.id, book.markers.map { it.spreadId }) { StoryEditSession(book, initialUndo) }
    var current by remember(book.id) { mutableStateOf(book) }
    var selectedId by
        remember(book.id) {
            mutableStateOf(
                initialOrganizeCurrent?.takeIf { id ->
                    initialOrganizeDraft?.markers?.any { it.spreadId == id } == true
                } ?: book.resumeSpreadId ?: book.markers.firstOrNull()?.spreadId.orEmpty(),
            )
        }
    var playingId by remember { mutableStateOf<String?>(null) }
    var playhead by remember { mutableStateOf<Long?>(null) }
    var waveform by remember { mutableStateOf<FloatArray?>(null) }
    var waveformKey by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var refs by remember { mutableStateOf(false) }
    var draft by remember(book.id) { mutableStateOf(initialOrganizeDraft) }
    var organizeBase by remember(book.id) { mutableStateOf(initialOrganizeDraft?.let { book }) }
    var organizeSelectedBase by remember(book.id) { mutableStateOf(initialOrganizeSelected) }
    var organizeImages by remember(book.id) { mutableStateOf(initialOrganizeImages) }
    var undoImages by
        remember(book.id) { mutableStateOf(listOfNotNull(initialUndoImage) + initialUndoImages) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val expanded = LocalConfiguration.current.screenWidthDp >= 700
    val selected = current.spreads.firstOrNull { it.spreadId == selectedId }
    fun stop() {
        player.stop()
        playingId = null
        playhead = null
    }
    fun choose(id: String) {
        stop()
        selectedId = id
        more = false
    }
    fun apply(next: StoryBook) {
        stop()
        current = session.apply(next, repository::save)
    }
    fun leave() {
        stop()
        onBack()
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    val selectedWaveformKey =
        selected?.sourceSegments?.joinToString("|") {
            "${it.file.absolutePath}:${it.file.length()}:${it.file.lastModified()}:${it.startMs}:${it.endMs}"
        }
    LaunchedEffect(selected?.spreadId, selectedWaveformKey) {
        val spread = selected ?: return@LaunchedEffect
        val key = requireNotNull(selectedWaveformKey)
        if (key != waveformKey) {
            waveform = null
            waveformKey = key
            val loaded =
                withContext(Dispatchers.IO) {
                    runCatching { compositeWaveform(diskCache, spread.sourceSegments) }
                        .getOrElse { FloatArray(0) }
                }
            if (waveformKey == key) waveform = loaded
        }
    }
    fun cancelOrganize() {
        organizeSelectedBase?.let { selectedId = it }
        organizeImages.forEach { image ->
            if (current.markers.none { marker -> marker.references.any { it.file == image } }) image.delete()
        }
        draft = null
        organizeBase = null
        organizeSelectedBase = null
        organizeImages = emptyList()
    }
    BackHandler {
        when {
            draft != null -> cancelOrganize()
            refs -> refs = false
            more -> more = false
            else -> leave()
        }
    }
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column {
            ReviewTopBar(
                current,
                draft != null,
                session.canUndo,
                ::leave,
                onPlayBook = {
                    stop()
                    onPlayBook(current)
                },
                batchRecapture = {
                    stop()
                    onBatchRecapture(
                        current,
                        current.markers.filter { it.references.isEmpty() }.map { it.spreadId },
                    )
                },
                undo = {
                    stop()
                    current = session.undo(repository::save)
                    undoImages.forEach { image ->
                        image.takeIf {
                            current.markers.none { marker ->
                                marker.references.any { it.file == image }
                            }
                        }
                        ?.delete()
                    }
                    undoImages = emptyList()
                    if (current.markers.none { it.spreadId == selectedId })
                        selectedId =
                            current.resumeSpreadId
                                ?: current.markers.firstOrNull()?.spreadId.orEmpty()
                },
                organize = {
                    stop()
                    organizeBase = current
                    organizeSelectedBase = selectedId
                    draft = current
                },
                cancel = ::cancelOrganize,
                done = {
                    draft?.let { staged ->
                        val base = requireNotNull(organizeBase)
                        undoImages
                            .filter { image ->
                                base.markers.none { marker -> marker.references.any { it.file == image } } &&
                                    staged.markers.none { marker -> marker.references.any { it.file == image } }
                            }
                            .forEach(File::delete)
                        val survivingInsertedImages =
                            organizeImages.filter { image ->
                                staged.markers.any { marker -> marker.references.any { it.file == image } }
                            }
                        organizeImages.filterNot(survivingInsertedImages::contains).forEach(File::delete)
                        apply(staged)
                        undoImages = survivingInsertedImages
                        if (staged.markers.none { it.spreadId == selectedId })
                            selectedId =
                                staged.resumeSpreadId
                                    ?: staged.markers.firstOrNull()?.spreadId.orEmpty()
                    }
                    draft = null
                    organizeBase = null
                    organizeSelectedBase = null
                    organizeImages = emptyList()
                },
            )
            if (draft != null)
                OrganizePane(
                    requireNotNull(draft),
                    selectedId,
                    expanded,
                    { selectedId = it },
                    { draft = it },
                    { deleteConfirm = true },
                ) {
                    stop()
                    onInsert(
                        requireNotNull(organizeBase),
                        requireNotNull(draft),
                        it,
                        organizeSelectedBase,
                        organizeImages,
                        session.undoSnapshot,
                        undoImages,
                    )
                }
            else if (selected != null)
                FocusPane(
                    current,
                    selected,
                    expanded,
                    playingId == selectedId,
                    waveform,
                    playhead,
                    more,
                    { more = !more },
                    ::choose,
                    play = { a, b ->
                        if (a == null && playingId == selectedId) stop()
                        else {
                            stop()
                            playingId = selectedId
                            playhead = a
                            val clips =
                                if (a == null || b == null) selected.effectiveSegments
                                else NarrationTimeline.clip(selected.sourceSegments, a, b)
                            if (
                                !player.play(
                                    clips,
                                    onError = { playingId = null },
                                    onFinished = {
                                        playingId = null
                                        playhead = null
                                    },
                                )
                            )
                                playingId = null
                        }
                    },
                    trim = { a, b ->
                        apply(StoryBookEditor.trimNarration(current, selectedId, a, b))
                    },
                    boundary = { id, p ->
                        apply(StoryBookEditor.moveBoundary(current, selectedId, id, p))
                    },
                    references = { refs = true },
                    rerecord = {
                        stop()
                        onRerecord(current, selectedId)
                    },
                )
            else
                Text(
                    "这本书暂时没有可编辑的书面",
                    modifier = Modifier.padding(24.dp),
                    color = Ink.copy(alpha = 0.68f),
                )
        }
    }
    if (refs && selected != null)
        ReferenceDialog(
            selected,
            { refs = false },
            {
                stop()
                onRecapture(current, selectedId, session.undoSnapshot, undoImages.singleOrNull())
            },
            { apply(StoryBookEditor.setPrimaryReference(current, selectedId, it)) },
            { apply(StoryBookEditor.deleteReference(current, selectedId, it)) },
        )
    if (deleteConfirm)
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除这个书面？") },
            text = { Text("只从整本结构中移除；完成前可以取消整理。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirm = false
                        val b = requireNotNull(draft)
                        val i = b.markers.indexOfFirst { it.spreadId == selectedId }
                        val n = StoryBookEditor.delete(b, selectedId)
                        draft = n
                        selectedId = n.markers[minOf(i, n.markers.lastIndex)].spreadId
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("再想想") } },
        )
}

@Composable
private fun ReviewTopBar(
    book: StoryBook,
    organizing: Boolean,
    canUndo: Boolean,
    back: () -> Unit,
    onPlayBook: () -> Unit,
    batchRecapture: () -> Unit,
    undo: () -> Unit,
    organize: () -> Unit,
    cancel: () -> Unit,
    done: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }
    Surface(color = if (organizing) Moss else Color.White, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (organizing) {
                TextButton(onClick = cancel) { Text("取消", color = Color.White) }
                Text(
                    "整理书面",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = done) {
                    Text("完成", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    book.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                if (
                    book.status == StoryStatus.COMPLETE &&
                        book.spreads.flatMap { it.effectiveSegments }.all { it.file.isFile }
                )
                    TextButton(onClick = onPlayBook) {
                        Text("整本播放", color = Coral, fontWeight = FontWeight.Bold)
                    }
                Box {
                    AppIconButton(AppIcons.More, "更多整理操作", { menuExpanded = true }, tint = Moss)
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (canUndo)
                            DropdownMenuItem(
                                text = { Text("撤销上一步") },
                                onClick = {
                                    menuExpanded = false
                                    undo()
                                },
                            )
                        val missingPhotos = book.markers.count { it.references.isEmpty() }
                        if (missingPhotos > 0)
                            DropdownMenuItem(
                                text = { Text("批量补拍书面照片（$missingPhotos）") },
                                onClick = {
                                    menuExpanded = false
                                    batchRecapture()
                                },
                            )
                        DropdownMenuItem(
                            text = { Text("整理整本书") },
                            onClick = {
                                menuExpanded = false
                                organize()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("返回书架") },
                            onClick = {
                                menuExpanded = false
                                back()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusPane(
    book: StoryBook,
    spread: StorySpread,
    expanded: Boolean,
    playing: Boolean,
    waveform: FloatArray?,
    playhead: Long?,
    more: Boolean,
    onMore: () -> Unit,
    choose: (String) -> Unit,
    play: (Long?, Long?) -> Unit,
    trim: (Long, Long) -> Unit,
    boundary: (String, Long) -> Unit,
    references: () -> Unit,
    rerecord: () -> Unit,
) {
    val visual: @Composable (Modifier) -> Unit = { m ->
        Column(m.padding(14.dp)) {
            StoryImage(
                spread.imageFile,
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)),
            )
            Playback(book, spread, playing, choose) { play(null, null) }
            Filmstrip(book, spread.spreadId, choose)
        }
    }
    val editor: @Composable (Modifier) -> Unit = { m ->
        AudioEditor(
            book,
            spread,
            waveform,
            playhead,
            more,
            onMore,
            { a, b -> play(a, b) },
            trim,
            boundary,
            references,
            rerecord,
            m,
        )
    }
    if (expanded)
        Row(Modifier.fillMaxSize().navigationBarsPadding()) {
            visual(Modifier.weight(.56f))
            editor(Modifier.weight(.44f).fillMaxHeight())
        }
    else
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            visual(Modifier.weight(.47f))
            editor(Modifier.weight(.53f))
        }
}

@Composable
private fun Playback(
    book: StoryBook,
    s: StorySpread,
    playing: Boolean,
    choose: (String) -> Unit,
    play: () -> Unit,
) {
    val i = book.spreads.indexOf(s)
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "书面 ${i+1}/${book.spreads.size} · ${formatDuration(s.durationMs)} · ${if(s.trimStartMs>0||s.trimEndMs<NarrationTimeline.duration(s.sourceSegments))"已修剪" else "完整范围"} · ${s.sourceSegments.size} 段录音",
            color = Ink.copy(.65f),
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            TextButton(enabled = i > 0, onClick = { choose(book.spreads[i - 1].spreadId) }) {
                AppIcon(AppIcons.Back, null)
                Text("上一书面", modifier = Modifier.padding(start = 4.dp))
            }
            Button(onClick = play, shape = CircleShape) {
                AppIcon(if (playing) AppIcons.Pause else AppIcons.Play, null, size = 28.dp)
                Text(if (playing) "停止" else "播放", modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(
                enabled = i < book.spreads.lastIndex,
                onClick = { choose(book.spreads[i + 1].spreadId) },
            ) {
                Text("下一书面", modifier = Modifier.padding(end = 4.dp))
                AppIcon(AppIcons.Forward, null)
            }
        }
    }
}

@Composable
private fun Filmstrip(book: StoryBook, id: String, choose: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        book.spreads.forEach { s ->
            Surface(
                color = if (s.spreadId == id) Honey.copy(.5f) else SoftWhite,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.size(68.dp, 54.dp).clickable { choose(s.spreadId) },
            ) {
                Box {
                    StoryImage(s.imageFile, Modifier.fillMaxSize())
                    Text(
                        "${s.ordinal}",
                        Modifier.align(Alignment.BottomEnd)
                            .background(Ink.copy(.65f))
                            .padding(horizontal = 5.dp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioEditor(
    book: StoryBook,
    s: StorySpread,
    peaks: FloatArray?,
    playhead: Long?,
    more: Boolean,
    onMore: () -> Unit,
    preview: (Long, Long) -> Unit,
    trim: (Long, Long) -> Unit,
    boundary: (String, Long) -> Unit,
    refs: () -> Unit,
    rerecord: () -> Unit,
    modifier: Modifier,
) {
    val total = NarrationTimeline.duration(s.sourceSegments)
    var range by
        remember(s.spreadId, s.trimStartMs, s.trimEndMs, s.sourceSegments) {
            mutableStateOf(s.trimStartMs.toFloat()..s.trimEndMs.toFloat())
        }
    var ignored by remember(s.spreadId, peaks) { mutableStateOf(false) }
    val suggestion =
        remember(peaks, total) {
            peaks
                ?.takeIf { s.sourceSegments.size == 1 }
                ?.let { WaveformMath.suggestSilenceTrim(it, 0, total) }
        }
    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("修剪旁白", style = MaterialTheme.typography.titleLarge)
        Text(
            "开头 ${formatBoundary(range.start.toLong())} · 结尾 ${formatBoundary(range.endInclusive.toLong())} · 已选 ${formatDuration((range.endInclusive-range.start).toLong())}",
            color = Ink.copy(.65f),
        )
        Wave(peaks, total, playhead, preview)
        if (total >= 500)
            RangeSlider(
                range,
                { if (it.endInclusive - it.start >= 500) range = it },
                valueRange = 0f..total.toFloat(),
                onValueChangeFinished = { trim(range.start.toLong(), range.endInclusive.toLong()) },
            )
        Button(
            onClick = { preview(range.start.toLong(), range.endInclusive.toLong()) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Moss),
        ) {
            Text("试听修剪结果")
        }
        TextButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) {
            AppIcon(if (more) AppIcons.ExpandLess else AppIcons.ExpandMore, null)
            Text(if (more) "收起音频操作" else "更多音频操作", modifier = Modifier.padding(start = 4.dp))
        }
        if (more) {
            if (!ignored && suggestion != null)
                Surface(color = Honey.copy(.25f), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(9.dp)) {
                        Text("检测到可能的首尾空白", fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(
                                onClick = {
                                    range = suggestion.startMs.toFloat()..suggestion.endMs.toFloat()
                                    trim(suggestion.startMs, suggestion.endMs)
                                }
                            ) {
                                Text("采用建议")
                            }
                            TextButton(onClick = { ignored = true }) { Text("忽略") }
                        }
                    }
                }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(
                    onClick = {
                        preview(
                            range.start.toLong(),
                            minOf(range.start.toLong() + 2000, range.endInclusive.toLong()),
                        )
                    }
                ) {
                    Text("试听开头")
                }
                TextButton(
                    onClick = {
                        preview(
                            maxOf(range.start.toLong(), range.endInclusive.toLong() - 2000),
                            range.endInclusive.toLong(),
                        )
                    }
                ) {
                    Text("试听结尾")
                }
                TextButton(
                    onClick = {
                        range = 0f..total.toFloat()
                        trim(0, total)
                    }
                ) {
                    Text("恢复完整范围")
                }
            }
            Boundary(book, s, boundary)
            Text("由 ${s.sourceSegments.size} 段原始录音连续播放；修剪不会删除录音。", color = Ink.copy(.6f))
            OutlinedButton(onClick = rerecord, modifier = Modifier.fillMaxWidth()) {
                Text("重新录制本书面")
            }
        }
        Surface(
            color = SoftWhite,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = refs).padding(14.dp),
        ) {
            Row {
                Text(
                    if (s.references.isEmpty()) "书面照片待补拍 · 点此添加"
                    else "参考图 ${s.references.size} 张 · ${qualityLabel(s.references.firstOrNull()?.quality)}",
                    modifier = Modifier.weight(1f),
                    color = if (s.references.isEmpty()) Coral else Ink,
                )
                AppIcon(AppIcons.Forward, "打开参考图")
            }
        }
    }
}

@Composable
private fun Wave(p: FloatArray?, total: Long, pos: Long?, preview: (Long, Long) -> Unit) {
    if (p == null) {
        Text("正在读取当前书面波形…")
        return
    }
    if (p.isEmpty() || total <= 0) {
        Text("当前书面没有可显示的波形")
        return
    }
    Canvas(
        Modifier.fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Paper)
            .pointerInput(p, total) {
                detectTapGestures {
                    val x = (it.x / size.width * total).toLong().coerceIn(0, maxOf(0, total - 1))
                    preview(x, minOf(total, x + 2000))
                }
            }
    ) {
        val c = size.height / 2
        p.forEachIndexed { i, v ->
            val x = (i + .5f) * size.width / p.size
            val h = maxOf(1f, v * c * .85f)
            drawLine(Moss, Offset(x, c - h), Offset(x, c + h), 1.5f)
        }
        pos?.takeIf { total > 0 }
            ?.let {
                val x = it.toFloat() / total * size.width
                drawLine(Coral, Offset(x, 0f), Offset(x, size.height), 3f)
            }
    }
}

@Composable
private fun Boundary(book: StoryBook, s: StorySpread, change: (String, Long) -> Unit) {
    val i = book.markers.indexOfFirst { it.spreadId == s.spreadId }
    val a = book.markers[i]
    val b = book.markers.getOrNull(i + 1) ?: return
    if (!StoryBookEditor.shareBoundary(a, b)) return
    val r = (a.segments.last().startMs + 500).toFloat()..(b.segments.first().endMs - 500).toFloat()
    if (r.endInclusive <= r.start) return
    var v by
        remember(a.segments.last().endMs) { mutableFloatStateOf(a.segments.last().endMs.toFloat()) }
    Text("与下一书面的录音分界", fontWeight = FontWeight.Bold)
    Slider(
        v.coerceIn(r.start, r.endInclusive),
        { v = it },
        valueRange = r,
        onValueChangeFinished = { change(b.spreadId, v.toLong()) },
    )
}

@Composable
private fun ReferenceDialog(
    s: StorySpread,
    dismiss: () -> Unit,
    add: () -> Unit,
    primary: (String) -> Unit,
    delete: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("参考图") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                s.references.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        StoryImage(
                            r.file,
                            Modifier.size(84.dp, 64.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(if (i == 0) "主参考图" else "参考图 ${i+1}", fontWeight = FontWeight.Bold)
                            Text(qualityLabel(r.quality))
                            if (i > 0)
                                TextButton(onClick = { primary(r.referenceId) }) { Text("设为主图") }
                            if (s.references.size > 1)
                                TextButton(onClick = { delete(r.referenceId) }) { Text("删除") }
                        }
                    }
                }
                Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("添加／重新拍摄") }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("完成") } },
    )
}

@Composable
private fun OrganizePane(
    book: StoryBook,
    id: String,
    expanded: Boolean,
    select: (String) -> Unit,
    update: (StoryBook) -> Unit,
    delete: () -> Unit,
    insert: (String) -> Unit,
) {
    val s = book.spreads.firstOrNull { it.spreadId == id } ?: book.spreads.firstOrNull()
    if (s == null) {
        Text(
            "这本书暂时没有可整理的书面",
            modifier = Modifier.padding(24.dp).navigationBarsPadding(),
            color = Ink.copy(alpha = 0.68f),
        )
        return
    }
    val i = book.spreads.indexOf(s)
    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text("这里只调整整本结构，不会修改音频内容", color = Moss, modifier = Modifier.padding(12.dp))
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(book.spreads, key = { it.spreadId }) { x ->
                    Surface(
                        color = if (x.spreadId == id) Honey.copy(.4f) else SoftWhite,
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth().clickable { select(x.spreadId) },
                    ) {
                        Row(
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(AppIcons.Drag, null)
                            StoryImage(
                                x.imageFile,
                                Modifier.padding(horizontal = 8.dp)
                                    .size(64.dp, 46.dp)
                                    .clip(RoundedCornerShape(7.dp)),
                            )
                            Column {
                                Text("${x.ordinal}. 书面 ${x.ordinal}", fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatDuration(x.durationMs)} · ${x.sourceSegments.size} 段录音",
                                    color = Ink.copy(.6f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    val actions: @Composable (Modifier) -> Unit = { m ->
        Column(m.padding(11.dp)) {
            Text("书面 ${i+1}", style = MaterialTheme.typography.titleLarge)
            if (expanded)
                StoryImage(
                    s.imageFile,
                    Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(11.dp)),
                )
            Row {
                OutlinedButton(
                    enabled = i > 0,
                    onClick = { update(StoryBookEditor.reorder(book, id, i - 1)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("上移")
                }
                OutlinedButton(
                    enabled = i < book.spreads.lastIndex,
                    onClick = { update(StoryBookEditor.reorder(book, id, i + 1)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("下移")
                }
            }
            OutlinedButton(
                enabled = i < book.spreads.lastIndex,
                onClick = { update(StoryBookEditor.mergeWithNext(book, id)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("与下一书面合并")
            }
            Row {
                OutlinedButton(
                    enabled = book.spreads.size > 1,
                    onClick = delete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("删除", color = Coral)
                }
                Button(
                    enabled = NarrationTimeline.duration(s.sourceSegments) >= 1000,
                    onClick = { insert(id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("在后面插入")
                }
            }
        }
    }
    if (expanded)
        Row(Modifier.fillMaxSize().navigationBarsPadding()) {
            list(Modifier.weight(.58f))
            actions(Modifier.weight(.42f).verticalScroll(rememberScrollState()))
        }
    else
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            list(Modifier.weight(1f))
            actions(Modifier.fillMaxWidth())
        }
}
