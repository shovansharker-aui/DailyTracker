package com.dailytracker.app.util

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WhatsAppCallListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppCallListener"
        private val _whatsappCallEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val whatsappCallEvents = _whatsappCallEvents.asSharedFlow()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        if (sbn.packageName == "com.whatsapp") {
            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            Log.d(TAG, "WhatsApp Notification Posted - Package: ${sbn.packageName}, Title: '$title', Text: '$text'")

            val lowerTitle = title.lowercase()
            val lowerText = text.lowercase()

            val isCallRelated = lowerTitle.contains("calling") ||
                    lowerTitle.contains("ongoing call") ||
                    lowerTitle.contains("missed call") ||
                    lowerText.contains("calling") ||
                    lowerText.contains("ongoing call") ||
                    lowerText.contains("missed call") ||
                    lowerTitle.contains("voice call") ||
                    lowerTitle.contains("video call") ||
                    lowerText.contains("voice call") ||
                    lowerText.contains("video call")

            if (isCallRelated) {
                Log.d(TAG, "WhatsApp Call Detected! Title: '$title', Text: '$text'")
                val contactNameCandidate = title.ifBlank { text }
                _whatsappCallEvents.tryEmit(contactNameCandidate)
            }
        }
    }
}
