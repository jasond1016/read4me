package com.read4me.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
        border = BorderStroke(1.dp, WarmLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { content() }
}

@Composable
fun WarmPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
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
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = WarmBrown, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WarmIllustration(
    @DrawableRes resource: Int,
    description: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = description,
        modifier = modifier.clip(RoundedCornerShape(26.dp)),
        contentScale = contentScale,
    )
}

enum class WarmShellTab { LIBRARY, READING, RECORD, ME }

@Composable
fun WarmBottomShell(selected: WarmShellTab, modifier: Modifier = Modifier, onSelect: (WarmShellTab) -> Unit) {
    NavigationBar(modifier = modifier, containerColor = SoftWhite, tonalElevation = 0.dp) {
        listOf(
            Triple(WarmShellTab.LIBRARY, AppIcons.Library, "书架"),
            Triple(WarmShellTab.READING, AppIcons.Reading, "翻页听"),
            Triple(WarmShellTab.RECORD, AppIcons.LibraryAdd, "录新书"),
            Triple(WarmShellTab.ME, AppIcons.Home, "管理"),
        ).forEach { (tab, glyph, label) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { AppIcon(glyph, null) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Ink, selectedTextColor = Ink,
                    indicatorColor = Honey.copy(alpha = .25f),
                    unselectedIconColor = Moss, unselectedTextColor = Moss,
                ),
            )
        }
    }
}
