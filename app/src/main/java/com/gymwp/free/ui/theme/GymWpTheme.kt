package com.gymwp.free.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GymOrange = Color(0xFFFF7711)
val GymOrangeDark = Color(0xFFFF6301)
val GymAccentCyan = Color(0xFF00B8D4)
val GymDarkBg = Color(0xFF121212)
val GymCardBg = Color(0xFF1F1F1F)
val GymSurfaceElevated = Color(0xFF282828)

// Muscle Fatigue 4-Band Colors
val FatigueGreen = Color(0xFF4CAF50)
val FatigueBlue = Color(0xFF2196F3)
val FatigueYellow = Color(0xFFFFEB3B)
val FatigueRed = Color(0xFFDB4437)
val SkinToneBase = Color(0xFFF2CFC4)

// 10 Day Routine Colors extracted from GymWP resources.arsc
val DayPalette = listOf(
    Color(0xFFDB4437), // 0: Red
    Color(0xFFE91E63), // 1: Pink
    Color(0xFF9C27B0), // 2: Purple
    Color(0xFF3F51B5), // 3: Indigo
    Color(0xFF4285F4), // 4: Blue
    Color(0xFF03A9F4), // 5: Light Blue
    Color(0xFF009688), // 6: Teal
    Color(0xFF0F9D58), // 7: Green
    Color(0xFFCDDC39), // 8: Lime
    Color(0xFFFF7F27)  // 9: Orange
)

fun getFatigueColor(percent: Int): Color = when {
    percent < 20 -> FatigueGreen
    percent < 45 -> FatigueBlue
    percent < 70 -> FatigueYellow
    else -> FatigueRed
}

private val GymWpColorScheme = darkColorScheme(
    primary = GymOrange,
    onPrimary = Color.White,
    primaryContainer = GymOrangeDark,
    secondary = GymAccentCyan,
    onSecondary = Color.Black,
    background = GymDarkBg,
    onBackground = Color.White,
    surface = GymCardBg,
    onSurface = Color.White,
    surfaceVariant = GymSurfaceElevated,
    onSurfaceVariant = Color(0xFFCCCCCC)
)

@Composable
fun GymWpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GymWpColorScheme,
        content = content
    )
}
