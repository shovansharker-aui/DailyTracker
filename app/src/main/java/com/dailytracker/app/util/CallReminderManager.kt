package com.dailytracker.app.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dailytracker.app.MainActivity
import com.dailytracker.app.data.FrequencyPeriod
import com.dailytracker.app.data.TrackedContact
import com.dailytracker.app.ui.ContactWithStats
import java.util.Calendar

class CallReminderManager : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Broadcast received from AlarmManager or boot
        // The ViewModel / App handles live checks, but this receiver can trigger a check
    }

    companion object {
        const val CHANNEL_ID = "kinkeep_call_reminders"
        const val CHANNEL_NAME = "Call Goal Reminders"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for contacts whose call targets are not yet met"
                    enableVibration(true)
                    setSound(soundUri, audioAttributes)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        private data class ReminderTrigger(
            val contact: TrackedContact,
            val message: String,
            val withSound: Boolean,
            val statusTitle: String
        )

        fun checkAndTriggerReminders(
            context: Context,
            contactsWithStats: List<ContactWithStats>,
            now: Long = System.currentTimeMillis()
        ) {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1, Monday=2, ..., Saturday=7
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

            createNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val triggeredReminders = mutableListOf<ReminderTrigger>()

            contactsWithStats.forEach { item ->
                val contact = item.contact

                // Skip if contact's reminder is currently snoozed
                if (contact.snoozedUntil > now) {
                    return@forEach
                }

                val period = contact.getPeriodEnum()
                val callsMade = item.callsInCurrentPeriod
                val target = contact.targetCount

                if (callsMade >= target) {
                    // Target already met!
                    return@forEach
                }

                var shouldNotify = false
                var withSound = false
                var message = ""

                when (period) {
                    FrequencyPeriod.DAY -> {
                        // Daily target check at 08:00 PM (20:00)
                        if (currentHour >= 20) {
                            shouldNotify = true
                            withSound = true
                            message = "Daily goal incomplete: You haven't called ${contact.name} today ($callsMade/$target)."
                        }
                    }
                    FrequencyPeriod.WEEK -> {
                        if (dayOfWeek == Calendar.FRIDAY) {
                            if (currentHour >= 19) {
                                shouldNotify = true
                                withSound = true
                                message = "Friday Evening Alert: You still need to call ${contact.name} ($callsMade/$target calls made)."
                            } else if (currentHour >= 11) {
                                shouldNotify = true
                                withSound = false
                                message = "Friday Reminder: Don't forget to call ${contact.name} before the week ends ($callsMade/$target)."
                            }
                        }
                    }
                    FrequencyPeriod.MONTH -> {
                        if (dayOfMonth >= 25 && currentHour >= 19) {
                            shouldNotify = true
                            withSound = true
                            message = "Monthly Target Alert: Only a few days left to meet call target for ${contact.name} ($callsMade/$target)."
                        } else if (dayOfMonth >= 20 && currentHour >= 20) {
                            shouldNotify = true
                            withSound = false
                            message = "Monthly Checkup: Don't forget your monthly goal for ${contact.name} ($callsMade/$target)."
                        }
                    }
                }

                if (shouldNotify) {
                    triggeredReminders.add(
                        ReminderTrigger(
                            contact = contact,
                            message = message,
                            withSound = withSound,
                            statusTitle = item.status.title
                        )
                    )
                }
            }

            if (triggeredReminders.size == 1) {
                val single = triggeredReminders.first()
                sendNotification(context, notificationManager, single.contact, single.message, single.withSound)
            } else if (triggeredReminders.size >= 2) {
                sendGroupedNotification(context, notificationManager, triggeredReminders)
            }
        }

        private fun sendGroupedNotification(
            context: Context,
            notificationManager: NotificationManager,
            reminders: List<ReminderTrigger>
        ) {
            val count = reminders.size
            val title = "$count people you should call"

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                8888,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText("KinKeep Call Reminders")

            reminders.forEach { r ->
                inboxStyle.addLine("${r.contact.name}: ${r.statusTitle}")
            }

            val withSound = reminders.any { it.withSound }
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(title)
                .setContentText("${reminders.first().contact.name} and ${count - 1} others need a call")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (withSound) {
                builder.setSound(soundUri)
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            }

            notificationManager.notify(9000, builder.build())
        }

        private fun sendNotification(
            context: Context,
            notificationManager: NotificationManager,
            contact: TrackedContact,
            message: String,
            withSound: Boolean
        ) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("contact_id", contact.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                contact.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationId = contact.id.toInt() + 1000

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("KinKeep Call Goal: ${contact.name}")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (withSound) {
                builder.setSound(soundUri)
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            }

            notificationManager.notify(notificationId, builder.build())
        }

        fun schedulePeriodicAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, CallReminderManager::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Repeat every 2 hours
                val intervalMs = 2 * 60 * 60 * 1000L
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 60000,
                    intervalMs,
                    pendingIntent
                )
            } catch (e: Exception) {
                android.util.Log.e("CallReminderManager", "Failed to schedule periodic alarm", e)
            }
        }
    }
}
