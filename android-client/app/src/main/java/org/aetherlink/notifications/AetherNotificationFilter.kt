package org.aetherlink.notifications

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * AetherNotificationFilter suppresses phone notifications from being forwarded to the Mac
 * when the corresponding native app (e.g. WhatsApp Desktop, Telegram, Slack) is already
 * running on macOS, avoiding duplicate alerts.
 */
object AetherNotificationFilter {
    private const val TAG = "AetherNotifFilter"

    private val suppressedPackages = ConcurrentHashMap.newKeySet<String>()

    fun setSuppressedPackages(packages: List<String>) {
        suppressedPackages.clear()
        suppressedPackages.addAll(packages)
        Log.i(TAG, "Updated suppressed packages from Mac: $packages")
    }

    fun isSuppressed(packageName: String): Boolean {
        val suppressed = suppressedPackages.contains(packageName)
        if (suppressed) {
            Log.d(TAG, "Notification for '$packageName' suppressed (app is active on Mac)")
        }
        return suppressed
    }

    fun getActiveSuppressedList(): List<String> = suppressedPackages.toList()
}
