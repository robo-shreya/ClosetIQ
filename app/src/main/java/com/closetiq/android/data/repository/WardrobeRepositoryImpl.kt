package com.closetiq.android.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.closetiq.android.data.image.ColorExtractor
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.data.local.ClosetDatabase
import com.closetiq.android.data.local.GarmentEntity
import com.closetiq.android.data.local.WearLogEntity
import com.closetiq.android.data.local.toDomain
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.GarmentStatus
import com.closetiq.android.domain.repository.Utilisation
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.RankDormantUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class WardrobeRepositoryImpl(
    private val db: ClosetDatabase,
    private val imageStore: ImageStore,
    private val colorExtractor: ColorExtractor,
    /** Background work runs here so addGarment can return the moment the row is inserted. */
    private val scope: CoroutineScope
) : WardrobeRepository {

    private val garments = db.garmentDao()
    private val wearLog = db.wearLogDao()

    override fun observeActiveGarments(): Flow<List<Garment>> =
        garments.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAllGarments(): Flow<List<Garment>> =
        garments.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getGarment(id: String): Garment? =
        garments.getById(id)?.toDomain()

    /**
     * Inserts as PROCESSING and returns immediately, then resolves colour in the background.
     *
     * This ordering is the whole reason adding an item feels instant: the closet grid is
     * driven by a Room Flow, so the tile appears the moment the row lands and fills itself
     * in when the colour arrives. Nothing is ever blocked on image work.
     */
    override suspend fun addGarment(label: String, category: Category, imagePath: String): String {
        val id = UUID.randomUUID().toString()

        garments.insert(
            GarmentEntity(
                id = id,
                label = label,
                category = category.name,
                imagePath = imagePath,
                cutoutPath = null,
                labL = PLACEHOLDER_L,
                labA = 0f,
                labB = 0f,
                addedAt = System.currentTimeMillis(),
                lastWornAt = null,
                wearCount = 0,
                status = GarmentStatus.PROCESSING.name,
                retiredAt = null,
                isSeed = false
            )
        )

        scope.launch { resolveColor(id, imagePath) }
        return id
    }

    private suspend fun resolveColor(id: String, imagePath: String) {
        runCatching {
            val bitmap = requireNotNull(imageStore.load(imagePath)) {
                "no bitmap at $imagePath"
            }
            // Background removal is cut from the 8-day scope, so there is no cutout to
            // sample. Centre-cropping is the stand-in: it drops most of the wall.
            colorExtractor.fromBitmapCentreCrop(bitmap)
        }.onSuccess { lab ->
            garments.updateResolved(id, null, lab.l, lab.a, lab.b, GarmentStatus.READY.name)
        }.onFailure { error ->
            // DominantColor.dominantLab is still TODO(), so this fires until you write it.
            // The tile stays greyed rather than showing a wrong colour, which is correct.
            Log.e(TAG, "colour extraction failed for $id", error)
            garments.updateStatus(id, GarmentStatus.FAILED.name)
        }
    }

    override suspend fun rename(garmentId: String, label: String) {
        garments.rename(garmentId, label)
    }

    /**
     * Same shape as [addGarment]: the row is updated immediately and the colour follows in
     * the background, so the tile shows the new photograph at once and greys out only while
     * the colour catches up.
     */
    override suspend fun replacePhoto(garmentId: String, imagePath: String) {
        garments.updatePhoto(garmentId, imagePath, GarmentStatus.PROCESSING.name)
        scope.launch { resolveColor(garmentId, imagePath) }
    }

    override suspend fun logWear(garmentId: String, at: Long) {
        db.withTransaction {
            garments.markWorn(garmentId, at)
            wearLog.insert(WearLogEntity(garmentId = garmentId, wornOn = at))
        }
    }

    override suspend fun retire(garmentId: String) {
        garments.retire(garmentId, System.currentTimeMillis())
    }

    /**
     * The row goes first and the file after it. That order matters: a failed file delete
     * leaves one orphaned image, while a failed row delete would have left a garment in the
     * closet pointing at a photograph that no longer exists.
     */
    override suspend fun delete(garmentId: String) {
        val existing = garments.getById(garmentId)
        garments.delete(garmentId)
        existing?.imagePath?.let(imageStore::delete)
        existing?.cutoutPath?.let(imageStore::delete)
    }

    /**
     * Counted in Kotlin off the existing active-garments flow rather than as its own
     * query, so there is one definition of "active" instead of two that can drift.
     * The window is measured when each value is emitted, not when collection starts.
     */
    override fun observeUtilisation(days: Int): Flow<Utilisation> =
        observeActiveGarments().map { active ->
            val since = System.currentTimeMillis() - days * RankDormantUseCase.MILLIS_PER_DAY
            Utilisation(
                wornCount = active.count { garment ->
                    garment.lastWornAt?.let { it >= since } == true
                },
                activeCount = active.size
            )
        }

    override suspend fun removeSeededGarments() {
        val removed = garments.deleteSeeded()
        if (removed > 0) Log.i(TAG, "removed $removed seeded garments")
    }

    private companion object {
        const val TAG = "WardrobeRepository"
        /** Mid-grey, shown while the real colour is still being extracted. */
        const val PLACEHOLDER_L = 50f
    }
}
