package com.dailytracker.app.ui

import com.dailytracker.app.data.ContactRepository
import com.dailytracker.app.data.TrackedContact
import kotlinx.coroutines.flow.first

/**
 * Builds the same [ContactWithStats] list used by the KinKeep dashboard, the
 * home-screen widget, and the background call-reminder check, so all three agree on
 * "how many calls has this contact had this period, and what's their status" instead
 * of each recomputing it slightly differently.
 */
suspend fun ContactRepository.getContactsWithStats(now: Long = System.currentTimeMillis()): List<ContactWithStats> {
    val contacts = allContacts.first()
    return contacts.map { contact -> contact.toContactWithStats(this, now) }
}

suspend fun TrackedContact.toContactWithStats(
    repository: ContactRepository,
    now: Long = System.currentTimeMillis()
): ContactWithStats {
    val callsInPeriod = repository.getCallsInCurrentPeriod(this)
    val status = getCalculatedStatus(callsInPeriod, now)
    val daysSince = getDaysSinceLastCall(now)
    return ContactWithStats(this, callsInPeriod, status, daysSince)
}
