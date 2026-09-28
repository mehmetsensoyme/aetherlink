package org.aetherlink.lock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.aetherlink.service.AetherAccessibilityService
import org.aetherlink.service.AetherCoreService

object RemoteLockManager {
    private const val TAG = "RemoteLockManager"

    private val _isLockingMac = MutableStateFlow(false)
    val isLockingMac: StateFlow<Boolean> = _isLockingMac

    fun lockMac() {
        Log.i(TAG, "Sending LOCK_MAC to macOS...")
        val payload = JsonObject().apply {
            addProperty("action", "lock")
            addProperty("sourceDevice", "android")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("LOCK_MAC", payload)
    }

    fun handleIncomingLockPhone(context: Context) {
        Log.i(TAG, "Handling incoming LOCK_PHONE from Mac...")
        var locked = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val acc = AetherAccessibilityService.instance
            if (acc != null) {
                locked = acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                Log.i(TAG, "Locked phone screen via Accessibility Service: $locked")
            } else {
                Log.w(TAG, "AetherAccessibilityService not active to lock screen directly")
            }
        }

        val resultPayload = JsonObject().apply {
            addProperty("status", if (locked) "success" else "fallback")
            addProperty("message", if (locked) "Telefon ekranı kilitlendi" else "Erişilebilirlik izni bekleniyor")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("LOCK_PHONE_RESULT", resultPayload)
    }

    fun handleLockMacResult(payload: JsonObject) {
        val status = payload.get("status")?.asString ?: "success"
        val message = payload.get("message")?.asString ?: "Mac ekranı kilitlendi"
        Log.i(TAG, "LOCK_MAC result: $status ($message)")
    }
}
