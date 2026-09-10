package com.example.nexora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Green60,
    onPrimary = Color.White,
    secondary = Clay60,
    onSecondary = Color.White,
    background = NexoraBackgroundLight,
    onBackground = NexoraPrimaryTextLight,
    surface = Color.White,
    onSurface = NexoraPrimaryTextLight,
    outline = Gray90,
    error = NexoraError
)

private val DarkColorScheme = darkColorScheme(
    primary = Green60,
    onPrimary = Green10,
    secondary = Clay60,
    onSecondary = Clay10,
    background = NexoraBackgroundDark,
    onBackground = NexoraPrimaryTextDark,
    surface = NexoraSurfaceDark,
    onSurface = NexoraPrimaryTextDark,
    surfaceVariant = Green30,
    outline = Green30,
    error = NexoraError
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
