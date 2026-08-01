package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.read4me.app.model.StoryBook

@Composable
fun CompletionSummaryScreen(book: StoryBook, onPlay: () -> Unit, onReview: () -> Unit) {
    BackHandler(onBack = onReview)
    Surface(Modifier.fillMaxSize(), color = WarmPaper) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("录制完成", style = MaterialTheme.typography.headlineLarge, color = WarmBrown)
                Text("这段陪读已经安全保存在设备中", color = WarmMoss, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                WarmCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        SummaryStat("${book.spreads.size}", "书面")
                        SummaryStat(formatSummaryDuration(book.playableDurationMs), "总时长")
                        SummaryStat("${book.spreads.count { it.imageFile?.isFile == true }}", "有照片")
                    }
                }
            }
            item { Text("书面一览", style = MaterialTheme.typography.titleLarge, color = WarmBrown) }
            items(book.spreads, key = { it.spreadId }) { spread ->
                WarmCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoryImage(spread.imageFile, Modifier.height(64.dp).fillMaxWidth(.28f))
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text("书面 ${spread.ordinal}", color = WarmBrown)
                            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).background(WarmLine, CircleShape)) {
                                Box(Modifier.fillMaxWidth((spread.durationMs.toFloat() / book.playableDurationMs.coerceAtLeast(1)).coerceIn(.08f, 1f)).height(6.dp).background(WarmCoral, CircleShape))
                            }
                        }
                    }
                }
            }
            item {
                WarmPrimaryButton("试听完整版本", onPlay, Modifier.fillMaxWidth())
                OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("去编辑整理") }
            }
        }
    }
}

@Composable private fun SummaryStat(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.headlineMedium, color = WarmCoral)
    Text(label, color = WarmBrown.copy(.65f))
}

private fun formatSummaryDuration(ms: Long): String = "${ms / 60000}:${((ms / 1000) % 60).toString().padStart(2, '0')}"
