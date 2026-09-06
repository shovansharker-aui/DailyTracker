package com.dailytracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailytracker.app.data.CallRecord
import com.dailytracker.app.data.ContactRepository
import com.dailytracker.app.data.ContactStatus
import com.dailytracker.app.data.KinKeepDatabase
import com.dailytracker.app.data.TrackedContact
import com.dailytracker.app.util.WhatsAppCallListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactWithStats(
    val contact: TrackedContact,
    val callsInCurrentPeriod: Int,
    val status: ContactStatus,
    val daysSinceLastCall: Long?
)

enum class FilterCategory(val label: String) {
    ALL("All"),
    OVERDUE("Overdue / Due Soon"),
    FAVORITES("Pinned")
}

data class WeeklyActivity(
    val weekLabel: String,
    val durationMinutes: Float
)

data class ConnectionHealthBreakdown(
    val onTrackCount: Int = 0,
    val pendingCount: Int = 0,
    val overdueCount: Int = 0,
    val totalContacts: Int = 0,
    val healthPercentage: Int = 0
)

data class DashboardAnalyticsState(
    val connectionHealth: ConnectionHealthBreakdown = ConnectionHealthBreakdown(),
    val weeklyActivities: List<WeeklyActivity> = emptyList(),
    val topOverdueContacts: List<ContactWithStats> = emptyList(),
    val recentCallRecords: List<CallRecord> = emptyList()
)

data class KinKeepUiState(
    val contactsWithStats: List<ContactWithStats> = emptyList(),
    val filteredContacts: List<ContactWithStats> = emptyList(),
    val selectedFilter: FilterCategory = FilterCategory.ALL,
    val isLoading: Boolean = false,
    val syncMessage: String? = null,
    val totalOverdueCount: Int = 0,
    val totalOnTrackCount: Int = 0,
    val analyticsState: DashboardAnalyticsState = DashboardAnalyticsState()
)

class KinKeepViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KinKeepViewModel"
        private const val MANUAL_CALL_DEFAULT_DURATION_SECONDS = 300
        // Lower-confidence than a manual log, so a smaller nominal duration than
        // MANUAL_CALL_DEFAULT_DURATION_SECONDS.
        private const val WHATSAPP_AUTO_LOG_DEFAULT_DURATION_SECONDS = 60
        // WhatsApp reposts an updated notification for the same call several times
        // (ringing -> ongoing -> ended); this window stops those from each logging
        // their own call record.
        private const val WHATSAPP_DEDUPE_WINDOW_MS = 3 * 60 * 1000L
    }
    private val database = KinKeepDatabase.getDatabase(application)
    private val repository = ContactRepository(database.contactDao(), application)

    private val _selectedFilter = MutableStateFlow(FilterCategory.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Dialog & Detail states
    private val _selectedContactForDetails = MutableStateFlow<ContactWithStats?>(null)
    val selectedContactForDetails = _selectedContactForDetails.asStateFlow()

    private val _contactCallHistory = MutableStateFlow<List<CallRecord>>(emptyList())
    val contactCallHistory = _contactCallHistory.asStateFlow()

    private val _showAddEditSheet = MutableStateFlow(false)
    val showAddEditSheet = _showAddEditSheet.asStateFlow()

    private val _contactToEdit = MutableStateFlow<TrackedContact?>(null)
    val contactToEdit = _contactToEdit.asStateFlow()

    init {
        // Auto-log a call whenever WhatsAppCallListenerService detects a WhatsApp call
        // notification. WhatsApp (VoIP) calls never appear in the system call log, so
        // without this, KinKeep has no way to see them at all.
        viewModelScope.launch {
            WhatsAppCallListenerService.whatsappCallEvents.collect { candidateText ->
                handleWhatsAppCallDetected(candidateText)
            }
        }
    }

    private suspend fun handleWhatsAppCallDetected(candidateText: String) {
        val normalizedCandidate = candidateText.trim().lowercase()
        if (normalizedCandidate.isBlank()) return

        // Best-effort match: the WhatsApp notification title is usually the caller's
        // WhatsApp display name. If it doesn't clearly name exactly one tracked
        // contact, skip rather than risk logging a call against the wrong person.
        val contacts = repository.allContacts.first()
        val matches = contacts.filter { contact ->
            val contactName = contact.name.trim().lowercase()
            contactName.isNotBlank() && normalizedCandidate.contains(contactName)
        }
        val match = matches.singleOrNull() ?: return

        val now = System.currentTimeMillis()
        val alreadyLogged = repository.hasCallRecordSince(match.id, now - WHATSAPP_DEDUPE_WINDOW_MS)
        if (alreadyLogged) return // WhatsApp reposts the same call's notification repeatedly.

        repository.logCallForContact(
            contactId = match.id,
            timestamp = now,
            durationSeconds = WHATSAPP_AUTO_LOG_DEFAULT_DURATION_SECONDS,
            callType = "OUTGOING",
            notes = "Auto-detected WhatsApp call"
        )
        _syncMessage.value = "Logged a WhatsApp call with ${match.name}"
    }

    val uiState: StateFlow<KinKeepUiState> = combine(
        repository.allContacts,
        repository.allRecentCallRecords,
        _selectedFilter,
        _isLoading,
        _syncMessage
    ) { contacts, callRecords, filter, loading, msg ->
        val now = System.currentTimeMillis()
        val statsList = contacts.map { contact -> contact.toContactWithStats(repository, now) }

        val onTrackCount = statsList.count { it.status == ContactStatus.ON_TRACK }
        val pendingCount = statsList.count { it.status == ContactStatus.DUE_SOON }
        val overdueCount = statsList.count { it.status == ContactStatus.OVERDUE || it.status == ContactStatus.NEVER_CALLED }
        val totalCount = statsList.size

        val healthPercentage = if (totalCount > 0) ((onTrackCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 100

        // Weekly Call Activity over past 4 weeks
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val w1Start = now - weekMs
        val w2Start = now - (2L * weekMs)
        val w3Start = now - (3L * weekMs)
        val w4Start = now - (4L * weekMs)

        var w1Mins = 0f
        var w2Mins = 0f
        var w3Mins = 0f
        var w4Mins = 0f

        callRecords.forEach { rec ->
            val durationMins = (if (rec.durationSeconds > 0) rec.durationSeconds else 300) / 60f
            when {
                rec.timestamp >= w1Start -> w1Mins += durationMins
                rec.timestamp in w2Start..<w1Start -> w2Mins += durationMins
                rec.timestamp in w3Start..<w2Start -> w3Mins += durationMins
                rec.timestamp in w4Start..<w3Start -> w4Mins += durationMins
            }
        }

        val weeklyActivities = listOf(
            WeeklyActivity("3 Wks Ago", w4Mins),
            WeeklyActivity("2 Wks Ago", w3Mins),
            WeeklyActivity("Last Wk", w2Mins),
            WeeklyActivity("This Wk", w1Mins)
        )

        val topOverdueList = statsList
            .filter { it.status == ContactStatus.OVERDUE || it.status == ContactStatus.NEVER_CALLED || it.status == ContactStatus.DUE_SOON }
            .sortedByDescending { item ->
                val daysSince = item.daysSinceLastCall ?: 30L
                val maxAllowed = when (item.contact.getPeriodEnum()) {
                    com.dailytracker.app.data.FrequencyPeriod.DAY -> 1L
                    com.dailytracker.app.data.FrequencyPeriod.WEEK -> (7L / item.contact.targetCount.coerceAtLeast(1))
                    com.dailytracker.app.data.FrequencyPeriod.MONTH -> (30L / item.contact.targetCount.coerceAtLeast(1))
                }
                val daysOverdue = (daysSince - maxAllowed).coerceAtLeast(1L)
                daysOverdue * item.contact.priorityWeight
            }
            .take(5)

        val filtered = statsList.filter { item ->
            when (filter) {
                FilterCategory.ALL -> true
                FilterCategory.OVERDUE -> item.status == ContactStatus.OVERDUE || item.status == ContactStatus.DUE_SOON || item.status == ContactStatus.NEVER_CALLED
                FilterCategory.FAVORITES -> item.contact.pinned
            }
        }

        val state = KinKeepUiState(
            contactsWithStats = statsList,
            filteredContacts = filtered,
            selectedFilter = filter,
            isLoading = loading,
            syncMessage = msg,
            totalOverdueCount = overdueCount,
            totalOnTrackCount = onTrackCount,
            analyticsState = DashboardAnalyticsState(
                connectionHealth = ConnectionHealthBreakdown(
                    onTrackCount = onTrackCount,
                    pendingCount = pendingCount,
                    overdueCount = overdueCount,
                    totalContacts = totalCount,
                    healthPercentage = healthPercentage
                ),
                weeklyActivities = weeklyActivities,
                topOverdueContacts = topOverdueList,
                recentCallRecords = callRecords
            )
        )

        // Check reminders for target deadlines
        try {
            com.dailytracker.app.util.CallReminderManager.checkAndTriggerReminders(getApplication(), statsList)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to check/trigger call reminders", e)
        }

        state
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KinKeepUiState()
    )

    fun onFilterSelected(filter: FilterCategory) {
        _selectedFilter.value = filter
    }

    fun syncCallLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _syncMessage.value = "Scanning phone call logs..."
            val contacts = repository.allContacts.first()
            val newSynced = repository.syncDeviceCallLogs(contacts)
            _isLoading.value = false
            _syncMessage.value = if (newSynced > 0) "Synced $newSynced call record(s) from phone!" else "Call logs up to date."
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    private var historyJob: Job? = null

    fun openContactDetails(item: ContactWithStats) {
        _selectedContactForDetails.value = item
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.getCallRecordsForContact(item.contact.id).collect { history ->
                _contactCallHistory.value = history
            }
        }
    }

    fun openContactDetailsById(contactId: Long) {
        viewModelScope.launch {
            val statsList = repository.allContacts.first()
            val contact = statsList.find { it.id == contactId } ?: return@launch
            val callsCount = repository.getCallsInCurrentPeriod(contact)
            val status = contact.getCalculatedStatus(callsCount)
            val daysSince = if (contact.lastCalledTimestamp > 0L) {
                (System.currentTimeMillis() - contact.lastCalledTimestamp) / (24 * 60 * 60 * 1000L)
            } else null

            openContactDetails(ContactWithStats(contact, callsCount, status, daysSince))
        }
    }

    fun closeContactDetails() {
        historyJob?.cancel()
        _selectedContactForDetails.value = null
        _contactCallHistory.value = emptyList()
    }

    fun openAddContactSheet() {
        _contactToEdit.value = null
        _showAddEditSheet.value = true
    }

    fun openEditContactSheet(contact: TrackedContact) {
        _contactToEdit.value = contact
        _showAddEditSheet.value = true
    }

    fun closeAddEditSheet() {
        _showAddEditSheet.value = false
        _contactToEdit.value = null
    }

    fun saveContact(
        id: Long = 0,
        name: String,
        phone: String,
        relationship: String,
        targetCount: Int,
        frequencyPeriod: String,
        notes: String,
        colorHex: String,
        pinned: Boolean,
        photoUri: String = "",
        priorityWeight: Int = 3
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val contactToSave = if (id > 0) {
                val existing = repository.getContactByIdSync(id)
                existing?.copy(
                    name = name,
                    phoneNumber = phone,
                    relationship = relationship,
                    targetCount = targetCount,
                    frequencyPeriod = frequencyPeriod,
                    notes = notes,
                    avatarColorHex = colorHex,
                    pinned = pinned,
                    photoUri = photoUri,
                    priorityWeight = priorityWeight
                ) ?: TrackedContact(
                    id = id,
                    name = name,
                    phoneNumber = phone,
                    relationship = relationship,
                    targetCount = targetCount,
                    frequencyPeriod = frequencyPeriod,
                    notes = notes,
                    avatarColorHex = colorHex,
                    pinned = pinned,
                    photoUri = photoUri,
                    priorityWeight = priorityWeight
                )
            } else {
                TrackedContact(
                    name = name,
                    phoneNumber = phone,
                    relationship = relationship,
                    targetCount = targetCount,
                    frequencyPeriod = frequencyPeriod,
                    notes = notes,
                    avatarColorHex = colorHex,
                    pinned = pinned,
                    photoUri = photoUri,
                    priorityWeight = priorityWeight
                )
            }
            repository.insertContact(contactToSave)
            _showAddEditSheet.value = false
        }
    }

    fun deleteContact(contact: TrackedContact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteContact(contact)
            if (_selectedContactForDetails.value?.contact?.id == contact.id) {
                closeContactDetails()
            }
        }
    }

    fun togglePin(contact: TrackedContact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateContact(contact.copy(pinned = !contact.pinned))
        }
    }

    fun updateContactColor(contact: TrackedContact, newColorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateContact(contact.copy(avatarColorHex = newColorHex))
        }
    }

    // Backup/restore lives in BackupManager itself (which dispatches its own I/O), and
    // is invoked directly from SuperAppSettingsScreen — no wrapper needed here.

    /**
     * Manually logs a call the user made outside the app (e.g. dialed straight from
     * their phone app). Uses a fixed, sensible default duration since we have no way
     * of knowing the real one — this is a deliberate manual log entry, not a guess.
     */
    fun logManualCall(contact: TrackedContact) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            repository.logCallForContact(
                contactId = contact.id,
                timestamp = now,
                durationSeconds = MANUAL_CALL_DEFAULT_DURATION_SECONDS,
                callType = "OUTGOING",
                notes = "Manually logged call"
            )
            _syncMessage.value = "Logged a call with ${contact.name}"
        }
    }
}
