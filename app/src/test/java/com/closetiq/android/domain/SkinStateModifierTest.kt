package com.closetiq.android.domain

import com.closetiq.android.domain.TestFixtures.BEIGE
import com.closetiq.android.domain.TestFixtures.CORAL
import com.closetiq.android.domain.TestFixtures.EMERALD
import com.closetiq.android.domain.TestFixtures.NAVY
import com.closetiq.android.domain.TestFixtures.reading
import com.closetiq.android.domain.engine.SkinStateModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests encode the actual product claim: that what suits you *today* differs from
 * what suits you in general. If these pass, the app is doing something no wardrobe
 * tracker does — and that is the whole reason it belongs in the Skin AI + VTO category.
 */
class SkinStateModifierTest {

    @Test
    fun `a calm reading is neutral about everything`() {
        val calm = reading(redness = 0f, dullness = 0f, darkCircles = 0f)

        listOf(NAVY, CORAL, BEIGE, EMERALD).forEach { color ->
            assertEquals(
                "with no skin signal, nothing should be favoured or punished",
                0.5f,
                SkinStateModifier.skinDayFit(color, calm),
                0.05f
            )
        }
    }

    @Test
    fun `result always stays within zero and one`() {
        val extreme = reading(redness = 1f, dullness = 1f, darkCircles = 1f)

        listOf(NAVY, CORAL, BEIGE, EMERALD, TestFixtures.BLACK, TestFixtures.WHITE)
            .forEach { color ->
                val fit = SkinStateModifier.skinDayFit(color, extreme)
                assertTrue("fit for $color was $fit, outside 0..1", fit in 0f..1f)
            }
    }

    /** The headline behaviour: a flush day rules out coral and leaves navy alone. */
    @Test
    fun `redness penalises coral more than navy`() {
        val flushed = reading(redness = 0.9f)

        val coralFit = SkinStateModifier.skinDayFit(CORAL, flushed)
        val navyFit = SkinStateModifier.skinDayFit(NAVY, flushed)

        assertTrue(
            "on a red day navy ($navyFit) should beat coral ($coralFit)",
            navyFit > coralFit
        )
    }

    @Test
    fun `redness does not change the ranking when skin is calm`() {
        val calm = reading(redness = 0f)

        val coralFit = SkinStateModifier.skinDayFit(CORAL, calm)
        val navyFit = SkinStateModifier.skinDayFit(NAVY, calm)

        assertEquals(
            "with no redness, coral and navy should be treated the same",
            coralFit,
            navyFit,
            0.05f
        )
    }

    /** Dull skin wants saturated colour near it, not more beige. */
    @Test
    fun `dullness favours saturated colour over muted`() {
        val dull = reading(dullness = 0.9f)

        val emeraldFit = SkinStateModifier.skinDayFit(EMERALD, dull)
        val beigeFit = SkinStateModifier.skinDayFit(BEIGE, dull)

        assertTrue(
            "on a dull day emerald ($emeraldFit) should beat beige ($beigeFit)",
            emeraldFit > beigeFit
        )
    }

    @Test
    fun `dark circles penalise very dark garments`() {
        val tired = reading(darkCircles = 0.9f)

        val blackFit = SkinStateModifier.skinDayFit(TestFixtures.BLACK, tired)
        val whiteFit = SkinStateModifier.skinDayFit(TestFixtures.WHITE, tired)

        assertTrue(
            "when tired, a light garment ($whiteFit) should beat a very dark one ($blackFit)",
            whiteFit > blackFit
        )
    }

    // ---- explain ----

    @Test
    fun `explain returns something short and non-empty`() {
        val text = SkinStateModifier.explain(NAVY, reading(redness = 0.8f))

        assertTrue("the reason line cannot be empty — it is the demo", text.isNotBlank())
        assertTrue("keep it to one sentence, was ${text.length} chars", text.length < 140)
    }
}
