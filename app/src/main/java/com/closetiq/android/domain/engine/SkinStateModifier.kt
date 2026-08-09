package com.closetiq.android.domain.engine

import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.SkinReading

/**
 * The volatile half of the brain, and the reason this app is in the
 * "Skin AI + Apparel VTO" category rather than being a wardrobe tracker.
 *
 * The palette says what suits you in general. This says what suits you *today*.
 * It is what lets a garment that scored badly last week get recommended this week —
 * which is the entire mechanism by which dormant clothes come back into rotation.
 */
object SkinStateModifier {

    /** Hue angle, in degrees, of facial flush in Lab. Reds sit near 0-30°. */
    const val FLUSH_HUE_DEGREES = 25f

    /**
     * TODO(you): score this garment colour against *today's* skin state. Return 0f..1f.
     *
     * Return 0.5f when nothing applies — this is a modifier, so neutral is the middle,
     * not zero. A garment that today's skin has no opinion about should not be punished.
     *
     * The three signals, and what each one implies:
     *
     *   redness high     → penalise garments whose hue sits near FLUSH_HUE_DEGREES.
     *                      Coral and warm pink amplify a flush; navy and green neutralise it.
     *                      Use ColorMath.hueAngle and ColorMath.hueDistance.
     *
     *   dullness high    → reward higher chroma and higher lightness (ColorMath.chroma,
     *                      and LabColor.l). Saturated colour reads as "awake"; a muted
     *                      beige next to dull skin reads as tired.
     *
     *   darkCircles high → penalise low-lightness garments, but only for things worn
     *                      near the face. This function does not know the category, so
     *                      apply it generally for now and refine later if it misfires.
     *
     * Each signal is already normalised 0f..1f, so you can use it directly as the strength
     * of its own effect: `0.5f - (reading.redness * clashAmount)` and so on.
     *
     * Clamp the result to 0f..1f before returning.
     *
     * See SkinStateModifierTest for the cases this needs to satisfy.
     */
    fun skinDayFit(garmentColor: LabColor, reading: SkinReading): Float {
        TODO("Return 0f..1f for how well this colour works with today's skin")
    }

    /**
     * TODO(you): one short sentence explaining why today favours this garment.
     *
     * This string goes straight on screen and into the demo video, so it matters more
     * than it looks. It should name the observation and the consequence:
     *
     *   "Your skin is reading even today — this deep green has nothing to fight."
     *   "A bit of redness around the cheeks, so warm coral is out. Navy calms it."
     *
     * Pick whichever of the three signals is strongest and speak to that one only.
     * Do not list all three; a hedged sentence reads as noise.
     */
    fun explain(garmentColor: LabColor, reading: SkinReading): String {
        TODO("Return one sentence naming the observation and the consequence")
    }
}
