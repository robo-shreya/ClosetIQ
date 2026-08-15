package com.closetiq.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "profile")

/**
 * The two things the app remembers about the person rather than the wardrobe.
 *
 * The person photo deliberately does NOT live on [SkinReadingEntity]. A skin reading
 * expires after five days; the photo of you does not. Tying them together is what made
 * "See it on me" forget the picture every time a reading went stale.
 */
class ProfileStore(private val context: Context) {

    fun observePersonPhoto(): Flow<String?> =
        context.profileDataStore.data.map { it[PERSON_PHOTO] }

    suspend fun personPhoto(): String? = observePersonPhoto().first()

    suspend fun setPersonPhoto(path: String) {
        context.profileDataStore.edit { it[PERSON_PHOTO] = path }
    }

    fun observeOnboarded(): Flow<Boolean> =
        context.profileDataStore.data.map { it[ONBOARDED] ?: false }

    suspend fun markOnboarded() {
        context.profileDataStore.edit { it[ONBOARDED] = true }
    }

    private companion object {
        val PERSON_PHOTO = stringPreferencesKey("person_photo_path")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }
}
