package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.model.Garment

/**
 * Dormancy — how forgotten a garment is. The sustainability lever.
 */
class RankDormantUseCase {

    /**
     * TODO(you): return 0f..1f for how overdue this garment is.
     *
     *   worn today                → 0f
     *   never worn, or worn 90+ days ago → 1f
     *   in between                → proportional
     *
     * The simple version is `min(daysSinceWorn / 90f, 1f)`, and it is a fine place to start.
     *
     * One decision you have to make and should write a test for: what does a garment
     * with lastWornAt == null mean? A never-worn item is either the most dormant thing
     * you own or a brand-new purchase you have not had the chance to wear yet.
     * [Garment.addedAt] is how you tell the difference. Decide which behaviour you want.
     *
     * See RankDormantUseCaseTest for the cases this needs to satisfy.
     */
    fun dormancyScore(garment: Garment, now: Long): Float {
        TODO("Return 0f..1f for how overdue this garment is")
    }

    /** Sorts a closet by dormancy, most-forgotten first. Free once the above works. */
    fun rank(garments: List<Garment>, now: Long): List<Garment> =
        garments.sortedByDescending { dormancyScore(it, now) }

    companion object {
        const val FULLY_DORMANT_DAYS = 90
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
