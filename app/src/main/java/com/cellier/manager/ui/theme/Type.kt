package com.cellier.manager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Typography Material 3 avec police système.
// Les tailles sont adaptées à un téléphone (base 16sp).
// Si un jour on veut une police custom (Poppins / Inter), on ajoute les fonts
// dans res/font/ et on les référence ici.

private val baseFont = FontFamily.SansSerif

val CellierTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp
    ),
    displayMedium = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = baseFont, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp
    )
)
