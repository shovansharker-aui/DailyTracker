package com.dailytracker.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device boot completed. Rescheduling KinKeep call reminders.")
            context?.let { ctx ->
                CallReminderManager.schedulePeriodicAlarm(ctx)
                com.dailytracker.app.miniapps.officetracker.AttendanceReminderReceiver.scheduleDaily9PmReminder(ctx)
            }
        }
    }
}
