package com.closetiq.android.domain.repository

import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.model.RenderTarget
import com.closetiq.android.domain.model.SkinReading
import kotlinx.coroutines.flow.Flow

/**
 * Interfaces live in `domain`; the implementations live in `data`.
 * That inversion is what keeps the scoring engine testable without Android.
 */

interface WardrobeRepository {
    fun observeActiveGarments(): Flow<List<Garment>>
    fun observeAllGarments(): Flow<List<Garment>>
    suspend fun getGarment(id: String): Garment?

    /** Inserts immediately with status = PROCESSING, then resolves colour in the background. */
    suspend fun addGarment(label: String, category: Category, imagePath: String): String

    suspend fun rename(garmentId: String, label: String)

    /**
     * Replaces the photograph of a garment already in the closet.
     *
     * Colour is re-extracted from the new picture rather than kept, because the colour is
     * what every score is computed from — a garment photographed again in better light is
     * a different input to the palette engine, and keeping the old value would leave the
     * closet ranking on a photo that is no longer there.
     */
    suspend fun replacePhoto(garmentId: String, imagePath: String)

    suspend fun logWear(garmentId: String, at: Long = System.currentTimeMillis())
    suspend fun retire(garmentId: String)

    /** Removes the garment and its photograph outright. Not undoable. */
    suspend fun delete(garmentId: String)

    /**
     * The headline number, as a stream rather than a one-shot read.
     *
     * "Wore it" is tapped on the Mirror, so the Closet is not the screen that logged it
     * and cannot know to go and re-read. Hanging this off the same Room flow the grid
     * uses means the wear lands in the database and the percentage follows on its own,
     * whichever screen caused it.
     */
    fun observeUtilisation(days: Int = 90): Flow<Utilisation>

    /**
     * Clears out the demo closet earlier versions seeded on first launch.
     *
     * Nothing is seeded any more — the closet starts empty and holds only what the user
     * photographs. Anything added by hand is left alone: only rows marked as seeds go,
     * which is what that column was kept for.
     */
    suspend fun removeSeededGarments()
}

/**
 * Both halves of the headline number, carried together.
 *
 * The screen used to recover the count by multiplying the fraction back out against the
 * size of the grid, which only agrees while every garment is active — add an item and
 * the grid grows a tile that the percentage does not count yet.
 */
data class Utilisation(
    val wornCount: Int,
    val activeCount: Int
) {
    /** 0f..1f. Zero rather than NaN for an empty closet. */
    val fraction: Float
        get() = if (activeCount == 0) 0f else wornCount.toFloat() / activeCount
}

interface SkinRepository {
    fun observeLatestReading(): Flow<SkinReading?>

    /** The stored reading if it is still fresh, otherwise null. */
    suspend fun currentFreshReading(): SkinReading?

    /**
     * Sends one selfie for Skin Analysis + Facial Color Tones and stores the result.
     * Both YouCam calls read the same image — the user takes one photo, not two.
     */
    suspend fun captureReading(selfiePath: String): Result<SkinReading>
}

/**
 * The person, as opposed to the wardrobe.
 *
 * Each photo is asked for by the screen that needs it — the selfie on the Mirror, a body
 * shot when a try-on wants one — and then reused for every reading and every render.
 * Replacing one is a deliberate act.
 *
 * They are kept per purpose rather than as a single image because a selfie and a try-on
 * shot are different photographs: see [PersonPhotos].
 */
interface ProfileRepository {
    fun observePhotos(): Flow<PersonPhotos>
    suspend fun photos(): PersonPhotos
    suspend fun setPhoto(slot: PhotoSlot, path: String)
}

interface TryOnRepository {
    /** Renders [garment] onto [personImagePath]. Returns a URL or local path to the render. */
    suspend fun render(
        personImagePath: String,
        garmentImagePath: String,
        target: RenderTarget
    ): Result<RenderResult>
}

data class RenderResult(
    /**
     * Null when `cloth` reported success but produced nothing — a valid but unusable pair
     * of photos, not an error. [note] explains it.
     */
    val imageUrl: String?,
    /** Provider commentary — explains an empty [imageUrl], or carries the plain error. */
    val note: String?
)
