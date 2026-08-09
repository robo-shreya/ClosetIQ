package com.closetiq.android.data.repository

import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.data.remote.CreateTaskRequest
import com.closetiq.android.data.remote.ImagePayload
import com.closetiq.android.data.remote.TaskKind
import com.closetiq.android.data.remote.TaskPoller
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.RenderTarget
import com.closetiq.android.domain.repository.RenderResult

/**
 * The YouCam playground confirmed Clothes VTO takes ONE reference image plus a body-part
 * target. It can render a whole outfit, but only from a single photo of that outfit — and
 * closet items are photographed separately. So a full look means chaining calls.
 *
 * That decision is deferred behind this interface. Ship [HeroRenderStrategy]; add
 * [ChainedRenderStrategy] only if feeding a render back into the generator holds up.
 * Nothing above the data layer changes either way.
 */
interface RenderStrategy {
    suspend fun render(
        personImagePath: String,
        garments: List<Garment>
    ): Result<RenderResult>
}

/**
 * One call. Renders only the hero garment — the dormant item being resurrected.
 * Everything else in the outfit is shown as a flat tile beside it.
 *
 * This is the shipping default: ~10s, one call, and no generative drift because the
 * person image is always the real photo.
 */
class HeroRenderStrategy(
    private val imageStore: ImageStore,
    private val poller: TaskPoller
) : RenderStrategy {

    override suspend fun render(
        personImagePath: String,
        garments: List<Garment>
    ): Result<RenderResult> {
        val hero = garments.firstOrNull()
            ?: return Result.failure(IllegalArgumentException("No garment to render"))

        val personBase64 = imageStore.toBase64(personImagePath)
            ?: return Result.failure(IllegalStateException("No person image at $personImagePath"))

        // Seeded garments have no photo. They can be scored and explained, but they
        // cannot be rendered — only a real photographed item can go through VTO.
        val garmentPath = hero.cutoutPath ?: hero.imagePath
            ?: return Result.failure(
                IllegalStateException("'${hero.label}' has no photo — seeded items cannot be rendered")
            )

        val garmentBase64 = imageStore.toBase64(garmentPath)
            ?: return Result.failure(IllegalStateException("No garment image at $garmentPath"))

        return poller.run(
            CreateTaskRequest(
                kind = TaskKind.TRY_ON,
                personImage = ImagePayload(base64 = personBase64),
                garmentImage = ImagePayload(base64 = garmentBase64),
                renderTarget = hero.category.renderTarget.name
            )
        ).map { RenderResult(imageUrl = it.imageUrl, note = it.note) }
    }
}

/**
 * Two to three chained calls: render the top, feed that output back in as the person
 * image, render the bottom, and so on.
 *
 * TODO(after the playground test): implement by looping [garments] in
 * UPPER_BODY → LOWER_BODY → SHOES order, downloading each render, saving it via
 * ImageStore, and using it as the person image for the next pass.
 *
 * Before writing this, verify the thing it depends on: download a VTO output, feed it
 * back in with a second garment, and look hard at the face and hands. Artefacts compound
 * across passes. If they show up, delete this class and keep [HeroRenderStrategy].
 */
class ChainedRenderStrategy(
    private val imageStore: ImageStore,
    private val poller: TaskPoller
) : RenderStrategy {

    private val order = listOf(
        RenderTarget.UPPER_BODY,
        RenderTarget.LOWER_BODY,
        RenderTarget.SHOES
    )

    override suspend fun render(
        personImagePath: String,
        garments: List<Garment>
    ): Result<RenderResult> {
        TODO("Chain one VTO call per garment, feeding each render in as the next person image")
    }
}
