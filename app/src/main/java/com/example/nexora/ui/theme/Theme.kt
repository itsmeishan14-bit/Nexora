package com.example.nexora.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NexoraGreen,
    secondary = NexoraLightGreen,
    tertiary = NexoraGold,
    background = NexoraForest,
    surface = NexoraForest
)

private val LightColorScheme = lightColorScheme(
    primary = NexoraGreen,
    secondary = NexoraForest,
    tertiary = NexoraGold,
    background = NexoraCream,
    surface = NexoraWhite
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