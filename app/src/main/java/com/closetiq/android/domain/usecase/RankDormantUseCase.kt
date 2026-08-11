package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.model.Garment

/**
 * Dormancy — how forgotten a garment is. The sustainability lever.
 */
class RankDormantUseCase {

    /**
     * How overdue this garment is, 0f..1f.
     *
     *   worn today        → 0f
     *   worn 90+ days ago → 1f
     *   in between        → proportional
     *
     * A garment that has never been worn falls back to [Garment.addedAt]. The question
     * that resolves is "how long since this item last had its moment" — and being added
     * to the closet counts as one. That makes something bought yesterday score near 0
     * (it has not had its chance yet) while something owned for 200 days and never worn
     * scores 1 (it is the most forgotten thing you own). Both are real cases and the
     * two of them need different answers.
     */
    fun dormancyScore(garment: Garment, now: Long): Float {
        val reference = garment.lastWornAt ?: garment.addedAt
        val daysSince = (now - reference).toFloat() / MILLIS_PER_DAY

        // coerceIn does two jobs: caps runaway values at 1, and floors the negative
        // that a future timestamp would otherwise produce.
        return (daysSince / FULLY_DORMANT_DAYS).coerceIn(0f, 1f)
    }

    /**
     * Sorts a closet by dormancy, most-forgotten first.
     *
     * The tie-break matters more than it looks. [dormancyScore] saturates at
     * [FULLY_DORMANT_DAYS], which is right for scoring — past three months, everything
     * is equally forgotten as far as the recommendation is concerned. But that makes
     * every such item score exactly 1f, and a stable sort then leaves them in whatever
     * order the database happened to return. On a seeded closet that looks like no
     * sorting at all.
     *
     * So: rank by score, then break ties on the actual timestamp, oldest first.
     * Sorting on the timestamp alone would give the same answer today, but it would
     * silently stop tracking [dormancyScore] the moment that gains another factor.
     */
    fun rank(garments: List<Garment>, now: Long): List<Garment> =
        garments.sortedWith(
            compareByDescending<Garment> { dormancyScore(it, now) }
                .thenBy { it.lastWornAt ?: it.addedAt }
        )

    companion object {
        const val FULLY_DORMANT_DAYS = 90
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
