package com.gymwp.free.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewBg: Color,
    val isLight: Boolean
) {
    ORANGE_RED(
        id = "orange_red",
        displayName = "Naranjo con Rojo (Por defecto)",
        subtitle = "Modo oscuro deportivo clásico",
        previewPrimary = Color(0xFFFF6D00),
        previewSecondary = Color(0xFFE53935),
        previewBg = Color(0xFF121214),
        isLight = false
    ),
    PINK_BLACK(
        id = "pink_black",
        displayName = "Rosado con Negro",
        subtitle = "Modo oscuro elegante en tonos rosa",
        previewPrimary = Color(0xFFFF4081),
        previewSecondary = Color(0xFFFF80AB),
        previewBg = Color(0xFF0E0E12),
        isLight = false
    ),
    PINK_WHITE(
        id = "pink_white",
        displayName = "Rosado con Blanco",
        subtitle = "Modo claro luminoso en tonos rosa",
        previewPrimary = Color(0xFFE91E63),
        previewSecondary = Color(0xFFAD1457),
        previewBg = Color(0xFFFFF6F9),
        isLight = true
    ),
    YELLOW_GREEN(
        id = "yellow_green",
        displayName = "Amarillo con Verde",
        subtitle = "Edición especial Cheese WP",
        previewPrimary = Color(0xFFFFB300),
        previewSecondary = Color(0xFF43A047),
        previewBg = Color(0xFF131814),
        isLight = false
    ),
    ORANGE_WHITE(
        id = "orange_white",
        displayName = "Naranjo con Blanco",
        subtitle = "Modo claro limpio y energético",
        previewPrimary = Color(0xFFFF6D00),
        previewSecondary = Color(0xFFD84315),
        previewBg = Color(0xFFF5F6F9),
        isLight = true
    );

    companion object {
        fun fromId(id: String?): AppThemeOption =
            entries.firstOrNull { it.id == id } ?: ORANGE_RED
    }
}

data class GymPalette(
    val primary: Color,
    val primaryDark: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val card: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isLight: Boolean
)

private fun paletteFor(theme: AppThemeOption): GymPalette = when (theme) {
    AppThemeOption.ORANGE_RED -> GymPalette(
        primary = Color(0xFFFF6D00),
        primaryDark = Color(0xFFD83B2D),
        onPrimary = Color.White,
        secondary = Color(0xFFFF5252),
        background = Color(0xFF121214),
        card = Color(0xFF1E1E22),
        surfaceElevated = Color(0xFF292930),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFC7C7D1),
        isLight = false
    )
    AppThemeOption.PINK_BLACK -> GymPalette(
        primary = Color(0xFFFF4081),
        primaryDark = Color(0xFFC51162),
        onPrimary = Color.White,
        secondary = Color(0xFFFF80AB),
        background = Color(0xFF0D0D11),
        card = Color(0xFF181820),
        surfaceElevated = Color(0xFF252431),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFD4C5CE),
        isLight = false
    )
    AppThemeOption.PINK_WHITE -> GymPalette(
        primary = Color(0xFFE91E63),
        primaryDark = Color(0xFFC2185B),
        onPrimary = Color.White,
        secondary = Color(0xFFAD1457),
        background = Color(0xFFFFF5F8),
        card = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFFCE4EC),
        textPrimary = Color(0xFF22151C),
        textSecondary = Color(0xFF694D5B),
        isLight = true
    )
    AppThemeOption.YELLOW_GREEN -> GymPalette(
        primary = Color(0xFFFFB300),
        primaryDark = Color(0xFF2E7D32),
        onPrimary = Color(0xFF1B1400),
        secondary = Color(0xFF4CAF50),
        background = Color(0xFF121713),
        card = Color(0xFF1B231D),
        surfaceElevated = Color(0xFF263229),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFC4D4C7),
        isLight = false
    )
    AppThemeOption.ORANGE_WHITE -> GymPalette(
        primary = Color(0xFFFF6D00),
        primaryDark = Color(0xFFE65100),
        onPrimary = Color.White,
        secondary = Color(0xFFD84315),
        background = Color(0xFFF4F6F9),
        card = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFFFEFE5),
        textPrimary = Color(0xFF181A1F),
        textSecondary = Color(0xFF5A606D),
        isLight = true
    )
}

val LocalGymPalette = staticCompositionLocalOf { paletteFor(AppThemeOption.ORANGE_RED) }

val GymOrange: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.primary

val GymOrangeDark: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.primaryDark

val GymOnPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.onPrimary

val GymAccentCyan: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.secondary

val GymDarkBg: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.background

val GymCardBg: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.card

val GymSurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.surfaceElevated

val GymTextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.textPrimary

val GymTextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.textSecondary

val GymBorder: Color
    @Composable @ReadOnlyComposable get() = LocalGymPalette.current.textSecondary.copy(alpha = 0.22f)

// Muscle Fatigue 4-Band Colors
val FatigueGreen = Color(0xFF4CAF50)
val FatigueBlue = Color(0xFF2196F3)
val FatigueYellow = Color(0xFFFFB300)
val FatigueRed = Color(0xFFDB4437)
val SkinToneBase = Color(0xFFE8C3B8)

// 10 Day Routine Colors
val DayPalette = listOf(
    Color(0xFFDB4437), // 0: Red
    Color(0xFFE91E63), // 1: Pink
    Color(0xFF9C27B0), // 2: Purple
    Color(0xFF3F51B5), // 3: Indigo
    Color(0xFF4285F4), // 4: Blue
    Color(0xFF0288D1), // 5: Light Blue
    Color(0xFF00897B), // 6: Teal
    Color(0xFF2E7D32), // 7: Green
    Color(0xFFF9A825), // 8: Golden Yellow
    Color(0xFFFF6D00)  // 9: Orange
)

fun getFatigueColor(percent: Int): Color = when {
    percent < 20 -> FatigueGreen
    percent < 45 -> FatigueBlue
    percent < 70 -> FatigueYellow
    else -> FatigueRed
}

@Composable
fun GymWpTheme(
    themeOption: AppThemeOption = AppThemeOption.ORANGE_RED,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeOption)
    val colorScheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.surfaceElevated,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.secondary,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary
        )
    } else {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryDark,
            onPrimaryContainer = Color.White,
            secondary = palette.secondary,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary
        )
    }

    CompositionLocalProvider(LocalGymPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
