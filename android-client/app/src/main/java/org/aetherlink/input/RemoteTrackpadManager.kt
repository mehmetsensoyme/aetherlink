package org.aetherlink.input

import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService

object RemoteTrackpadManager {
    private const val TAG = "RemoteTrackpadManager"

    fun sendMove(dx: Float, dy: Float) {
        val payload = JsonObject().apply {
            addProperty("action", "move")
            addProperty("dx", dx)
            addProperty("dy", dy)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("REMOTE_INPUT", payload)
    }

    fun sendClick() {
        val payload = JsonObject().apply {
            addProperty("action", "click")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("REMOTE_INPUT", payload)
    }

    fun sendRightClick() {
        val payload = JsonObject().apply {
            addProperty("action", "rightClick")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("REMOTE_INPUT", payload)
    }

    fun sendDoubleClick() {
        val payload = JsonObject().apply {
            addProperty("action", "doubleClick")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("REMOTE_INPUT", payload)
    }

    fun sendScroll(dx: Float, dy: Float) {
        val payload = JsonObject().apply {
            addProperty("action", "scroll")
            addProperty("dx", dx)
            addProperty("dy", dy)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("REMOTE_INPUT", payload)
    }

    fun sendKey(key: String) {
        val payload = JsonObject().apply {
            addProperty("action", "key")
            addProperty("key", key)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("REMOTE_INPUT", payload)
    }
}
