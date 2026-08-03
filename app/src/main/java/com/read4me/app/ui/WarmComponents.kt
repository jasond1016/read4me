package com.read4me.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val WarmPaper = Color(0xFFFFF9EF)
val WarmBrown = Color(0xFF49352A)
val WarmAmber = Color(0xFFE8A936)
val WarmCoral = Color(0xFFD9604C)
val WarmMoss = Color(0xFF64745B)
val WarmLine = Color(0xFFEADCC8)

@Composable
fun WarmCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) { content() }
}

@Composable
fun WarmPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WarmAmber, contentColor = WarmBrown),
    ) {
        icon?.let { AppIcon(it, null, tint = WarmBrown, size = 28.dp) }
        Text(text, fontWeight = FontWeight.Bold, modifier = if (icon == null) Modifier else Modifier.padding(start = 8.dp))
    }
}

@Composable
fun WarmTopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                AppIcon(AppIcons.Back, null, tint = WarmMoss)
                Text("返回", color = WarmMoss, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = WarmBrown, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WarmIllustration(@DrawableRes resource: Int, description: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(resource),
        contentDescription = description,
        modifier = modifier.clip(RoundedCornerShape(26.dp)),
        contentScale = ContentScale.Crop,
    )
}

enum class WarmShellTab { LIBRARY, READING, RECORD, ME }

@Composable
fun WarmBottomShell(selected: WarmShellTab, modifier: Modifier = Modifier, onSelect: (WarmShellTab) -> Unit) {
    Surface(modifier = modifier, color = Color(0xFFFFFEFB), shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Triple(WarmShellTab.LIBRARY, AppIcons.Library, "书架"),
                Triple(WarmShellTab.READING, AppIcons.Reading, "陪读"),
                Triple(WarmShellTab.RECORD, AppIcons.LibraryAdd, "录制"),
                Triple(WarmShellTab.ME, AppIcons.Home, "我的"),
            ).forEach { (tab, glyph, label) ->
                val active = selected == tab
                TextButton(
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(30.dp).then(
                                if (active) Modifier.background(WarmCoral.copy(alpha = .12f), CircleShape)
                                else Modifier,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(
                                glyph,
                                contentDescription = null,
                                tint = if (active) WarmCoral else WarmBrown.copy(.58f),
                                size = 24.dp,
                            )
                        }
                        Text(label, color = if (active) WarmCoral else WarmBrown.copy(.62f), style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
