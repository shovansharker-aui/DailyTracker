package com.dailytracker.app.miniapps.businesscard

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class BusinessCardConverters {
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, BrandInfo::class.java)
    private val adapter = moshi.adapter<List<BrandInfo>>(listType)

    @TypeConverter
    fun fromBrandList(brands: List<BrandInfo>): String = adapter.toJson(brands)

    @TypeConverter
    fun toBrandList(json: String): List<BrandInfo> =
        try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
}
