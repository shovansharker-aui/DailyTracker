package com.dailytracker.app.miniapps.officetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OfficeTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = OfficeTrackerDatabase.getDatabase(application)
    private val dao = db.attendanceDao()
    private val govtHolidayDao = db.govtHolidayDao()

    // yyyy-MM is a machine-readable key that prefix-matches the `date` primary key in
    // AttendanceDao — it must stay in Locale.US so its digits always match the
    // Locale.US date keys built in OfficeTrackerDashboard/OfficeTrackerCalculator,
    // regardless of the device's locale (a locale with non-Latin digits would
    // otherwise make every month query return nothing).
    private val sdfYearMonth = SimpleDateFormat("yyyy-MM", Locale.US)

    private val _selectedCalendar = MutableStateFlow(Calendar.getInstance())
    val selectedCalendar: StateFlow<Calendar> = _selectedCalendar.asStateFlow()

    private val _currentYearMonth = MutableStateFlow(sdfYearMonth.format(Date()))
    val currentYearMonth: StateFlow<String> = _currentYearMonth.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyRecords: StateFlow<List<AttendanceRecord>> = _currentYearMonth.flatMapLatest { ym ->
        dao.getRecordsForMonth(ym)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allRecordsMap: StateFlow<Map<String, AttendanceRecord>> = dao.getAllRecords()
        .map { list -> list.associateBy { it.date } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val govtHolidays: StateFlow<List<GovtHoliday>> = govtHolidayDao.getAllGovtHolidays()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val govtHolidaysSet: StateFlow<Set<String>> = govtHolidays
        .map { list -> list.map { it.date }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    init {
        AttendanceReminderReceiver.scheduleDaily9PmReminder(application)
    }

    fun addGovtHoliday(dateDdMmYyyy: String, label: String) {
        val iso = parseDdMmYyyyToIso(dateDdMmYyyy) ?: return
        viewModelScope.launch {
            govtHolidayDao.insertHoliday(GovtHoliday(date = iso, label = label.trim()))
        }
    }

    fun deleteGovtHoliday(dateIso: String) {
        viewModelScope.launch {
            govtHolidayDao.deleteHoliday(dateIso)
        }
    }

    fun previousMonth() {
        val cal = _selectedCalendar.value.clone() as Calendar
        cal.add(Calendar.MONTH, -1)
        _selectedCalendar.value = cal
        _currentYearMonth.value = sdfYearMonth.format(cal.time)
    }

    fun nextMonth() {
        val cal = _selectedCalendar.value.clone() as Calendar
        cal.add(Calendar.MONTH, 1)
        _selectedCalendar.value = cal
        _currentYearMonth.value = sdfYearMonth.format(cal.time)
    }

    fun markAttendance(
        dateStr: String,
        status: AttendanceStatus,
        otHours: Int = 0,
        isNightDuty: Boolean = false
    ) {
        viewModelScope.launch {
            if (status == AttendanceStatus.UNMARKED) {
                dao.deleteRecord(dateStr)
            } else {
                val record = AttendanceRecord(
                    date = dateStr,
                    status = status.name,
                    otHours = if (status == AttendanceStatus.PRESENT) otHours else 0,
                    isNightDuty = isNightDuty
                )
                dao.insertOrUpdate(record)
            }
        }
    }

    fun deleteRecord(dateStr: String) {
        viewModelScope.launch {
            dao.deleteRecord(dateStr)
        }
    }
}
