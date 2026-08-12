package com.closetiq.android.domain

import com.closetiq.android.domain.TestFixtures.CORAL
import com.closetiq.android.domain.TestFixtures.MUSTARD
import com.closetiq.android.domain.TestFixtures.NAVY
import com.closetiq.android.domain.TestFixtures.NOW
import com.closetiq.android.domain.TestFixtures.garment
import com.closetiq.android.domain.TestFixtures.reading
import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.Undertone
import com.closetiq.android.domain.usecase.CheckDuplicateUseCase
import com.closetiq.android.domain.usecase.CheckDuplicateUseCase.BuyAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckDuplicateUseCaseTest {

    private val useCase = CheckDuplicateUseCase()

    /** Cool, so navy scores well and mustard scores badly — see PaletteEngine. */
    private val coolPalette = PaletteEngine.buildPalette(reading(undertone = Undertone.COOL))

    private fun check(
        closet: List<com.closetiq.android.domain.model.Garment>,
        color: LabColor = NAVY,
        category: Category = Category.TOP
    ) = useCase(closet, color, category, coolPalette, NOW)

    // ---- matching ----

    @Test
    fun `an identical colour in the same category is a match`() {
        val verdict = check(listOf(garment(id = "navy-tee", color = NAVY, category = Category.TOP)))

        assertEquals(listOf("navy-tee"), verdict.matches.map { it.id })
    }

    /**
     * The whole point of the category filter: owning navy jeans is not a reason to
     * skip a navy shirt.
     */
    @Test
    fun `the same colour in another category is not a match`() {
        val verdict = check(
            listOf(garment(id = "navy-jeans", color = NAVY, category = Category.BOTTOM)),
            category = Category.TOP
        )

        assertTrue("bottoms must not match a top", verdict.matches.isEmpty())
    }

    @Test
    fun `a clearly different colour in the same category is not a match`() {
        val verdict = check(listOf(garment(id = "coral-tee", color = CORAL, category = Category.TOP)))

        assertTrue("coral is nowhere near navy", verdict.matches.isEmpty())
    }

    @Test
    fun `retired garments are not things you own`() {
        val verdict = check(
            listOf(
                garment(id = "retired", color = NAVY, category = Category.TOP, retiredAt = NOW)
            )
        )

        assertTrue(verdict.matches.isEmpty())
    }

    @Test
    fun `matches come back nearest colour first`() {
        val verdict = check(
            listOf(
                garment(id = "further", color = LabColor(36f, 5f, -18f), category = Category.TOP),
                garment(id = "exact", color = NAVY, category = Category.TOP)
            )
        )

        assertEquals(listOf("exact", "further"), verdict.matches.map { it.id })
    }

    // ---- advice ----

    @Test
    fun `an empty closet is a buy`() {
        assertEquals(BuyAdvice.BUY, check(emptyList()).advice)
    }

    @Test
    fun `one match is worth thinking about`() {
        val verdict = check(listOf(garment(id = "a", color = NAVY, category = Category.TOP)))

        assertEquals(BuyAdvice.THINK_TWICE, verdict.advice)
    }

    @Test
    fun `two matches means you already own it`() {
        val verdict = check(
            listOf(
                garment(id = "a", color = NAVY, category = Category.TOP),
                garment(id = "b", color = NAVY, category = Category.TOP)
            )
        )

        assertEquals(BuyAdvice.ALREADY_OWN, verdict.advice)
        assertTrue("the count belongs in the headline", verdict.headline.contains("2"))
    }

    /** Nothing like it, but it still does not suit you — that is not a green light. */
    @Test
    fun `a poor palette fit is not a buy even with no matches`() {
        val verdict = check(emptyList(), color = MUSTARD)

        assertTrue(
            "mustard should score badly against a cool palette, was ${verdict.paletteFit}",
            verdict.paletteFit < CheckDuplicateUseCase.WEAK_PALETTE_FIT
        )
        assertEquals(BuyAdvice.THINK_TWICE, verdict.advice)
    }

    // ---- dormancy, the argument ----

    @Test
    fun `counts how many of the matches have been forgotten`() {
        val verdict = check(
            listOf(
                garment(id = "worn", color = NAVY, category = Category.TOP, daysSinceWorn = 2),
                garment(id = "old-1", color = NAVY, category = Category.TOP, daysSinceWorn = 120),
                garment(id = "old-2", color = NAVY, category = Category.TOP, daysSinceWorn = 200)
            )
        )

        assertEquals(3, verdict.matches.size)
        assertEquals(2, verdict.dormantMatches)
        assertTrue(
            "the dormant count carries the argument, was '${verdict.detail}'",
            verdict.detail!!.contains("2")
        )
    }

    /**
     * Caught on device: the headline said "you already own 2" while the detail said
     * "this would be a second one". It would be a third.
     */
    @Test
    fun `does not call a third copy a second one`() {
        val verdict = check(
            listOf(
                garment(id = "a", color = NAVY, category = Category.TOP, daysSinceWorn = 1),
                garment(id = "b", color = NAVY, category = Category.TOP, daysSinceWorn = 2)
            )
        )

        assertTrue(
            "two matches must not be described as a second one, was '${verdict.detail}'",
            !verdict.detail!!.contains("second")
        )
        assertTrue("it would be number 3, was '${verdict.detail}'", verdict.detail!!.contains("3"))
    }

    @Test
    fun `calls a second copy a second one`() {
        val verdict = check(
            listOf(garment(id = "a", color = NAVY, category = Category.TOP, daysSinceWorn = 1))
        )

        assertTrue(verdict.detail!!.contains("second"))
    }

    @Test
    fun `says nothing about dormancy when every match is in rotation`() {
        val verdict = check(
            listOf(
                garment(id = "a", color = NAVY, category = Category.TOP, daysSinceWorn = 1),
                garment(id = "b", color = NAVY, category = Category.TOP, daysSinceWorn = 3)
            )
        )

        assertEquals(0, verdict.dormantMatches)
    }

    @Test
    fun `every verdict carries a headline`() {
        val closets = listOf(
            emptyList(),
            listOf(garment(color = NAVY, category = Category.TOP)),
            listOf(
                garment(id = "a", color = NAVY, category = Category.TOP),
                garment(id = "b", color = NAVY, category = Category.TOP)
            )
        )

        closets.forEach { closet ->
            assertTrue("a verdict with no headline cannot be shown", check(closet).headline.isNotBlank())
        }
    }
}
