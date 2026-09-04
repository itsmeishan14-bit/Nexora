package com.example.nexora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NexoraGreen,
    onPrimary = NexoraForest,

    secondary = NexoraLightGreen,
    onSecondary = NexoraForest,

    tertiary = NexoraGold,
    onTertiary = NexoraForest,

    background = NexoraForest,
    onBackground = NexoraWhite,

    surface = NexoraForest,
    onSurface = NexoraWhite,

    surfaceVariant = NexoraText,
    onSurfaceVariant = NexoraLightGreen,

    outline = NexoraBorder,

    error = NexoraRed,
    onError = NexoraWhite
)

private val LightColorScheme = lightColorScheme(
    primary = NexoraGreen,
    onPrimary = NexoraWhite,

    secondary = NexoraLightGreen,
    onSecondary = NexoraText,

    tertiary = NexoraGold,
    onTertiary = NexoraWhite,

    background = NexoraCream,
    onBackground = NexoraText,

    surface = NexoraWhite,
    onSurface = NexoraText,

    surfaceVariant = NexoraLightGreen,
    onSurfaceVariant = NexoraSecondaryText,

    outline = NexoraBorder,

    error = NexoraRed,
    onError = NexoraWhite
)

@Composable
fun NexoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}