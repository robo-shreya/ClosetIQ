package com.closetiq.android.data.repository

import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.data.local.ClosetDatabase
import com.closetiq.android.data.local.toDomain
import com.closetiq.android.data.local.toEntity
import com.closetiq.android.data.remote.CreateTaskRequest
import com.closetiq.android.data.remote.ImagePayload
import com.closetiq.android.data.remote.TaskKind
import com.closetiq.android.data.remote.TaskPoller
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.model.Undertone
import com.closetiq.android.domain.repository.SkinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * One selfie in, one reading out.
 *
 * The reading deliberately expires after [FRESH_FOR_DAYS]. That is a product decision,
 * not a caching detail: it is what stops the app asking for a photo every single morning,
 * which was the thing most likely to make people stop using it.
 */
class SkinRepositoryImpl(
    private val db: ClosetDatabase,
    private val imageStore: ImageStore,
    private val poller: TaskPoller
) : SkinRepository {

    private val readings = db.skinReadingDao()

    override fun observeLatestReading(): Flow<SkinReading?> =
        readings.observeLatest().map { it?.toDomain() }

    override suspend fun currentFreshReading(): SkinReading? =
        readings.latest()?.toDomain()?.takeIf { it.isFreshAt(System.currentTimeMillis()) }

    override suspend fun captureReading(selfiePath: String): Result<SkinReading> {
        val base64 = imageStore.toBase64(selfiePath)
            ?: return Result.failure(IllegalStateException("Could not read selfie at $selfiePath"))

        // One image, one task. On the real YouCam backend this fans out to two calls —
        // Skin Analysis and Facial Color Tones — and merges them before replying.
        val result = poller.run(
            CreateTaskRequest(
                kind = TaskKind.SKIN_ANALYSIS,
                personImage = ImagePayload(base64 = base64)
            )
        )

        return result.mapCatching { taskResult ->
            val skin = requireNotNull(taskResult.skin) {
                "Skin analysis task returned no skin block"
            }

            val now = System.currentTimeMillis()
            val reading = SkinReading(
                id = UUID.randomUUID().toString(),
                capturedAt = now,
                undertone = runCatching { Undertone.valueOf(skin.undertone.uppercase()) }
                    .getOrDefault(Undertone.NEUTRAL),
                fitzpatrick = skin.fitzpatrick.coerceIn(1, 6),
                redness = skin.redness.coerceIn(0f, 1f),
                dullness = skin.dullness.coerceIn(0f, 1f),
                darkCircles = skin.darkCircles.coerceIn(0f, 1f),
                staleAfter = now + TimeUnit.DAYS.toMillis(FRESH_FOR_DAYS),
                selfiePath = selfiePath
            )

            readings.insert(reading.toEntity())
            reading
        }
    }

    override suspend fun clearReadings() = readings.deleteAll()

    companion object {
        const val FRESH_FOR_DAYS = 5L
    }
}
