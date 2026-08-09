package com.closetiq.android.domain.engine

/**
 * The knobs. Kept in one place so you can tune the whole recommendation feel
 * without hunting through logic, and so tests can pin specific weights.
 *
 * [dormancy] is deliberately the largest. If it is too low the app quietly recommends
 * your five favourite items forever and the entire premise of the product collapses.
 * Start at roughly double the others and only lower it if recommendations feel random.
 */
data class ScoringWeights(
    val paletteFit: Float = 1.0f,
    val skinDayFit: Float = 1.0f,
    val dormancy: Float = 2.0f,
    val recentRepeatPenalty: Float = 1.5f
) {
    companion object {
        val Default = ScoringWeights()
    }
}
