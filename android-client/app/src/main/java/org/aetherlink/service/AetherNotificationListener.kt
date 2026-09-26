package org.aetherlink.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap

class AetherNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AetherNotifListener"
        var instance: AetherNotificationListener? = null
            private set

        // Whitelisted messaging apps for high-priority mirroring
        private val MESSAGING_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "org.thoughtcrime.securesms", // Signal
            "com.instagram.android",
            "com.discord",
            "com.Slack"
        )
    }

    private val cachedActions = ConcurrentHashMap<String, NotificationCompat.Action>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "AetherNotificationListener initialized.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val pkg = sbn.packageName
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Filter system notifications or ongoing progress bars
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (text.isEmpty() && !MESSAGING_PACKAGES.contains(pkg)) return

        // Extract Inline Reply Action (RemoteInput)
        var replyAction: NotificationCompat.Action? = null
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        for (action in wearableExtender.actions) {
            if (action.remoteInputs != null && action.remoteInputs!!.isNotEmpty()) {
                replyAction = action
                break
            }
        }

        if (replyAction == null) {
            val actions = NotificationCompat.getActionCount(notification)
            for (i in 0 until actions) {
                val action = NotificationCompat.getAction(notification, i)
                if (action?.remoteInputs != null && action.remoteInputs!!.isNotEmpty()) {
                    replyAction = action
                    break
                }
            }
        }

        val canReply = replyAction != null
        if (canReply) {
            cachedActions[sbn.key] = replyAction!!
        }

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

        val payload = JsonObject().apply {
            addProperty("id", sbn.id.toString())
            addProperty("key", sbn.key)
            addProperty("packageName", pkg)
            addProperty("appName", appName)
            addProperty("title", title)
            addProperty("text", text)
            addProperty("timestamp", sbn.postTime.toDouble())
            addProperty("canReply", canReply)
        }

        AetherCoreService.instance?.sendMessage("NOTIFICATION_POSTED", payload)
        Log.d(TAG, "Mirrored notification from $appName ($title): canReply=$canReply")
    }

    fun sendReply(key: String, replyText: String) {
        val action = cachedActions[key] ?: run {
            Log.w(TAG, "No cached reply action found for notification key: $key")
            return
        }

        val remoteInput = action.remoteInputs?.firstOrNull() ?: return
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(remoteInput.resultKey, replyText)
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

        try {
            action.actionIntent.send(this, 0, intent)
            Log.i(TAG, "Successfully dispatched remote inline reply for key: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send pending reply intent: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.key?.let { cachedActions.remove(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
