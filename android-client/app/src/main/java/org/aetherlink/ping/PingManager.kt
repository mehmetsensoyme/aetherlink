package org.aetherlink.ping

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.aetherlink.service.AetherCoreService

object PingManager {
    private const val TAG = "PingManager"

    private val _pingLatencyMs = MutableStateFlow<Double?>(null)
    val pingLatencyMs: StateFlow<Double?> = _pingLatencyMs

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging

    fun sendPing() {
        if (_isPinging.value) return
        _isPinging.value = true
        val now = System.currentTimeMillis().toDouble()
        val payload = JsonObject().apply {
            addProperty("message", "Ping from Android")
            addProperty("clientTimestamp", now)
        }
        AetherCoreService.currentInstance?.sendMessage("PING", payload)
        Log.i(TAG, "Sent PING to macOS")
    }

    fun handlePong(payload: JsonObject) {
        _isPinging.value = false
        val clientTimestamp = payload.get("clientTimestamp")?.asDouble ?: return
        val now = System.currentTimeMillis().toDouble()
        val rtt = (now - clientTimestamp).coerceAtLeast(1.0)
        _pingLatencyMs.value = rtt
        Log.i(TAG, "Received PONG from macOS. Round-trip latency: $rtt ms")
    }

    fun handleIncomingPing(payload: JsonObject) {
        val clientTimestamp = payload.get("clientTimestamp")?.asDouble ?: System.currentTimeMillis().toDouble()
        val pongPayload = JsonObject().apply {
            addProperty("message", "PONG from Android")
            addProperty("clientTimestamp", clientTimestamp)
            addProperty("serverTimestamp", System.currentTimeMillis().toDouble())
        }
        AetherCoreService.currentInstance?.sendMessage("PONG", pongPayload)
        Log.i(TAG, "Replied with PONG to macOS")
    }
}
