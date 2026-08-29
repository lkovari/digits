package com.lkovari.mobile.apps.digits.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NumbersBlue = Color(0xFF1E88E5)
val NumbersBlueLight = Color(0xFFBBDEFB)
val NumbersBlueWash = Color(0xFFEBF9FD)
val NumbersBluePastel = Color(0xFFD6EEF8)
val NumbersSelected = Color(0xFF90CAF9)
val NumbersCompleted = Color(0xFF43A047)
val NumbersCompletedPastel = Color(0xFFC8E6C9)
val NumbersIncompletePastel = Color(0xFFFFE0B2)
val NumbersInk = Color(0xFF212121)
val NumbersSurface = Color(0xFFFFFFFF)
val NumbersMagenta = Color(0xFFE91E63)

private val NumbersColorScheme = lightColorScheme(
    primary = NumbersBlue,
    onPrimary = Color.White,
    secondary = NumbersBlueLight,
    onSecondary = NumbersInk,
    background = NumbersBlueWash,
    onBackground = NumbersInk,
    surface = NumbersBlueWash,
    onSurface = NumbersInk
)

@Composable
fun NumbersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NumbersColorScheme,
        content = content
    )
}
