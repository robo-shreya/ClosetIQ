package com.closetiq.android.domain.engine

import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.Palette
import com.closetiq.android.domain.model.SkinReading

/**
 * Turns a skin reading into a palette, and scores a garment against that palette.
 *
 * This is the stable half of the brain: it depends only on undertone and Fitzpatrick type,
 * which barely change. The volatile half — today's redness, dullness, dark circles —
 * lives in SkinStateModifier.
 */
object PaletteEngine {

    /**
     * TODO(you): build the set of anchor colours that suit this person.
     *
     * Inputs that matter: [reading].undertone and [reading].fitzpatrick.
     *
     * The classic seasonal-analysis shape, stated in Lab terms:
     *   WARM     → anchors shifted toward positive b (yellow/gold/olive/rust)
     *   COOL     → anchors shifted toward negative b and positive a→blue side
     *               (navy, emerald, berry, true white)
     *   NEUTRAL  → a mix, lower chroma
     *
     * Fitzpatrick should drive the *lightness* range of the anchors: a deeper skin tone
     * carries higher-contrast, higher-chroma colour well; a lighter one is more easily
     * overwhelmed by it.
     *
     * Six to ten anchors is plenty. Hardcoding them as literal LabColor values is a
     * perfectly good first version — do not build a colour-theory framework here.
     *
     * See PaletteEngineTest for the cases this needs to satisfy.
     */
    fun buildPalette(reading: SkinReading): Palette {
        TODO("Derive palette anchors from undertone + fitzpatrick")
    }

    /**
     * TODO(you): how well does this garment colour sit in this palette? Return 0f..1f,
     * where 1f is a perfect match.
     *
     * The shape of the answer:
     *   1. Find the *nearest* anchor with ColorMath.deltaE76 — a garment only has to
     *      match one anchor, not all of them.
     *   2. Map that distance to 0..1. Distance 0 → 1f. Some cutoff distance → 0f.
     *      Around 60 is a reasonable cutoff to start with; tune it once you can see
     *      real recommendations on screen.
     *   3. Clamp. Never return outside 0f..1f — the weighted sum in ScoreGarmentUseCase
     *      assumes every component is normalised, and a single unclamped term will
     *      quietly dominate everything else.
     *
     * See PaletteEngineTest for the cases this needs to satisfy.
     */
    fun paletteFit(garmentColor: LabColor, palette: Palette): Float {
        TODO("Return 0f..1f for how well this colour fits the palette")
    }
}
