package com.dailytracker.app.miniapps.officetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GovtHolidayDao {
    @Query("SELECT * FROM govt_holidays ORDER BY date ASC")
    fun getAllGovtHolidays(): Flow<List<GovtHoliday>>

    @Query("SELECT * FROM govt_holidays ORDER BY date ASC")
    suspend fun getAllGovtHolidaysList(): List<GovtHoliday>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoliday(holiday: GovtHoliday)

    @Query("DELETE FROM govt_holidays WHERE date = :date")
    suspend fun deleteHoliday(date: String)

    @Query("DELETE FROM govt_holidays")
    suspend fun deleteAllHolidays()
}
