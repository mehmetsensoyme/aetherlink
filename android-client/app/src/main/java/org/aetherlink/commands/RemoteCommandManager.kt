package org.aetherlink.commands

import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService

object RemoteCommandManager {
    private const val TAG = "RemoteCommandMgr"

    fun execute(key: String) {
        val payload = JsonObject().apply {
            addProperty("key", key)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("REMOTE_COMMAND", payload)
        Log.i(TAG, "Sent REMOTE_COMMAND: $key")
    }
}
