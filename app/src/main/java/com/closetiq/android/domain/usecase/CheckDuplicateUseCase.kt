package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.color.ColorMath
import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.Palette
import kotlin.math.roundToInt

/**
 * The buy check: point the app at something you are about to buy and it answers two
 * questions — does this suit you, and do you already own it?
 *
 * Every other screen helps you wear what you own. This is the only place the app tells
 * you not to acquire more, which in a sustainability product is the point.
 *
 * Note what is deliberately absent: today's skin. The Mirror weighs SkinStateModifier
 * because "what suits you today" is the question there. A purchase is a long-term
 * decision, so a flare-up this morning says nothing about whether you should own
 * something at all. Only the stable half — undertone and Fitzpatrick, via
 * [PaletteEngine] — is consulted here.
 */
class CheckDuplicateUseCase(
    private val rankDormant: RankDormantUseCase = RankDormantUseCase()
) {

    enum class BuyAdvice { BUY, THINK_TWICE, ALREADY_OWN }

    data class Verdict(
        val advice: BuyAdvice,
        /** Things you already own that are near enough to count, nearest colour first. */
        val matches: List<Garment>,
        /** How many of [matches] you have forgotten. The argument, as opposed to the fact. */
        val dormantMatches: Int,
        val paletteFit: Float,
        val headline: String,
        val detail: String?
    ) {
        /**
         * [paletteFit] on a five-point scale, for display.
         *
         * A bare "1" out of an unstated range tells nobody anything — and read next to a
         * verdict it looked like a contradiction. Five points is coarse enough to be
         * honest about how rough the underlying measure is.
         */
        val paletteOutOfFive: Int get() = (paletteFit * SCALE).roundToInt().coerceIn(0, SCALE)

        /** True when the verdict argues against buying, so the UI can raise its voice. */
        val discouraging: Boolean
            get() = advice == BuyAdvice.ALREADY_OWN || paletteFit < WEAK_PALETTE_FIT
    }

    operator fun invoke(
        closet: List<Garment>,
        candidateColor: LabColor,
        candidateCategory: Category,
        palette: Palette,
        now: Long = System.currentTimeMillis()
    ): Verdict {
        val matches = closet
            .filter { it.isActive && it.category == candidateCategory }
            .map { it to ColorMath.deltaE76(candidateColor, it.color) }
            .filter { (_, distance) -> distance <= SAME_COLOUR_DISTANCE }
            .sortedBy { (_, distance) -> distance }
            .map { (garment, _) -> garment }

        val dormant = matches.filter {
            rankDormant.dormancyScore(it, now) >= DORMANT_THRESHOLD
        }

        val paletteFit = PaletteEngine.paletteFit(candidateColor, palette)

        val advice = when {
            matches.size >= ALREADY_OWN_COUNT -> BuyAdvice.ALREADY_OWN
            matches.isNotEmpty() -> BuyAdvice.THINK_TWICE
            paletteFit < WEAK_PALETTE_FIT -> BuyAdvice.THINK_TWICE
            else -> BuyAdvice.BUY
        }

        return Verdict(
            advice = advice,
            matches = matches,
            dormantMatches = dormant.size,
            paletteFit = paletteFit,
            headline = headlineFor(advice, matches.size, candidateCategory),
            detail = detailFor(advice, matches, dormant, paletteFit)
        )
    }

    private fun headlineFor(advice: BuyAdvice, matchCount: Int, category: Category): String {
        val noun = category.plural()

        return when (advice) {
            BuyAdvice.ALREADY_OWN -> "You already own $matchCount $noun this colour."
            // Not "it fights your colouring": one letter away from "fits", which means the
            // opposite, and at this size the two are indistinguishable.
            BuyAdvice.THINK_TWICE ->
                if (matchCount > 0) {
                    "You own one of these already."
                } else {
                    "This colour works against you."
                }
            BuyAdvice.BUY -> "Nothing like it in your closet."
        }
    }

    private fun detailFor(
        advice: BuyAdvice,
        matches: List<Garment>,
        dormant: List<Garment>,
        paletteFit: Float
    ): String? = when {
        // The line the whole feature exists to produce.
        dormant.size >= 2 ->
            "${dormant.size} of them haven't been worn in months."

        dormant.size == 1 ->
            "And you haven't worn the ${dormant.first().label.lowercase()} in months."

        matches.size == 1 ->
            "You wear the ${matches.first().label.lowercase()} regularly, " +
                "so this would be a second one."

        matches.isNotEmpty() ->
            "You wear all of them, so this would be number ${matches.size + 1}."

        advice == BuyAdvice.THINK_TWICE ->
            "Nothing in your closet is close, but this sits outside your palette."

        paletteFit >= STRONG_PALETTE_FIT ->
            "It also suits your colouring well."

        else -> null
    }

    private fun Category.plural(): String = when (this) {
        Category.TOP -> "tops"
        Category.BOTTOM -> "bottoms"
        Category.OUTERWEAR -> "layers"
        Category.DRESS -> "dresses"
        Category.SHOES -> "pairs of shoes"
    }

    companion object {
        /**
         * Below this ΔE, two garments read as "the same colour" to a person. Expect to
         * tune it once real photographs — which carry lighting shifts flat swatches do
         * not — start going through the extractor.
         */
        const val SAME_COLOUR_DISTANCE = 18f

        /** A match this dormant is one you have forgotten rather than one you rotate. */
        const val DORMANT_THRESHOLD = 0.66f

        /** Owning this many already stops being a coincidence. */
        const val ALREADY_OWN_COUNT = 2

        const val WEAK_PALETTE_FIT = 0.5f
        const val STRONG_PALETTE_FIT = 0.75f

        /** Points on the displayed scale. */
        const val SCALE = 5
    }
}
