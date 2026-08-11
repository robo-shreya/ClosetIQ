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

        // Both halves, always. The dormancy half is the product's premise and the skin
        // half is what makes today different from yesterday — either one alone reads as
        // a lesser app. Skin is appended only when there is a reading, so the sentence
        // degrades to just the premise rather than to nothing.
        val reason = buildString {
            append(dormancyPhrase(hero.garment, now))
            if (reading != null) {
                append(' ')
                append(SkinStateModifier.explain(hero.garment.color, reading))
            }
        }

        return OutfitPick(hero = hero, supporting = supporting, reason = reason)
    }

    private fun dormancyPhrase(garment: Garment, now: Long): String {
        val lastWorn = garment.lastWornAt ?: return "You've never worn this."

        return when (val days = (now - lastWorn) / RankDormantUseCase.MILLIS_PER_DAY) {
            0L -> "You wore this today."
            1L -> "You wore this yesterday."
            else -> "You haven't worn this in $days days."
        }
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
