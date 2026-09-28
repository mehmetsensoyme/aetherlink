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
        var instance: AetherInCallService? = null

        fun handleRemoteAction(callId: String, action: String) {
            val call = activeCalls[callId] ?: activeCalls.values.firstOrNull() ?: run {
                Log.w(TAG, "No active call found with id: $callId (active count: ${activeCalls.size})")
                return
            }

            when (action) {
                "answer", "ACCEPT_CALL", "accept" -> {
                    call.answer(0)
                    try {
                        instance?.setAudioRoute(android.telecom.CallAudioState.ROUTE_SPEAKER)
                    } catch (_: Exception) {}
                    Log.i(TAG, "Answered call via InCallService: $callId (routed to speaker)")
                }
                "decline", "hangup", "REJECT_CALL", "reject" -> {
                    call.disconnect()
                    Log.i(TAG, "Disconnected call via InCallService: $callId")
                }
            }
        }

        fun setSpeaker(enabled: Boolean) {
            try {
                val target = if (enabled) {
                    android.telecom.CallAudioState.ROUTE_SPEAKER
                } else {
                    android.telecom.CallAudioState.ROUTE_EARPIECE
                }
                instance?.setAudioRoute(target)
                Log.i(TAG, "Applied InCallService.setAudioRoute: $target")
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting CallAudioRoute: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callId = call.hashCode().toString()
        activeCalls[callId] = call

        val details = call.details
        val callerHandle = details.handle?.schemeSpecificPart
        val resolved = ContactResolver.resolve(this, callerHandle)

        val direction = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (details.callDirection == Call.Details.DIRECTION_OUTGOING) "outgoing" else "incoming"
        } else {
            if (call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING) "outgoing" else "incoming"
        }

        val contactName = if (resolved.isKnown) {
            resolved.contactName
        } else {
            details.callerDisplayName ?: resolved.contactName
        }

        val payload = JsonObject().apply {
            addProperty("callId", callId)
            addProperty("appType", "cellular")
            addProperty("callerName", contactName)
            addProperty("contact_name", if (resolved.isKnown) resolved.contactName else null)
            addProperty("phoneNumber", resolved.phoneNumber)
            addProperty("timestamp", System.currentTimeMillis().toDouble())
            addProperty("hasVideo", details.hasProperty(Call.Details.PROPERTY_WIFI))
            addProperty("direction", direction)
        }

        AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
        Log.i(TAG, "Call added and relayed to Mac: $contactName ($callId, direction: $direction)")

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call?, state: Int) {
                super.onStateChanged(call, state)
                if (state == Call.STATE_ACTIVE) {
                    val activePayload = JsonObject().apply {
                        addProperty("callId", callId)
                        addProperty("action", "answered")
                        addProperty("timestamp", System.currentTimeMillis().toDouble())
                    }
                    AetherCoreService.instance?.sendMessage("CALL_ACTION", activePayload)
                } else if (state == Call.STATE_DISCONNECTED) {
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
