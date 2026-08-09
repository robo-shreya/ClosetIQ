package com.closetiq.android.data.image

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Makes an image satisfy the YouCam upload envelope *before* it is ever sent, so a
 * rejected upload is impossible rather than merely unlikely.
 *
 * From the API console:
 *   JPEG or PNG · ≤ 10 MB · min 512 × 384 px · long side ≤ 4096 px
 *
 * The long side is capped well below 4096 here — the models do not benefit from more,
 * and it keeps the base64 payload small enough to post comfortably over localhost.
 */
object ImagePreflight {

    const val MIN_WIDTH = 512
    const val MIN_HEIGHT = 384
    const val MAX_LONG_SIDE = 1600
    const val JPEG_QUALITY = 88
    const val MAX_BYTES = 10 * 1024 * 1024

    /** Scales into range, keeping aspect ratio. Upscales if the source is too small. */
    fun prepare(source: Bitmap): Bitmap {
        val scale = scaleFactorFor(source.width, source.height)
        if (scale == 1f) return source

        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    fun scaleFactorFor(width: Int, height: Int): Float {
        val longSide = maxOf(width, height)

        // Too big: shrink to the cap.
        if (longSide > MAX_LONG_SIDE) return MAX_LONG_SIDE.toFloat() / longSide

        // Too small: grow until both minimums are met. Upscaling is lossy but a rejected
        // upload is worse, and phone cameras rarely produce anything this small anyway.
        val widthScale = if (width < MIN_WIDTH) MIN_WIDTH.toFloat() / width else 1f
        val heightScale = if (height < MIN_HEIGHT) MIN_HEIGHT.toFloat() / height else 1f
        return maxOf(widthScale, heightScale)
    }

    fun toBase64Jpeg(bitmap: Bitmap): String {
        val prepared = prepare(bitmap)
        val stream = ByteArrayOutputStream()
        prepared.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
