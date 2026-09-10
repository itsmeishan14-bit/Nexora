package com.example.nexora.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.ui.unit.dp

object NexoraMotion {
    // Easing specs
    val QuickSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val SmoothSpec = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)
    val SpringSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 400f)

    // Layout constants
    val EntranceOffset = 16.dp
    const val SectionStagger = 100
}
