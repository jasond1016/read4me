package com.read4me.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import com.read4me.app.audio.PersistentWaveformCache
import com.read4me.app.audio.allocateWaveformBuckets
import com.read4me.app.model.NarrationTimeline
import com.read4me.app.model.PhotoQuality
import com.read4me.app.vision.OrbPageMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln

internal fun compositeWaveform(cache: PersistentWaveformCache, segments: List<com.read4me.app.model.NarrationSegment>, buckets: Int = 150): FloatArray {
    val total = NarrationTimeline.duration(segments)
    if (total <= 0L) return FloatArray(0)
    val counts = allocateWaveformBuckets(segments.map { it.durationMs }, buckets)
    return segments.flatMapIndexed { index, segment ->
        if (counts[index] == 0) emptyList() else cache.loadOrExtract(segment.file, segment.startMs, segment.endMs, counts[index]).asIterable()
    }.toFloatArray()
}

@Composable
internal fun StoryImage(
    file: File?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val imageKey = file?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    var bitmap by remember(imageKey) {
        mutableStateOf(file?.let(StoryImageLoader::cached))
    }
    LaunchedEffect(imageKey) {
        if (bitmap == null && file != null) {
            bitmap = withContext(Dispatchers.IO) { StoryImageLoader.load(context.applicationContext, file) }
        }
    }
    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = "绘本书面",
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Color(0xFFE4D8C8)), contentAlignment = Alignment.Center) {
            Text("书", style = MaterialTheme.typography.headlineLarge, color = Moss)
        }
    }
}

internal fun amplitudeLevel(value: Int): Float {
    if (value <= 0) return 0f
    return (ln(value.toFloat()) / ln(32767f)).coerceIn(0f, 1f)
}

internal fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

internal fun formatBoundary(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0)
    val totalSeconds = safe / 1000
    val millis = safe % 1000
    return "%02d:%02d.%03d".format(totalSeconds / 60, totalSeconds % 60, millis)
}

internal fun recognitionDiagnostic(decision: OrbPageMatcher.Decision): String {
    val best = decision.best
    val second = decision.second
    val bestText = best?.let { "最佳 ${it.reference.spreadOrdinal}：${it.inliers} 内点" }
        ?: "没有候选"
    val secondText = second?.let { "，次佳 ${it.reference.spreadOrdinal}：${it.inliers} 内点" }.orEmpty()
    val state = when (decision.state) {
        OrbPageMatcher.State.LOW_INLIERS -> "几何匹配不足"
        OrbPageMatcher.State.AMBIGUOUS -> "候选太接近"
        OrbPageMatcher.State.CONFIRMING -> "确认 ${decision.confirmationCount}/${decision.confirmationsRequired}"
        OrbPageMatcher.State.CONFIRMED, OrbPageMatcher.State.STABLE -> "已确认"
        OrbPageMatcher.State.NO_REFERENCES -> "没有参考照片"
        OrbPageMatcher.State.TOO_FEW_FEATURES -> "画面特征太少"
    }
    val path = when (decision.searchPath) {
        OrbPageMatcher.SearchPath.NONE -> "未搜索"
        OrbPageMatcher.SearchPath.ADJACENT -> "邻页快查"
        OrbPageMatcher.SearchPath.LIBRARY -> "全库查找"
    }
    val search = "索引 ${decision.indexedReferences} · 几何 ${decision.geometricallyVerified} · $path"
    return "$bestText$secondText · $state · $search"
}

internal fun qualityLabel(quality: PhotoQuality?): String = when (quality?.status) {
    null -> "尚未检查"
    PhotoQuality.Status.GOOD -> "质量良好"
    PhotoQuality.Status.UNAVAILABLE -> "无法检查"
    PhotoQuality.Status.ISSUES -> "建议重拍：" + quality.issues.joinToString("、") {
        when (it) {
            PhotoQuality.Issue.BLURRY -> "模糊"
            PhotoQuality.Issue.TOO_FEW_DETAILS -> "细节过少"
            PhotoQuality.Issue.TOO_DARK -> "过暗"
            PhotoQuality.Issue.OVEREXPOSED -> "过曝"
        }
    }
}
