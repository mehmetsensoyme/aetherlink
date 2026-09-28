package org.aetherlink.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
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

        @Volatile
        var activeCallAnswerIntent: PendingIntent? = null
            private set

        @Volatile
        var activeCallRejectIntent: PendingIntent? = null
            private set

        @Volatile
        var activeCallKey: String? = null
            private set

        fun clearActiveCallIntents() {
            activeCallAnswerIntent = null
            activeCallRejectIntent = null
            activeCallKey = null
        }

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

        private val CALL_PACKAGES = setOf(
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.android.phone"
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

        // Intercept Phone Call Notifications (Samsung One UI, Pixel, Telecom)
        val isCallNotification = notification.category == Notification.CATEGORY_CALL ||
            pkg in CALL_PACKAGES ||
            extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$CallStyle"

        if (isCallNotification) {
            handleCallNotification(sbn, notification, extras)
            return
        }

        // Check duplicate suppression (e.g. WhatsApp or Telegram is active on Mac)
        if (org.aetherlink.notifications.AetherNotificationFilter.isSuppressed(pkg)) {
            Log.d(TAG, "Suppressed forwarding notification from '$pkg' because it is already active on Mac.")
            return
        }

        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Check for Media Playback (Spotify / Apple Music)
        if (pkg == "com.spotify.music" || pkg == "com.spotify.lite" || pkg == "com.apple.android.music") {
            val trackTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val album = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            
            if (trackTitle.isNotEmpty()) {
                var isPlaying = true
                val actionsCount = NotificationCompat.getActionCount(notification)
                for (i in 0 until actionsCount) {
                    val act = NotificationCompat.getAction(notification, i)
                    val actionTitle = act?.title?.toString()?.lowercase() ?: ""
                    if (actionTitle.contains("play") || actionTitle.contains("oynat") || actionTitle.contains("çal")) {
                        isPlaying = false
                        break
                    }
                }
                
                val mediaPayload = JsonObject().apply {
                    addProperty("packageName", pkg)
                    addProperty("trackTitle", trackTitle)
                    addProperty("artist", artist)
                    addProperty("album", album)
                    addProperty("isPlaying", isPlaying)
                    addProperty("positionMs", 0.0)
                    addProperty("durationMs", 0.0)
                    addProperty("artworkBase64", "")
                }
                AetherCoreService.instance?.sendMessage("MEDIA_UPDATE", mediaPayload)
                Log.i(TAG, "Relayed media track to Mac: [$pkg] $trackTitle - $artist (isPlaying: $isPlaying)")
            }
        }

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

    private fun handleCallNotification(
        sbn: StatusBarNotification,
        notification: Notification,
        extras: Bundle
    ) {
        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        Log.i(TAG, "Detected Call Notification from ${sbn.packageName}: title='$title', text='$text'")

        // 1. Extract Call Action PendingIntents (Answer & Reject/Decline)
        var foundAnswer: PendingIntent? = null
        var foundReject: PendingIntent? = null

        // Android 12+ (API 31+) CallStyle extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val answerPi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable("android.answerIntent", PendingIntent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable("android.answerIntent") as? PendingIntent
                }
                if (answerPi != null) foundAnswer = answerPi

                val declinePi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable("android.declineIntent", PendingIntent::class.java)
                        ?: extras.getParcelable("android.hangUpIntent", PendingIntent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    (extras.get("android.declineIntent") ?: extras.get("android.hangUpIntent")) as? PendingIntent
                }
                if (declinePi != null) foundReject = declinePi
            } catch (e: Exception) {
                Log.w(TAG, "Error reading CallStyle intent extras: ${e.message}")
            }
        }

        // Notification.actions array
        notification.actions?.forEach { act ->
            val actTitle = act.title?.toString()?.lowercase() ?: ""
            val isAnswer = actTitle.contains("yanıt") || actTitle.contains("cevap") ||
                    actTitle.contains("answer") || actTitle.contains("accept") ||
                    actTitle == "aç" || actTitle == "katıl"

            val isReject = actTitle.contains("red") || actTitle.contains("decline") ||
                    actTitle.contains("reject") || actTitle.contains("kapat") ||
                    actTitle.contains("sonlandır") || actTitle.contains("meşgul") ||
                    actTitle.contains("yoksay") || actTitle.contains("dismiss")

            if (isAnswer && foundAnswer == null) {
                foundAnswer = act.actionIntent
                Log.i(TAG, "Found call answer action: ${act.title}")
            }
            if (isReject && foundReject == null) {
                foundReject = act.actionIntent
                Log.i(TAG, "Found call reject action: ${act.title}")
            }
        }

        // NotificationCompat actions fallback
        val actionCount = NotificationCompat.getActionCount(notification)
        for (i in 0 until actionCount) {
            val act = NotificationCompat.getAction(notification, i) ?: continue
            val actTitle = act.title?.toString()?.lowercase() ?: ""
            val isAnswer = actTitle.contains("yanıt") || actTitle.contains("cevap") ||
                    actTitle.contains("answer") || actTitle.contains("accept") || actTitle == "aç"
            val isReject = actTitle.contains("red") || actTitle.contains("decline") ||
                    actTitle.contains("reject") || actTitle.contains("kapat") ||
                    actTitle.contains("sonlandır") || actTitle.contains("meşgul")

            if (isAnswer && foundAnswer == null) {
                foundAnswer = act.actionIntent
            }
            if (isReject && foundReject == null) {
                foundReject = act.actionIntent
            }
        }

        if (foundAnswer != null) activeCallAnswerIntent = foundAnswer
        if (foundReject != null) activeCallRejectIntent = foundReject
        activeCallKey = sbn.key

        // Detect if call is already ONGOING / ANSWERED (e.g. CallStyle ongoing, chronometer, or only reject/hangup button exists)
        val callType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            extras.getInt("android.callType", -1)
        } else -1

        val isOngoingCall = callType == 2 ||
                extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) ||
                (foundAnswer == null && foundReject != null)

        if (isOngoingCall) {
            Log.i(TAG, "Call notification indicates ACTIVE/ANSWERED call (isOngoingCall=true, callType=$callType)")
            org.aetherlink.receiver.CallStateReceiver.notifyCallAnswered(this)
        } else if (title.isNotBlank()) {
            // 2. Ringing incoming call: Resolve Caller Name & Number and update Mac immediately
            val resolved = org.aetherlink.telecom.ContactResolver.resolveFromNotification(this, title, text)
            Log.i(TAG, "Call notification resolved: name='${resolved.contactName}', number='${resolved.phoneNumber}'")
            org.aetherlink.receiver.CallStateReceiver.updateCallInfoFromNotification(
                this,
                resolved.contactName,
                resolved.phoneNumber
            )
        }
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
        androidx.core.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

        try {
            action.actionIntent?.send(this, 0, intent)
            Log.i(TAG, "Successfully dispatched remote inline reply for key: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send pending reply intent: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val pkg = sbn?.packageName ?: return
        val key = sbn.key
        if (key != null) {
            cachedActions.remove(key)
            if (key == activeCallKey) {
                clearActiveCallIntents()
                Log.i(TAG, "Active call notification removed ($key), cleared action intents")
            }
        }
        if (pkg in CALL_PACKAGES) {
            clearActiveCallIntents()
        }
        if (pkg == "com.spotify.music" || pkg == "com.spotify.lite" || pkg == "com.apple.android.music") {
            val mediaPayload = JsonObject().apply {
                addProperty("packageName", pkg)
                addProperty("trackTitle", "")
                addProperty("artist", "")
                addProperty("album", "")
                addProperty("isPlaying", false)
                addProperty("positionMs", 0.0)
                addProperty("durationMs", 0.0)
                addProperty("artworkBase64", "")
            }
            AetherCoreService.instance?.sendMessage("MEDIA_UPDATE", mediaPayload)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearActiveCallIntents()
        instance = null
    }
}
