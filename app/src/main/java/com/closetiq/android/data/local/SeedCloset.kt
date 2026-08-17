package com.closetiq.android.data.local

import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.GarmentStatus
import java.util.concurrent.TimeUnit

/**
 * The demo closet.
 *
 * Dormancy cannot be demonstrated with a wardrobe the user built five minutes ago — "you
 * forgot about this" needs a closet with a past, and there is no way to film that
 * otherwise. That is all this exists for.
 *
 * **Every seeded item carries a real photograph.** [Seed.asset] is non-null on purpose, so
 * a swatch-only demo item cannot be added back by accident. Swatches were fine for
 * scoring, which only reads colour, but they are indefensible on screen: virtual try-on
 * cannot render one, so the button disables itself on exactly the items the app has just
 * finished recommending.
 *
 * The photographs are also all unworn garments — flat-lay or on a hanger, no model. A
 * demo photo of someone who is not the user raises a question the app then has to answer,
 * and it is a distraction in a thirty-second video.
 *
 * Their Lab values were measured from the photos using the same bucketing
 * DominantColor applies, so the stored colour and the picture on screen agree.
 *
 * Note that all five are dormant — 150 days or more, or never worn. There is no longer an
 * "in rotation" group, which means utilisation starts at 0% and the recentRepeat penalty
 * never fires until the user wears something. Both are honest for a closet of five
 * forgotten things, and the intent is that the user adds their own garments on top.
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
        /** File in assets/seed. Required — see the note on swatches above. */
        val asset: String
    )

    private val SEEDS = listOf(
        Seed("Green trousers", Category.BOTTOM, 33f, -14f, 9f, 156, 3, "seed/green-pants.jpg"),
        Seed("Blue tee", Category.TOP, 33f, -1f, -34f, 173, 1, "seed/blue-tshirt.jpg"),
        Seed("Khaki chinos", Category.BOTTOM, 52f, 2f, 21f, 189, 2, "seed/khaki-pants.jpg"),
        Seed("Green tee", Category.TOP, 37f, -37f, 20f, 204, 1, "seed/green-tshirt.jpg"),
        Seed("Beige blazer", Category.OUTERWEAR, 75f, 6f, 3f, null, 0, "seed/beige-blazer.jpg")
    )

    /** Asset file for a seeded id. Null only when the id is not a seeded one. */
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
