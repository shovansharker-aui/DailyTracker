package com.dailytracker.app.miniapps.officetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "govt_holidays")
data class GovtHoliday(
    @PrimaryKey val date: String, // "yyyy-MM-dd"
    val label: String = ""
)
