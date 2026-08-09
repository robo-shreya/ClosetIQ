package com.closetiq.android.data.image

import android.graphics.Bitmap
import com.closetiq.android.domain.color.DominantColor
import com.closetiq.android.domain.model.LabColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges Android's Bitmap to the pure-Kotlin DominantColor you are writing.
 *
 * The Android-specific half is done for you. The decision half — which pixels count as
 * "the garment" — is DominantColor.dominantLab.
 */
class ColorExtractor(private val imageStore: ImageStore) {

    suspend fun fromPath(path: String): LabColor? = withContext(Dispatchers.Default) {
        val bitmap = imageStore.load(path) ?: return@withContext null
        fromBitmap(bitmap)
    }

    fun fromBitmap(bitmap: Bitmap): LabColor {
        // Downsample first. A 1600px photo is 2.5M pixels and the answer does not change;
        // 128x128 is 16k pixels and runs in single-digit milliseconds.
        val small = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)

        return DominantColor.dominantLab(pixels)
    }

    /**
     * Without background removal, the frame edge is mostly wall. Cropping to the middle
     * before sampling is the cheap substitute — it is why the add-item screen tells the
     * user to fill the frame.
     */
    fun fromBitmapCentreCrop(bitmap: Bitmap, keepFraction: Float = 0.6f): LabColor {
        val cropWidth = (bitmap.width * keepFraction).toInt().coerceAtLeast(1)
        val cropHeight = (bitmap.height * keepFraction).toInt().coerceAtLeast(1)
        val x = (bitmap.width - cropWidth) / 2
        val y = (bitmap.height - cropHeight) / 2

        val cropped = Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
        return fromBitmap(cropped)
    }

    private companion object {
        const val SAMPLE_SIZE = 128
    }
}
