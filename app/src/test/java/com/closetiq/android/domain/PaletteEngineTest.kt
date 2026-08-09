package com.closetiq.android.domain

import com.closetiq.android.domain.TestFixtures.BEIGE
import com.closetiq.android.domain.TestFixtures.NAVY
import com.closetiq.android.domain.TestFixtures.reading
import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.model.Undertone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteEngineTest {

    // ---- buildPalette ----

    @Test
    fun `palette carries the undertone it was built from`() {
        val palette = PaletteEngine.buildPalette(reading(undertone = Undertone.WARM))
        assertEquals(Undertone.WARM, palette.undertone)
    }

    @Test
    fun `palette has anchors`() {
        val palette = PaletteEngine.buildPalette(reading())
        assertTrue("a palette with no anchors cannot score anything", palette.anchors.isNotEmpty())
    }

    @Test
    fun `warm and cool produce different palettes`() {
        val warm = PaletteEngine.buildPalette(reading(undertone = Undertone.WARM))
        val cool = PaletteEngine.buildPalette(reading(undertone = Undertone.COOL))
        assertNotEquals(warm.anchors, cool.anchors)
    }

    /**
     * The b axis is blue (negative) to yellow (positive). A warm palette should lean
     * yellow on average; a cool one should not.
     */
    @Test
    fun `warm anchors lean yellow relative to cool anchors`() {
        val warmB = PaletteEngine.buildPalette(reading(undertone = Undertone.WARM))
            .anchors.map { it.b }.average()
        val coolB = PaletteEngine.buildPalette(reading(undertone = Undertone.COOL))
            .anchors.map { it.b }.average()

        assertTrue("warm avg b ($warmB) should exceed cool avg b ($coolB)", warmB > coolB)
    }

    // ---- paletteFit ----

    @Test
    fun `an exact anchor match scores one`() {
        val palette = PaletteEngine.buildPalette(reading())
        val anchor = palette.anchors.first()

        assertEquals(1f, PaletteEngine.paletteFit(anchor, palette), 0.02f)
    }

    @Test
    fun `fit always stays within zero and one`() {
        val palette = PaletteEngine.buildPalette(reading())

        listOf(NAVY, BEIGE, TestFixtures.CORAL, TestFixtures.BLACK, TestFixtures.WHITE)
            .forEach { color ->
                val fit = PaletteEngine.paletteFit(color, palette)
                assertTrue("fit for $color was $fit, outside 0..1", fit in 0f..1f)
            }
    }

    /**
     * Matching one anchor is enough — a garment does not have to suit the whole palette.
     * This is the test that catches an implementation that averaged across all anchors
     * instead of taking the nearest.
     */
    @Test
    fun `nearest anchor decides the fit, not the average`() {
        val palette = PaletteEngine.buildPalette(reading())
        val nearAnchor = palette.anchors.first().copy(l = palette.anchors.first().l + 2f)

        assertTrue(
            "a colour sitting on top of one anchor should score high",
            PaletteEngine.paletteFit(nearAnchor, palette) > 0.85f
        )
    }
}
