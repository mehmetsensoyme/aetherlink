package org.aetherlink.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService
import java.security.MessageDigest

object ClipboardSyncManager {

    private const val TAG = "ClipboardSyncManager"
    private var clipboardManager: ClipboardManager? = null
    private var lastSyncedHash: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener {
            handleLocalClipChanged()
        }
    }

    private fun handleLocalClipChanged() {
        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0).text?.toString() ?: return
        if (text.isEmpty()) return

        val hash = sha256(text)
        if (hash == lastSyncedHash) return // Avoid loopback echo

        lastSyncedHash = hash

        val payload = JsonObject().apply {
            addProperty("contentType", "text/plain")
            addProperty("data", text)
            addProperty("sha256Hash", hash)
            addProperty("timestamp", System.currentTimeMillis().toDouble())
            addProperty("sourceDevice", "android")
        }

        AetherCoreService.instance?.sendMessage("CLIPBOARD_SYNC", payload)
        Log.d(TAG, "Synced Android clipboard to Mac: ${text.take(20)}...")
    }

    fun writeToClipboard(text: String, hash: String) {
        if (hash == lastSyncedHash) return
        lastSyncedHash = hash

        mainHandler.post {
            try {
                val clip = ClipData.newPlainText("AetherLink Sync", text)
                clipboardManager?.setPrimaryClip(clip)
                Log.d(TAG, "Applied remote Mac clipboard to Android: ${text.take(20)}...")
            } catch (e: Exception) {
                Log.w(TAG, "Could not apply remote clipboard: ${e.message}")
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
