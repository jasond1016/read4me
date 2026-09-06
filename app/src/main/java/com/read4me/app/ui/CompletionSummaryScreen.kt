package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import com.read4me.app.model.readiness
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.read4me.app.model.StoryBook

@Composable
fun CompletionSummaryScreen(
    book: StoryBook,
    onPlay: () -> Unit,
    onReview: () -> Unit,
    onRepairPhotos: () -> Unit,
    onRepairAudio: (String) -> Unit,
    onLibrary: () -> Unit,
) {
    val readiness = book.readiness()
    val missingPhotos = readiness.missingPhotoIds.size
    BackHandler(onBack = onReview)

    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        Column(Modifier.fillMaxSize()) {
            CompletionTopBar(onReview)
            Box(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(color = WarmMoss.copy(alpha = .14f), shape = CircleShape, modifier = Modifier.size(72.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = WarmMoss, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        if (readiness.canPlay) "录好了，现在就能听" else "绘本已保存，还有录音待补齐",
                        style = MaterialTheme.typography.headlineSmall,
                        color = WarmBrown,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = WarmBrown.copy(alpha = .78f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    WarmCard(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            SummaryStat("${book.spreads.size}", "书面")
                            SummaryStat(formatSummaryDuration(book.playableDurationMs), "总时长")
                        }
                    }
                    Surface(
                        color = if (missingPhotos == 0) WarmMoss.copy(alpha = .11f) else WarmAmber.copy(alpha = .13f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Text(
                            when {
                                readiness.missingAudioIds.isNotEmpty() -> "${readiness.missingAudioIds.size} 个书面的录音不可用，补录后即可整本播放。"
                                missingPhotos > 0 -> "现在可以整本播放。补拍 $missingPhotos 个书面后，还能让孩子翻书听。"
                                else -> "照片和录音都齐了，也可以从书架开启翻页听。"
                            },
                            color = WarmBrown,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            CompletionActions(
                canPlay = readiness.canPlay,
                missingPhotos = missingPhotos,
                onPlay = onPlay,
                onReview = onReview,
                onRepair = { readiness.missingAudioIds.firstOrNull()?.let(onRepairAudio) ?: onRepairPhotos() },
                onLibrary = onLibrary,
            )
        }
    }
}

@Composable
private fun CompletionTopBar(onBack: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(AppIcons.Back, "返回书籍详情", onBack, Modifier.size(52.dp), tint = WarmBrown)
            Text(
                "录制完成",
                style = MaterialTheme.typography.titleLarge,
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Spacer(Modifier.size(52.dp))
        }
    }
}

@Composable
private fun CompletionActions(
    canPlay: Boolean, missingPhotos: Int, onPlay: () -> Unit, onReview: () -> Unit,
    onRepair: () -> Unit, onLibrary: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.Center)) {
                WarmPrimaryButton(if (canPlay) "试听这本书" else "补录缺失声音", if (canPlay) onPlay else onRepair, Modifier.fillMaxWidth(), AppIcons.Play)
                OutlinedButton(
                    onClick = if (canPlay && missingPhotos > 0) onRepair else onReview,
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(if (canPlay && missingPhotos > 0) "补拍照片（$missingPhotos 个书面）" else "查看绘本", color = WarmMoss, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("返回书架，稍后再听") }
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = WarmCoral, fontWeight = FontWeight.Bold)
        Text(label, color = WarmBrown.copy(alpha = .65f))
    }
}

private fun formatSummaryDuration(ms: Long): String =
    "${ms / 60_000}:${((ms / 1_000) % 60).toString().padStart(2, '0')}"
