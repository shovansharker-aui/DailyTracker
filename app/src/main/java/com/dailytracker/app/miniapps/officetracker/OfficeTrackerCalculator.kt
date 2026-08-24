package com.dailytracker.app.miniapps.officetracker

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DerivedDayStatus(
    val dateStr: String,                // "yyyy-MM-dd"
    val dayOfWeek: Int,                 // Calendar.SATURDAY (7), etc.
    val rawRecord: AttendanceRecord?,
    val prevDayRecord: AttendanceRecord?,
    val isGovtHoliday: Boolean,
    val isWeeklyHoliday: Boolean,       // Thursday or Friday
    val displayStatusText: String,      // "Present", "Absent", "Night Duty", "DO", "Duty on Day off", "Govt Holiday", "Weekly Off", "Unmarked"
    val isNightDuty: Boolean,
    val isDerivedDO: Boolean,           // Day after night duty, left unmarked or ABSENT
    val isDutyOnDayOff: Boolean,        // Day after night duty, marked PRESENT
    val otHours: Int,
    val allowanceTaka: Int              // Total extra allowance in taka for this day
)

object OfficeTrackerCalculator {

    fun getPreviousDateIso(dateIso: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            val date = sdf.parse(dateIso) ?: return ""
            cal.time = date
            cal.add(Calendar.DAY_OF_MONTH, -1)
            sdf.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }

    fun computeDerivedStatus(
        dateIso: String,
        currentRecord: AttendanceRecord?,
        prevDayRecord: AttendanceRecord?,
        isGovtHoliday: Boolean
    ): DerivedDayStatus {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val date = sdf.parse(dateIso)
        if (date != null) cal.time = date
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeeklyHoliday = (dayOfWeek == Calendar.THURSDAY || dayOfWeek == Calendar.FRIDAY)

        val prevWasNightDuty = prevDayRecord?.isNightDuty == true
        val currentStatusEnum = AttendanceStatus.fromString(currentRecord?.status ?: "UNMARKED")

        // 1) Night Duty on current day
        if (currentRecord?.isNightDuty == true) {
            val totalAllowance = (OfficeTrackerPayRates.NIGHT_DUTY_OT_HOURS * OfficeTrackerPayRates.OT_RATE_PER_HOUR) + OfficeTrackerPayRates.NIGHT_ALLOWANCE // 7*60 + 700 = 1120
            return DerivedDayStatus(
                dateStr = dateIso,
                dayOfWeek = dayOfWeek,
                rawRecord = currentRecord,
                prevDayRecord = prevDayRecord,
                isGovtHoliday = isGovtHoliday,
                isWeeklyHoliday = isWeeklyHoliday,
                displayStatusText = "Night Duty",
                isNightDuty = true,
                isDerivedDO = false,
                isDutyOnDayOff = false,
                otHours = OfficeTrackerPayRates.NIGHT_DUTY_OT_HOURS,
                allowanceTaka = totalAllowance
            )
        }

        // 2) Day immediately after a night duty day
        if (prevWasNightDuty) {
            if (currentStatusEnum == AttendanceStatus.PRESENT) {
                // Marked PRESENT -> "Duty on Day off", allowance = 400
                return DerivedDayStatus(
                    dateStr = dateIso,
                    dayOfWeek = dayOfWeek,
                    rawRecord = currentRecord,
                    prevDayRecord = prevDayRecord,
                    isGovtHoliday = isGovtHoliday,
                    isWeeklyHoliday = isWeeklyHoliday,
                    displayStatusText = "Duty on Day off",
                    isNightDuty = false,
                    isDerivedDO = false,
                    isDutyOnDayOff = true,
                    otHours = currentRecord?.otHours ?: 0,
                    allowanceTaka = OfficeTrackerPayRates.HOLIDAY_RATE_PER_DAY // 400
                )
            } else {
                // Left unmarked or marked ABSENT -> "DO" (Day Off), allowance = 0
                return DerivedDayStatus(
                    dateStr = dateIso,
                    dayOfWeek = dayOfWeek,
                    rawRecord = currentRecord,
                    prevDayRecord = prevDayRecord,
                    isGovtHoliday = isGovtHoliday,
                    isWeeklyHoliday = isWeeklyHoliday,
                    displayStatusText = "DO",
                    isNightDuty = false,
                    isDerivedDO = true,
                    isDutyOnDayOff = false,
                    otHours = 0,
                    allowanceTaka = 0
                )
            }
        }

        // 3) Normal days (not night duty, not day after night duty)
        if (currentStatusEnum == AttendanceStatus.PRESENT) {
            val ot = currentRecord?.otHours ?: 0
            val allowance = ot * OfficeTrackerPayRates.OT_RATE_PER_HOUR
            return DerivedDayStatus(
                dateStr = dateIso,
                dayOfWeek = dayOfWeek,
                rawRecord = currentRecord,
                prevDayRecord = prevDayRecord,
                isGovtHoliday = isGovtHoliday,
                isWeeklyHoliday = isWeeklyHoliday,
                displayStatusText = "Present",
                isNightDuty = false,
                isDerivedDO = false,
                isDutyOnDayOff = false,
                otHours = ot,
                allowanceTaka = allowance
            )
        }

        if (currentStatusEnum == AttendanceStatus.ABSENT) {
            return DerivedDayStatus(
                dateStr = dateIso,
                dayOfWeek = dayOfWeek,
                rawRecord = currentRecord,
                prevDayRecord = prevDayRecord,
                isGovtHoliday = isGovtHoliday,
                isWeeklyHoliday = isWeeklyHoliday,
                displayStatusText = "Absent",
                isNightDuty = false,
                isDerivedDO = false,
                isDutyOnDayOff = false,
                otHours = 0,
                allowanceTaka = 0
            )
        }

        if (currentStatusEnum == AttendanceStatus.HOLIDAY) {
            return DerivedDayStatus(
                dateStr = dateIso,
                dayOfWeek = dayOfWeek,
                rawRecord = currentRecord,
                prevDayRecord = prevDayRecord,
                isGovtHoliday = isGovtHoliday,
                isWeeklyHoliday = isWeeklyHoliday,
                displayStatusText = "Holiday",
                isNightDuty = false,
                isDerivedDO = false,
                isDutyOnDayOff = false,
                otHours = 0,
                allowanceTaka = 0
            )
        }

        // Unmarked
        val defaultText = when {
            isGovtHoliday -> "Govt Holiday"
            isWeeklyHoliday -> "Weekly Off"
            else -> "Unmarked"
        }
        return DerivedDayStatus(
            dateStr = dateIso,
            dayOfWeek = dayOfWeek,
            rawRecord = currentRecord,
            prevDayRecord = prevDayRecord,
            isGovtHoliday = isGovtHoliday,
            isWeeklyHoliday = isWeeklyHoliday,
            displayStatusText = defaultText,
            isNightDuty = false,
            isDerivedDO = false,
            isDutyOnDayOff = false,
            otHours = 0,
            allowanceTaka = 0
        )
    }
}
