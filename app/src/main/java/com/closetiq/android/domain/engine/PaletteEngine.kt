package com.closetiq.android.domain.engine

import com.closetiq.android.domain.color.ColorMath
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.Palette
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.model.Undertone

/**
 * Turns a skin reading into a palette, and scores a garment against that palette.
 *
 * This is the stable half of the brain: it depends only on undertone and Fitzpatrick type,
 * which barely change. The volatile half — today's redness, dullness, dark circles —
 * lives in SkinStateModifier.
 */
object PaletteEngine {

    /**
     * Beyond this Lab distance from every anchor, a colour scores 0. Roughly the
     * distance between navy and coral — genuinely unrelated colours.
     */
    const val MAX_USEFUL_DISTANCE = 60f

    /**
     * Lightness counts for half as much as hue when measuring distance to an anchor.
     *
     * A navy and a lighter blue both suit the same person; a navy and an olive do not.
     * Plain ΔE weights L equally with a and b, which would punish a pale version of a
     * flattering colour as hard as an outright wrong one.
     */
    private const val LIGHTNESS_WEIGHT = 0.5f

    /** Golden, earthy, sunlit. Positive b throughout. */
    private val WARM_ANCHORS = listOf(
        LabColor(45f, 30f, 35f),   // rust
        LabColor(70f, 8f, 55f),    // mustard
        LabColor(45f, -8f, 25f),   // olive
        LabColor(88f, 2f, 14f),    // cream
        LabColor(32f, 12f, 20f),   // chocolate
        LabColor(66f, 32f, 24f),   // coral
        LabColor(40f, -20f, 22f),  // moss
        LabColor(58f, 18f, 30f)    // caramel
    )

    /** Blue-based, cool, jewel-toned. Mostly negative b. */
    private val COOL_ANCHORS = listOf(
        LabColor(28f, 3f, -24f),   // navy
        LabColor(46f, -32f, 12f),  // emerald
        LabColor(32f, 30f, 0f),    // berry
        LabColor(94f, 0f, -2f),    // optic white
        LabColor(35f, 0f, -3f),    // charcoal
        LabColor(62f, 12f, -18f),  // lavender
        LabColor(52f, -22f, -6f),  // teal
        LabColor(45f, 2f, -35f)    // true blue
    )

    /** Muted and low-chroma — the tones that read as neither warm nor cool. */
    private val NEUTRAL_ANCHORS = listOf(
        LabColor(34f, 2f, -16f),   // soft navy
        LabColor(60f, 4f, 10f),    // taupe
        LabColor(64f, -12f, 12f),  // sage
        LabColor(92f, 0f, 4f),     // soft white
        LabColor(45f, -2f, -6f),   // slate
        LabColor(60f, 18f, 8f),    // dusty rose
        LabColor(30f, 4f, 6f),     // espresso
        LabColor(72f, -6f, 2f)     // stone
    )

    /**
     * The set of anchor colours that suit this person.
     *
     * Undertone picks the family; Fitzpatrick tunes it. These are hand-picked constants
     * rather than anything generated — colour analysis is a matter of taste, and taste
     * is easier to argue with when it is written down as eight literal values.
     */
    fun buildPalette(reading: SkinReading): Palette {
        val base = when (reading.undertone) {
            Undertone.WARM -> WARM_ANCHORS
            Undertone.COOL -> COOL_ANCHORS
            Undertone.NEUTRAL -> NEUTRAL_ANCHORS
        }

        return Palette(
            anchors = base.map { forFitzpatrick(it, reading.fitzpatrick) },
            undertone = reading.undertone
        )
    }

    /**
     * Deeper skin carries saturation and contrast that lighter skin gets overwhelmed by,
     * so chroma scales up with Fitzpatrick type and lightness spreads further from mid.
     * Both effects are deliberately gentle — this nudges the palette, it does not
     * replace it.
     */
    private fun forFitzpatrick(anchor: LabColor, fitzpatrick: Int): LabColor {
        val type = fitzpatrick.coerceIn(1, 6)

        val chromaScale = 0.85f + (type - 1) * 0.06f      // 0.85 at I, 1.15 at VI
        val contrast = 1f + (type - 3.5f) * 0.03f          // pushes L away from 50

        return LabColor(
            l = (50f + (anchor.l - 50f) * contrast).coerceIn(0f, 100f),
            a = anchor.a * chromaScale,
            b = anchor.b * chromaScale
        )
    }

    /**
     * How well this garment colour sits in this palette, 0f..1f.
     *
     * A garment only has to match ONE anchor — nobody needs a shirt that suits their
     * whole palette at once — so this takes the nearest, not the average.
     */
    fun paletteFit(garmentColor: LabColor, palette: Palette): Float {
        if (palette.anchors.isEmpty()) return 0.5f

        val nearest = palette.anchors.minOf { distanceTo(garmentColor, it) }

        return (1f - nearest / MAX_USEFUL_DISTANCE).coerceIn(0f, 1f)
    }

    /** ΔE76 with lightness discounted — see [LIGHTNESS_WEIGHT]. */
    private fun distanceTo(color: LabColor, anchor: LabColor): Float {
        val weighted = LabColor(
            l = anchor.l + (color.l - anchor.l) * LIGHTNESS_WEIGHT,
            a = color.a,
            b = color.b
        )
        return ColorMath.deltaE76(weighted, anchor)
    }
}
