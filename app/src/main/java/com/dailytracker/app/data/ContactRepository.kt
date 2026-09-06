package com.dailytracker.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ContactRepository(
    val contactDao: ContactDao,
    private val context: Context
) {
    val allContacts: Flow<List<TrackedContact>> = contactDao.getAllContacts()
    val allRecentCallRecords: Flow<List<CallRecord>> = contactDao.getAllRecentCallRecords()
    fun getAllCallRecordsSince(sinceTimestamp: Long): Flow<List<CallRecord>> = contactDao.getAllCallRecordsSince(sinceTimestamp)
    private val callLogScanner = CallLogScanner(context, contactDao)

    suspend fun getContactByIdSync(id: Long): TrackedContact? = contactDao.getContactByIdSync(id)

    fun getCallRecordsForContact(contactId: Long): Flow<List<CallRecord>> =
        contactDao.getCallRecordsForContact(contactId)

    suspend fun insertContact(contact: TrackedContact): Long {
        val id = contactDao.insertContact(contact)
        com.dailytracker.app.widget.KinKeepWidgetProvider.updateAllWidgets(context)
        return id
    }

    suspend fun updateContact(contact: TrackedContact) {
        contactDao.updateContact(contact)
        com.dailytracker.app.widget.KinKeepWidgetProvider.updateAllWidgets(context)
    }

    suspend fun deleteContact(contact: TrackedContact) {
        contactDao.deleteContact(contact)
        com.dailytracker.app.widget.KinKeepWidgetProvider.updateAllWidgets(context)
    }

    suspend fun logCallForContact(
        contactId: Long,
        timestamp: Long = System.currentTimeMillis(),
        durationSeconds: Int = 300,
        callType: String = "OUTGOING",
        notes: String = ""
    ) {
        contactDao.insertCallRecord(
            CallRecord(
                contactId = contactId,
                timestamp = timestamp,
                durationSeconds = durationSeconds,
                callType = callType,
                notes = notes
            )
        )
        contactDao.updateLastCalledIfNewer(contactId, timestamp)
        com.dailytracker.app.widget.KinKeepWidgetProvider.updateAllWidgets(context)
    }

    suspend fun getCallsInCurrentPeriod(contact: TrackedContact): Int {
        val period = contact.getPeriodEnum()
        val startTimestamp = CallLogScanner.getStartOfPeriodTimestamp(period)
        val records = contactDao.getCallRecordsSince(contact.id, startTimestamp)
        return records.size
    }

    /** True if this contact already has a logged call at or after [sinceTimestamp]. */
    suspend fun hasCallRecordSince(contactId: Long, sinceTimestamp: Long): Boolean =
        contactDao.getCallRecordsSince(contactId, sinceTimestamp).isNotEmpty()

    suspend fun syncDeviceCallLogs(contacts: List<TrackedContact>): Int {
        return callLogScanner.scanCallLogsAndSync(contacts)
    }

    suspend fun deleteAllContacts() {
        contactDao.deleteAllCallRecords()
        contactDao.deleteAllContacts()
    }
}
