package com.closetiq.android.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.GarmentStatus
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.model.Undertone

/**
 * Room entities.
 *
 * Enums and Lab colours are stored as primitives (String / Float) rather than via
 * TypeConverters. It is slightly more verbose here but it means zero converter setup,
 * zero converter bugs, and a schema you can read directly in the database inspector.
 * The mapping to domain models happens in the toDomain() functions at the bottom.
 */

@Entity(tableName = "garments")
data class GarmentEntity(
    @PrimaryKey val id: String,
    val label: String,
    val category: String,
    val imagePath: String?,
    val cutoutPath: String?,
    val labL: Float,
    val labA: Float,
    val labB: Float,
    val addedAt: Long,
    val lastWornAt: Long?,
    val wearCount: Int,
    val status: String,
    val retiredAt: Long?,
    /** True for the demo closet. Lets you wipe seeds without losing real photos. */
    val isSeed: Boolean
)

@Entity(
    tableName = "wear_log",
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("garmentId")]
)
data class WearLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val garmentId: String,
    val wornOn: Long
)

@Entity(tableName = "skin_readings")
data class SkinReadingEntity(
    @PrimaryKey val id: String,
    val capturedAt: Long,
    val undertone: String,
    val fitzpatrick: Int,
    val redness: Float,
    val dullness: Float,
    val darkCircles: Float,
    val staleAfter: Long,
    val selfiePath: String?
)

// ---- mapping ----

fun GarmentEntity.toDomain(): Garment = Garment(
    id = id,
    label = label,
    category = enumValueOrDefault(category, Category.TOP),
    imagePath = imagePath,
    cutoutPath = cutoutPath,
    color = LabColor(labL, labA, labB),
    addedAt = addedAt,
    lastWornAt = lastWornAt,
    wearCount = wearCount,
    status = enumValueOrDefault(status, GarmentStatus.READY),
    retiredAt = retiredAt
)

fun SkinReadingEntity.toDomain(): SkinReading = SkinReading(
    id = id,
    capturedAt = capturedAt,
    undertone = enumValueOrDefault(undertone, Undertone.NEUTRAL),
    fitzpatrick = fitzpatrick,
    redness = redness,
    dullness = dullness,
    darkCircles = darkCircles,
    staleAfter = staleAfter,
    selfiePath = selfiePath
)

fun SkinReading.toEntity(): SkinReadingEntity = SkinReadingEntity(
    id = id,
    capturedAt = capturedAt,
    undertone = undertone.name,
    fitzpatrick = fitzpatrick,
    redness = redness,
    dullness = dullness,
    darkCircles = darkCircles,
    staleAfter = staleAfter,
    selfiePath = selfiePath
)

/** Never crash on an unknown stored string — old rows outlive enum renames. */
private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
