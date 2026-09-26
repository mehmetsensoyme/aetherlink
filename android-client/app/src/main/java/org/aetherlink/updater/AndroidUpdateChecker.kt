package org.aetherlink.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String
)

object AndroidUpdateChecker {

    private const val TAG = "AndroidUpdateChecker"
    const val CURRENT_VERSION = "1.0.0"
    private const val REPO_OWNER = "mehmetsensoyme"
    private const val REPO_NAME = "aetherlink"

    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "AetherLink-Android/$CURRENT_VERSION")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateInfo(false, CURRENT_VERSION, CURRENT_VERSION, "", "", "")
            }

            val body = response.body?.string() ?: return@withContext UpdateInfo(false, CURRENT_VERSION, CURRENT_VERSION, "", "", "")
            val json = Gson().fromJson(body, JsonObject::class.java)

            val tagName = json.get("tag_name")?.asString?.removePrefix("v") ?: CURRENT_VERSION
            val title = json.get("name")?.asString ?: "Yeni Sürüm"
            val changelog = json.get("body")?.asString ?: "Bu sürüm için açıklama eklenmedi."
            val htmlUrl = json.get("html_url")?.asString ?: ""

            var apkUrl = htmlUrl
            val assets = json.getAsJsonArray("assets")
            if (assets != null) {
                for (elem in assets) {
                    val asset = elem.asJsonObject
                    val name = asset.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.get("browser_download_url")?.asString ?: htmlUrl
                        break
                    }
                }
            }

            val isNewer = compareSemVer(CURRENT_VERSION, tagName) > 0

            UpdateInfo(
                hasUpdate = isNewer,
                currentVersion = CURRENT_VERSION,
                latestVersion = tagName,
                title = title,
                changelog = changelog,
                downloadUrl = apkUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            UpdateInfo(false, CURRENT_VERSION, CURRENT_VERSION, "", "", "")
        }
    }

    fun downloadApk(context: Context, downloadUrl: String, version: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("AetherLink v$version İndiriliyor")
                .setDescription("Yeni AetherLink sürümü indiriliyor...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AetherLink-v$version.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Download enqueue error: ${e.message}")
        }
    }

    private fun compareSemVer(v1: String, v2: String): Int {
        val parts1 = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p2 > p1) return 1
            if (p2 < p1) return -1
        }
        return 0
    }
}
