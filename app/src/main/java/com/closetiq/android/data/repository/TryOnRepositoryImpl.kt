package com.closetiq.android.data.repository

import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.data.remote.CreateTaskRequest
import com.closetiq.android.data.remote.ImagePayload
import com.closetiq.android.data.remote.TaskKind
import com.closetiq.android.data.remote.TaskPoller
import com.closetiq.android.domain.model.RenderTarget
import com.closetiq.android.domain.repository.RenderResult
import com.closetiq.android.domain.repository.TryOnRepository

class TryOnRepositoryImpl(
    private val imageStore: ImageStore,
    private val poller: TaskPoller
) : TryOnRepository {

    override suspend fun render(
        personImagePath: String,
        garmentImagePath: String,
        target: RenderTarget
    ): Result<RenderResult> {
        val personBase64 = imageStore.toBase64(personImagePath)
            ?: return Result.failure(IllegalStateException("No person image at $personImagePath"))

        val garmentBase64 = imageStore.toBase64(garmentImagePath)
            ?: return Result.failure(IllegalStateException("No garment image at $garmentImagePath"))

        return poller.run(
            CreateTaskRequest(
                kind = TaskKind.TRY_ON,
                personImage = ImagePayload(base64 = personBase64),
                garmentImage = ImagePayload(base64 = garmentBase64),
                renderTarget = target.name
            )
        ).map { RenderResult(imageUrl = it.imageUrl, note = it.note) }
    }
}
