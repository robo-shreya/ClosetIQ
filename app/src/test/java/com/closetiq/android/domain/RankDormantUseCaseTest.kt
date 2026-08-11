package com.closetiq.android.domain

import com.closetiq.android.domain.TestFixtures.NOW
import com.closetiq.android.domain.TestFixtures.garment
import com.closetiq.android.domain.usecase.RankDormantUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * START HERE. This is the smallest of the four TODOs and the only one with a single
 * obviously-correct answer, so it is the cheapest way to get your first green test.
 */
class RankDormantUseCaseTest {

    private val useCase = RankDormantUseCase()

    @Test
    fun `worn today scores zero`() {
        val score = useCase.dormancyScore(garment(daysSinceWorn = 0), NOW)
        assertEquals(0f, score, 0.01f)
    }

    @Test
    fun `worn 90 days ago is fully dormant`() {
        val score = useCase.dormancyScore(garment(daysSinceWorn = 90), NOW)
        assertEquals(1f, score, 0.01f)
    }

    @Test
    fun `worn 45 days ago sits halfway`() {
        val score = useCase.dormancyScore(garment(daysSinceWorn = 45), NOW)
        assertEquals(0.5f, score, 0.05f)
    }

    @Test
    fun `never exceeds one, however long ago`() {
        val score = useCase.dormancyScore(garment(daysSinceWorn = 500), NOW)
        assertTrue("dormancy must stay in 0..1, was $score", score <= 1f)
    }

    /**
     * Your call to make. A garment owned for 200 days and never worn is the most
     * forgotten thing in the closet — this test says so. If you decide never-worn should
     * mean something else, change the test first, then the code.
     */
    @Test
    fun `owned a long time and never worn is fully dormant`() {
        val score = useCase.dormancyScore(
            garment(daysSinceWorn = null, daysSinceAdded = 200),
            NOW
        )
        assertEquals(1f, score, 0.01f)
    }

    /** Bought yesterday and not yet worn is not "forgotten". Do not nag about it. */
    @Test
    fun `added yesterday and never worn is not dormant`() {
        val score = useCase.dormancyScore(
            garment(daysSinceWorn = null, daysSinceAdded = 1),
            NOW
        )
        assertTrue("a brand new item should not read as forgotten, was $score", score < 0.2f)
    }

    @Test
    fun `rank puts the most forgotten first`() {
        val fresh = garment(id = "fresh", daysSinceWorn = 1)
        val middling = garment(id = "middling", daysSinceWorn = 40)
        val forgotten = garment(id = "forgotten", daysSinceWorn = 150)

        val ranked = useCase.rank(listOf(fresh, forgotten, middling), NOW)

        assertEquals(listOf("forgotten", "middling", "fresh"), ranked.map { it.id })
    }

    /**
     * Everything past 90 days scores exactly 1f, so ranking cannot rely on the score
     * alone to order them. Without a tie-break these come back in database order, which
     * on a seeded closet looks like no sorting happened at all.
     */
    @Test
    fun `items past the saturation point are still ordered oldest first`() {
        val ranked = useCase.rank(
            listOf(
                garment(id = "94d", daysSinceWorn = 94),
                garment(id = "210d", daysSinceWorn = 210),
                garment(id = "128d", daysSinceWorn = 128)
            ),
            NOW
        )

        assertEquals(listOf("210d", "128d", "94d"), ranked.map { it.id })
    }

    @Test
    fun `a never worn item outranks one worn longer ago than it was added`() {
        val ranked = useCase.rank(
            listOf(
                garment(id = "worn-210d", daysSinceWorn = 210, daysSinceAdded = 240),
                garment(id = "never-worn", daysSinceWorn = null, daysSinceAdded = 240)
            ),
            NOW
        )

        assertEquals(listOf("never-worn", "worn-210d"), ranked.map { it.id })
    }
}
