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

        fun onCallLogChanged(context: Context) {
            val currentCallId = activeCallId ?: return
            if (isCurrentCallOutgoing && (resolvedContactName == null || resolvedContactName == "Giden Arama" || resolvedContactName == ContactResolver.UNKNOWN_NUMBER)) {
                val latest = ContactResolver.getLatestOutgoingCall(context)
                if (latest != null && latest.contactName != ContactResolver.UNKNOWN_NUMBER) {
                    resolvedContactName = latest.contactName
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
        
        Log.i(TAG, "Phone state changed: $stateStr (previous: $lastState, incoming: $rawIncomingNumber)")

        if (stateStr == lastState) return
        val previousState = lastState
        lastState = stateStr

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming Call Ringing
                isCurrentCallOutgoing = false
                val resolved = ContactResolver.resolve(context, rawIncomingNumber)
                val callId = System.currentTimeMillis().toString()
                activeCallId = callId
                resolvedContactName = resolved.contactName

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
                } else if (previousState == TelephonyManager.EXTRA_STATE_IDLE) {
                    // Outgoing call was initiated
                    isCurrentCallOutgoing = true
                    val callId = System.currentTimeMillis().toString()
                    activeCallId = callId

                    val rawTarget = intent.getStringExtra("extra_outgoing_number") ?: lastOutgoingNumber
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
                activeCallId = null
                lastOutgoingNumber = null
                isCurrentCallOutgoing = false
                resolvedContactName = null
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
