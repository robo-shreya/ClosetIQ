package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.repository.WardrobeRepository

/**
 * "Wore it" — resets the dormancy clock and moves the utilisation number.
 *
 * Written for you: it is a one-line delegation. The interesting behaviour is in
 * WardrobeRepository.logWear, which bumps wearCount and lastWornAt in one transaction.
 */
class LogWearUseCase(
    private val wardrobe: WardrobeRepository
) {
    suspend operator fun invoke(garmentId: String, at: Long = System.currentTimeMillis()) {
        wardrobe.logWear(garmentId, at)
    }
}
