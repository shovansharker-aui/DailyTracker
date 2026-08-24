package com.dailytracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val timestamp: Long,
    val durationSeconds: Int = 0,
    val callType: String = "OUTGOING", // "OUTGOING", "INCOMING", "MISSED", "MANUAL"
    val notes: String = ""
)