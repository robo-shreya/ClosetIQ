package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.engine.ScoringWeights
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
    private val weights: ScoringWeights = ScoringWeights.Default
) {

    /**
     * TODO(you): combine the four signals into one score.
     *
     *   score = w1·paletteFit
     *         + w2·skinDayFit
     *         + w3·dormancy
     *         − w4·recentRepeat
     *
     * Call PaletteEngine.paletteFit, SkinStateModifier.skinDayFit and
     * RankDormantUseCase.dormancyScore to get the first three. Compute
     * recentRepeat here: 1f if worn within the last 7 days, 0f otherwise.
     * (A hard cutoff is fine. A smooth decay is nicer. Start hard.)
     *
     * Two things to be careful about:
     *
     *   - [reading] is nullable. The app must work with no skin data at all — that was
     *     a deliberate product decision, not an oversight. When it is null, use the
     *     neutral 0.5f for skinDayFit rather than skipping the term, so scores stay
     *     comparable between sessions that have a reading and sessions that do not.
     *
     *   - Return the breakdown, not just the total. ScoredGarment carries every component
     *     because the UI explains its reasoning, and because when a recommendation looks
     *     wrong you will want to see which term caused it.
     *
     * See ScoreGarmentUseCaseTest for the cases this needs to satisfy.
     */
    operator fun invoke(
        garment: Garment,
        palette: Palette,
        reading: SkinReading?,
        now: Long
    ): ScoredGarment {
        TODO("Combine paletteFit, skinDayFit, dormancy and recentRepeat into a ScoredGarment")
    }
}
