package com.dailytracker.app.miniapps.businesscard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_cards")
data class BusinessCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessName: String,
    val personName: String,
    val address: String,
    val phoneNumber: String,
    val supportedBrands: List<BrandInfo>,
    /** Absolute path to the captured card photo in app-private storage. */
    val cardImagePath: String,
    val capturedAt: Long = System.currentTimeMillis()
)
