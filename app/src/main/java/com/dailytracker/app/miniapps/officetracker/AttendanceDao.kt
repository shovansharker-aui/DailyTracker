package com.dailytracker.app.miniapps.officetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE date LIKE :yearMonth || '%' ORDER BY date ASC")
    fun getRecordsForMonth(yearMonth: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date LIMIT 1")
    suspend fun getRecordForDate(date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE date = :date")
    suspend fun deleteRecord(date: String)
}
