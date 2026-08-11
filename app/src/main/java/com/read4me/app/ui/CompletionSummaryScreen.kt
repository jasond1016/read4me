package com.read4me.app.ui

import androidx.activity.compose.BackHandler
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
fun CompletionSummaryScreen(book: StoryBook, onPlay: () -> Unit, onReview: () -> Unit) {
    val missingPhotos = book.spreads.count { it.imageFile?.isFile != true }
    BackHandler(onBack = onReview)

    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        Column(Modifier.fillMaxSize()) {
            CompletionTopBar(onReview)
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(color = WarmMoss.copy(alpha = .14f), shape = CircleShape, modifier = Modifier.size(88.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = WarmMoss, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "录音已经安全保存",
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
                    WarmCard(Modifier.fillMaxWidth().padding(top = 26.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
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
                            if (missingPhotos == 0) "全部书面均有图片"
                            else "还有 $missingPhotos 个书面需要补拍图片，可稍后在书籍详情中处理",
                            color = WarmBrown,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            CompletionActions(onPlay, onReview)
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
private fun CompletionActions(onPlay: () -> Unit, onReview: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.Center)) {
                WarmPrimaryButton("试听整本旁白", onPlay, Modifier.fillMaxWidth(), AppIcons.Play)
                OutlinedButton(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("查看和整理书籍", color = WarmMoss, fontWeight = FontWeight.Bold)
                }
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
