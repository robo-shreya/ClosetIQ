package com.closetiq.android.domain

import com.closetiq.android.domain.engine.ScoringWeights
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.usecase.GetTodaysPickUseCase
import com.closetiq.android.domain.usecase.RankDormantUseCase
import com.closetiq.android.domain.usecase.ScoreGarmentUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The Shuffle button on the Mirror.
 *
 * Where [GetTodaysPickUseCase.invoke] is deterministic, this draws one garment per slot at
 * random — so the property to hold is not "which outfit" but that it stays a whole outfit,
 * and that repeated draws actually move.
 */
class ShuffledPickTest {

    private val rankDormant = RankDormantUseCase()
    private val useCase = GetTodaysPickUseCase(
        scoreGarment = ScoreGarmentUseCase(ScoringWeights.Default, rankDormant),
        rankDormant = rankDormant
    )

    private fun wardrobe() = listOf(
        TestFixtures.garment(id = "top-a", category = Category.TOP, daysSinceWorn = 200),
        TestFixtures.garment(id = "top-b", category = Category.TOP, daysSinceWorn = 150),
        TestFixtures.garment(id = "top-c", category = Category.TOP, daysSinceWorn = 20),
        TestFixtures.garment(id = "bottom-a", category = Category.BOTTOM, daysSinceWorn = 120),
        TestFixtures.garment(id = "bottom-b", category = Category.BOTTOM, daysSinceWorn = 30),
        TestFixtures.garment(id = "shoes-a", category = Category.SHOES, daysSinceWorn = 100),
        TestFixtures.garment(id = "shoes-b", category = Category.SHOES, daysSinceWorn = 10),
        TestFixtures.garment(id = "coat", category = Category.OUTERWEAR, daysSinceWorn = 90)
    )

    @Test
    fun `a shuffle returns a whole outfit`() {
        val pick = useCase.shuffled(wardrobe(), reading = null, now = TestFixtures.NOW)

        assertNotNull("shuffle returned nothing on a full wardrobe", pick)

        val categories = (listOf(pick!!.hero) + pick.supporting).map { it.garment.category }
        assertEquals(
            "shuffle should draw one garment per slot: $categories",
            setOf(Category.TOP, Category.BOTTOM, Category.SHOES, Category.OUTERWEAR),
            categories.toSet()
        )
    }

    @Test
    fun `shuffling repeatedly reaches more than one outfit`() {
        val wardrobe = wardrobe()

        val seen = (1..40)
            .mapNotNull { useCase.shuffled(wardrobe, null, Random(it), TestFixtures.NOW) }
            .map { pick -> (listOf(pick.hero) + pick.supporting).map { it.garment.id }.toSet() }
            .toSet()

        assertTrue("shuffle kept returning the same outfit: $seen", seen.size > 1)
    }

    @Test
    fun `an empty closet shuffles to nothing rather than throwing`() {
        assertNull(useCase.shuffled(emptyList(), reading = null, now = TestFixtures.NOW))
    }
}
