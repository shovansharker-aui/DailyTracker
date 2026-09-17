package com.dailytracker.app.miniapps.businesscard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [BusinessCardEntity::class], version = 1, exportSchema = false)
@TypeConverters(BusinessCardConverters::class)
abstract class BusinessCardDatabase : RoomDatabase() {
    abstract fun businessCardDao(): BusinessCardDao

    companion object {
        @Volatile
        private var INSTANCE: BusinessCardDatabase? = null

        fun getDatabase(context: Context): BusinessCardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BusinessCardDatabase::class.java,
                    "business_card_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
