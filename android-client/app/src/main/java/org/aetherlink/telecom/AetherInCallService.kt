package org.aetherlink.telecom

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService
import java.util.concurrent.ConcurrentHashMap

class AetherInCallService : InCallService() {

    companion object {
        private const val TAG = "AetherInCallService"
        private val activeCalls = ConcurrentHashMap<String, Call>()

        fun handleRemoteAction(callId: String, action: String) {
            val call = activeCalls[callId] ?: run {
                Log.w(TAG, "No active call found with id: $callId")
                return
            }

            when (action) {
                "answer" -> {
                    call.answer(0)
                    Log.i(TAG, "Answered call via remote Mac command: $callId")
                }
                "decline", "hangup" -> {
                    call.disconnect()
                    Log.i(TAG, "Disconnected call via remote Mac command: $callId")
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callId = call.hashCode().toString()
        activeCalls[callId] = call

        val details = call.details
        val callerHandle = details.handle?.schemeSpecificPart ?: "Bilinmeyen Numara"
        val callerName = details.callerDisplayName ?: callerHandle

        val payload = JsonObject().apply {
            addProperty("callId", callId)
            addProperty("appType", "cellular")
            addProperty("callerName", callerName)
            addProperty("phoneNumber", callerHandle)
            addProperty("timestamp", System.currentTimeMillis().toDouble())
            addProperty("hasVideo", details.hasProperty(Call.Details.PROPERTY_WIFI))
        }

        AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
        Log.i(TAG, "Incoming call added and relayed to Mac: $callerName ($callId)")

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call?, state: Int) {
                super.onStateChanged(call, state)
                if (state == Call.STATE_DISCONNECTED) {
                    activeCalls.remove(callId)
                    val dropPayload = JsonObject().apply {
                        addProperty("callId", callId)
                        addProperty("action", "hangup")
                        addProperty("timestamp", System.currentTimeMillis().toDouble())
                    }
                    AetherCoreService.instance?.sendMessage("CALL_ACTION", dropPayload)
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val callId = call.hashCode().toString()
        activeCalls.remove(callId)
        Log.i(TAG, "Call removed: $callId")
    }
}
