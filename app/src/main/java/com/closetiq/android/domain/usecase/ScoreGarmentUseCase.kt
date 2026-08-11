package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.engine.ScoringWeights
import com.closetiq.android.domain.engine.SkinStateModifier
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.Palette
import com.closetiq.android.domain.model.ScoredGarment
import com.closetiq.android.domain.model.SkinReading

/**
 * The product, in one function.
 *
 * Everything else in this app is plumbing that exists to call this and to show
 * what it returned.
 */
class ScoreGarmentUseCase(
    private val weights: ScoringWeights = ScoringWeights.Default,
    private val rankDormant: RankDormantUseCase = RankDormantUseCase()
) {

    /**
     * score = w1·paletteFit + w2·skinDayFit + w3·dormancy − w4·recentRepeat
     *
     * The breakdown comes back alongside the total, not just the number. The UI explains
     * its own reasoning from these, and when a recommendation looks wrong the first
     * question is always which term caused it.
     */
    operator fun invoke(
        garment: Garment,
        palette: Palette,
        reading: SkinReading?,
        now: Long
    ): ScoredGarment {
        val paletteFit = PaletteEngine.paletteFit(garment.color, palette)

        // A null reading is a supported state, not a missing one: the app has to work
        // for someone who never takes a photo. Substituting the neutral 0.5f rather
        // than dropping the term keeps totals comparable across both cases.
        val skinDayFit = reading
            ?.let { SkinStateModifier.skinDayFit(garment.color, it) }
            ?: NEUTRAL_SKIN_FIT

        val dormancy = rankDormant.dormancyScore(garment, now)
        val recentRepeat = if (wornRecently(garment, now)) 1f else 0f

        val total = weights.paletteFit * paletteFit +
            weights.skinDayFit * skinDayFit +
            weights.dormancy * dormancy -
            weights.recentRepeatPenalty * recentRepeat

        return ScoredGarment(
            garment = garment,
            total = total,
            paletteFit = paletteFit,
            skinDayFit = skinDayFit,
            dormancy = dormancy,
            recentRepeatPenalty = recentRepeat
        )
    }

    /**
     * A hard cutoff rather than a decay. Wearing something twice in a week is the thing
     * being discouraged, and the day it stops mattering is genuinely abrupt — a smooth
     * curve would imply a precision this does not have.
     */
    private fun wornRecently(garment: Garment, now: Long): Boolean {
        val lastWorn = garment.lastWornAt ?: return false
        val days = (now - lastWorn) / RankDormantUseCase.MILLIS_PER_DAY
        return days in 0 until RECENT_REPEAT_DAYS
    }

    companion object {
        /** What skinDayFit returns when there is no reading — see SkinStateModifier. */
        const val NEUTRAL_SKIN_FIT = 0.5f

        const val RECENT_REPEAT_DAYS = 7L
    }
}
