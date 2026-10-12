package com.example.nexora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================
// MATERIAL 3 COLOR SCHEMES
// ============================================================

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = SagePillBgLight,
    onPrimaryContainer = ForestGreenDeep,
    secondary = ClayWarmPrimary,
    onSecondary = Color.White,
    secondaryContainer = ClaySurfaceLight,
    onSecondaryContainer = ClayWarmDeep,
    background = NexoraBgLight,
    onBackground = NexoraTextPrimaryLight,
    surface = NexoraSurfacePureLight,
    onSurface = NexoraTextPrimaryLight,
    surfaceVariant = NexoraSurfaceContainerLight,
    onSurfaceVariant = NexoraTextSecondaryLight,
    outline = NexoraBorderHairlineLight,
    outlineVariant = NexoraBorderSubtleLight,
    error = StatusError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = LuminousSage,
    onPrimary = NexoraBgDark,
    primaryContainer = SagePillBgDark,
    onPrimaryContainer = MintSage,
    secondary = ClayWarmAccent,
    onSecondary = ClayWarmDeep,
    secondaryContainer = ClaySurfaceDark,
    onSecondaryContainer = ClayWarmAccent,
    background = NexoraBgDark,
    onBackground = NexoraTextPrimaryDark,
    surface = NexoraSurfacePureDark,
    onSurface = NexoraTextPrimaryDark,
    surfaceVariant = NexoraSurfaceContainerDark,
    onSurfaceVariant = NexoraTextSecondaryDark,
    outline = NexoraBorderHairlineDark,
    outlineVariant = NexoraBorderSubtleDark,
    error = PriorityUrgent,
    onError = NexoraBgDark
)

// ============================================================
// CUSTOM SEMANTIC DESIGN TOKENS
// ============================================================

@Immutable
data class NexoraCustomColors(
    val surfaceElevated: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentGreen: Color,
    val accentGreenMuted: Color,
    val aiSurface: Color,
    val aiBorder: Color,
    val aiAccent: Color,
    val priorityUrgent: Color,
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
    val success: Color,
    val warning: Color,
    val error: Color
)

private val LightCustomColors = NexoraCustomColors(
    surfaceElevated = NexoraSurfaceElevatedLight,
    cardBackground = NexoraSurfacePureLight,
    cardBorder = NexoraBorderHairlineLight,
    textPrimary = NexoraTextPrimaryLight,
    textSecondary = NexoraTextSecondaryLight,
    textTertiary = NexoraTextTertiaryLight,
    accentGreen = ForestGreenPrimary,
    accentGreenMuted = SagePillBgLight,
    aiSurface = ClaySurfaceLight,
    aiBorder = ClayBorderLight,
    aiAccent = ClayWarmAccent,
    priorityUrgent = PriorityUrgent,
    priorityHigh = PriorityHigh,
    priorityMedium = PriorityMedium,
    priorityLow = PriorityLow,
    success = StatusSuccess,
    warning = StatusWarning,
    error = StatusError
)

private val DarkCustomColors = NexoraCustomColors(
    surfaceElevated = NexoraSurfaceElevatedDark,
    cardBackground = NexoraSurfacePureDark,
    cardBorder = NexoraBorderHairlineDark,
    textPrimary = NexoraTextPrimaryDark,
    textSecondary = NexoraTextSecondaryDark,
    textTertiary = NexoraTextTertiaryDark,
    accentGreen = LuminousSage,
    accentGreenMuted = SagePillBgDark,
    aiSurface = ClaySurfaceDark,
    aiBorder = ClayBorderDark,
    aiAccent = ClayWarmAccent,
    priorityUrgent = PriorityUrgent,
    priorityHigh = PriorityHigh,
    priorityMedium = PriorityMediumDark,
    priorityLow = PriorityLowDark,
    success = StatusSuccess,
    warning = StatusWarning,
    error = StatusError
)

val LocalNexoraCustomColors = staticCompositionLocalOf { LightCustomColors }

object NexoraTheme {
    val customColors: NexoraCustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNexoraCustomColors.current
}

// ============================================================
// SHAPES
// ============================================================

val NexoraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ============================================================
// ROOT THEME COMPOSABLE
// ============================================================

@Composable
fun NexoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) DarkCustomColors else LightCustomColors

    CompositionLocalProvider(
        LocalNexoraCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = NexoraShapes,
            content = content
        )
    }
}
