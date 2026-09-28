package org.aetherlink.share

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import org.aetherlink.AetherLinkApplication
import org.aetherlink.service.AetherCoreService
import java.io.File
import java.io.FileOutputStream

object AetherShareManager {
    private const val TAG = "AetherShareManager"
    private const val NOTIF_ID_BASE = 9900

    fun shareUrl(url: String) {
        val payload = JsonObject().apply {
            addProperty("url", url.trim())
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("SHARE_URL", payload)
        Log.i(TAG, "Sent SHARE_URL: $url")
    }

    fun shareText(text: String) {
        val payload = JsonObject().apply {
            addProperty("text", text)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("SHARE_TEXT", payload)
        Log.i(TAG, "Sent SHARE_TEXT: $text")
    }

    fun shareFile(context: Context, uri: Uri) {
        try {
            var fileName = "paylasilan_dosya"
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex) ?: fileName
                    }
                }
            }

            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val bytes = inputStream.readBytes()
            inputStream.close()

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val payload = JsonObject().apply {
                addProperty("fileName", fileName)
                addProperty("base64Data", base64)
                addProperty("fileSize", bytes.size)
                addProperty("timestamp", System.currentTimeMillis())
            }

            AetherCoreService.currentInstance?.sendMessage("SHARE_FILE", payload)
            Log.i(TAG, "Sent SHARE_FILE: $fileName (${bytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing file: ${e.message}", e)
        }
    }

    fun handleIncomingFile(context: Context, fileName: String, base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)

            FileOutputStream(destFile).use { it.write(bytes) }
            Log.i(TAG, "Saved incoming file: ${destFile.absolutePath} (${bytes.size} bytes)")

            // Show system notification with open intent
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, context.contentResolver.getType(contentUri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, AetherLinkApplication.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("📥 Mac'ten Dosya Alındı")
                .setContentText("$fileName indirildi. Açmak için dokunun.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID_BASE + (0..999).random(), notif)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving incoming file: ${e.message}", e)
        }
    }
}
