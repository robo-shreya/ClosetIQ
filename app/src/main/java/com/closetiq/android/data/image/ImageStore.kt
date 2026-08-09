package com.closetiq.android.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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

    fun delete(path: String): Boolean = File(path).takeIf { it.exists() }?.delete() ?: false
}
