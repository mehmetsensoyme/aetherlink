package org.aetherlink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var lastOutgoingNumber: String? = null
        private var activeCallId: String? = null
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

        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawIncomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        
        Log.i(TAG, "Phone state changed: $stateStr (previous: $lastState, incoming: $rawIncomingNumber)")

        if (stateStr == lastState) return
        val previousState = lastState
        lastState = stateStr

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming Call Ringing
                val number = if (!rawIncomingNumber.isNullOrBlank()) rawIncomingNumber else "Bilinmeyen Numara"
                val contactName = getContactName(context, number) ?: number
                val callId = System.currentTimeMillis().toString()
                activeCallId = callId

                val payload = JsonObject().apply {
                    addProperty("callId", callId)
                    addProperty("appType", "cellular")
                    addProperty("callerName", contactName)
                    addProperty("phoneNumber", number)
                    addProperty("timestamp", System.currentTimeMillis().toDouble())
                    addProperty("hasVideo", false)
                    addProperty("direction", "incoming")
                }
                AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
                Log.i(TAG, "Relayed RINGING incoming call to Mac: $contactName ($number)")
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
                    val number = lastOutgoingNumber ?: getLatestOutgoingNumber(context) ?: "Numara Çevriliyor"
                    val contactName = getContactName(context, number) ?: number
                    val callId = System.currentTimeMillis().toString()
                    activeCallId = callId

                    val payload = JsonObject().apply {
                        addProperty("callId", callId)
                        addProperty("appType", "cellular")
                        addProperty("callerName", contactName)
                        addProperty("phoneNumber", number)
                        addProperty("timestamp", System.currentTimeMillis().toDouble())
                        addProperty("hasVideo", false)
                        addProperty("direction", "outgoing")
                    }
                    AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
                    Log.i(TAG, "Relayed OFFHOOK outgoing call to Mac: $contactName ($number)")
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
            }
        }
    }

    private fun getContactName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank() || phoneNumber == "Bilinmeyen Numara" || phoneNumber == "Numara Çevriliyor") {
            return null
        }
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contact lookup failed: ${e.message}")
        }
        return null
    }

    private fun getLatestOutgoingNumber(context: Context): String? {
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    if (numIdx >= 0) return it.getString(numIdx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CallLog lookup failed: ${e.message}")
        }
        return null
    }
}
