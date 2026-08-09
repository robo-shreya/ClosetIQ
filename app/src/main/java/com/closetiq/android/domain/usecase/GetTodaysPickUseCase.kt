package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.engine.SkinStateModifier
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.OutfitPick
import com.closetiq.android.domain.model.SkinReading

/**
 * Orchestration only — this one is written for you, because the wiring is not the lesson.
 *
 * It calls the three functions you are writing. Until those return real values this
 * throws, which is exactly what you want: the Mirror screen stays visibly broken until
 * the brain works, rather than showing something plausible and wrong.
 */
class GetTodaysPickUseCase(
    private val scoreGarment: ScoreGarmentUseCase,
    private val rankDormant: RankDormantUseCase
) {

    operator fun invoke(
        garments: List<Garment>,
        reading: SkinReading?,
        now: Long = System.currentTimeMillis()
    ): OutfitPick? {
        val active = garments.filter { it.isActive }
        if (active.isEmpty()) return null

        // With no reading we still need a palette to score against. A neutral reading
        // gives the baseline behaviour: colour still matters, today's skin just doesn't.
        val palette = PaletteEngine.buildPalette(reading ?: NEUTRAL_BASELINE)

        val scored = active
            .map { scoreGarment(it, palette, reading, now) }
            .sortedByDescending { it.total }

        val hero = scored.first()

        // The hero is the one we render. Everything else is shown as flat tiles beside it.
        val supporting = scored
            .drop(1)
            .filter { it.garment.category != hero.garment.category }
            .take(2)

        val reason = if (reading != null) {
            SkinStateModifier.explain(hero.garment.color, reading)
        } else {
            "You haven't worn this in ${daysSince(hero.garment.lastWornAt, now)} days."
        }

        return OutfitPick(hero = hero, supporting = supporting, reason = reason)
    }

    private fun daysSince(then: Long?, now: Long): Long {
        if (then == null) return 0
        return (now - then) / RankDormantUseCase.MILLIS_PER_DAY
    }

    companion object {
        /** Used only to keep colour scoring meaningful when no selfie has ever been taken. */
        private val NEUTRAL_BASELINE = SkinReading(
            id = "baseline",
            capturedAt = 0L,
            undertone = com.closetiq.android.domain.model.Undertone.NEUTRAL,
            fitzpatrick = 3,
            redness = 0f,
            dullness = 0f,
            darkCircles = 0f,
            staleAfter = 0L,
            selfiePath = null
        )
    }
}
