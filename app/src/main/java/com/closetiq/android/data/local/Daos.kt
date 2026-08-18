package com.closetiq.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GarmentDao {

    @Query("SELECT * FROM garments WHERE retiredAt IS NULL AND status = 'READY' ORDER BY addedAt DESC")
    fun observeActive(): Flow<List<GarmentEntity>>

    @Query("SELECT * FROM garments ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<GarmentEntity>>

    @Query("SELECT * FROM garments WHERE id = :id")
    suspend fun getById(id: String): GarmentEntity?

    @Query("SELECT COUNT(*) FROM garments")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(garment: GarmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(garments: List<GarmentEntity>)

    @Query("UPDATE garments SET cutoutPath = :cutoutPath, labL = :l, labA = :a, labB = :b, status = :status WHERE id = :id")
    suspend fun updateResolved(id: String, cutoutPath: String?, l: Float, a: Float, b: Float, status: String)

    @Query("UPDATE garments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE garments SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    /**
     * A new photograph of the same garment. The cutout is cleared along with it — it was
     * derived from the old picture, and a stale cutout is what try-on would have uploaded.
     */
    @Query("UPDATE garments SET imagePath = :imagePath, cutoutPath = NULL, status = :status WHERE id = :id")
    suspend fun updatePhoto(id: String, imagePath: String, status: String)

    @Query("UPDATE garments SET lastWornAt = :at, wearCount = wearCount + 1 WHERE id = :id")
    suspend fun markWorn(id: String, at: Long)

    @Query("UPDATE garments SET retiredAt = :at WHERE id = :id")
    suspend fun retire(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM garments WHERE retiredAt IS NULL AND status = 'READY'")
    suspend fun activeCount(): Int

    @Query("SELECT COUNT(*) FROM garments WHERE retiredAt IS NULL AND status = 'READY' AND lastWornAt IS NOT NULL AND lastWornAt >= :since")
    suspend fun wornSinceCount(since: Long): Int
}

@Dao
interface WearLogDao {

    @Insert
    suspend fun insert(entry: WearLogEntity)

    @Query("SELECT * FROM wear_log WHERE garmentId = :garmentId ORDER BY wornOn DESC")
    suspend fun forGarment(garmentId: String): List<WearLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<WearLogEntity>)
}

@Dao
interface SkinReadingDao {

    @Query("SELECT * FROM skin_readings ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatest(): Flow<SkinReadingEntity?>

    @Query("SELECT * FROM skin_readings ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latest(): SkinReadingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: SkinReadingEntity)
}
