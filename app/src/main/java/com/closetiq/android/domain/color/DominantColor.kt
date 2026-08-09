package com.closetiq.android.domain.color

import com.closetiq.android.domain.model.LabColor

/**
 * Reduce a bag of pixels to the one colour that best represents the garment.
 *
 * Kept in `domain` (pure Kotlin, takes an IntArray rather than a Bitmap) so you can
 * unit-test it on the JVM. The Android-specific part — turning a photo into this
 * IntArray — lives in data/image/ColorExtractor.kt and is already written.
 */
object DominantColor {

    /**
     * TODO(you): return the dominant colour of these pixels, in Lab.
     *
     * [pixels] is ARGB, as returned by Bitmap.getPixels. Unpack with:
     *     val r = (pixel shr 16) and 0xFF
     *     val g = (pixel shr 8)  and 0xFF
     *     val b =  pixel         and 0xFF
     *     val alpha = (pixel ushr 24) and 0xFF
     *
     * A workable approach, roughly in order of effort:
     *   1. Skip transparent pixels (alpha < 128) — those are cut-out background.
     *   2. Skip near-white and near-black pixels; they are usually the wall or a shadow,
     *      not the garment. (Careful: a genuinely black shirt is a real case. Decide what
     *      you want here and write a test for it.)
     *   3. Convert what remains with ColorMath.rgbToLab and average.
     *
     * Averaging in Lab is fine to start. It goes wrong on patterned items — a red-and-white
     * stripe averages to pink. If that bothers you later, bucket the pixels into a coarse
     * grid and return the biggest bucket's centre instead. Write the failing test first.
     *
     * See DominantColorTest for the cases this needs to satisfy.
     */
    fun dominantLab(pixels: IntArray): LabColor {
        TODO("Return the dominant Lab colour of these pixels")
    }
}
