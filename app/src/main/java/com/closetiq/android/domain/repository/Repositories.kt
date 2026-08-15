package com.closetiq.android.domain.repository

import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
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

    suspend fun logWear(garmentId: String, at: Long = System.currentTimeMillis())
    suspend fun retire(garmentId: String)

    /** Fraction of the active closet worn in the last [days]. 0f..1f — the headline number. */
    suspend fun utilisation(days: Int = 90): Float

    /** Populates the demo closet on first launch. Safe to call repeatedly. */
    suspend fun seedIfEmpty()
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
 * One photo is attached once and reused as the person image for every try-on, so the
 * user is never asked for it again mid-flow. Replacing it is a deliberate act.
 */
interface ProfileRepository {
    fun observePersonPhoto(): Flow<String?>
    suspend fun personPhoto(): String?
    suspend fun setPersonPhoto(path: String)

    fun observeOnboarded(): Flow<Boolean>
    suspend fun markOnboarded()
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
    /** Null when the provider returned no image — the mock backend often does. */
    val imageUrl: String?,
    /** Provider commentary. With the Gemma mock this is the interesting part. */
    val note: String?
)
