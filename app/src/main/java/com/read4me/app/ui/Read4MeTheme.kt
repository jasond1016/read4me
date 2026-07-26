package com.read4me.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF6F0E5)
val Ink = Color(0xFF302D29)
val Coral = Color(0xFFD9654F)
val Moss = Color(0xFF526B57)
val Honey = Color(0xFFF1C56F)
val SoftWhite = Color(0xFFFFFBF4)

private val Read4MeColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Moss,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = SoftWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9DFD0),
    outline = Color(0xFF8C8173),
)

private val Read4MeTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 21.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
)

@Composable
fun Read4MeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Read4MeColors,
        typography = Read4MeTypography,
        content = content,
    )
}
