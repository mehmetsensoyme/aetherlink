package org.aetherlink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Arayan Numara"

        Log.i(TAG, "Phone state changed: $stateStr, number: $incomingNumber")

        if (stateStr == lastState) return
        lastState = stateStr

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val payload = JsonObject().apply {
                    addProperty("callId", System.currentTimeMillis().toString())
                    addProperty("appType", "cellular")
                    addProperty("callerName", incomingNumber)
                    addProperty("phoneNumber", incomingNumber)
                    addProperty("timestamp", System.currentTimeMillis().toDouble())
                    addProperty("hasVideo", false)
                }
                AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
                Log.i(TAG, "Relayed RINGING call to Mac: $incomingNumber")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.i(TAG, "Call in progress / offhook")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val dropPayload = JsonObject().apply {
                    addProperty("action", "hangup")
                    addProperty("timestamp", System.currentTimeMillis().toDouble())
                }
                AetherCoreService.instance?.sendMessage("CALL_ACTION", dropPayload)
                Log.i(TAG, "Call ended / idle relayed to Mac")
            }
        }
    }
}
