package com.closetiq.android.domain.engine

import com.closetiq.android.domain.color.ColorMath
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

    /** Chroma at which a colour counts as fully saturated for these purposes. */
    private const val FULL_CHROMA = 40f

    /** Hue distance at which a colour stops interacting with the flush at all. */
    private const val HUE_NEUTRAL_DEGREES = 90f

    // How far each signal can move the result. They sum to 1.0, so a maximal reading
    // can push a garment the whole way from neutral to either end.
    private const val MAX_REDNESS_EFFECT = 0.4f
    private const val MAX_DULLNESS_EFFECT = 0.3f
    private const val MAX_DARK_CIRCLE_EFFECT = 0.3f

    /** Below this, a signal is not worth mentioning in [explain]. */
    private const val WORTH_MENTIONING = 0.15f

    /**
     * How far an effect must move before [explain] will claim the garment is helped or
     * hurt. Without it, an effect of 0.01 — indistinguishable from neutral — produces a
     * confident sentence about a clash, and the app ends up recommending a garment while
     * explaining why it is wrong.
     */
    private const val EFFECT_DEAD_BAND = 0.03f

    /**
     * How well this colour works with *today's* skin. 0f..1f, neutral at 0.5f.
     *
     * Neutral is the middle rather than zero because this is a modifier: a garment
     * today's skin has no opinion about must not be punished for it. On a calm reading
     * every colour returns exactly 0.5f and the recommendation falls back to palette
     * and dormancy, which is the correct behaviour, not a degenerate one.
     *
     * Each signal returns a signed nudge, negative meaning "this helps today".
     */
    fun skinDayFit(garmentColor: LabColor, reading: SkinReading): Float {
        val penalty = rednessEffect(garmentColor, reading.redness) +
            dullnessEffect(garmentColor, reading.dullness) +
            darkCircleEffect(garmentColor, reading.darkCircles)

        return (0.5f - penalty).coerceIn(0f, 1f)
    }

    /**
     * A flush is a red in the face. Wearing that same red beside it amplifies it;
     * wearing its opposite settles it.
     *
     * Scaled by chroma, because a washed-out warm beige barely interacts with a flush
     * while a saturated coral shouts at it. Greys and blacks have no hue at all and so
     * are left alone entirely.
     */
    private fun rednessEffect(color: LabColor, redness: Float): Float {
        if (redness <= 0f) return 0f

        val distance = ColorMath.hueDistance(ColorMath.hueAngle(color), FLUSH_HUE_DEGREES)

        // +1 sitting on the flush hue, 0 at 90° away, -1 directly opposite.
        val clash = (1f - distance / HUE_NEUTRAL_DEGREES).coerceIn(-1f, 1f)

        return redness * clash * chromaFactor(color) * MAX_REDNESS_EFFECT
    }

    /**
     * Dull skin wants colour near it, not more mud. Chroma does most of the work and
     * lightness the rest — a saturated emerald lifts, an oatmeal knit disappears.
     */
    private fun dullnessEffect(color: LabColor, dullness: Float): Float {
        if (dullness <= 0f) return 0f

        val vitality = chromaFactor(color) * 0.7f + (color.l / 100f) * 0.3f

        // Above the midpoint it lifts, below it flattens. Doubled to span -1..1.
        return -dullness * (vitality - 0.5f) * 2f * MAX_DULLNESS_EFFECT
    }

    /**
     * Shadow under the eyes is deepened by more shadow next to it. This does not know
     * the garment's category, so it applies everywhere — imprecise for shoes, but the
     * error is small and the alternative is threading category through the whole engine.
     */
    private fun darkCircleEffect(color: LabColor, darkCircles: Float): Float {
        if (darkCircles <= 0f) return 0f

        // +1 at black, 0 at mid lightness, -1 at white.
        val darkness = ((0.5f - color.l / 100f) * 2f).coerceIn(-1f, 1f)

        return darkCircles * darkness * MAX_DARK_CIRCLE_EFFECT
    }

    private fun chromaFactor(color: LabColor): Float =
        (ColorMath.chroma(color) / FULL_CHROMA).coerceIn(0f, 1f)

    /**
     * One sentence naming the observation and the consequence.
     *
     * This goes straight on screen and into the demo video, so it speaks to the single
     * strongest signal only. A sentence that hedges across all three reads as noise and
     * convinces nobody.
     */
    fun explain(garmentColor: LabColor, reading: SkinReading): String {
        val strongest = listOf(
            Signal.REDNESS to reading.redness,
            Signal.DULLNESS to reading.dullness,
            Signal.DARK_CIRCLES to reading.darkCircles
        ).maxBy { it.second }

        if (strongest.second < WORTH_MENTIONING) {
            return "Your skin is reading even today — this has nothing to fight."
        }

        val effect = when (strongest.first) {
            Signal.REDNESS -> rednessEffect(garmentColor, reading.redness)
            Signal.DULLNESS -> dullnessEffect(garmentColor, reading.dullness)
            Signal.DARK_CIRCLES -> darkCircleEffect(garmentColor, reading.darkCircles)
        }

        // Positive effect means the garment works against today's skin.
        val verdict = when {
            effect > EFFECT_DEAD_BAND -> Verdict.HURTS
            effect < -EFFECT_DEAD_BAND -> Verdict.HELPS
            else -> Verdict.UNAFFECTED
        }

        return when (strongest.first) {
            Signal.REDNESS -> when (verdict) {
                Verdict.HURTS -> "Some redness around the cheeks today, so warm reds amplify it."
                Verdict.HELPS -> "Some redness around the cheeks today — this cooler tone settles it."
                Verdict.UNAFFECTED -> "Some redness around the cheeks today, but this sits clear of it."
            }

            Signal.DULLNESS -> when (verdict) {
                Verdict.HURTS -> "Skin is reading flat today, and this is too muted to lift it."
                Verdict.HELPS -> "Skin is reading flat today — this much colour wakes it up."
                Verdict.UNAFFECTED -> "Skin is reading flat today, and this holds its own."
            }

            Signal.DARK_CIRCLES -> when (verdict) {
                Verdict.HURTS -> "Tired around the eyes today, and this depth drags the face down."
                Verdict.HELPS -> "Tired around the eyes today — something this light lifts the face."
                Verdict.UNAFFECTED -> "Tired around the eyes today, but this won't add to it."
            }
        }
    }

    private enum class Signal { REDNESS, DULLNESS, DARK_CIRCLES }

    private enum class Verdict { HURTS, HELPS, UNAFFECTED }
}
