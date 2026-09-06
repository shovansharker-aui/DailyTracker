package com.dailytracker.app.miniapps.officetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarGridCell(
    val dayOfMonth: Int?,
    val dateStr: String?,
    val derivedStatus: DerivedDayStatus?,
    val isToday: Boolean = false,
    val isFuture: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeTrackerDashboard(
    viewModel: OfficeTrackerViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCalendar by viewModel.selectedCalendar.collectAsStateWithLifecycle()
    val records by viewModel.monthlyRecords.collectAsStateWithLifecycle()
    val allRecordsMap by viewModel.allRecordsMap.collectAsStateWithLifecycle()
    val govtHolidaysSet by viewModel.govtHolidaysSet.collectAsStateWithLifecycle()

    // Locale.US to match the Locale.US keys built below (dateStr) and in
    // OfficeTrackerCalculator — this is a machine-readable key, not display text, so it
    // must not vary with the device's digit system.
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val calendarCells = remember(selectedCalendar, allRecordsMap, govtHolidaysSet) {
        val cells = mutableListOf<CalendarGridCell>()
        val cal = selectedCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon... 7=Sat
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        // Saturday-first offset calculation
        val satOffset = when (firstDayOfWeek) {
            Calendar.SATURDAY -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.TUESDAY -> 3
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 5
            Calendar.FRIDAY -> 6
            else -> 0
        }

        for (i in 0 until satOffset) {
            cells.add(CalendarGridCell(null, null, null))
        }

        val todayCal = Calendar.getInstance()

        for (day in 1..daysInMonth) {
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            val dayCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            val isFuture = dayCal.after(todayCal) && dateStr != todayIso
            val isToday = dateStr == todayIso

            val currentRecord = allRecordsMap[dateStr]
            val prevDateIso = OfficeTrackerCalculator.getPreviousDateIso(dateStr)
            val prevRecord = allRecordsMap[prevDateIso]
            val isGovt = govtHolidaysSet.contains(dateStr)

            val derived = OfficeTrackerCalculator.computeDerivedStatus(
                dateIso = dateStr,
                currentRecord = currentRecord,
                prevDayRecord = prevRecord,
                isGovtHoliday = isGovt
            )

            cells.add(
                CalendarGridCell(
                    dayOfMonth = day,
                    dateStr = dateStr,
                    derivedStatus = derived,
                    isToday = isToday,
                    isFuture = isFuture
                )
            )
        }
        cells
    }

    // Monthly Totals calculation
    val presentCount = calendarCells.count { it.derivedStatus?.displayStatusText == "Present" || it.derivedStatus?.displayStatusText == "Duty on Day off" }
    val absentCount = calendarCells.count { it.derivedStatus?.displayStatusText == "Absent" }
    val holidayCount = calendarCells.count { it.derivedStatus?.isGovtHoliday == true || it.derivedStatus?.isWeeklyHoliday == true || it.derivedStatus?.isDerivedDO == true }
    val totalOtHours = calendarCells.mapNotNull { it.derivedStatus }.sumOf { it.otHours }
    val nightDutyCount = calendarCells.count { it.derivedStatus?.isNightDuty == true }

    val monthlyExtraAllowance = remember(calendarCells) {
        calendarCells.mapNotNull { it.derivedStatus }.sumOf { it.allowanceTaka }
    }

    var selectedCellForMarking by remember { mutableStateOf<CalendarGridCell?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Office Tracker",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    Surface(
                        onClick = onNavigateToHome,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("home_button_office_dashboard")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("open_office_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Government Holidays Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month Selector Bar (iOS style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.previousMonth() },
                    modifier = Modifier.testTag("prev_month_btn")
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }

                val sdfHeader = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                Text(
                    text = sdfHeader.format(selectedCalendar.time),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                IconButton(
                    onClick = { viewModel.nextMonth() },
                    modifier = Modifier.testTag("next_month_btn")
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
            }

            // Monthly Summary Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Present",
                    value = "$presentCount d",
                    subtitle = "+${totalOtHours}h OT",
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Absent",
                    value = "$absentCount d",
                    subtitle = "Off Duty",
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Holiday / Night",
                    value = "$holidayCount / $nightDutyCount",
                    subtitle = "Shift Logs",
                    containerColor = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100),
                    modifier = Modifier.weight(1.1f)
                )
            }

            // iOS-Style Month Calendar Grid Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Saturday-first Header
                    val weekDays = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        weekDays.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (day == "Thu" || day == "Fri") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 7-column Calendar Days Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(calendarCells.size) { index ->
                            val cell = calendarCells[index]
                            CalendarCellComposable(
                                cell = cell,
                                onClick = {
                                    if (cell.dateStr != null) {
                                        selectedCellForMarking = cell
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Extra Allowance Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("extra_allowance_summary_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "ALLOWANCE TOTAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Extra Allowance (expected): ৳$monthlyExtraAllowance",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }

    // Attendance Marking Dialog
    selectedCellForMarking?.let { cell ->
        MarkAttendanceBottomSheet(
            cell = cell,
            onDismiss = { selectedCellForMarking = null },
            onSave = { status, otHours, isNight ->
                if (cell.dateStr != null) {
                    viewModel.markAttendance(cell.dateStr, status, otHours, isNight)
                }
                selectedCellForMarking = null
            }
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun CalendarCellComposable(
    cell: CalendarGridCell,
    onClick: () -> Unit
) {
    if (cell.dayOfMonth == null || cell.derivedStatus == null) {
        Box(modifier = Modifier.aspectRatio(1f))
        return
    }

    val derived = cell.derivedStatus

    val (bgColor, textColor) = when {
        derived.isNightDuty -> Color(0xFFEDE7F6) to Color(0xFF512DA8)
        derived.isDutyOnDayOff -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
        derived.isDerivedDO || derived.isGovtHoliday || derived.isWeeklyHoliday -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        derived.displayStatusText == "Present" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        derived.displayStatusText == "Absent" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .testTag("date_cell_${cell.dateStr}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (cell.isToday) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = if (cell.isToday) MaterialTheme.colorScheme.primary else textColor
            )

            if (derived.isNightDuty) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                        tint = textColor
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    Text(
                        text = "+7h",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }
            } else if (derived.isDutyOnDayOff) {
                Text(
                    text = "Duty DO",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = textColor
                )
            } else if (derived.isDerivedDO) {
                Text(
                    text = "DO",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = textColor
                )
            } else if (derived.otHours > 0) {
                Text(
                    text = "+${derived.otHours}h",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = textColor
                )
            } else if (derived.isGovtHoliday) {
                Text(
                    text = "Govt",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = textColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkAttendanceBottomSheet(
    cell: CalendarGridCell,
    onDismiss: () -> Unit,
    onSave: (AttendanceStatus, Int, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val initialRaw = cell.derivedStatus?.rawRecord
    val initialStatus = AttendanceStatus.fromString(initialRaw?.status ?: "PRESENT")

    var selectedStatus by remember { mutableStateOf(initialStatus) }
    var isNightDuty by remember { mutableStateOf(initialRaw?.isNightDuty ?: false) }
    var otHours by remember { mutableFloatStateOf(if (isNightDuty) 7f else (initialRaw?.otHours ?: 0).toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag("mark_attendance_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Log Attendance for ${cell.dateStr}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            // Status Option Buttons: Present, Absent, Holiday
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { selectedStatus = AttendanceStatus.PRESENT },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedStatus == AttendanceStatus.PRESENT) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_mark_present")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "PRESENT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedStatus == AttendanceStatus.PRESENT) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    onClick = {
                        selectedStatus = AttendanceStatus.ABSENT
                        otHours = 0f
                        isNightDuty = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedStatus == AttendanceStatus.ABSENT) Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_mark_absent")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ABSENT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedStatus == AttendanceStatus.ABSENT) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    onClick = {
                        selectedStatus = AttendanceStatus.HOLIDAY
                        otHours = 0f
                        isNightDuty = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedStatus == AttendanceStatus.HOLIDAY) Color(0xFFE65100) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_mark_holiday")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "HOLIDAY",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedStatus == AttendanceStatus.HOLIDAY) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (selectedStatus == AttendanceStatus.PRESENT) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Night Duty Shift",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Switch(
                                checked = isNightDuty,
                                onCheckedChange = { checked ->
                                    isNightDuty = checked
                                    if (checked) {
                                        otHours = 7f // Night duty always uses exactly 7 OT hours
                                    }
                                },
                                modifier = Modifier.testTag("night_duty_switch")
                            )
                        }

                        if (!isNightDuty) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "OT Hours",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Text(
                                    text = "${otHours.toInt()} Hours",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Slider(
                                value = otHours,
                                onValueChange = { otHours = it },
                                valueRange = 0f..5f,
                                steps = 4,
                                modifier = Modifier.testTag("ot_hours_slider")
                            )
                        } else {
                            Text(
                                text = "Night duty fixed at 7 OT hours + ৳700 Night Allowance (৳1120 total)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val finalOt = if (selectedStatus == AttendanceStatus.PRESENT) {
                            if (isNightDuty) OfficeTrackerPayRates.NIGHT_DUTY_OT_HOURS else otHours.toInt()
                        } else 0
                        onSave(selectedStatus, finalOt, isNightDuty)
                    },
                    modifier = Modifier.testTag("save_attendance_btn")
                ) {
                    Text("Save Entry")
                }
            }
        }
    }
}
