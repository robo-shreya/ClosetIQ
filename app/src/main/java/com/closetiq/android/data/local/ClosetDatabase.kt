package com.closetiq.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GarmentEntity::class,
        WearLogEntity::class,
        SkinReadingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ClosetDatabase : RoomDatabase() {

    abstract fun garmentDao(): GarmentDao
    abstract fun wearLogDao(): WearLogDao
    abstract fun skinReadingDao(): SkinReadingDao

    companion object {
        fun build(context: Context): ClosetDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ClosetDatabase::class.java,
                "closetiq.db"
            )
                // Fine for a hackathon: any schema change wipes and rebuilds, and the
                // seed data repopulates on next launch. Write real migrations later.
                .fallbackToDestructiveMigration()
                .build()
    }
}
