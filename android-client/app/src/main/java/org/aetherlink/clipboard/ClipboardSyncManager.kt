package org.aetherlink.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.aetherlink.service.AetherCoreService
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ClipboardSyncManager {

    private const val TAG = "ClipboardSyncManager"
    private var clipboardManager: ClipboardManager? = null
    private var appContext: Context? = null
    private var lastSyncedHash: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastCheckTime = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        try {
            clipboardManager?.addPrimaryClipChangedListener {
                handleLocalClipChanged()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error adding primary clip listener: ${e.message}")
        }
    }

    private fun handleLocalClipChanged() {
        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return

        val item = clip.getItemAt(0)
        
        // 1. Check for Image URI
        val uri = item.uri
        if (uri != null && appContext != null) {
            val mimeType = appContext?.contentResolver?.getType(uri) ?: ""
            if (mimeType.startsWith("image/")) {
                syncLocalImage(uri, mimeType)
                return
            }
        }

        // 2. Fallback to Plain Text
        val text = item.text?.toString() ?: return
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
        Log.d(TAG, "Synced Android foreground clipboard to Mac: ${text.take(20)}...")
    }

    private fun syncLocalImage(uri: Uri, mimeType: String) {
        val context = appContext ?: return
        scope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.use { it.readBytes() }
                
                // Limit to 10MB to maintain zero-lag directive
                if (bytes.isEmpty() || bytes.size > 10_000_000) return@launch
                
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val hash = sha256(base64)
                if (hash == lastSyncedHash) return@launch
                lastSyncedHash = hash

                val payload = JsonObject().apply {
                    addProperty("contentType", mimeType)
                    addProperty("data", base64)
                    addProperty("sha256Hash", hash)
                    addProperty("timestamp", System.currentTimeMillis().toDouble())
                    addProperty("sourceDevice", "android")
                }

                AetherCoreService.instance?.sendMessage("CLIPBOARD_SYNC", payload)
                Log.d(TAG, "Synced Android image clipboard to Mac (${bytes.size} bytes, $mimeType)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync image clipboard: ${e.message}")
            }
        }
    }

    fun checkAndSyncClipboard(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < 200) return
        lastCheckTime = now

        mainHandler.post {
            try {
                val cm = clipboardManager ?: (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                val clip = cm?.primaryClip ?: return@post
                if (clip.itemCount == 0) return@post

                val item = clip.getItemAt(0)
                val uri = item.uri
                if (uri != null) {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    if (mime.startsWith("image/")) {
                        syncLocalImage(uri, mime)
                        return@post
                    }
                }

                val text = item.text?.toString() ?: return@post
                if (text.isEmpty()) return@post

                val hash = sha256(text)
                if (hash == lastSyncedHash) return@post

                lastSyncedHash = hash

                val payload = JsonObject().apply {
                    addProperty("contentType", "text/plain")
                    addProperty("data", text)
                    addProperty("sha256Hash", hash)
                    addProperty("timestamp", System.currentTimeMillis().toDouble())
                    addProperty("sourceDevice", "android")
                }

                scope.launch {
                    AetherCoreService.instance?.sendMessage("CLIPBOARD_SYNC", payload)
                }
                Log.i(TAG, "Accessibility captured background copy -> Mac: ${text.take(25)}...")
            } catch (e: Exception) {
                Log.w(TAG, "checkAndSyncClipboard error: ${e.message}")
            }
        }
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

    fun writeImageToClipboard(context: Context, base64Data: String, hash: String) {
        if (hash == lastSyncedHash) return
        lastSyncedHash = hash

        scope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val cacheDir = File(context.cacheDir, "clipboard_sync")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val imageFile = File(cacheDir, "synced_image.png")
                FileOutputStream(imageFile).use { it.write(bytes) }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )

                withContext(Dispatchers.Main) {
                    val clip = ClipData.newUri(context.contentResolver, "AetherLink Image", uri)
                    clipboardManager?.setPrimaryClip(clip)
                    Log.d(TAG, "Applied remote Mac image clipboard to Android: ${bytes.size} bytes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not apply remote image clipboard: ${e.message}", e)
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
