package org.aetherlink.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.aetherlink.clipboard.ClipboardSyncManager

/**
 * AetherAccessibilityService enables:
 * 1. Background Universal Clipboard synchronization on Android 10-16.
 * 2. Remote Screen Lock (GLOBAL_ACTION_LOCK_SCREEN).
 */
class AetherAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AetherAccessibility"
        var instance: AetherAccessibilityService? = null
            private set
    }

    private var lastEventTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AetherAccessibilityService connected and ready for universal clipboard & lock.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val type = event.eventType
        // Only react to selection changes, clicks, or window state changes where copy actions occur
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val now = System.currentTimeMillis()
            if (now - lastEventTime > 200) {
                lastEventTime = now
                ClipboardSyncManager.checkAndSyncClipboard(this)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AetherAccessibilityService interrupted")
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }
}
