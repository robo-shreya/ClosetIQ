package com.closetiq.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Nocturne specifies Inter throughout. Inter is not bundled here — shipping a font file
 * or wiring downloadable fonts is not worth it before the deadline — so this uses the
 * platform sans and carries the parts of the spec that actually do the work: the sizes,
 * the 500 weight ceiling on headings, and the negative tracking that makes headings sit
 * tight. Swapping in Inter later is a one-line change to [NocturneFont].
 *
 * Hierarchy here is size and space. Headings never go past weight 500.
 */
private val NocturneFont = FontFamily.SansSerif

val NocturneTypography = Typography(
    // Screen titles.
    headlineMedium = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.52).sp
    ),
    // The closet's headline percentage.
    displaySmall = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.02).sp
    ),
    // Card titles — the hero garment, the undertone.
    titleMedium = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.17).sp
    ),
    titleSmall = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5f.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5f.sp,
        lineHeight = 22.sp
    ),
    // The reason line, and most explanatory copy.
    bodyMedium = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.5f.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5f.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // Kickers and eyebrows. Uppercase and tracking are applied where used.
    labelSmall = TextStyle(
        fontFamily = NocturneFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5f.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.05f.sp
    )
)
