package com.dailytracker.app.miniapps.officetracker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MonthDayItem(
    val dateStr: String, // "yyyy-MM-dd"
    val dayOfMonth: Int,
    val dayOfWeekName: String,
    val record: AttendanceRecord?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfficeDetailListView(
    viewModel: OfficeTrackerViewModel,
    onBackToCalendar: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCalendar by viewModel.selectedCalendar.collectAsStateWithLifecycle()
    val records by viewModel.monthlyRecords.collectAsStateWithLifecycle()

    val monthDays = remember(selectedCalendar, records) {
        val list = mutableListOf<MonthDayItem>()
        val cal = selectedCalendar.clone() as Calendar
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val sdfDayOfWeek = SimpleDateFormat("EEE", Locale.getDefault())
        val recordsMap = records.associateBy { it.date }

        for (day in 1..daysInMonth) {
            val dayCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            val dayOfWeekName = sdfDayOfWeek.format(dayCal.time)
            list.add(
                MonthDayItem(
                    dateStr = dateStr,
                    dayOfMonth = day,
                    dayOfWeekName = dayOfWeekName,
                    record = recordsMap[dateStr]
                )
            )
        }
        list
    }

    var editingItem by remember { mutableStateOf<MonthDayItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val sdfHeader = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    Text(
                        text = "${sdfHeader.format(selectedCalendar.time)} Logs",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onNavigateToHome,
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag("home_button_office_list")
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

                        IconButton(
                            onClick = onBackToCalendar,
                            modifier = Modifier.testTag("back_to_calendar_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Month Grid View"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "LONG-PRESS ANY DAY TO EDIT ATTENDANCE, OT, OR NIGHT DUTY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            items(monthDays, key = { it.dateStr }) { item ->
                val record = item.record
                val status = AttendanceStatus.fromString(record?.status ?: "UNMARKED")

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { editingItem = item },
                            onLongClick = { editingItem = item }
                        )
                        .testTag("list_row_${item.dateStr}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date Badge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(44.dp)
                        ) {
                            Text(
                                text = item.dayOfWeekName.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = item.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (status) {
                                AttendanceStatus.PRESENT -> Color(0xFFE8F5E9)
                                AttendanceStatus.ABSENT -> Color(0xFFFFEBEE)
                                AttendanceStatus.HOLIDAY -> Color(0xFFFFF3E0)
                                AttendanceStatus.UNMARKED -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = status.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = when (status) {
                                    AttendanceStatus.PRESENT -> Color(0xFF2E7D32)
                                    AttendanceStatus.ABSENT -> Color(0xFFC62828)
                                    AttendanceStatus.HOLIDAY -> Color(0xFFE65100)
                                    AttendanceStatus.UNMARKED -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // OT Hours & Night Duty Pills
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (record != null && record.otHours > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "+${record.otHours}h OT",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            if (record != null && record.isNightDuty) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.NightsStay,
                                            contentDescription = "Night Duty",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Modal BottomSheet
    editingItem?.let { item ->
        EditAttendanceBottomSheet(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { status, ot, night ->
                viewModel.markAttendance(item.dateStr, status, ot, night)
                editingItem = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAttendanceBottomSheet(
    item: MonthDayItem,
    onDismiss: () -> Unit,
    onSave: (AttendanceStatus, Int, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val initialRecord = item.record
    val initialStatus = AttendanceStatus.fromString(initialRecord?.status ?: "PRESENT")

    var selectedStatus by remember { mutableStateOf(initialStatus) }
    var otHours by remember { mutableFloatStateOf((initialRecord?.otHours ?: 0).toFloat()) }
    var isNightDuty by remember { mutableStateOf(initialRecord?.isNightDuty ?: false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag("edit_attendance_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit Log: ${item.dateStr}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            // Status Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceStatus.values().filter { it != AttendanceStatus.UNMARKED }.forEach { status ->
                    val isSelected = selectedStatus == status
                    Surface(
                        onClick = { selectedStatus = status },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            when (status) {
                                AttendanceStatus.PRESENT -> Color(0xFF2E7D32)
                                AttendanceStatus.ABSENT -> Color(0xFFC62828)
                                AttendanceStatus.HOLIDAY -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        } else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("status_chip_${status.name}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = status.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // OT Slider (revealed when Present)
            if (selectedStatus == AttendanceStatus.PRESENT) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overtime (OT Hours)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${otHours.toInt()} Hours",
                            style = MaterialTheme.typography.titleSmall.copy(
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
                        modifier = Modifier.testTag("edit_ot_slider")
                    )
                }

                // Night Duty Switch
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
                        onCheckedChange = { isNightDuty = it },
                        modifier = Modifier.testTag("edit_night_duty_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onSave(selectedStatus, otHours.toInt(), isNightDuty)
                    },
                    modifier = Modifier.testTag("save_edit_attendance_btn")
                ) {
                    Text(
                        text = "Save Log",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
