package com.closetiq.android.domain

import com.closetiq.android.domain.TestFixtures.NAVY
import com.closetiq.android.domain.TestFixtures.NOW
import com.closetiq.android.domain.TestFixtures.garment
import com.closetiq.android.domain.TestFixtures.reading
import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.engine.ScoringWeights
import com.closetiq.android.domain.usecase.ScoreGarmentUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Write this one last. It depends on the other three, so nothing here can pass until
 * they do — which is exactly why it is the right place to stop and check the whole
 * brain works together.
 */
class ScoreGarmentUseCaseTest {

    private val useCase = ScoreGarmentUseCase(ScoringWeights.Default)
    private val palette by lazy { PaletteEngine.buildPalette(reading()) }

    @Test
    fun `the breakdown is filled in, not just the total`() {
        val scored = useCase(garment(color = NAVY), palette, reading(), NOW)

        assertTrue("paletteFit not populated", scored.paletteFit in 0f..1f)
        assertTrue("skinDayFit not populated", scored.skinDayFit in 0f..1f)
        assertTrue("dormancy not populated", scored.dormancy in 0f..1f)
        assertEquals("the scored garment should be the one passed in", "g1", scored.garment.id)
    }

    /** The premise of the product, as a test. */
    @Test
    fun `a forgotten item outscores an identical item worn yesterday`() {
        val forgotten = garment(id = "forgotten", color = NAVY, daysSinceWorn = 150)
        val recent = garment(id = "recent", color = NAVY, daysSinceWorn = 1)

        val forgottenScore = useCase(forgotten, palette, reading(), NOW).total
        val recentScore = useCase(recent, palette, reading(), NOW).total

        assertTrue(
            "dormancy must dominate: forgotten ($forgottenScore) vs recent ($recentScore)",
            forgottenScore > recentScore
        )
    }

    @Test
    fun `something worn in the last week is penalised`() {
        val threeDaysAgo = useCase(garment(daysSinceWorn = 3), palette, reading(), NOW)
        assertTrue(
            "recentRepeatPenalty should be applied within 7 days",
            threeDaysAgo.recentRepeatPenalty > 0f
        )
    }

    @Test
    fun `something worn a month ago is not penalised as a repeat`() {
        val monthAgo = useCase(garment(daysSinceWorn = 30), palette, reading(), NOW)
        assertEquals(
            "30 days is not a recent repeat",
            0f,
            monthAgo.recentRepeatPenalty,
            0.001f
        )
    }

    /**
     * The app must work with no skin data at all. This was a deliberate product decision:
     * if it is useless without a selfie it is dead, and if it is better with one people
     * will take one.
     */
    @Test
    fun `scoring works with a null reading`() {
        val scored = useCase(garment(color = NAVY), palette, null, NOW)

        assertEquals(
            "with no reading, skinDayFit should be the neutral 0.5",
            0.5f,
            scored.skinDayFit,
            0.01f
        )
        assertTrue("total should still be a real number", scored.total.isFinite())
    }

    @Test
    fun `weights actually change the outcome`() {
        val dormancyHeavy = ScoreGarmentUseCase(ScoringWeights(dormancy = 10f))
        val dormancyBlind = ScoreGarmentUseCase(ScoringWeights(dormancy = 0f))

        val forgotten = garment(daysSinceWorn = 150, color = NAVY)

        assertTrue(
            "raising the dormancy weight must raise a dormant item's score",
            dormancyHeavy(forgotten, palette, reading(), NOW).total >
                dormancyBlind(forgotten, palette, reading(), NOW).total
        )
    }
}
