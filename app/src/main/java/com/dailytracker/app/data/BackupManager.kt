package com.dailytracker.app.data

import android.content.Context
import android.net.Uri
import com.dailytracker.app.miniapps.officetracker.AttendanceRecord
import com.dailytracker.app.miniapps.officetracker.GovtHoliday
import com.dailytracker.app.miniapps.officetracker.OfficeTrackerDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object BackupManager {

    suspend fun exportBackup(context: Context, uri: Uri) {
        val kinKeepDao = KinKeepDatabase.getDatabase(context).contactDao()
        val officeDb = OfficeTrackerDatabase.getDatabase(context)
        val attendanceDao = officeDb.attendanceDao()
        val govtHolidayDao = officeDb.govtHolidayDao()
        val prefs = context.getSharedPreferences("super_app_prefs", Context.MODE_PRIVATE)

        val rootJson = JSONObject()
        rootJson.put("version", 1)
        rootJson.put("exportedAt", System.currentTimeMillis())

        // 1. KinKeep Data
        val kinkeepJson = JSONObject()
        val contacts = kinKeepDao.getAllContactsList()
        val callRecords = kinKeepDao.getAllCallRecordsList()

        val contactsArray = JSONArray()
        contacts.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phoneNumber", c.phoneNumber)
                put("relationship", c.relationship)
                put("targetCount", c.targetCount)
                put("frequencyPeriod", c.frequencyPeriod)
                put("lastCalledTimestamp", c.lastCalledTimestamp)
                put("notes", c.notes)
                put("pinned", c.pinned)
                put("avatarColorHex", c.avatarColorHex)
                put("snoozedUntil", c.snoozedUntil)
                put("priorityWeight", c.priorityWeight)
                put("createdAt", c.createdAt)
                put("photoUri", c.photoUri)
            }
            contactsArray.put(obj)
        }
        kinkeepJson.put("contacts", contactsArray)

        val recordsArray = JSONArray()
        callRecords.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("contactId", r.contactId)
                put("timestamp", r.timestamp)
                put("callType", r.callType)
                put("notes", r.notes)
                put("durationSeconds", r.durationSeconds)
            }
            recordsArray.put(obj)
        }
        kinkeepJson.put("callRecords", recordsArray)
        rootJson.put("kinkeep", kinkeepJson)

        // 2. Office Tracker Data
        val officeJson = JSONObject()
        val allAttendance = mutableListOf<AttendanceRecord>()
        val cursor = officeDb.query("SELECT * FROM attendance_records", null)
        cursor.use { c ->
            val idxDate = c.getColumnIndex("date")
            val idxStatus = c.getColumnIndex("status")
            val idxOt = c.getColumnIndex("otHours")
            val idxNight = c.getColumnIndex("isNightDuty")
            val idxUpdated = c.getColumnIndex("updatedAt")
            while (c.moveToNext()) {
                allAttendance.add(
                    AttendanceRecord(
                        date = if (idxDate >= 0) c.getString(idxDate) else "",
                        status = if (idxStatus >= 0) c.getString(idxStatus) else "UNMARKED",
                        otHours = if (idxOt >= 0) c.getInt(idxOt) else 0,
                        isNightDuty = if (idxNight >= 0) c.getInt(idxNight) == 1 else false,
                        updatedAt = if (idxUpdated >= 0) c.getLong(idxUpdated) else 0L
                    )
                )
            }
        }

        val attendanceArray = JSONArray()
        allAttendance.forEach { att ->
            val obj = JSONObject().apply {
                put("date", att.date)
                put("status", att.status)
                put("otHours", att.otHours)
                put("isNightDuty", att.isNightDuty)
                put("updatedAt", att.updatedAt)
            }
            attendanceArray.put(obj)
        }
        officeJson.put("attendanceRecords", attendanceArray)

        val holidays = govtHolidayDao.getAllGovtHolidaysList()
        val holidayArray = JSONArray()
        holidays.forEach { h ->
            val obj = JSONObject().apply {
                put("date", h.date)
                put("label", h.label)
            }
            holidayArray.put(obj)
        }
        officeJson.put("govtHolidays", holidayArray)
        rootJson.put("officetracker", officeJson)

        // 3. App Preferences
        val prefsJson = JSONObject().apply {
            put("theme_mode", prefs.getString("theme_mode", "MONOCHROME"))
            put("mini_app_order", prefs.getString("mini_app_order", "kinkeep,bluetoothtracker,officetracker"))
        }
        rootJson.put("preferences", prefsJson)

        // Write to OutputStream
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw Exception("Could not open output stream")
    }

    suspend fun importBackup(context: Context, uri: Uri) {
        val kinKeepDao = KinKeepDatabase.getDatabase(context).contactDao()
        val officeDb = OfficeTrackerDatabase.getDatabase(context)
        val attendanceDao = officeDb.attendanceDao()
        val govtHolidayDao = officeDb.govtHolidayDao()
        val prefs = context.getSharedPreferences("super_app_prefs", Context.MODE_PRIVATE)

        val content = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    content.append(line)
                    line = reader.readLine()
                }
            }
        } ?: throw Exception("Could not open input stream")

        val rootJson = JSONObject(content.toString())

        // 1. KinKeep Import
        val kinkeepObj = when {
            rootJson.has("kinkeep") -> rootJson.getJSONObject("kinkeep")
            else -> rootJson // Backward compatibility with top-level contacts/callRecords
        }

        if (kinkeepObj.has("contacts")) {
            kinKeepDao.deleteAllContacts()
            kinKeepDao.deleteAllCallRecords()

            val contactsArray = kinkeepObj.getJSONArray("contacts")
            for (i in 0 until contactsArray.length()) {
                val obj = contactsArray.getJSONObject(i)
                val contact = TrackedContact(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    relationship = obj.optString("relationship", "Family"),
                    targetCount = obj.optInt("targetCount", 1),
                    frequencyPeriod = obj.optString("frequencyPeriod", "WEEK"),
                    lastCalledTimestamp = obj.optLong("lastCalledTimestamp", 0L),
                    notes = obj.optString("notes", ""),
                    pinned = obj.optBoolean("pinned", false),
                    avatarColorHex = obj.optString("avatarColorHex", "#D9534F"),
                    snoozedUntil = obj.optLong("snoozedUntil", 0L),
                    priorityWeight = obj.optInt("priorityWeight", 3),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    photoUri = obj.optString("photoUri", "")
                )
                kinKeepDao.insertContact(contact)
            }
        }

        if (kinkeepObj.has("callRecords")) {
            val recordsArray = kinkeepObj.getJSONArray("callRecords")
            for (i in 0 until recordsArray.length()) {
                val obj = recordsArray.getJSONObject(i)
                val record = CallRecord(
                    id = obj.optLong("id", 0L),
                    contactId = obj.optLong("contactId", 0L),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    callType = obj.optString("callType", obj.optString("type", "OUTGOING")),
                    notes = obj.optString("notes", obj.optString("note", "")),
                    durationSeconds = obj.optInt("durationSeconds", 0)
                )
                kinKeepDao.insertCallRecord(record)
            }
        }

        // 2. Office Tracker Import
        val officeObj = when {
            rootJson.has("officetracker") -> rootJson.getJSONObject("officetracker")
            else -> null
        }

        if (officeObj != null) {
            if (officeObj.has("attendanceRecords")) {
                val attArray = officeObj.getJSONArray("attendanceRecords")
                for (i in 0 until attArray.length()) {
                    val obj = attArray.getJSONObject(i)
                    val att = AttendanceRecord(
                        date = obj.getString("date"),
                        status = obj.getString("status"),
                        otHours = obj.optInt("otHours", 0),
                        isNightDuty = obj.optBoolean("isNightDuty", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    attendanceDao.insertOrUpdate(att)
                }
            }

            if (officeObj.has("govtHolidays")) {
                govtHolidayDao.deleteAllHolidays()
                val holArray = officeObj.getJSONArray("govtHolidays")
                for (i in 0 until holArray.length()) {
                    val obj = holArray.getJSONObject(i)
                    val hol = GovtHoliday(
                        date = obj.getString("date"),
                        label = obj.optString("label", "")
                    )
                    govtHolidayDao.insertHoliday(hol)
                }
            }
        }

        // 3. Preferences Import
        if (rootJson.has("preferences")) {
            val prefObj = rootJson.getJSONObject("preferences")
            val themeMode = prefObj.optString("theme_mode", "MONOCHROME")
            val appOrder = prefObj.optString("mini_app_order", "kinkeep,bluetoothtracker,officetracker")
            prefs.edit()
                .putString("theme_mode", themeMode)
                .putString("mini_app_order", appOrder)
                .apply()
        }
    }
}