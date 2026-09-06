package com.read4me.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.read4me.app.model.RecordingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SetupScreen(
    message: String?,
    onBack: () -> Unit,
    onStart: (String, RecordingMode) -> Unit,
) {
    BackHandler(onBack = onBack)
    var title by rememberSaveable {
        mutableStateOf("我们的故事 · ${SimpleDateFormat("M月d日", Locale.CHINA).format(Date())}")
    }
    var mode by rememberSaveable { mutableStateOf(RecordingMode.CAMERA) }
    val focusManager = LocalFocusManager.current
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            WarmTopBar("录一本新书", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .widthIn(max = 600.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("读一次，随时听", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("选好录制方式，下一步就可以开始。", color = Moss)
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("绘本名称") }, singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("之后也可以在书架中重命名") },
                )
                Text("录制方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecordingModeOption(
                        "自动记录翻页 · 推荐", "拍下书面并自动分段，孩子翻书就能听。", mode == RecordingMode.CAMERA,
                    ) { mode = RecordingMode.CAMERA }
                    RecordingModeOption(
                        "只录声音", "无需摄像头；每次翻页时，点一下按钮分段。", mode == RecordingMode.MANUAL,
                    ) { mode = RecordingMode.MANUAL }
                }
                Surface(color = Moss.copy(alpha = .08f), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        if (mode == RecordingMode.CAMERA) "把设备固定在绘本上方，让整页进入画面，并保持光线均匀。下一步可预览画面，准备好后再开始录音。"
                        else "只需要麦克风权限。录完即可整本播放，之后补拍书面也能使用翻页听。",
                        modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
            }
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 24.dp, vertical = 12.dp)) {
                WarmPrimaryButton(
                    if (mode == RecordingMode.CAMERA) "下一步：对准绘本" else "下一步：准备录音",
                    { focusManager.clearFocus(); onStart(title.trim().ifBlank { "我们的故事" }, mode) },
                    Modifier.fillMaxWidth(), AppIcons.Forward,
                )
            }
        }
    }
}

@Composable
private fun RecordingModeOption(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        color = if (selected) Honey.copy(alpha = .12f) else SoftWhite,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Moss else WarmLine),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Moss)
            }
        }
    }
}
