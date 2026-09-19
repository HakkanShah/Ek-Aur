package com.ekaur.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScrollEventEntity::class,
        HourlyCountEntity::class,
        DailyCountEntity::class,
        SessionRecordEntity::class,
        MilestoneFiredEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class EkAurDatabase : RoomDatabase() {

    abstract fun scrollEvents(): ScrollEventDao
    abstract fun dailyCounts(): DailyCountDao
    abstract fun hourlyCounts(): HourlyCountDao
    abstract fun sessions(): SessionDao
    abstract fun milestones(): MilestoneDao

    companion object {
        // Every table the later phases need is defined up front, deliberately.
        // The device already holds real counts, and a migration that goes wrong
        // loses them -- adding tables now costs nothing and avoids that.
        fun build(context: Context): EkAurDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                EkAurDatabase::class.java,
                "ekaur.db",
            ).build()
    }
}
