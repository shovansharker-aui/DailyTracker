package com.dailytracker.app.miniapps.officetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    HOLIDAY,
    UNMARKED;

    companion object {
        fun fromString(value: String): AttendanceStatus {
            return try {
                valueOf(value)
            } catch (e: Exception) {
                UNMARKED
            }
        }
    }
}

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey val date: String, // ISO format: "yyyy-MM-dd"
    val status: String,           // "PRESENT", "ABSENT", "HOLIDAY"
    val otHours: Int = 0,
    val isNightDuty: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
