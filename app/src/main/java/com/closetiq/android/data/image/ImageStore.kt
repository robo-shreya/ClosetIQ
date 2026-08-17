package com.closetiq.android.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

/**
 * App-private image storage. Photos picked from the gallery are copied here so the app
 * keeps working after the original is deleted or the content:// permission lapses.
 */
class ImageStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(BitmapFactory.decodeStream(input)) { "Could not decode $uri" }
        }
        val prepared = ImagePreflight.prepare(bitmap)
        save(prepared)
    }

    suspend fun save(bitmap: Bitmap, name: String = "${UUID.randomUUID()}.jpg"): String =
        withContext(Dispatchers.IO) {
            val file = File(dir, name)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, ImagePreflight.JPEG_QUALITY, out)
            }
            file.absolutePath
        }

    suspend fun load(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) BitmapFactory.decodeFile(path) else null
    }

    suspend fun toBase64(path: String): String? = withContext(Dispatchers.IO) {
        val bitmap = load(path) ?: return@withContext null
        ImagePreflight.toBase64Jpeg(bitmap)
    }

    /**
     * Copies a bundled asset into app storage and returns its path.
     *
     * The seeded closet ships its photos as assets, but an asset has no file path, and
     * both the colour extractor and the try-on upload need one. Copying on first launch
     * makes a seeded garment indistinguishable from one the user photographed.
     */
    suspend fun importFromAsset(assetName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = context.assets.open(assetName).use { input ->
                requireNotNull(BitmapFactory.decodeStream(input)) { "Could not decode $assetName" }
            }
            save(ImagePreflight.prepare(bitmap), name = assetName.substringAfterLast('/'))
        }.getOrNull()
    }

    /**
     * Downloads a finished render and keeps it.
     *
     * Needed twice over. Chaining try-on calls means feeding one render back in as the
     * person image for the next garment, and the API only ever hands back a URL — so the
     * bytes have to come down before they can go up again. It also solves the expiry
     * problem on its own: YouCam signs result URLs with `X-Amz-Expires=7200`, so a render
     * that is only ever referenced by link is gone two hours later.
     */
    suspend fun importFromUrl(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = URL(url).openStream().use { input ->
                requireNotNull(BitmapFactory.decodeStream(input)) { "Could not decode $url" }
            }
            save(ImagePreflight.prepare(bitmap))
        }.getOrNull()
    }

    fun delete(path: String): Boolean = File(path).takeIf { it.exists() }?.delete() ?: false
}
