package org.aetherlink.presenter

import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService

object PresenterRemoteManager {
    private const val TAG = "PresenterRemoteMgr"

    fun sendNext() {
        sendCommand("next")
    }

    fun sendPrev() {
        sendCommand("prev")
    }

    fun sendFullscreen() {
        sendCommand("fullscreen")
    }

    fun sendExit() {
        sendCommand("exit")
    }

    fun sendBlackScreen() {
        sendCommand("black")
    }

    fun sendWhiteScreen() {
        sendCommand("white")
    }

    private fun sendCommand(action: String) {
        val payload = JsonObject().apply {
            addProperty("action", action)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("PRESENTER_COMMAND", payload)
        Log.i(TAG, "Sent PRESENTER_COMMAND: $action")
    }
}
