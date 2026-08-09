package com.closetiq.android.domain

import com.closetiq.android.domain.color.ColorMath
import com.closetiq.android.domain.color.DominantColor
import org.junit.Assert.assertTrue
import org.junit.Test

class DominantColorTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun solid(r: Int, g: Int, b: Int, count: Int = 100): IntArray =
        IntArray(count) { argb(255, r, g, b) }

    @Test
    fun `a solid block returns that colour`() {
        val expected = ColorMath.rgbToLab(200, 40, 40)
        val actual = DominantColor.dominantLab(solid(200, 40, 40))

        assertTrue(
            "expected $expected, got $actual",
            ColorMath.deltaE76(expected, actual) < 5f
        )
    }

    /** Transparent pixels are removed background, not part of the garment. */
    @Test
    fun `fully transparent pixels are ignored`() {
        val pixels = solid(200, 40, 40, 50) + IntArray(50) { argb(0, 255, 255, 255) }
        val actual = DominantColor.dominantLab(pixels)

        assertTrue(
            "transparent white should not lighten the result, got $actual",
            actual.l < 60f
        )
    }

    /** A garment shot against a white wall should not come back as pale pink. */
    @Test
    fun `a white background does not dominate a coloured garment`() {
        val pixels = solid(30, 90, 60, 60) + solid(252, 252, 250, 40)
        val actual = DominantColor.dominantLab(pixels)
        val green = ColorMath.rgbToLab(30, 90, 60)

        assertTrue(
            "expected something close to the green garment, got $actual",
            ColorMath.deltaE76(green, actual) < 25f
        )
    }

    /**
     * Your call. A genuinely black garment is a real case, so "skip dark pixels" cannot
     * be unconditional. If you decide differently, change this test first.
     */
    @Test
    fun `an all-black garment still returns black`() {
        val actual = DominantColor.dominantLab(solid(10, 10, 10))
        assertTrue("a black shirt should read as dark, got $actual", actual.l < 30f)
    }
}
