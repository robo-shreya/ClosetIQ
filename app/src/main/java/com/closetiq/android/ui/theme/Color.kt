package com.closetiq.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Nocturne — a quiet, compact dark interface.
 *
 * A near-neutral blue-grey ground with one blurple accent used as a line, a mark and a
 * glow rather than a flood. Contrast comes from the tonal ramps, not from saturation,
 * which is why the garment swatches read as the only saturated things on screen.
 *
 * Values are the design system's tokens verbatim. Material3's scheme cannot carry the
 * full 100–900 ramps, so the steps the UI needs are exposed here directly.
 */
object Nocturne {

    val Bg = Color(0xFF161826)
    val Surface = Color(0xFF232532)
    val Text = Color(0xFFE9E9ED)

    val Accent = Color(0xFF9184D9)
    val Accent200 = Color(0xFFE7E5FE)
    val Accent300 = Color(0xFFD2CEFD)
    val Accent600 = Color(0xFF796CBF)
    val Accent700 = Color(0xFF5D5294)
    val Accent800 = Color(0xFF423A6A)
    val Accent900 = Color(0xFF2B2741)

    val Neutral300 = Color(0xFFCFD3E5)
    val Neutral400 = Color(0xFFB2B6CA)
    val Neutral500 = Color(0xFF9397AB)
    val Neutral600 = Color(0xFF75798C)
    val Neutral700 = Color(0xFF595D6C)
    val Neutral800 = Color(0xFF3F424D)
    val Neutral900 = Color(0xFF292B31)

    /** Bottom bar ground — a step below the page, so the bar reads as chrome. */
    val TabBar = Color(0xFF14161F)

    /** The skin card's 160° gradient. */
    val SkinCardTop = Color(0xFF20222F)
    val SkinCardBottom = Color(0xFF1A1C29)

    /** Inset fields and the add-item drop area. */
    val Field = Color(0xFF1B1D2A)

    /** Rules fade to transparent at their ends — a Nocturne signature. */
    val Divider = Color(0x29E9E9ED)
}
