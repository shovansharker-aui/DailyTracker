package com.dailytracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.dailytracker.app.MainActivity
import com.dailytracker.app.R
import com.dailytracker.app.data.ContactStatus
import com.dailytracker.app.data.KinKeepDatabase
import com.dailytracker.app.ui.ContactWithStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KinKeepWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, KinKeepWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = KinKeepDatabase.getDatabase(context)
                    val dao = db.contactDao()
                    val contacts = dao.getAllContactsList()
                    val now = System.currentTimeMillis()

                    val contactsWithStats = contacts.map { contact ->
                        val periodStart = com.dailytracker.app.data.CallLogScanner.getStartOfPeriodTimestamp(contact.getPeriodEnum(), now)
                        val records = dao.getCallRecordsSince(contact.id, periodStart)
                        val callsInPeriod = records.size
                        val status = contact.getCalculatedStatus(callsInPeriod)
                        val daysSince = if (contact.lastCalledTimestamp > 0L) {
                            (now - contact.lastCalledTimestamp) / (24 * 60 * 60 * 1000L)
                        } else null
                        ContactWithStats(contact, callsInPeriod, status, daysSince)
                    }

                    val top3 = contactsWithStats
                        .filter {
                            it.status == ContactStatus.OVERDUE ||
                            it.status == ContactStatus.DUE_SOON ||
                            it.status == ContactStatus.NEVER_CALLED
                        }
                        .sortedWith(
                            compareByDescending<ContactWithStats> {
                                it.status == ContactStatus.OVERDUE || it.status == ContactStatus.NEVER_CALLED
                            }.thenByDescending { it.daysSinceLastCall ?: 9999L }
                        )
                        .take(3)

                    val views = RemoteViews(context.packageName, R.layout.widget_kinkeep)

                    // Header tap intent
                    val headerIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val headerPendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        headerIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_header, headerPendingIntent)

                    val rowIds = listOf(R.id.widget_contact_row_1, R.id.widget_contact_row_2, R.id.widget_contact_row_3)
                    val nameIds = listOf(R.id.widget_contact_name_1, R.id.widget_contact_name_2, R.id.widget_contact_name_3)
                    val daysIds = listOf(R.id.widget_contact_days_1, R.id.widget_contact_days_2, R.id.widget_contact_days_3)
                    val statusIds = listOf(R.id.widget_contact_status_1, R.id.widget_contact_status_2, R.id.widget_contact_status_3)

                    if (top3.isEmpty()) {
                        views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                        for (rowId in rowIds) {
                            views.setViewVisibility(rowId, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                        rowIds.forEachIndexed { index, rowId ->
                            if (index < top3.size) {
                                val item = top3[index]
                                views.setViewVisibility(rowId, View.VISIBLE)
                                views.setTextViewText(nameIds[index], item.contact.name)

                                val daysText = item.daysSinceLastCall?.let { d ->
                                    if (d == 0L) "Last called today"
                                    else if (d == 1L) "Last called yesterday"
                                    else "Last called $d days ago"
                                } ?: "Never called yet"
                                views.setTextViewText(daysIds[index], daysText)

                                val statusLabel = when (item.status) {
                                    ContactStatus.OVERDUE, ContactStatus.NEVER_CALLED -> "Overdue"
                                    ContactStatus.DUE_SOON -> "Due Soon"
                                    else -> "On Track"
                                }
                                views.setTextViewText(statusIds[index], statusLabel)

                                // Contact tap intent -> launch MainActivity with contact_id
                                val contactIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra("contact_id", item.contact.id)
                                }
                                val contactPendingIntent = PendingIntent.getActivity(
                                    context,
                                    (item.contact.id + 5000).toInt(),
                                    contactIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                views.setOnClickPendingIntent(rowId, contactPendingIntent)
                            } else {
                                views.setViewVisibility(rowId, View.GONE)
                            }
                        }
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
