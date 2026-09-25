package com.dailytracker.app.miniapps.dataautoconfirm

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Taps "Turn on" in SystemUI's "Turn on mobile data?" dialog.
 *
 * SystemUI always draws the dialog before we hear about it, so the goal is to
 * shrink the visible window to a frame or two: no event batching
 * (notificationTimeout=0), SystemUI-only events, and we read the dialog from
 * the event's own window instead of waiting for rootInActiveWindow to catch up.
 */
class DataConfirmService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != SYSTEMUI) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Fast path: the window the event came from.
                val eventRoot = event.source?.window?.root
                if (eventRoot != null && tryConfirm(eventRoot)) return
                // Fallback: any SystemUI window on screen.
                for (window in windows) {
                    val root = window.root ?: continue
                    if (root.packageName == SYSTEMUI && tryConfirm(root)) return
                }
            }
        }
    }

    private fun tryConfirm(root: AccessibilityNodeInfo): Boolean {
        if (root.findAccessibilityNodeInfosByText(DIALOG_MARKER).isEmpty()) return false
        for (node in root.findAccessibilityNodeInfosByText(BUTTON_TEXT)) {
            if (!node.text?.toString().equals(BUTTON_TEXT, ignoreCase = true)) continue
            val target = clickableSelfOrAncestor(node) ?: continue
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            val n = current ?: return null
            if (n.isClickable) return n
            current = n.parent
        }
        return null
    }

    override fun onInterrupt() {}

    companion object {
        private const val SYSTEMUI = "com.android.systemui"
        private const val DIALOG_MARKER = "mobile data"
        private const val BUTTON_TEXT = "Turn on"

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val self = ComponentName(context, DataConfirmService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }
}
