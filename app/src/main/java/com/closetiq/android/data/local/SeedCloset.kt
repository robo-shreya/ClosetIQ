package com.closetiq.android.data.local

import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.GarmentStatus
import java.util.concurrent.TimeUnit

/**
 * The demo closet.
 *
 * This is the single highest-leverage decision in the 8-day plan. Dormancy cannot be
 * demonstrated with a wardrobe the user built five minutes ago — "you forgot about this"
 * needs a closet with a past, and there is no way to film that otherwise.
 *
 * Six of the most dormant items carry a real photograph from assets/seed; the rest
 * render as colour swatches. That split is deliberate: colour is what the scoring uses,
 * so a swatch fakes nothing that matters — but virtual try-on needs an actual picture,
 * so the items most likely to be surfaced as today's pick are the ones that have one.
 *
 * Their Lab values were measured from the photos using the same bucketing
 * DominantColor applies, so the stored colour and the picture on screen agree.
 *
 * The wear history is deliberately spread across three groups:
 *   - in rotation      (worn 1-6 days ago)   → these should NOT be recommended
 *   - drifting         (worn 30-60 days ago)
 *   - genuinely dormant (worn 90-210 days ago, or never) → the rescue candidates
 */
object SeedCloset {

    private data class Seed(
        val label: String,
        val category: Category,
        val l: Float,
        val a: Float,
        val b: Float,
        val daysSinceWorn: Int?,
        val wearCount: Int,
        /**
         * File in assets/seed. Items with one render as a photograph and can go through
         * virtual try-on; the rest render as colour swatches and cannot.
         */
        val asset: String? = null
    )

    private val SEEDS = listOf(
        // --- in rotation ---
        Seed("Black cotton tee", Category.TOP, 22f, 0f, 0f, 1, 42),
        Seed("Indigo jeans", Category.BOTTOM, 32f, 4f, -26f, 2, 61),
        Seed("Grey hoodie", Category.OUTERWEAR, 55f, -1f, -2f, 3, 38),
        Seed("White sneakers", Category.SHOES, 88f, 0f, 2f, 2, 55),
        Seed("Navy crewneck", Category.TOP, 28f, 3f, -24f, 5, 29),
        Seed("Charcoal chinos", Category.BOTTOM, 34f, 0f, -1f, 6, 24),

        // --- drifting ---
        Seed("Sage linen shirt", Category.TOP, 68f, -14f, 12f, 34, 9),
        Seed("Stone shorts", Category.BOTTOM, 74f, 2f, 14f, 41, 7),
        Seed("Denim jacket", Category.OUTERWEAR, 48f, -2f, -18f, 38, 11),
        Seed("Brown leather boots", Category.SHOES, 30f, 12f, 18f, 52, 14),
        Seed("Oatmeal knit", Category.TOP, 78f, 3f, 13f, 47, 8),

        // --- dormant: the rescue queue ---
        Seed("Olive field jacket", Category.OUTERWEAR, 42f, -8f, 22f, 94, 4),
        Seed("Rust corduroy shirt", Category.TOP, 45f, 24f, 30f, 112, 3),
        Seed("Black crew tee", Category.TOP, 6f, -1f, -3f, 128, 2, "seed/black-tshirt.jpg"),
        Seed("Mustard cardigan", Category.OUTERWEAR, 70f, 8f, 52f, 141, 2),
        Seed("Green trousers", Category.BOTTOM, 33f, -14f, 9f, 156, 3, "seed/green-pants.jpg"),
        Seed("Blue tee", Category.TOP, 33f, -1f, -34f, 173, 1, "seed/blue-tshirt.jpg"),
        Seed("Khaki chinos", Category.BOTTOM, 52f, 2f, 21f, 189, 2, "seed/khaki-pants.jpg"),
        Seed("Green tee", Category.TOP, 37f, -37f, 20f, 204, 1, "seed/green-tshirt.jpg"),
        Seed("Tan suede loafers", Category.SHOES, 58f, 9f, 24f, 210, 2),
        Seed("Beige blazer", Category.OUTERWEAR, 75f, 6f, 3f, null, 0, "seed/beige-blazer.jpg")
    )

    /** Asset file for a seeded id, or null when that item is a plain colour swatch. */
    fun assetFor(id: String): String? =
        id.removePrefix("seed-").toIntOrNull()?.let { SEEDS.getOrNull(it)?.asset }

    fun entities(now: Long = System.currentTimeMillis()): List<GarmentEntity> =
        SEEDS.mapIndexed { index, seed ->
            val lastWorn = seed.daysSinceWorn?.let { now - TimeUnit.DAYS.toMillis(it.toLong()) }

            GarmentEntity(
                id = "seed-%02d".format(index),
                label = seed.label,
                category = seed.category.name,
                imagePath = null, // filled in from [Seed.asset] when the closet is seeded
                cutoutPath = null,
                labL = seed.l,
                labA = seed.a,
                labB = seed.b,
                // Added well before the oldest wear, so a never-worn item still looks owned.
                addedAt = now - TimeUnit.DAYS.toMillis(240),
                lastWornAt = lastWorn,
                wearCount = seed.wearCount,
                status = GarmentStatus.READY.name,
                retiredAt = null,
                isSeed = true
            )
        }

    /** One log row per seeded wear, so the history is real rather than just a counter. */
    fun wearLog(now: Long = System.currentTimeMillis()): List<WearLogEntity> =
        entities(now).mapNotNull { garment ->
            garment.lastWornAt?.let { WearLogEntity(garmentId = garment.id, wornOn = it) }
        }
}
