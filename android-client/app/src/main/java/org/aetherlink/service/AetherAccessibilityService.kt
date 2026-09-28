package org.aetherlink.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AetherAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AetherAccessibility"
        var instance: AetherAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AetherAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener; no intrusive processing
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
