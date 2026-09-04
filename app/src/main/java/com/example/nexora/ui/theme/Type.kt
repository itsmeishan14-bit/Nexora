package com.example.nexora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Nexora typography
 *
 * Manrope -> headlines and titles
 * Inter   -> body and supporting text
 *
 * Font files will be connected once they are added to res/font.
 */

// Temporary font families.
// These will be replaced with Manrope and Inter after the
// font files are added to res/font.
private val NexoraHeadlineFont = FontFamily.SansSerif
private val NexoraBodyFont = FontFamily.SansSerif

val Typography = Typography(

    // ============================================================
    // HEADLINES
    // ============================================================

    headlineLarge = TextStyle(
        fontFamily = NexoraHeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),

    // ============================================================
    // TITLES
    // ============================================================

    titleLarge = TextStyle(
        fontFamily = NexoraHeadlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),

    // ============================================================
    // BODY
    // ============================================================

    bodyMedium = TextStyle(
        fontFamily = NexoraBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),

    // ============================================================
    // SMALL LABELS
    // ============================================================

    labelSmall = TextStyle(
        fontFamily = NexoraBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp
    )
)