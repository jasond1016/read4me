package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.read4me.app.model.readiness
import com.read4me.app.model.hasUsablePhoto
import com.read4me.app.model.StoryBookEditor
import com.read4me.app.model.StoryEditSession
import com.read4me.app.model.StorySpread
import com.read4me.app.model.StoryStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun BookOverviewScreen(
    book: StoryBook,
    onBack: () -> Unit,
    onStartReading: () -> Unit,
    onContinueRecording: () -> Unit,
    onPlayBook: () -> Unit,
    onBrowseSpreads: () -> Unit,
    onOpenSpread: (String) -> Unit,
    onOrganize: () -> Unit,
    onBatchRecapture: () -> Unit,
    onCreateBook: () -> Unit,
    onOpenMe: () -> Unit,
) {
    val playable = book.readiness().canPlay
    val missingPhotos = book.readiness().missingPhotoIds.size
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(WarmPaper)) {
        Column(Modifier.fillMaxSize().padding(bottom = 80.dp).navigationBarsPadding()) {
            BookOverviewTopBar(
                onBack = onBack,
                onOrganize = onOrganize,
                missingPhotos = missingPhotos,
                onBatchRecapture = onBatchRecapture,
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 840.dp
                if (expanded) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                    ) {
                        Column(
                            Modifier.weight(.42f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            BookIdentity(book)
                            BookOverviewActions(
                                book,
                                playable,
                                onStartReading,
                                onContinueRecording,
                                onPlayBook,
                                onBrowseSpreads,
                                onBatchRecapture,
                            )
                        }
                        OverviewPages(
                            book = book,
                            maximum = 6,
                            columns = 3,
                            missingPhotos = missingPhotos,
                            onBrowse = onBrowseSpreads,
                            onOpenSpread = onOpenSpread,
                            modifier = Modifier.weight(.58f).verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        BookIdentity(book)
                        BookOverviewActions(
                            book,
                            playable,
                            onStartReading,
                            onContinueRecording,
                            onPlayBook,
                            onBrowseSpreads,
                            onBatchRecapture,
                        )
                        OverviewPages(
                            book = book,
                            maximum = 2,
                            columns = 2,
                            missingPhotos = missingPhotos,
                            onBrowse = onBrowseSpreads,
                            onOpenSpread = onOpenSpread,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        WarmBottomShell(WarmShellTab.LIBRARY, Modifier.align(Alignment.BottomCenter)) { tab ->
            when (tab) {
                WarmShellTab.LIBRARY -> onBack()
                WarmShellTab.READING -> onStartReading()
                WarmShellTab.RECORD -> onCreateBook()
                WarmShellTab.ME -> onOpenMe()
            }
        }
    }
}

@Composable
private fun BookOverviewTopBar(
    onBack: () -> Unit,
    onOrganize: () -> Unit,
    missingPhotos: Int,
    onBatchRecapture: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(AppIcons.Back, "返回书架", onBack, Modifier.size(52.dp), tint = WarmBrown)
            Text(
                "书籍详情",
                style = MaterialTheme.typography.titleLarge,
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Box {
                AppIconButton(AppIcons.More, "更多书籍操作", { menuExpanded = true }, tint = WarmBrown)
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (missingPhotos > 0) {
                        DropdownMenuItem(
                            text = { Text("批量补拍书面照片（$missingPhotos）") },
                            onClick = {
                                menuExpanded = false
                                onBatchRecapture()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("整理这本书") },
                        onClick = {
                            menuExpanded = false
                            onOrganize()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookIdentity(book: StoryBook) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StoryImage(
            book.spreads.firstOrNull()?.imageFile,
            Modifier.width(132.dp).aspectRatio(.76f).clip(RoundedCornerShape(24.dp)),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                book.title,
                style = MaterialTheme.typography.headlineSmall,
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(color = WarmMoss, shape = RoundedCornerShape(12.dp)) {
                Text(
                    if (book.status == StoryStatus.COMPLETE) "已完成" else "录制中",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(
                "${book.spreads.size} 个书面 · ${formatDuration(book.playableDurationMs)}",
                color = WarmBrown.copy(alpha = .67f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun BookOverviewActions(
    book: StoryBook,
    playable: Boolean,
    onStartReading: () -> Unit,
    onContinueRecording: () -> Unit,
    onPlayBook: () -> Unit,
    onBrowseSpreads: () -> Unit,
    onBatchRecapture: () -> Unit,
) {
    val complete = book.status == StoryStatus.COMPLETE
    val hasPhotos = book.spreads.any { it.hasUsablePhoto }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = if (complete) onPlayBook else onContinueRecording,
            enabled = !complete || playable,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            AppIcon(if (complete) AppIcons.Play else AppIcons.Restart, null)
            Text(if (complete) "播放这本书" else "继续录制", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        if (!playable && complete) Text("暂无可播放的录音，可在书面详情中重新录制。", color = WarmMoss)
        OutlinedButton(
            onClick = if (!complete) onPlayBook else if (hasPhotos) onStartReading else onBatchRecapture,
            enabled = playable,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            AppIcon(if (complete) AppIcons.Reading else AppIcons.Play, null)
            Text(if (!complete) "试听已录内容" else if (hasPhotos) "翻页听 · 对准纸质绘本" else "补拍照片，开启翻页听", modifier = Modifier.padding(start = 8.dp))
        }
        if (complete && !hasPhotos) Text("补拍书面照片后，就能让孩子翻书听录音。", style = MaterialTheme.typography.bodyMedium, color = WarmMoss)
        TextButton(onClick = onBrowseSpreads, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            AppIcon(AppIcons.List, null)
            Text("逐页试听与修复", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun OverviewPages(
    book: StoryBook,
    maximum: Int,
    columns: Int,
    missingPhotos: Int,
    onBrowse: () -> Unit,
    onOpenSpread: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "书面",
                style = MaterialTheme.typography.titleLarge,
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBrowse) {
                Text("查看全部", color = WarmMoss)
                AppIcon(AppIcons.Forward, null, tint = WarmMoss, size = 18.dp)
            }
        }
        book.spreads.take(maximum).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { spread ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier.weight(1f).clickable { onOpenSpread(spread.spreadId) },
                    ) {
                        Column {
                            StoryImage(
                                spread.imageFile,
                                Modifier.fillMaxWidth().aspectRatio(.92f),
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("第 ${spread.ordinal} 页", color = WarmBrown, modifier = Modifier.weight(1f))
                                Text(formatDuration(spread.durationMs), color = WarmBrown.copy(alpha = .58f))
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (book.spreads.isEmpty()) {
            Text("这本书还没有书面", color = WarmBrown.copy(alpha = .58f))
        }
        if (missingPhotos > 0) {
            Surface(color = WarmAmber.copy(alpha = .14f), shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(AppIcons.Reading, null, tint = WarmAmber)
                    Text(
                        "$missingPhotos 个书面还需要补拍照片",
                        color = WarmBrown,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewScreen(
    book: StoryBook,
    repository: StoryRepository,
    initialSelectedSpreadId: String? = null,
    returnActionLabel: String? = null,
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
        rememberSaveable(book.id) {
            mutableStateOf(
                initialOrganizeCurrent?.takeIf { id ->
                    initialOrganizeDraft?.markers?.any { it.spreadId == id } == true
                } ?: initialSelectedSpreadId?.takeIf { id -> book.markers.any { it.spreadId == id } }
                    ?: book.resumeSpreadId ?: book.markers.firstOrNull()?.spreadId.orEmpty(),
            )
        }
    var playingId by remember { mutableStateOf<String?>(null) }
    var playhead by remember { mutableStateOf<Long?>(null) }
    var playbackStart by remember { mutableStateOf<Long?>(null) }
    var playbackEnd by remember { mutableStateOf<Long?>(null) }
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
        playbackStart = null
        playbackEnd = null
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
    LaunchedEffect(playingId, playbackStart, playbackEnd) {
        if (playingId == null) return@LaunchedEffect
        val start = playbackStart ?: return@LaunchedEffect
        val end = playbackEnd ?: return@LaunchedEffect
        while (true) {
            player.progress()?.let { progress ->
                playhead = start + ((end - start) * progress).toLong()
            }
            delay(50)
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
                returnActionLabel,
                selected?.ordinal,
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
                AudioEditor(
                    current,
                    selected,
                    waveform,
                    playhead,
                    playbackStart,
                    more,
                    false,
                    { more = !more },
                    { a, b ->
                        stop()
                        playingId = selectedId
                        playbackStart = a
                        playbackEnd = b
                        playhead = a
                        val clips = NarrationTimeline.clip(selected.sourceSegments, a, b)
                        if (
                            !player.play(
                                clips,
                                onError = {
                                    playingId = null
                                    playhead = null
                                    playbackStart = null
                                    playbackEnd = null
                                },
                                onFinished = {
                                    playingId = null
                                    playhead = null
                                    playbackStart = null
                                    playbackEnd = null
                                },
                            )
                        ) {
                            playingId = null
                            playhead = null
                            playbackStart = null
                            playbackEnd = null
                        }
                    },
                    { a, b ->
                        apply(StoryBookEditor.trimNarration(current, selectedId, a, b))
                    },
                    { id, p ->
                        apply(StoryBookEditor.moveBoundary(current, selectedId, id, p))
                    },
                    { refs = true },
                    {
                        stop()
                        onRerecord(current, selectedId)
                    },
                    Modifier.fillMaxSize().navigationBarsPadding(),
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
    returnActionLabel: String?,
    focusedPage: Int?,
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
                AppIconButton(AppIcons.Back, "返回", back, Modifier.size(48.dp), tint = Ink)
                Text(
                    if (returnActionLabel != null && focusedPage != null) "编辑第 $focusedPage 页" else book.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                if (returnActionLabel != null) {
                    TextButton(onClick = back, modifier = Modifier.height(48.dp)) {
                        Text(returnActionLabel, color = Moss, fontWeight = FontWeight.Bold)
                    }
                }
                if (returnActionLabel == null &&
                    book.status == StoryStatus.COMPLETE &&
                        book.readiness().canPlay
                )
                    TextButton(onClick = onPlayBook) {
                        Text("整本播放", color = Coral, fontWeight = FontWeight.Bold)
                    }
                if (returnActionLabel == null) Box {
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
                        val missingPhotos = book.readiness().missingPhotoIds.size
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
private fun AudioEditor(
    book: StoryBook,
    s: StorySpread,
    peaks: FloatArray?,
    playhead: Long?,
    playbackStart: Long?,
    more: Boolean,
    allowAdvanced: Boolean,
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
                ?.let { WaveformMath.suggestSilenceTrim(it, 0, total) }
        }
    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("修剪旁白", style = MaterialTheme.typography.titleLarge)
        Text(
            "开头 ${formatBoundary(range.start.toLong())} · 结尾 ${formatBoundary(range.endInclusive.toLong())} · 已选 ${formatDuration((range.endInclusive-range.start).toLong())}",
            color = Ink.copy(.65f),
        )
        Wave(peaks, total, playbackStart, playhead, preview)
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
        if (!allowAdvanced) {
            OutlinedButton(
                onClick = {
                    val detected = suggestion ?: return@OutlinedButton
                    range = detected.startMs.toFloat()..detected.endMs.toFloat()
                    trim(detected.startMs, detected.endMs)
                },
                enabled = suggestion != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    when {
                        peaks == null -> "正在分析首尾空白…"
                        suggestion != null -> "自动去除首尾空白"
                        else -> "未检测到明显首尾空白"
                    }
                )
            }
            OutlinedButton(
                onClick = {
                    range = 0f..total.toFloat()
                    trim(0, total)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("恢复完整范围") }
        }
        if (allowAdvanced) TextButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) {
            AppIcon(if (more) AppIcons.ExpandLess else AppIcons.ExpandMore, null)
            Text(if (more) "收起音频操作" else "更多音频操作", modifier = Modifier.padding(start = 4.dp))
        }
        if (allowAdvanced && more) {
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
        if (allowAdvanced) Surface(
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
private fun Wave(
    p: FloatArray?,
    total: Long,
    playbackStart: Long?,
    pos: Long?,
    preview: (Long, Long) -> Unit,
) {
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
        val playedFrom = playbackStart?.coerceIn(0, total)?.toFloat()
        val playedTo = pos?.coerceIn(0, total)?.toFloat()
        p.forEachIndexed { i, v ->
            val x = (i + .5f) * size.width / p.size
            val time = (i + .5f) * total / p.size
            val h = maxOf(1f, v * c * .85f)
            val color =
                if (playedFrom != null && playedTo != null && time in playedFrom..playedTo) Honey
                else Moss
            drawLine(color, Offset(x, c - h), Offset(x, c + h), 1.5f)
        }
        pos?.takeIf { total > 0 }
            ?.let {
                val x = it.toFloat() / total * size.width
                drawLine(Honey, Offset(x, 0f), Offset(x, size.height), 3f)
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
