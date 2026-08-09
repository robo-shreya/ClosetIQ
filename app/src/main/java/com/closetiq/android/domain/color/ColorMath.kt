package com.closetiq.android.domain.color

import com.closetiq.android.domain.model.LabColor
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Colour-space conversion and distance.
 *
 * These are published formulas — copying them teaches you nothing, so they are
 * implemented for you. The interesting decisions built *on top* of them
 * (which anchors, which weights) are the TODOs in PaletteEngine.
 */
object ColorMath {

    /** sRGB (0..255 per channel, as packed by Android's Color.rgb) to CIELAB (D65). */
    fun rgbToLab(r: Int, g: Int, b: Int): LabColor {
        // 1. sRGB -> linear RGB
        fun linearise(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        val rl = linearise(r)
        val gl = linearise(g)
        val bl = linearise(b)

        // 2. linear RGB -> XYZ (sRGB D65 matrix)
        val x = (rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375) / 0.95047
        val y = (rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750) / 1.00000
        val z = (rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041) / 1.08883

        // 3. XYZ -> Lab
        fun f(t: Double): Double =
            if (t > 0.008856) cbrt(t) else (7.787 * t) + (16.0 / 116.0)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        return LabColor(
            l = ((116.0 * fy) - 16.0).toFloat(),
            a = (500.0 * (fx - fy)).toFloat(),
            b = (200.0 * (fy - fz)).toFloat()
        )
    }

    /**
     * CIELAB back to sRGB, packed as 0xRRGGBB.
     *
     * Needed because seeded garments have no photo and render as a colour swatch.
     * Also handy for debugging: it lets you actually see what a Lab value looks like.
     */
    fun labToRgb(color: LabColor): Int {
        val fy = (color.l + 16.0) / 116.0
        val fx = fy + (color.a / 500.0)
        val fz = fy - (color.b / 200.0)

        fun finv(t: Double): Double {
            val cube = t * t * t
            return if (cube > 0.008856) cube else (t - 16.0 / 116.0) / 7.787
        }

        val x = finv(fx) * 0.95047
        val y = finv(fy) * 1.00000
        val z = finv(fz) * 1.08883

        val rl = x * 3.2404542 + y * -1.5371385 + z * -0.4985314
        val gl = x * -0.9692660 + y * 1.8760108 + z * 0.0415560
        val bl = x * 0.0556434 + y * -0.2040259 + z * 1.0572252

        fun delinearise(c: Double): Int {
            val v = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
            return (v * 255.0).roundToInt().coerceIn(0, 255)
        }

        return (delinearise(rl) shl 16) or (delinearise(gl) shl 8) or delinearise(bl)
    }

    /**
     * CIE76 colour difference — plain Euclidean distance in Lab.
     *
     * Good enough to build against, and simple enough to reason about:
     * roughly, < 2.3 is "indistinguishable", > 10 is "clearly different",
     * > 50 is "unrelated colours".
     *
     * Upgrading to ΔE2000 later is a drop-in replacement for this one function.
     */
    fun deltaE76(x: LabColor, y: LabColor): Float {
        val dl = (x.l - y.l).toDouble()
        val da = (x.a - y.a).toDouble()
        val db = (x.b - y.b).toDouble()
        return sqrt(dl * dl + da * da + db * db).toFloat()
    }

    /** Hue angle in degrees, 0..360. Useful for "is this garment in the red family". */
    fun hueAngle(color: LabColor): Float {
        var deg = Math.toDegrees(kotlin.math.atan2(color.b.toDouble(), color.a.toDouble()))
        if (deg < 0) deg += 360.0
        return deg.toFloat()
    }

    /** Chroma — how saturated the colour is. 0 is grey. */
    fun chroma(color: LabColor): Float =
        sqrt((color.a * color.a + color.b * color.b).toDouble()).toFloat()

    /** Smallest angle between two hues, 0..180. Handles the wrap at 360. */
    fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}
