package com.closetiq.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "profile")

/**
 * The things the app remembers about the person rather than the wardrobe.
 *
 * The photos deliberately do NOT live on [SkinReadingEntity]. A skin reading expires
 * after five days; a picture of you does not. Tying them together is what made
 * "See it on me" forget the picture every time a reading went stale.
 */
class ProfileStore(private val context: Context) {

    fun observePhotos(): Flow<PersonPhotos> =
        context.profileDataStore.data.map(::readPhotos)

    suspend fun photos(): PersonPhotos = observePhotos().first()

    suspend fun setPhoto(slot: PhotoSlot, path: String) {
        context.profileDataStore.edit { it[keyFor(slot)] = path }
    }

    /**
     * The selfie falls back to [LEGACY_PERSON_PHOTO], the single key used before photos
     * were split by purpose. Anyone who attached one earlier keeps it rather than being
     * silently asked for it again.
     */
    private fun readPhotos(prefs: Preferences) = PersonPhotos(
        selfie = prefs[keyFor(PhotoSlot.SELFIE)] ?: prefs[LEGACY_PERSON_PHOTO],
        fullBody = prefs[keyFor(PhotoSlot.FULL_BODY)],
        upperBody = prefs[keyFor(PhotoSlot.UPPER_BODY)],
        lowerBody = prefs[keyFor(PhotoSlot.LOWER_BODY)]
    )

    private fun keyFor(slot: PhotoSlot) = when (slot) {
        PhotoSlot.SELFIE -> SELFIE
        PhotoSlot.FULL_BODY -> FULL_BODY
        PhotoSlot.UPPER_BODY -> UPPER_BODY
        PhotoSlot.LOWER_BODY -> LOWER_BODY
    }

    private companion object {
        val SELFIE = stringPreferencesKey("person_photo_selfie")
        val FULL_BODY = stringPreferencesKey("person_photo_full_body")
        val UPPER_BODY = stringPreferencesKey("person_photo_upper_body")
        val LOWER_BODY = stringPreferencesKey("person_photo_lower_body")

        /** Pre-split key. Read for the selfie, never written to again. */
        val LEGACY_PERSON_PHOTO = stringPreferencesKey("person_photo_path")
    }
}
