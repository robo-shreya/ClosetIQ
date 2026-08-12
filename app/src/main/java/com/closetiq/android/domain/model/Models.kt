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
