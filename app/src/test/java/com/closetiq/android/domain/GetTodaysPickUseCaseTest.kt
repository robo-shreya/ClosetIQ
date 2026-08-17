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

/**
 * What the Mirror shows beside the hero.
 *
 * The rule under test is that the tiles complete an *outfit* — a torso, legs and feet —
 * rather than simply being the next two highest scores. Ranking alone reliably produces
 * two of the same kind of thing, because the most neglected garments in a real wardrobe
 * cluster: nobody forgets about one item per category evenly. It showed up on a real
 * closet as two tee shirts sitting beside a blazer.
 */
class GetTodaysPickUseCaseTest {

    private val rankDormant = RankDormantUseCase()
    private val useCase = GetTodaysPickUseCase(
        scoreGarment = ScoreGarmentUseCase(ScoringWeights.Default, rankDormant),
        rankDormant = rankDormant
    )

    /** Dormancy is weighted 2x, so days-since-worn is the lever that sets the hero. */
    private fun wardrobe() = listOf(
        TestFixtures.garment(id = "top-old", category = Category.TOP, daysSinceWorn = 200),
        TestFixtures.garment(id = "top-newer", category = Category.TOP, daysSinceWorn = 150),
        TestFixtures.garment(id = "bottom", category = Category.BOTTOM, daysSinceWorn = 120),
        TestFixtures.garment(id = "shoes", category = Category.SHOES, daysSinceWorn = 100),
        TestFixtures.garment(id = "coat", category = Category.OUTERWEAR, daysSinceWorn = 90)
    )

    @Test
    fun `the two tiles never repeat a category`() {
        val pick = useCase(wardrobe(), reading = null, now = TestFixtures.NOW)!!

        val categories = pick.supporting.map { it.garment.category }
        assertEquals(
            "supporting tiles duplicated a category: $categories",
            categories.distinct().size,
            categories.size
        )
    }

    @Test
    fun `a top hero is completed with outerwear, a bottom and shoes`() {
        val pick = useCase(wardrobe(), reading = null, now = TestFixtures.NOW)!!

        assertEquals(Category.TOP, pick.hero.garment.category)
        assertEquals(
            listOf(Category.OUTERWEAR, Category.BOTTOM, Category.SHOES),
            pick.supporting.map { it.garment.category }
        )
    }

    @Test
    fun `outerwear and a top are both suggested rather than substituted`() {
        // They fill separate slots: a blazer and the shirt under it are two decisions,
        // even though only one of them can be rendered.
        val pick = useCase(wardrobe(), reading = null, now = TestFixtures.NOW)!!

        val everything = listOf(pick.hero.garment.category) +
            pick.supporting.map { it.garment.category }

        assertTrue(everything.containsAll(
            listOf(Category.OUTERWEAR, Category.TOP, Category.BOTTOM, Category.SHOES)
        ))
    }

    @Test
    fun `a bottom hero is completed with a torso and feet`() {
        val garments = listOf(
            TestFixtures.garment(id = "bottom", category = Category.BOTTOM, daysSinceWorn = 200),
            TestFixtures.garment(id = "top", category = Category.TOP, daysSinceWorn = 120),
            TestFixtures.garment(id = "shoes", category = Category.SHOES, daysSinceWorn = 100)
        )

        val pick = useCase(garments, reading = null, now = TestFixtures.NOW)!!

        assertEquals(Category.BOTTOM, pick.hero.garment.category)
        assertEquals(
            listOf(Category.TOP, Category.SHOES),
            pick.supporting.map { it.garment.category }
        )
    }

    @Test
    fun `a dress needs only shoes, because it already covers both halves`() {
        val garments = listOf(
            TestFixtures.garment(id = "dress", category = Category.DRESS, daysSinceWorn = 200),
            TestFixtures.garment(id = "shoes", category = Category.SHOES, daysSinceWorn = 150),
            TestFixtures.garment(id = "top", category = Category.TOP, daysSinceWorn = 140),
            TestFixtures.garment(id = "bottom", category = Category.BOTTOM, daysSinceWorn = 130)
        )

        val pick = useCase(garments, reading = null, now = TestFixtures.NOW)!!

        assertEquals(Category.DRESS, pick.hero.garment.category)
        assertEquals(listOf(Category.SHOES), pick.supporting.map { it.garment.category })
    }

    @Test
    fun `outerwear covers the torso, so it asks for legs and feet rather than a top`() {
        val garments = listOf(
            TestFixtures.garment(id = "coat", category = Category.OUTERWEAR, daysSinceWorn = 200),
            TestFixtures.garment(id = "top", category = Category.TOP, daysSinceWorn = 190),
            TestFixtures.garment(id = "bottom", category = Category.BOTTOM, daysSinceWorn = 150),
            TestFixtures.garment(id = "shoes", category = Category.SHOES, daysSinceWorn = 140)
        )

        val pick = useCase(garments, reading = null, now = TestFixtures.NOW)!!

        assertEquals(Category.OUTERWEAR, pick.hero.garment.category)
        assertEquals(
            listOf(Category.TOP, Category.BOTTOM, Category.SHOES),
            pick.supporting.map { it.garment.category }
        )
    }

    @Test
    fun `a closet with no plain top simply omits that slot`() {
        val garments = listOf(
            TestFixtures.garment(id = "bottom", category = Category.BOTTOM, daysSinceWorn = 200),
            TestFixtures.garment(id = "coat", category = Category.OUTERWEAR, daysSinceWorn = 150),
            TestFixtures.garment(id = "shoes", category = Category.SHOES, daysSinceWorn = 100)
        )

        val pick = useCase(garments, reading = null, now = TestFixtures.NOW)!!

        assertEquals(
            listOf(Category.OUTERWEAR, Category.SHOES),
            pick.supporting.map { it.garment.category }
        )
    }

    @Test
    fun `a missing category is left out rather than padded with a duplicate`() {
        // No shoes at all. The old rule would have filled the second tile with whatever
        // scored next, which is how two tops ended up side by side.
        val garments = listOf(
            TestFixtures.garment(id = "top", category = Category.TOP, daysSinceWorn = 200),
            TestFixtures.garment(id = "top2", category = Category.TOP, daysSinceWorn = 190),
            TestFixtures.garment(id = "bottom", category = Category.BOTTOM, daysSinceWorn = 150)
        )

        val pick = useCase(garments, reading = null, now = TestFixtures.NOW)!!

        assertEquals(listOf(Category.BOTTOM), pick.supporting.map { it.garment.category })
    }

    @Test
    fun `the highest scorer within a needed slot still wins`() {
        // Two bottoms, one far more dormant. Slot filling must not throw away the ranking.
        val garments = listOf(
            TestFixtures.garment(id = "top", category = Category.TOP, daysSinceWorn = 300),
            TestFixtures.garment(id = "bottom-fresh", category = Category.BOTTOM, daysSinceWorn = 10),
            TestFixtures.garment(id = "bottom-dormant", category = Category.BOTTOM, daysSinceWorn = 250)
        )

        val pick = useCase(garments, reading = null, now = TestFixtures.NOW)!!

        assertEquals("bottom-dormant", pick.supporting.single().garment.id)
    }

    @Test
    fun `a one-garment closet still returns a pick with no tiles`() {
        val pick = useCase(
            listOf(TestFixtures.garment(id = "only", category = Category.TOP)),
            reading = null,
            now = TestFixtures.NOW
        )

        assertNotNull(pick)
        assertTrue(pick!!.supporting.isEmpty())
    }

    @Test
    fun `an empty closet has no pick`() {
        assertNull(useCase(emptyList(), reading = null, now = TestFixtures.NOW))
    }
}
