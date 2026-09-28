package org.aetherlink.caffeinate

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.aetherlink.service.AetherCoreService

object CaffeinateManager {
    private const val TAG = "CaffeinateManager"

    private val _isCaffeinateActive = MutableStateFlow(false)
    val isCaffeinateActive: StateFlow<Boolean> = _isCaffeinateActive

    fun toggleCaffeinate() {
        val target = !_isCaffeinateActive.value
        _isCaffeinateActive.value = target
        val payload = JsonObject().apply {
            addProperty("isActive", target)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("CAFFEINATE_REQUEST", payload)
        Log.i(TAG, "Sent CAFFEINATE_REQUEST: $target")
    }

    fun handleStatus(isActive: Boolean) {
        _isCaffeinateActive.value = isActive
        Log.i(TAG, "Updated Caffeinate status: $isActive")
    }
}
