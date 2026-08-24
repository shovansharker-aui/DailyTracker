package com.dailytracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.concurrent.TimeUnit

enum class FrequencyPeriod(val label: String, val singularLabel: String, val daysInPeriod: Int) {
    DAY("Day", "daily", 1),
    WEEK("Week", "weekly", 7),
    MONTH("Month", "monthly", 30);

    companion object {
        fun fromName(name: String): FrequencyPeriod {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: WEEK
        }
    }
}

enum class ContactStatus(val title: String) {
    ON_TRACK("On Track"),
    DUE_SOON("Due Soon"),
    OVERDUE("Overdue"),
    NEVER_CALLED("Needs First Call")
}

@Entity(tableName = "tracked_contacts")
data class TrackedContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lookupKey: String = "",
    val name: String,
    val phoneNumber: String,
    val relationship: String = "",
    val targetCount: Int = 1,
    val frequencyPeriod: String = "WEEK", // "DAY", "WEEK", "MONTH"
    val lastCalledTimestamp: Long = 0L,
    val notes: String = "",
    val photoUri: String = "",
    val pinned: Boolean = false,
    val avatarColorHex: String = "#D9534F",
    val snoozedUntil: Long = 0L,
    val priorityWeight: Int = 3,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getPhoneNumbersList(): List<String> {
        return phoneNumber.split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun getPeriodEnum(): FrequencyPeriod = FrequencyPeriod.fromName(frequencyPeriod)

    fun getDaysSinceLastCall(now: Long = System.currentTimeMillis()): Long? {
        if (lastCalledTimestamp <= 0L) return null
        val diffMs = now - lastCalledTimestamp
        return TimeUnit.MILLISECONDS.toDays(diffMs).coerceAtLeast(0)
    }

    fun getCalculatedStatus(callsInCurrentPeriod: Int, now: Long = System.currentTimeMillis()): ContactStatus {
        if (lastCalledTimestamp <= 0L && callsInCurrentPeriod == 0) {
            return ContactStatus.NEVER_CALLED
        }
        if (callsInCurrentPeriod >= targetCount) {
            return ContactStatus.ON_TRACK
        }

        val period = getPeriodEnum()
        val daysSince = getDaysSinceLastCall(now) ?: Long.MAX_VALUE

        // Overdue threshold calculation
        val maxAllowedIntervalDays = when (period) {
            FrequencyPeriod.DAY -> 1
            FrequencyPeriod.WEEK -> 7 / targetCount.coerceAtLeast(1)
            FrequencyPeriod.MONTH -> 30 / targetCount.coerceAtLeast(1)
        }

        return when {
            daysSince > maxAllowedIntervalDays + 2 -> ContactStatus.OVERDUE
            daysSince >= maxAllowedIntervalDays -> ContactStatus.DUE_SOON
            else -> if (callsInCurrentPeriod < targetCount) ContactStatus.DUE_SOON else ContactStatus.ON_TRACK
        }
    }
}