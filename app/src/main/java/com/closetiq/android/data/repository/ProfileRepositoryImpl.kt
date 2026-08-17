package com.closetiq.android.data.repository

import com.closetiq.android.data.local.ProfileStore
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow

class ProfileRepositoryImpl(private val store: ProfileStore) : ProfileRepository {

    override fun observePhotos(): Flow<PersonPhotos> = store.observePhotos()

    override suspend fun photos(): PersonPhotos = store.photos()

    override suspend fun setPhoto(slot: PhotoSlot, path: String) = store.setPhoto(slot, path)
}
