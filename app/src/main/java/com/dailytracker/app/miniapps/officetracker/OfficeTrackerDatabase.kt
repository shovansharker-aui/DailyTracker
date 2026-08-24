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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
