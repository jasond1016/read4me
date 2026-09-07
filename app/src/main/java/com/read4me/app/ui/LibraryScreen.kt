package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.StoryBook
import com.read4me.app.model.readiness
import com.read4me.app.model.hasUsablePhoto
import com.read4me.app.model.StoryStatus
import com.read4me.app.vision.RecognitionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    books: List<StoryBook>,
    trashedBooks: List<StoryRepository.TrashedBook>,
    recognitionSummaries: List<RecognitionSummary>,
    initialShellTab: WarmShellTab = WarmShellTab.LIBRARY,
    onCreateBook: () -> Unit,
    onChildMode: () -> Unit,
    onOpenBook: (StoryBook) -> Unit,
    onPlayBook: (StoryBook) -> Unit,
    onContinueBook: (StoryBook) -> Unit,
    archiveMessage: String?,
    onImport: () -> Unit,
    onExport: (StoryBook) -> Unit,
    onRename: (StoryBook, String) -> Unit,
    onMoveToTrash: (StoryBook) -> Unit,
    onRestore: (StoryRepository.TrashedBook) -> Unit,
    onPermanentlyDelete: (StoryRepository.TrashedBook) -> Unit,
    onImportLibrary: () -> Unit,
    onExportLibrary: () -> Unit,
    onClearMediaCache: () -> Unit,
) {
    var shellTab by rememberSaveable(initialShellTab) { mutableStateOf(initialShellTab) }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var showReadingHelp by remember { mutableStateOf(false) }
    val shelfState = rememberSaveableStateHolder()
    LaunchedEffect(initialShellTab) { shellTab = initialShellTab }
    BackHandler(enabled = shellTab == WarmShellTab.ME) { shellTab = WarmShellTab.LIBRARY }
    Box(Modifier.fillMaxSize().background(WarmPaper)) {
        Surface(
            Modifier.fillMaxSize().padding(bottom = 80.dp).statusBarsPadding().navigationBarsPadding(),
            color = WarmPaper,
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 700.dp
                if (shellTab == WarmShellTab.LIBRARY) {
                    shelfState.SaveableStateProvider("shelf") { LibraryHome(
                        books, recognitionSummaries, filter, { filter = it }, wide, onCreateBook,
                        onOpenBook, onPlayBook, onContinueBook, onExport, onRename, onMoveToTrash,
                    ) }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = if (wide) 36.dp else 20.dp, vertical = 24.dp),
                    ) {
                        LibraryMaintenance(
                            trashedBooks, wide, archiveMessage, onImport,
                            onImportLibrary, onExportLibrary, onClearMediaCache,
                            onRestore, onPermanentlyDelete,
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
        WarmBottomShell(shellTab, Modifier.align(Alignment.BottomCenter)) { tab ->
            when (tab) {
                WarmShellTab.READING -> {
                    if (books.any { book -> isPlayableBook(book) && book.spreads.any { it.hasUsablePhoto } }) onChildMode()
                    else showReadingHelp = true
                }
                WarmShellTab.RECORD -> onCreateBook()
                else -> shellTab = tab
            }
        }
    }
    if (showReadingHelp) AlertDialog(
        onDismissRequest = { showReadingHelp = false },
        title = { Text("让孩子翻书就能听") },
        text = { Text(if (books.isEmpty()) "先录一本绘本，拍下书面并留下你的声音。录完后，对准绘本就会自动播放。" else "翻页听需要已完成的录音和书面照片。请先完成录制，或进入绘本详情补拍照片。") },
        confirmButton = {
            TextButton(onClick = { showReadingHelp = false; if (books.isEmpty()) onCreateBook() else { shellTab = WarmShellTab.LIBRARY; filter = "全部" } }) {
                Text(if (books.isEmpty()) "录第一本绘本" else "去书架看看")
            }
        },
        dismissButton = { TextButton(onClick = { showReadingHelp = false }) { Text("稍后再说") } },
    )
}

@Composable
private fun LibraryHome(
    books: List<StoryBook>,
    recognitionSummaries: List<RecognitionSummary>,
    filter: String,
    onFilter: (String) -> Unit,
    wide: Boolean,
    onCreateBook: () -> Unit,
    onOpenBook: (StoryBook) -> Unit,
    onPlayBook: (StoryBook) -> Unit,
    onContinueBook: (StoryBook) -> Unit,
    onExport: (StoryBook) -> Unit,
    onRename: (StoryBook, String) -> Unit,
    onMoveToTrash: (StoryBook) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val repairBookIds = recognitionSummaries.filter { it.needsNewReference }.map { it.bookId }.toSet() +
        books.filter { it.readiness().missingPhotoIds.isNotEmpty() }.map { it.id }
    val visible = books.filter { book ->
        book.title.contains(query.trim(), ignoreCase = true) && when (filter) {
            "已完成" -> book.status == StoryStatus.COMPLETE
            "录制中" -> book.status == StoryStatus.IN_PROGRESS
            "待补拍" -> book.id in repairBookIds
            else -> true
        }
    }
    val recent = books.firstOrNull(::isPlayableBook)
    val left: @Composable () -> Unit = {
        Column {
            Text("留声绘本", style = MaterialTheme.typography.displayLarge, color = Ink)
            Text("把陪伴的声音，留在每一次翻页里。", color = Ink.copy(.66f), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            WarmIllustration(
                R.drawable.illustration_home_reading,
                "亲子一起阅读绘本",
                Modifier.fillMaxWidth().height(if (wide) 260.dp else 205.dp),
                ContentScale.Fit,
            )
            if (recent != null) {
                WarmCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoryImage(recent.spreads.firstOrNull()?.imageFile, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("随时播放", style = MaterialTheme.typography.labelMedium, color = Moss)
                            Text(recent.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(onClick = { onPlayBook(recent) }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { AppIcon(AppIcons.Play, "播放绘本", tint = Color.White, size = 28.dp) }
                    }
                }
            }
            if (books.isEmpty()) {
                EmptyLibraryCard()
                WarmPrimaryButton("录下第一本绘本", onCreateBook, Modifier.fillMaxWidth().padding(top = 14.dp), AppIcons.Add)
            }
        }
    }
    val shelfHeader: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的书架", style = MaterialTheme.typography.headlineLarge, color = Ink, fontWeight = FontWeight.Bold)
                Text(if (books.isEmpty()) "把你的声音，留给孩子" else "${books.size} 本绘本 · 声音保存在本机", style = MaterialTheme.typography.bodyMedium, color = Moss)
            }
            if (books.isNotEmpty()) TextButton(onClick = onCreateBook) {
                AppIcon(AppIcons.Add, null)
                Text("录新书")
            }
        }
        if (books.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("搜索绘本") },
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } },
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "已完成", "录制中", "待补拍").forEach { label ->
                    val selected = filter == label
                    FilterChip(selected = selected, onClick = { onFilter(label) }, label = { Text(label) })
                }
            }
        }
    }
    val bookCard: @Composable (StoryBook) -> Unit = { book ->
        BookCard(book, { onOpenBook(book) }, { onExport(book) }, { onRename(book, it) }, { onMoveToTrash(book) }, { onContinueBook(book) }, { onPlayBook(book) }, grid = !wide)
    }
    val padding = PaddingValues(horizontal = if (wide) 36.dp else 20.dp, vertical = 24.dp)
    if (wide) {
        Row(Modifier.fillMaxSize().padding(padding), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(.88f).verticalScroll(rememberScrollState())) { left() }
            LazyColumn(Modifier.weight(1.12f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { shelfHeader() }
                if (books.isNotEmpty() && visible.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (query.isNotBlank()) "没有找到“${query.trim()}”" else "暂无${filter}的绘本", fontWeight = FontWeight.Bold)
                        Text("换个名称，或查看全部绘本", color = Moss, modifier = Modifier.padding(top = 6.dp))
                        TextButton(onClick = { query = ""; onFilter("全部") }) { Text("查看全部") }
                    }
                }
                items(visible, key = { it.id }) { bookCard(it) }
                item { Spacer(Modifier.height(36.dp)) }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                shelfHeader()
                if (books.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    WarmIllustration(
                        R.drawable.illustration_home_reading,
                        "亲子一起阅读绘本",
                        Modifier.fillMaxWidth().height(190.dp),
                        ContentScale.Fit,
                    )
                    EmptyLibraryCard()
                    WarmPrimaryButton("录下第一本绘本", onCreateBook, Modifier.fillMaxWidth().padding(top = 14.dp), AppIcons.Add)
                }
            }
            if (books.isNotEmpty() && visible.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (query.isNotBlank()) "没有找到“${query.trim()}”" else "暂无${filter}的绘本", fontWeight = FontWeight.Bold)
                        Text("换个名称，或查看全部绘本", color = Moss, modifier = Modifier.padding(top = 6.dp))
                        TextButton(onClick = { query = ""; onFilter("全部") }) { Text("查看全部") }
                    }
                }
            items(visible.chunked(2), key = { row -> row.joinToString(":") { it.id } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    row.forEach { book -> Box(Modifier.weight(1f)) { bookCard(book) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(36.dp)) }
        }
    }
}

@Composable
private fun LibraryMaintenance(
    trashedBooks: List<StoryRepository.TrashedBook>,
    wide: Boolean,
    archiveMessage: String?,
    onImport: () -> Unit,
    onImportLibrary: () -> Unit,
    onExportLibrary: () -> Unit,
    onClearMediaCache: () -> Unit,
    onRestore: (StoryRepository.TrashedBook) -> Unit,
    onPermanentlyDelete: (StoryRepository.TrashedBook) -> Unit,
) {
    Text("管理", style = MaterialTheme.typography.headlineLarge, color = Ink)
    Text("备份与本机维护", color = Ink.copy(.62f), modifier = Modifier.padding(bottom = 18.dp))
    val maintenance: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WarmCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
                Text("本地备份", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("文件只在你选择的位置读写", color = Ink.copy(.58f), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("导入单本绘本") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportLibrary, modifier = Modifier.weight(1f)) { Text("导出整库") }
                    OutlinedButton(onClick = onImportLibrary, modifier = Modifier.weight(1f)) { Text("恢复整库") }
                }
            } }
            WarmCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("媒体缓存", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("波形和缩略图可自动重建", color = Ink.copy(.58f)) }
                OutlinedButton(onClick = onClearMediaCache) { Text("清除") }
            } }
            archiveMessage?.let { Text(it, color = Moss, modifier = Modifier.padding(horizontal = 6.dp)) }
        }
    }
    val trash: @Composable () -> Unit = {
        Column {
            Text("回收站", style = MaterialTheme.typography.headlineMedium)
            Text("保留 30 天后自动永久删除", color = Ink.copy(.58f), modifier = Modifier.padding(bottom = 10.dp))
            if (trashedBooks.isEmpty()) Text("回收站是空的", color = Ink.copy(.55f), modifier = Modifier.padding(vertical = 18.dp))
            trashedBooks.forEach { trashed ->
                TrashBookCard(trashed, { onRestore(trashed) }, { onPermanentlyDelete(trashed) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) { maintenance() }; Box(Modifier.weight(1f)) { trash() }
    } else Column { maintenance(); Spacer(Modifier.height(28.dp)); trash() }
}

internal fun isPlayableBook(book: StoryBook): Boolean =
    book.status == StoryStatus.COMPLETE && book.readiness().canPlay

@Composable
private fun EmptyLibraryCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(26.dp)) {
            Text("一本书，一段家的声音", style = MaterialTheme.typography.headlineMedium)
            Text(
                "固定好手机，把书放进画面，然后像平常一样读。翻页时，App 会替你记住每个书面。",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepPebble("1", "放好书")
                StepPebble("2", "读一遍")
                StepPebble("3", "留下来")
            }
        }
    }
}

@Composable
private fun StepPebble(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(44.dp).background(Honey, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontWeight = FontWeight.Bold, color = Ink)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BookCard(
    book: StoryBook,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onRename: (String) -> Unit,
    onMoveToTrash: () -> Unit,
    onContinue: () -> Unit,
    onPlay: () -> Unit,
    grid: Boolean = false,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var title by remember(book.title) { mutableStateOf(book.title) }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (grid) Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(.82f)) {
                StoryImage(book.spreads.firstOrNull()?.imageFile, Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)))
                Surface(color = if (book.status == StoryStatus.COMPLETE) Moss.copy(.9f) else Honey.copy(.9f), shape = CircleShape, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text(if (book.status == StoryStatus.COMPLETE) "已完成" else "录制中", color = if (book.status == StoryStatus.COMPLETE) Color.White else Ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    AppIconButton(
                        AppIcons.More,
                        "${book.title}更多操作",
                        { showMenu = true },
                        Modifier.padding(6.dp).size(42.dp).background(Ink.copy(.52f), CircleShape),
                        tint = Color.White,
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("分享给家人") }, onClick = { showMenu = false; onExport() })
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { showMenu = false; showRename = true })
                        DropdownMenuItem(text = { Text("移到回收站", color = Coral) }, onClick = { showMenu = false; showDelete = true })
                    }
                }
                if (isPlayableBook(book)) AppIconButton(
                    AppIcons.Play,
                    "播放${book.title}",
                    onPlay,
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(48.dp).background(SoftWhite.copy(.92f), CircleShape),
                    tint = Coral,
                    iconSize = 30.dp,
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.spreads.size} 个书面 · ${formatDuration(book.playableDurationMs)}", style = MaterialTheme.typography.bodySmall, color = Ink.copy(.62f), maxLines = 1)
                if (book.status == StoryStatus.IN_PROGRESS) TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("继续录制", color = Moss, fontWeight = FontWeight.Bold) }
            }
        } else Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryImage(
                file = book.spreads.firstOrNull()?.imageFile,
                modifier = Modifier.size(width = 78.dp, height = 68.dp).clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${book.spreads.size} 个书面 · ${formatDuration(book.playableDurationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 3.dp),
                )
                Surface(color = if (book.status == StoryStatus.COMPLETE) Moss.copy(.13f) else Honey.copy(.35f), shape = CircleShape, modifier = Modifier.padding(top = 5.dp)) {
                    Text(if (book.status == StoryStatus.COMPLETE) "已完成" else "录制中", color = if (book.status == StoryStatus.COMPLETE) Moss else Ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            if (book.status == StoryStatus.IN_PROGRESS) TextButton(onClick = onContinue, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("续录", color = Moss, fontWeight = FontWeight.Bold) }
            else if (isPlayableBook(book)) AppIconButton(AppIcons.Play, "播放${book.title}", onPlay, Modifier.size(48.dp), tint = Coral, iconSize = 28.dp)
            Box {
                AppIconButton(AppIcons.More, "更多操作", { showMenu = true }, Modifier.size(48.dp), tint = Ink)
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("打开详情") }, onClick = { showMenu = false; onClick() })
                    DropdownMenuItem(text = { Text("分享给家人") }, onClick = { showMenu = false; onExport() })
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { showMenu = false; showRename = true })
                    DropdownMenuItem(text = { Text("移到回收站", color = Coral) }, onClick = { showMenu = false; showDelete = true })
                }
            }
        }
    }
    if (showRename) AlertDialog(
        onDismissRequest = { showRename = false },
        title = { Text("重命名绘本") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("绘本名称") })
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { showRename = false; onRename(title) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } },
    )
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("移到回收站？") },
        text = { Text("录音和照片会保留 30 天，可以从回收站恢复。") },
        confirmButton = { TextButton(onClick = { showDelete = false; onMoveToTrash() }) { Text("移到回收站", color = Coral) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun TrashBookCard(
    trashed: StoryRepository.TrashedBook,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit,
) {
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SoftWhite)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(trashed.book.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "删除于 ${SimpleDateFormat("M月d日", Locale.CHINA).format(Date(trashed.deletedAtMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = .58f),
                )
            }
            TextButton(onClick = onRestore) { Text("恢复") }
            TextButton(onClick = { confirmPermanentDelete = true }) { Text("永久删除", color = Coral) }
        }
    }
    if (confirmPermanentDelete) AlertDialog(
        onDismissRequest = { confirmPermanentDelete = false },
        title = { Text("永久删除这本绘本？") },
        text = { Text("所有录音和照片都会被删除，且无法恢复。") },
        confirmButton = {
            TextButton(onClick = { confirmPermanentDelete = false; onPermanentlyDelete() }) { Text("永久删除", color = Coral) }
        },
        dismissButton = { TextButton(onClick = { confirmPermanentDelete = false }) { Text("取消") } },
    )
}
