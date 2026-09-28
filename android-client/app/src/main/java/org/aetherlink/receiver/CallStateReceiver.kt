package org.aetherlink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.aetherlink.service.AetherCoreService
import org.aetherlink.service.AetherNotificationListener
import org.aetherlink.telecom.ContactResolver
import org.aetherlink.telecom.ResolvedContact

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var lastOutgoingNumber: String? = null
        private var activeCallId: String? = null
        private var isCurrentCallOutgoing = false
        private var resolvedContactName: String? = null
        private var currentPhoneNumber: String? = null

        /**
         * Called when AetherNotificationListener intercepts a dialer notification (e.g. from Samsung One UI).
         * Supplies the true contact name and clean phone number directly from the native Phone app.
         */
        fun updateCallInfoFromNotification(context: Context, contactName: String, phoneNumber: String) {
            val callId = activeCallId ?: run {
                val newId = System.currentTimeMillis().toString()
                activeCallId = newId
                newId
            }

            val isBetterName = !contactName.isBlank() &&
                    contactName != ContactResolver.UNKNOWN_NUMBER &&
                    contactName != "Gelen Arama" &&
                    contactName != "Giden Arama"

            val isNameChanged = contactName != resolvedContactName
            val isPhoneChanged = phoneNumber != currentPhoneNumber && !phoneNumber.isBlank() && phoneNumber != ContactResolver.UNKNOWN_NUMBER

            if (isBetterName || isNameChanged || isPhoneChanged) {
                if (isBetterName) {
                    resolvedContactName = contactName
                }
                if (!phoneNumber.isBlank() && phoneNumber != ContactResolver.UNKNOWN_NUMBER) {
                    currentPhoneNumber = phoneNumber
                }

                val finalName = resolvedContactName ?: contactName
                val finalNumber = currentPhoneNumber ?: phoneNumber

                Log.i(TAG, "Updating call info on Mac from notification: '$finalName' ($finalNumber)")
                val payload = JsonObject().apply {
                    addProperty("callId", callId)
                    addProperty("appType", "cellular")
                    addProperty("callerName", finalName)
                    addProperty("contact_name", finalName)
                    addProperty("phoneNumber", finalNumber)
                    addProperty("timestamp", System.currentTimeMillis().toDouble())
                    addProperty("hasVideo", false)
                    addProperty("direction", if (isCurrentCallOutgoing) "outgoing" else "incoming")
                }
                AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
            }
        }

        fun onCallLogChanged(context: Context) {
            val currentCallId = activeCallId ?: return
            if (isCurrentCallOutgoing && (resolvedContactName == null || resolvedContactName == "Giden Arama" || resolvedContactName == ContactResolver.UNKNOWN_NUMBER)) {
                val latest = ContactResolver.getLatestOutgoingCall(context)
                if (latest != null && latest.contactName != ContactResolver.UNKNOWN_NUMBER) {
                    resolvedContactName = latest.contactName
                    currentPhoneNumber = latest.phoneNumber
                    Log.i(TAG, "CallLog observer resolved outgoing call: ${latest.contactName} (${latest.phoneNumber})")
                    val payload = JsonObject().apply {
                        addProperty("callId", currentCallId)
                        addProperty("appType", "cellular")
                        addProperty("callerName", latest.contactName)
                        addProperty("contact_name", if (latest.isKnown) latest.contactName else null)
                        addProperty("phoneNumber", latest.phoneNumber)
                        addProperty("timestamp", System.currentTimeMillis().toDouble())
                        addProperty("hasVideo", false)
                        addProperty("direction", "outgoing")
                    }
                    AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
                }
            }
        }

        fun handleStateChange(context: Context, stateStr: String, rawIncomingNumber: String?) {
            Log.i(TAG, "Phone state transition: $stateStr (previous: $lastState, incoming: $rawIncomingNumber)")
            if (stateStr == lastState) return
            val previousState = lastState
            lastState = stateStr

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Incoming Call Ringing
                    isCurrentCallOutgoing = false
                    val callId = activeCallId ?: System.currentTimeMillis().toString()
                    activeCallId = callId

                    val resolved = ContactResolver.resolve(context, rawIncomingNumber)
                    resolvedContactName = resolved.contactName
                    currentPhoneNumber = resolved.phoneNumber

                    val payload = JsonObject().apply {
                        addProperty("callId", callId)
                        addProperty("appType", "cellular")
                        addProperty("callerName", resolved.contactName)
                        addProperty("contact_name", if (resolved.isKnown) resolved.contactName else null)
                        addProperty("phoneNumber", resolved.phoneNumber)
                        addProperty("timestamp", System.currentTimeMillis().toDouble())
                        addProperty("hasVideo", false)
                        addProperty("direction", "incoming")
                    }
                    AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
                    Log.i(TAG, "Relayed RINGING incoming call to Mac: ${resolved.contactName} (${resolved.phoneNumber})")
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (previousState == TelephonyManager.EXTRA_STATE_RINGING) {
                        // Incoming call was answered
                        val callId = activeCallId ?: System.currentTimeMillis().toString()
                        val answeredPayload = JsonObject().apply {
                            addProperty("callId", callId)
                            addProperty("action", "answered")
                            addProperty("timestamp", System.currentTimeMillis().toDouble())
                        }
                        AetherCoreService.instance?.sendMessage("CALL_ACTION", answeredPayload)
                        Log.i(TAG, "Relayed answered call state to Mac: $callId")
                        org.aetherlink.audio.CallAudioRelayManager.start(context)
                    } else if (previousState == TelephonyManager.EXTRA_STATE_IDLE) {
                        // Outgoing call was initiated
                        org.aetherlink.audio.CallAudioRelayManager.start(context)
                        isCurrentCallOutgoing = true
                        val callId = System.currentTimeMillis().toString()
                        activeCallId = callId

                        val rawTarget = rawIncomingNumber ?: lastOutgoingNumber
                        var resolved = if (!rawTarget.isNullOrBlank()) {
                            ContactResolver.resolve(context, rawTarget)
                        } else {
                            ContactResolver.getLatestOutgoingCall(context)
                        }

                        if (resolved == null) {
                            resolved = ResolvedContact(
                                contactName = "Giden Arama",
                                phoneNumber = ContactResolver.UNKNOWN_NUMBER,
                                isKnown = false
                            )
                            scheduleOutgoingCallLookup(context, callId)
                        }

                        resolvedContactName = resolved.contactName
                        currentPhoneNumber = resolved.phoneNumber

                        val payload = JsonObject().apply {
                            addProperty("callId", callId)
                            addProperty("appType", "cellular")
                            addProperty("callerName", resolved.contactName)
                            addProperty("contact_name", if (resolved.isKnown) resolved.contactName else null)
                            addProperty("phoneNumber", resolved.phoneNumber)
                            addProperty("timestamp", System.currentTimeMillis().toDouble())
                            addProperty("hasVideo", false)
                            addProperty("direction", "outgoing")
                        }
                        AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
                        Log.i(TAG, "Relayed OFFHOOK outgoing call to Mac: ${resolved.contactName} (${resolved.phoneNumber})")
                    }
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call terminated / idle
                    val callId = activeCallId ?: ""
                    val dropPayload = JsonObject().apply {
                        addProperty("callId", callId)
                        addProperty("action", "hangup")
                        addProperty("timestamp", System.currentTimeMillis().toDouble())
                    }
                    AetherCoreService.instance?.sendMessage("CALL_ACTION", dropPayload)
                    Log.i(TAG, "Relayed IDLE / call ended to Mac: $callId")

                    org.aetherlink.audio.CallAudioRelayManager.stop(context)
                    activeCallId = null
                    lastOutgoingNumber = null
                    isCurrentCallOutgoing = false
                    resolvedContactName = null
                    currentPhoneNumber = null
                    AetherNotificationListener.clearActiveCallIntents()
                }
            }
        }

        private fun scheduleOutgoingCallLookup(context: Context, callId: String) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val delays = listOf(350L, 800L, 1600L)
                for (delayMs in delays) {
                    delay(delayMs)
                    if (activeCallId != callId) break
                    val latest = ContactResolver.getLatestOutgoingCall(appContext)
                    if (latest != null && latest.contactName != ContactResolver.UNKNOWN_NUMBER) {
                        resolvedContactName = latest.contactName
                        currentPhoneNumber = latest.phoneNumber
                        Log.i(TAG, "Async outgoing lookup succeeded: ${latest.contactName} (${latest.phoneNumber})")
                        val payload = JsonObject().apply {
                            addProperty("callId", callId)
                            addProperty("appType", "cellular")
                            addProperty("callerName", latest.contactName)
                            addProperty("contact_name", if (latest.isKnown) latest.contactName else null)
                            addProperty("phoneNumber", latest.phoneNumber)
                            addProperty("timestamp", System.currentTimeMillis().toDouble())
                            addProperty("hasVideo", false)
                            addProperty("direction", "outgoing")
                        }
                        AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
                        break
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Handle Outgoing Call broadcast (Dialing number capture)
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            if (!dialedNumber.isNullOrBlank()) {
                lastOutgoingNumber = dialedNumber
                Log.i(TAG, "Captured outgoing dialed number: $dialedNumber")
            }
            return
        }

        val isTestPhoneState = (intent.action == "org.aetherlink.test.PHONE_STATE")
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED && !isTestPhoneState) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawIncomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?: intent.getStringExtra("extra_outgoing_number")

        handleStateChange(context, stateStr, rawIncomingNumber)
    }
}
