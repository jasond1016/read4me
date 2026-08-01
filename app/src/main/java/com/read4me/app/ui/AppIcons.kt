package com.read4me.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.read4me.app.R

/** Semantic names for the locally bundled Material Symbols Rounded assets. */
object AppIcons {
    val Back = R.drawable.ic_arrow_back
    val Forward = R.drawable.ic_arrow_forward
    val More = R.drawable.ic_more_vert
    val Fullscreen = R.drawable.ic_fullscreen
    val Play = R.drawable.ic_play_arrow
    val Pause = R.drawable.ic_pause
    val Previous = R.drawable.ic_skip_previous
    val Next = R.drawable.ic_skip_next
    val Replay = R.drawable.ic_replay
    val ForwardMedia = R.drawable.ic_forward_media
    val Restart = R.drawable.ic_restart_alt
    val List = R.drawable.ic_format_list_bulleted
    val Timer = R.drawable.ic_timer
    val Speed = R.drawable.ic_speed
    val ExpandLess = R.drawable.ic_expand_less
    val ExpandMore = R.drawable.ic_expand_more
    val Library = R.drawable.ic_library_books
    val Reading = R.drawable.ic_menu_book
    val Add = R.drawable.ic_add
    val Home = R.drawable.ic_home
    val Drag = R.drawable.ic_drag_handle
}

@Composable
fun AppIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
) = Icon(
    painterResource(icon),
    contentDescription,
    modifier.size(size),
    if (tint == Color.Unspecified) LocalContentColor.current else tint,
)

@Composable
fun AppIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconSize: Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(icon),
            contentDescription,
            Modifier.size(iconSize),
            if (tint == Color.Unspecified) LocalContentColor.current else tint,
        )
    }
}
