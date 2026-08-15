package com.closetiq.android.data.repository

import com.closetiq.android.data.local.ProfileStore
import com.closetiq.android.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow

class ProfileRepositoryImpl(private val store: ProfileStore) : ProfileRepository {

    override fun observePersonPhoto(): Flow<String?> = store.observePersonPhoto()

    override suspend fun personPhoto(): String? = store.personPhoto()

    override suspend fun setPersonPhoto(path: String) = store.setPersonPhoto(path)

    override fun observeOnboarded(): Flow<Boolean> = store.observeOnboarded()

    override suspend fun markOnboarded() = store.markOnboarded()
}
