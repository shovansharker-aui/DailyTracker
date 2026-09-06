package com.dailytracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackedContact::class, CallRecord::class], version = 3, exportSchema = false)
abstract class KinKeepDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: KinKeepDatabase? = null

        fun getDatabase(context: Context): KinKeepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KinKeepDatabase::class.java,
                    "kinkeep_database"
                )
                // Only versions already shipped (1, 2) are allowed to fall back to a
                // destructive rebuild. Any FUTURE version bump without a real Migration
                // will now throw loudly during development instead of silently wiping
                // every contact and call record on real devices. When you bump
                // `version` above, add a `Migration` via `.addMigrations(...)` first.
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
