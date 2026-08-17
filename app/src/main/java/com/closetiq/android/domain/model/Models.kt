package com.closetiq.android.domain.model

/**
 * Domain models. Pure Kotlin — no Android imports allowed in this package.
 * That is what lets everything here run in a plain JVM unit test, in milliseconds,
 * with no emulator.
 */

enum class Category {
    TOP,
    BOTTOM,
    OUTERWEAR,
    DRESS,
    SHOES;

    /** Which VTO body-part target this category maps to. */
    val renderTarget: RenderTarget
        get() = when (this) {
            TOP, OUTERWEAR -> RenderTarget.UPPER_BODY
            BOTTOM -> RenderTarget.LOWER_BODY
            DRESS -> RenderTarget.FULL_BODY
            SHOES -> RenderTarget.SHOES
        }
}

enum class RenderTarget {
    AUTO,
    FULL_BODY,
    UPPER_BODY,
    LOWER_BODY,
    SHOES
}

enum class GarmentStatus {
    /** Saved locally, cutout/colour not resolved yet. */
    PROCESSING,
    READY,
    FAILED
}

enum class Undertone {
    WARM,
    COOL,
    NEUTRAL
}

/**
 * CIELAB colour.
 *
 * L = lightness   0 (black) .. 100 (white)
 * a = green–red   roughly -128 .. +127
 * b = blue–yellow roughly -128 .. +127
 *
 * Lab is used instead of RGB because distance in Lab roughly matches
 * "how different these look to a human eye", which is exactly what the
 * palette matching needs.
 */
data class LabColor(
    val l: Float,
    val a: Float,
    val b: Float
)

data class Garment(
    val id: String,
    val label: String,
    val category: Category,
    /** Local file path or content:// uri. Null for seeded items, which render as a colour swatch. */
    val imagePath: String?,
    val cutoutPath: String?,
    val color: LabColor,
    val addedAt: Long,
    val lastWornAt: Long?,
    val wearCount: Int,
    val status: GarmentStatus,
    val retiredAt: Long?
) {
    val isActive: Boolean get() = retiredAt == null && status == GarmentStatus.READY
}

/**
 * One skin reading — a snapshot, not a profile. Deliberately expires.
 *
 * [redness], [dullness] and [darkCircles] are normalised 0f..1f where
 * higher means *more* of that concern.
 */
data class SkinReading(
    val id: String,
    val capturedAt: Long,
    val undertone: Undertone,
    /** Fitzpatrick skin type, 1..6. */
    val fitzpatrick: Int,
    val redness: Float,
    val dullness: Float,
    val darkCircles: Float,
    val staleAfter: Long,
    val selfiePath: String?
) {
    fun isFreshAt(now: Long): Boolean = now < staleAfter

    companion object {
        /**
         * Stands in when no photo has ever been taken.
         *
         * Colour still matters without a reading — it is only *today's* skin that is
         * unknown — so this keeps palette scoring meaningful rather than switching it
         * off. Every concern is zero, which SkinStateModifier reads as "no opinion".
         */
        val NEUTRAL = SkinReading(
            id = "neutral-baseline",
            capturedAt = 0L,
            undertone = Undertone.NEUTRAL,
            fitzpatrick = 3,
            redness = 0f,
            dullness = 0f,
            darkCircles = 0f,
            staleAfter = 0L,
            selfiePath = null
        )
    }
}

/**
 * The pictures of the user, one per purpose.
 *
 * A selfie and a try-on shot are not the same photograph and never were. Skin Analysis
 * wants a close, well-lit face; `cloth` wants a body with the target region actually in
 * frame. Asking one image to do both is why renders came back empty — a head-and-
 * shoulders selfie has no legs in it, so a lower-body try-on has nothing to replace.
 *
 * Only [SELFIE] is really needed. The rest sharpen try-on and are optional, in keeping
 * with the app never being blocked on a photo.
 */
enum class PhotoSlot {
    /** Head and shoulders. The one Skin Analysis reads. */
    SELFIE,

    /** Head to feet. The most useful single try-on shot — it can stand in for any region. */
    FULL_BODY,

    /** Waist up. */
    UPPER_BODY,

    /** Waist down. */
    LOWER_BODY
}

data class PersonPhotos(
    val selfie: String? = null,
    val fullBody: String? = null,
    val upperBody: String? = null,
    val lowerBody: String? = null
) {
    operator fun get(slot: PhotoSlot): String? = when (slot) {
        PhotoSlot.SELFIE -> selfie
        PhotoSlot.FULL_BODY -> fullBody
        PhotoSlot.UPPER_BODY -> upperBody
        PhotoSlot.LOWER_BODY -> lowerBody
    }

    fun with(slot: PhotoSlot, path: String): PersonPhotos = when (slot) {
        PhotoSlot.SELFIE -> copy(selfie = path)
        PhotoSlot.FULL_BODY -> copy(fullBody = path)
        PhotoSlot.UPPER_BODY -> copy(upperBody = path)
        PhotoSlot.LOWER_BODY -> copy(lowerBody = path)
    }

    val hasAny: Boolean get() = PhotoSlot.entries.any { this[it] != null }

    /**
     * The best photo to render a [target] region onto, or null when nothing on file could
     * plausibly work.
     *
     * Returning null matters more than it looks: a lower-body render onto a selfie is a
     * guaranteed empty result, and `cloth` reports that as *success* with no image. So
     * falling back to any photo at all would spend a real credit to produce nothing and
     * no error. Better to say up front which picture is missing.
     *
     * **The selfie is never a render source.** It was briefly allowed to stand in for
     * [RenderTarget.UPPER_BODY], on the theory that a head-and-shoulders shot carries
     * enough chest to dress. It does not: YouCam rejected exactly that pairing with
     * `error_src_face_too_small`, from the Mirror, on a real device. It also contradicted
     * what the app tells the user — that the selfie reads skin and the body shots are what
     * try-on renders onto. The selfie now does only the job it is asked for.
     */
    fun bestFor(target: RenderTarget): String? = when (target) {
        RenderTarget.UPPER_BODY -> upperBody ?: fullBody
        RenderTarget.LOWER_BODY -> lowerBody ?: fullBody
        RenderTarget.FULL_BODY -> fullBody ?: upperBody
        RenderTarget.SHOES -> fullBody ?: lowerBody
        RenderTarget.AUTO -> fullBody ?: upperBody
    }

    /**
     * The slot [target] would ideally be rendered from.
     *
     * Serves two purposes, which are the same question asked from either side: when
     * [bestFor] returns null this is the photo to ask the user for, and when the user
     * supplies one this is the slot it belongs in.
     */
    fun preferredSlotFor(target: RenderTarget): PhotoSlot = when (target) {
        RenderTarget.LOWER_BODY -> PhotoSlot.LOWER_BODY
        RenderTarget.SHOES, RenderTarget.FULL_BODY, RenderTarget.AUTO -> PhotoSlot.FULL_BODY
        RenderTarget.UPPER_BODY -> PhotoSlot.UPPER_BODY
    }

    companion object {
        val EMPTY = PersonPhotos()
    }
}

/**
 * The colour palette derived from a skin reading — a set of anchor colours
 * that suit this person. A garment scores well when it sits close to an anchor.
 */
data class Palette(
    val anchors: List<LabColor>,
    val undertone: Undertone
)

/** A scored garment, with the breakdown kept so the UI can explain itself. */
data class ScoredGarment(
    val garment: Garment,
    val total: Float,
    val paletteFit: Float,
    val skinDayFit: Float,
    val dormancy: Float,
    val recentRepeatPenalty: Float
)

/** What the Mirror screen ends up showing. */
data class OutfitPick(
    val hero: ScoredGarment,
    val supporting: List<ScoredGarment>,
    val reason: String
)
