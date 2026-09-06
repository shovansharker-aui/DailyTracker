package com.dailytracker.app.miniapps.officetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AttendanceRecord::class, GovtHoliday::class], version = 2, exportSchema = false)
abstract class OfficeTrackerDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao
    abstract fun govtHolidayDao(): GovtHolidayDao

    companion object {
        @Volatile
        private var INSTANCE: OfficeTrackerDatabase? = null

        fun getDatabase(context: Context): OfficeTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfficeTrackerDatabase::class.java,
                    "office_tracker_database"
                )
                // Only version 1 (already shipped) is allowed to fall back to a
                // destructive rebuild. Any FUTURE version bump without a real Migration
                // will now throw loudly during development instead of silently wiping
                // every attendance record and holiday on real devices. When you bump
                // `version` above, add a `Migration` via `.addMigrations(...)` first.
                .fallbackToDestructiveMigrationFrom(1)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
