package com.closetiq.android.domain

import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.GarmentStatus
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.model.Undertone
import java.util.concurrent.TimeUnit

/** Shared builders, so each test states only what it actually cares about. */
object TestFixtures {

    const val NOW = 1_800_000_000_000L

    fun days(n: Int): Long = TimeUnit.DAYS.toMillis(n.toLong())

    fun garment(
        id: String = "g1",
        label: String = "Test garment",
        category: Category = Category.TOP,
        color: LabColor = LabColor(50f, 0f, 0f),
        daysSinceWorn: Int? = 0,
        daysSinceAdded: Int = 200,
        wearCount: Int = 1,
        status: GarmentStatus = GarmentStatus.READY,
        retiredAt: Long? = null
    ) = Garment(
        id = id,
        label = label,
        category = category,
        imagePath = null,
        cutoutPath = null,
        color = color,
        addedAt = NOW - days(daysSinceAdded),
        lastWornAt = daysSinceWorn?.let { NOW - days(it) },
        wearCount = wearCount,
        status = status,
        retiredAt = retiredAt
    )

    fun reading(
        undertone: Undertone = Undertone.NEUTRAL,
        fitzpatrick: Int = 3,
        redness: Float = 0f,
        dullness: Float = 0f,
        darkCircles: Float = 0f
    ) = SkinReading(
        id = "r1",
        capturedAt = NOW,
        undertone = undertone,
        fitzpatrick = fitzpatrick,
        redness = redness,
        dullness = dullness,
        darkCircles = darkCircles,
        staleAfter = NOW + days(5),
        selfiePath = null
    )

    // A few reference colours, so tests read as intent rather than as numbers.
    val NAVY = LabColor(28f, 3f, -24f)
    val CORAL = LabColor(66f, 32f, 24f)
    val OLIVE = LabColor(42f, -8f, 22f)
    val BEIGE = LabColor(78f, 3f, 13f)
    val BLACK = LabColor(12f, 0f, 0f)
    val WHITE = LabColor(95f, 0f, 0f)
    val EMERALD = LabColor(46f, -32f, 12f)

    /** Strongly warm — the reference for something a cool palette should reject. */
    val MUSTARD = LabColor(70f, 8f, 52f)
}
