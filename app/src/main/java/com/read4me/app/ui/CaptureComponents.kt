package com.read4me.app.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun BookGuideFrame(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(1.32f)
            .border(
                width = 3.dp,
                color = if (active) Honey else Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
            ),
    )
}

@Composable
internal fun CaptureFramingSelector(
    framing: CaptureFraming,
    onFramingChange: (CaptureFraming) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Surface(
        modifier = modifier,
        color = Ink.copy(alpha = .76f),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CaptureFraming.entries.forEach { option ->
                    val selected = option == framing
                    Surface(
                        color = if (selected) WarmCoral else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onFramingChange(option) },
                    ) {
                        Text(
                            option.label,
                            color = Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Text(
                if (framing == CaptureFraming.TWO_PAGE_SPREAD && !isLandscape) "横屏拍双页更清晰 · ↑顶部朝上 · 书脊居中"
                else if (framing == CaptureFraming.TWO_PAGE_SPREAD) "↑ 文字顶部朝上 · 书脊对准中线"
                else "↑ 文字顶部朝上 · 只放入这一页",
                color = Color.White.copy(alpha = .9f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
            )
        }
    }
}

@Composable
private fun BookCaptureGuide(
    framing: CaptureFraming,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        val guideColor = if (active) Honey else Color.White.copy(alpha = .94f)
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val shadowWidth = 7.dp.toPx()
            val shadowColor = Ink.copy(alpha = .72f)
            val inset = strokeWidth / 2f
            val cornerLength = minOf(30.dp.toPx(), size.minDimension * .18f)
            val right = size.width - inset
            val bottom = size.height - inset
            val top = inset
            val left = inset

            fun guideLine(start: Offset, end: Offset) {
                drawLine(shadowColor, start, end, shadowWidth, StrokeCap.Round)
                drawLine(guideColor, start, end, strokeWidth, StrokeCap.Round)
            }

            fun corner(x: Float, y: Float, horizontal: Float, vertical: Float) {
                guideLine(Offset(x, y), Offset(x + horizontal, y))
                guideLine(Offset(x, y), Offset(x, y + vertical))
            }

            corner(left, top, cornerLength, cornerLength)
            corner(right, top, -cornerLength, cornerLength)
            corner(left, bottom, cornerLength, -cornerLength)
            corner(right, bottom, -cornerLength, -cornerLength)

            if (framing == CaptureFraming.TWO_PAGE_SPREAD) {
                guideLine(
                    Offset(size.width / 2f, size.height * .08f),
                    Offset(size.width / 2f, size.height * .92f),
                )
            }
        }
    }
}

@Composable
internal fun BookCaptureViewport(
    controller: LifecycleCameraController,
    framing: CaptureFraming,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val targetAspect = if (androidx.compose.ui.platform.LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            4f / 3f
        } else {
            3f / 4f
        }
        val cameraViewport = centeredCaptureFrame(maxWidth.value, maxHeight.value, targetAspect)
        val captureFrame = centeredCaptureFrame(cameraViewport.width, cameraViewport.height, framing)
        Box(
            Modifier.width(cameraViewport.width.dp).height(cameraViewport.height.dp).align(Alignment.Center).background(Ink),
        ) {
            Box(
                Modifier.width(captureFrame.width.dp).height(captureFrame.height.dp).align(Alignment.Center),
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            this.controller = controller
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                BookCaptureGuide(framing, active = active, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun CaptureStatusChip(text: String) {
    Surface(color = Ink.copy(alpha = .82f), shape = RoundedCornerShape(16.dp)) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun CaptureShutter(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    circular: Boolean,
) {
    if (circular) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(64.dp).semantics { this.contentDescription = contentDescription },
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmber, contentColor = WarmBrown),
            ) { Text("拍", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 112.dp),
            )
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().height(58.dp)
                .semantics { this.contentDescription = contentDescription },
            shape = RoundedCornerShape(29.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarmAmber, contentColor = WarmBrown),
        ) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private typealias CaptureContent = @Composable () -> Unit

@Composable
internal fun BookCaptureScaffold(
    controller: LifecycleCameraController,
    framing: CaptureFraming,
    active: Boolean,
    onBack: () -> Unit,
    backContentDescription: String,
    onFramingChange: (CaptureFraming) -> Unit,
    portraitBottomInset: Dp,
    landscapeStartRailWidth: Dp,
    landscapeEndRailWidth: Dp,
    topStatus: String? = null,
    portraitTopEnd: CaptureContent? = null,
    landscapeStartExtra: CaptureContent = {},
    portraitControls: CaptureContent,
    landscapeControls: CaptureContent,
) {
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        BookCaptureViewport(
            controller = controller,
            framing = framing,
            active = active,
            modifier = Modifier.fillMaxSize().then(
                if (isLandscape) Modifier.padding(start = landscapeStartRailWidth, end = landscapeEndRailWidth)
                else Modifier.padding(bottom = portraitBottomInset),
            ),
        )
        if (isLandscape) {
            Column(
                Modifier.align(Alignment.CenterStart).width(landscapeStartRailWidth).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = Ink.copy(alpha = .76f), shape = CircleShape) {
                    AppIconButton(AppIcons.Back, backContentDescription, onBack, Modifier.size(48.dp), tint = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                CaptureFramingSelector(framing, onFramingChange)
                if (topStatus != null) {
                    Spacer(Modifier.height(10.dp))
                    CaptureStatusChip(topStatus)
                }
                landscapeStartExtra()
            }
            Column(
                Modifier.align(Alignment.CenterEnd).width(landscapeEndRailWidth).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { landscapeControls() }
        } else {
            Surface(
                color = Ink.copy(alpha = .76f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                AppIconButton(AppIcons.Back, backContentDescription, onBack, Modifier.size(48.dp), tint = Color.White)
            }
            portraitTopEnd?.let { content ->
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { content() }
            }
            topStatus?.let {
                Surface(
                    color = Ink.copy(alpha = .82f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                ) { Text(it, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
            }
            CaptureFramingSelector(
                framing,
                onFramingChange,
                Modifier.align(Alignment.TopCenter).padding(top = if (topStatus == null) 14.dp else 58.dp),
            )
            Column(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { portraitControls() }
        }
    }
}

@Composable
internal fun KeepScreenOn(enabled: Boolean = true) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val window = (context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
