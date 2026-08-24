package com.dailytracker.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM tracked_contacts ORDER BY pinned DESC, name ASC")
    fun getAllContacts(): Flow<List<TrackedContact>>

    @Query("SELECT * FROM tracked_contacts WHERE id = :id")
    fun getContactById(id: Long): Flow<TrackedContact?>

    @Query("SELECT * FROM tracked_contacts WHERE id = :id")
    suspend fun getContactByIdSync(id: Long): TrackedContact?

    @Query("SELECT * FROM tracked_contacts WHERE phoneNumber = :phoneNumber OR phoneNumber LIKE '%' || :phoneNumber")
    suspend fun getContactsByPhone(phoneNumber: String): List<TrackedContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: TrackedContact): Long

    @Update
    suspend fun updateContact(contact: TrackedContact)

    @Delete
    suspend fun deleteContact(contact: TrackedContact)

    @Query("DELETE FROM tracked_contacts WHERE id = :id")
    suspend fun deleteContactById(id: Long)

    @Query("DELETE FROM tracked_contacts")
    suspend fun deleteAllContacts()

    @Query("DELETE FROM call_records")
    suspend fun deleteAllCallRecords()

    @Query("SELECT * FROM tracked_contacts")
    suspend fun getAllContactsList(): List<TrackedContact>

    @Query("SELECT * FROM call_records")
    suspend fun getAllCallRecordsList(): List<CallRecord>

    @Query("SELECT * FROM call_records WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getCallRecordsForContact(contactId: Long): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC LIMIT 50")
    fun getAllRecentCallRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE timestamp >= :sinceTimestamp")
    fun getAllCallRecordsSince(sinceTimestamp: Long): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE contactId = :contactId AND timestamp >= :sinceTimestamp")
    suspend fun getCallRecordsSince(contactId: Long, sinceTimestamp: Long): List<CallRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecord): Long

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteCallRecord(id: Long)

    @Query("UPDATE tracked_contacts SET lastCalledTimestamp = :timestamp WHERE id = :contactId AND :timestamp > lastCalledTimestamp")
    suspend fun updateLastCalledIfNewer(contactId: Long, timestamp: Long)
}
