package com.closetiq.android.data.repository

import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.data.remote.CreateTaskRequest
import com.closetiq.android.data.remote.ImagePayload
import com.closetiq.android.data.remote.TaskKind
import com.closetiq.android.data.remote.TaskPoller
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
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
    /**
     * @param onProgress called as `(pass, totalPasses)` before each call, so a screen can
     *   say "2 of 3" through a chain that takes half a minute instead of showing one
     *   undifferentiated spinner.
     */
    suspend fun render(
        personImagePath: String,
        garments: List<Garment>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
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
        garments: List<Garment>,
        onProgress: (Int, Int) -> Unit
    ): Result<RenderResult> {
        onProgress(1, 1)

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
 * One call per body region, each rendering onto the output of the last: the blazer onto
 * your photo, the trousers onto that render, the shoes onto that one. The result is a
 * whole outfit rather than one garment over your own clothes.
 *
 * Passes run in the order a person dresses — top, bottom, then outerwear over the top,
 * then shoes — so an open blazer or overshirt has a shirt underneath it to show.
 *
 * Costs one call per pass at roughly ten seconds each, so a four-piece outfit is about
 * forty-five seconds and four credits. Generative drift compounds across passes: each one
 * re-generates the whole person, including the face, from an image that was itself
 * generated. Seams and re-framing are the artefacts to watch for.
 */
class ChainedRenderStrategy(
    private val imageStore: ImageStore,
    private val poller: TaskPoller
) : RenderStrategy {

    override suspend fun render(
        personImagePath: String,
        garments: List<Garment>,
        onProgress: (Int, Int) -> Unit
    ): Result<RenderResult> {
        val passes = planPasses(garments)
        if (passes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nothing in this outfit can be rendered"))
        }

        var personPath = personImagePath
        var lastUrl: String? = null
        var lastError: Throwable? = null
        val skipped = mutableListOf<String>()

        passes.forEachIndexed { index, garment ->
            onProgress(index + 1, passes.size)

            // A layer that errors is dropped, not fatal. `cloth` fails a single generation
            // now and then — `error_editing_failed` on a person image it had accepted
            // seconds earlier — and chaining multiplies the exposure: four passes are four
            // chances to hit it. Aborting threw away the layers that had already rendered
            // and showed an error instead of the outfit those credits had already bought.
            val url = renderOne(personPath, garment).getOrElse { error ->
                lastError = error
                skipped += garment.label
                return@forEachIndexed
            }

            if (url == null) {
                // A pass that produced nothing is not fatal — the passes before it are
                // still a real render. Say which garment dropped out rather than silently
                // returning a partial outfit as if it were complete.
                skipped += garment.label
                return@forEachIndexed
            }

            // The chain lives or dies on this: the next pass needs bytes, and the API only
            // ever returns a URL. A failed download ends the chain where it stands rather
            // than sending the previous person image again and rendering the same layer twice.
            val saved = imageStore.importFromUrl(url)
            lastUrl = url

            if (saved == null && index < passes.lastIndex) {
                skipped += passes.drop(index + 1).map { it.label }
                return@forEachIndexed
            }
            if (saved != null) personPath = saved
        }

        // Nothing rendered at all, and something went wrong doing it: report the failure
        // rather than a blank success, so the actual API error reaches the screen.
        if (lastUrl == null) lastError?.let { return Result.failure(it) }

        val note = when {
            lastUrl == null -> "Try-on produced no image for any layer of this outfit."
            skipped.isEmpty() -> null
            else -> "Rendered without ${skipped.joinToString(" and ")} — " +
                "YouCam couldn't render those layers. Tap again to retry them."
        }

        return Result.success(RenderResult(imageUrl = lastUrl, note = note))
    }

    /**
     * The outfit, dressed in the order a person dresses: base layers first, then what goes
     * over them.
     *
     * The top is rendered *and* the outerwear on top of it. It would be easy to assume the
     * second upper-body pass simply discards the first — both target `upper_body`, and VTO
     * replaces a region rather than compositing layers. But a blazer or an overshirt worn
     * open shows the shirt underneath, and the outerwear pass reads a source image that
     * already has the tee in it, so the model has something to render the opening over.
     *
     * A dress is its own base layer and makes a separate top and bottom meaningless, so it
     * replaces both.
     */
    private fun planPasses(garments: List<Garment>): List<Garment> {
        val renderable = garments.filter { (it.cutoutPath ?: it.imagePath) != null }
        fun of(category: Category) = renderable.firstOrNull { it.category == category }

        val dress = of(Category.DRESS)

        val base = if (dress != null) {
            listOf(dress)
        } else {
            listOfNotNull(of(Category.TOP), of(Category.BOTTOM))
        }

        return base + listOfNotNull(of(Category.OUTERWEAR), of(Category.SHOES))
    }

    private suspend fun renderOne(personPath: String, garment: Garment): Result<String?> {
        val personBase64 = imageStore.toBase64(personPath)
            ?: return Result.failure(IllegalStateException("No person image at $personPath"))

        val garmentPath = garment.cutoutPath ?: garment.imagePath
            ?: return Result.failure(
                IllegalStateException("'${garment.label}' has no photo")
            )

        val garmentBase64 = imageStore.toBase64(garmentPath)
            ?: return Result.failure(IllegalStateException("No garment image at $garmentPath"))

        return poller.run(
            CreateTaskRequest(
                kind = TaskKind.TRY_ON,
                personImage = ImagePayload(base64 = personBase64),
                garmentImage = ImagePayload(base64 = garmentBase64),
                renderTarget = garment.category.renderTarget.name
            )
        ).map { it.imageUrl }
    }

}
