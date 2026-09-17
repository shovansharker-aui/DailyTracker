package com.dailytracker.app.miniapps.businesscard

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessCardDao {
    @Query("SELECT * FROM business_cards ORDER BY capturedAt DESC")
    fun getAllCards(): Flow<List<BusinessCardEntity>>

    // supportedBrands is stored as its JSON encoding, so this also catches a
    // search term that matches a saved brand name.
    @Query(
        """
        SELECT * FROM business_cards
        WHERE businessName LIKE '%' || :query || '%'
           OR personName LIKE '%' || :query || '%'
           OR phoneNumber LIKE '%' || :query || '%'
           OR supportedBrands LIKE '%' || :query || '%'
        ORDER BY capturedAt DESC
        """
    )
    fun searchCards(query: String): Flow<List<BusinessCardEntity>>

    @Query("SELECT * FROM business_cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): BusinessCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: BusinessCardEntity): Long

    @Delete
    suspend fun deleteCard(card: BusinessCardEntity)
}
