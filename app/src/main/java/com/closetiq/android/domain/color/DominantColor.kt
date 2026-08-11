package com.closetiq.android.domain.color

import com.closetiq.android.domain.model.LabColor
import kotlin.math.floor

/**
 * Reduce a bag of pixels to the one colour that best represents the garment.
 *
 * Kept in `domain` (pure Kotlin, takes an IntArray rather than a Bitmap) so it can be
 * unit-tested on the JVM. The Android-specific part — turning a photo into this
 * IntArray — lives in data/image/ColorExtractor.kt.
 */
object DominantColor {

    /** Below this, a pixel is removed background rather than garment. */
    private const val MIN_ALPHA = 128

    // What counts as wall rather than cloth: bright AND colourless together. The chroma
    // half of that pair is what lets a cream knit through, since cream carries real
    // yellow even at high lightness.
    //
    // 85 rather than something closer to pure white because a white wall does not
    // photograph at 95 — shading and falloff put most of it between 79 and 95.
    //
    // There is deliberately no matching rule at the dark end. Dark garments sit at the
    // same lightness as shadow, so a shadow filter deletes most of a black coat, and a
    // mid-tone background then supplies enough survivors that the fallback below never
    // fires — the extractor confidently returns the wall. Bucketing handles dark
    // backgrounds on its own, because the garment fills most of a centre-cropped frame.
    private const val BACKGROUND_LIGHTNESS = 85f
    private const val BACKGROUND_CHROMA = 10f

    /**
     * If the filter leaves fewer than this share of the pixels, the "background" was
     * probably the garment — a white shirt on a white wall — so the filter is abandoned
     * rather than trusted.
     */
    private const val MIN_SURVIVING_FRACTION = 0.15f

    // Bucket size in Lab. Coarse enough that shading across a single garment collapses
    // into one bucket, fine enough to keep two genuinely different colours apart.
    private const val BUCKET_LIGHTNESS = 10f
    private const val BUCKET_CHROMA = 12f

    /** Mid-grey, for the degenerate case of no usable pixels at all. */
    private val NEUTRAL = LabColor(50f, 0f, 0f)

    /**
     * The dominant colour of these pixels, in Lab.
     *
     * [pixels] is ARGB, as returned by Bitmap.getPixels.
     *
     * This buckets rather than averages. Averaging is the obvious approach and it is
     * wrong on anything patterned: a red-and-white stripe averages to pink, a colour
     * that appears nowhere in the garment. Grouping into coarse Lab cells and taking
     * the largest cell returns a colour the garment actually contains.
     */
    fun dominantLab(pixels: IntArray): LabColor {
        val opaque = pixels.filter { pixel -> (pixel ushr 24 and 0xFF) >= MIN_ALPHA }
        if (opaque.isEmpty()) return NEUTRAL

        val all = opaque.map { pixel ->
            ColorMath.rgbToLab(
                r = (pixel shr 16) and 0xFF,
                g = (pixel shr 8) and 0xFF,
                b = pixel and 0xFF
            )
        }

        val foreground = all.filterNot { it.looksLikeBackground() }
        val considered = if (foreground.size >= all.size * MIN_SURVIVING_FRACTION) {
            foreground
        } else {
            all
        }

        // Average within the winning bucket, so the answer keeps the precision the
        // bucket grid threw away.
        return considered
            .groupBy { it.bucket() }
            .maxBy { (_, members) -> members.size }
            .value
            .averageColor()
    }

    /** Bright and colourless — a wall, a sheet, an overexposed backdrop. */
    private fun LabColor.looksLikeBackground(): Boolean =
        l >= BACKGROUND_LIGHTNESS && ColorMath.chroma(this) <= BACKGROUND_CHROMA

    /**
     * floor rather than toInt: truncation rounds toward zero, which would make one
     * double-width bucket straddling a = 0 and b = 0.
     */
    private fun LabColor.bucket(): Int {
        val lCell = floor(l / BUCKET_LIGHTNESS).toInt()
        val aCell = floor(a / BUCKET_CHROMA).toInt()
        val bCell = floor(b / BUCKET_CHROMA).toInt()

        // Packed so the key is a primitive rather than a boxed triple.
        return (lCell * 31 + aCell) * 31 + bCell
    }

    private fun List<LabColor>.averageColor(): LabColor = LabColor(
        l = (sumOf { it.l.toDouble() } / size).toFloat(),
        a = (sumOf { it.a.toDouble() } / size).toFloat(),
        b = (sumOf { it.b.toDouble() } / size).toFloat()
    )
}
