package com.example.nexora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NexoraPrimaryGreen,
    onPrimary = NexoraWhite,
    secondary = NexoraSoftGreen,
    onSecondary = NexoraPrimaryText,
    background = NexoraBackground,
    onBackground = NexoraPrimaryText,
    surface = NexoraWhite,
    onSurface = NexoraPrimaryText,
    surfaceVariant = NexoraSoftGreen,
    onSurfaceVariant = NexoraMutedText,
    outline = NexoraBorder,
    error = NexoraError,
    onError = NexoraWhite
)

// Dark mode currently uses a darker version of the same palette
private val DarkColorScheme = darkColorScheme(
    primary = NexoraPrimaryGreen,
    onPrimary = NexoraPrimaryText,
    secondary = NexoraPrimaryText,
    onSecondary = NexoraSoftGreen,
    background = NexoraPrimaryText,
    onBackground = NexoraBackground,
    surface = NexoraPrimaryText,
    onSurface = NexoraBackground,
    surfaceVariant = NexoraMutedText,
    onSurfaceVariant = NexoraSoftGreen,
    outline = NexoraMutedText,
    error = NexoraError,
    onError = NexoraWhite
)

val NexoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NexoraShapes,
        content = content
    )
}
