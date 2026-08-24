package com.dailytracker.app.data

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class CallLogScanner(private val context: Context, private val contactDao: ContactDao) {

    suspend fun scanCallLogsAndSync(contacts: List<TrackedContact>): Int = withContext(Dispatchers.IO) {
        if (contacts.isEmpty()) return@withContext 0

        // Check if permission is granted
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.d("CallLogScanner", "READ_CALL_LOG permission not granted")
            return@withContext 0
        }

        var newRecordsAdded = 0
        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        val selection = "${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(ninetyDaysAgo.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { c ->
                val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)

                while (c.moveToNext()) {
                    val rawNumber = if (numberIdx >= 0) c.getString(numberIdx) ?: "" else ""
                    val callDate = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val duration = if (durationIdx >= 0) c.getInt(durationIdx) else 0
                    val typeInt = if (typeIdx >= 0) c.getInt(typeIdx) else CallLog.Calls.OUTGOING_TYPE

                    val isConnectedCall = (typeInt == CallLog.Calls.INCOMING_TYPE || typeInt == CallLog.Calls.OUTGOING_TYPE) && duration > 0
                    if (!isConnectedCall) continue

                    val callTypeStr = if (typeInt == CallLog.Calls.INCOMING_TYPE) "INCOMING" else "OUTGOING"

                    // Match number with tracked contacts
                    val matchedContact = findMatchingContact(rawNumber, contacts)
                    if (matchedContact != null && callDate > 0L) {
                        // Check if record already exists within 2 seconds window
                        val existing = contactDao.getCallRecordsSince(matchedContact.id, callDate - 2000)
                            .any { Math.abs(it.timestamp - callDate) < 2000 }

                        if (!existing) {
                            contactDao.insertCallRecord(
                                CallRecord(
                                    contactId = matchedContact.id,
                                    timestamp = callDate,
                                    durationSeconds = duration,
                                    callType = callTypeStr,
                                    notes = "Auto-synced from Phone Call Log"
                                )
                            )
                            contactDao.updateLastCalledIfNewer(matchedContact.id, callDate)
                            newRecordsAdded++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CallLogScanner", "Error scanning call log", e)
        }

        return@withContext newRecordsAdded
    }

    private fun findMatchingContact(rawPhone: String, contacts: List<TrackedContact>): TrackedContact? {
        val cleanRaw = normalizePhoneNumber(rawPhone)
        if (cleanRaw.isEmpty()) return null

        return contacts.firstOrNull { contact ->
            contact.getPhoneNumbersList().any { phoneNum ->
                val cleanTarget = normalizePhoneNumber(phoneNum)
                if (cleanTarget.isEmpty()) false
                else if (cleanRaw == cleanTarget) true
                else if (cleanRaw.length >= 7 && cleanTarget.length >= 7 &&
                    (cleanRaw.endsWith(cleanTarget) || cleanTarget.endsWith(cleanRaw))) true
                else false
            }
        }
    }

    companion object {
        fun normalizePhoneNumber(phone: String): String {
            return phone.replace(Regex("[^0-9]"), "")
        }

        fun getStartOfPeriodTimestamp(period: FrequencyPeriod, now: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now

            when (period) {
                FrequencyPeriod.DAY -> {
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
                FrequencyPeriod.WEEK -> {
                    // Week is Saturday to Friday
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1, ..., Saturday=7
                    val daysSinceSaturday = (dayOfWeek - Calendar.SATURDAY + 7) % 7
                    cal.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
                FrequencyPeriod.MONTH -> {
                    // Calendar month starts on 1st of current month
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
            }
            return cal.timeInMillis
        }
    }
}