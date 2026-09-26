package org.aetherlink.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AndroidUpdateChecker {

    private const val TAG = "AndroidUpdateChecker"
    private const val REPO_OWNER = "mehmetsensoyme"
    private const val REPO_NAME = "aetherlink"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.3.0"
        } catch (e: Exception) {
            "1.3.0"
        }
    }

    suspend fun check(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = getAppVersion(context)
        val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "AetherLink-Android/$currentVersion")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.code == 403) {
                Log.w(TAG, "GitHub API rate limit reached")
                return@withContext UpdateCheckResult.Error("GitHub API erişim sınırına ulaşıldı. Lütfen daha sonra tekrar deneyin.")
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API request failed: HTTP ${response.code}")
                return@withContext UpdateCheckResult.Error("GitHub sunucusuna ulaşılamadı (HTTP ${response.code}).")
            }

            val body = response.body?.string()
                ?: return@withContext UpdateCheckResult.Error("Sunucudan boş yanıt alındı.")

            val json = Gson().fromJson(body, JsonObject::class.java)

            val tagName = json.get("tag_name")?.asString?.removePrefix("v") ?: currentVersion
            val title = json.get("name")?.asString ?: "AetherLink v$tagName"
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

            val isNewer = compareSemVer(currentVersion, tagName) > 0

            val updateInfo = UpdateInfo(
                hasUpdate = isNewer,
                currentVersion = currentVersion,
                latestVersion = tagName,
                title = title,
                changelog = changelog,
                downloadUrl = apkUrl
            )

            if (isNewer) {
                UpdateCheckResult.Available(updateInfo)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            UpdateCheckResult.Error("Güncelleme denetlenemedi: ${e.localizedMessage ?: "Bağlantı hatası"}")
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        version: String,
        onProgress: (Float) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("İndirme başarısız oldu (HTTP ${response.code})")
                }
                return@withContext
            }

            val body = response.body ?: run {
                withContext(Dispatchers.Main) { onError("Dosya içeriği boş.") }
                return@withContext
            }

            val totalBytes = body.contentLength()
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val apkFile = File(downloadsDir, "AetherLink-v$version.apk")

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = totalRead.toFloat() / totalBytes.toFloat()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onSuccess()
                launchPackageInstaller(context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download and install failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError("İndirme sırasında hata oluştu: ${e.localizedMessage}")
            }
        }
    }

    fun launchPackageInstaller(context: Context, apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
        }
    }

    fun downloadApkViaDownloadManager(context: Context, downloadUrl: String, version: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("AetherLink v$version İndiriliyor")
                .setDescription("Yeni AetherLink APK paketi indiriliyor...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AetherLink-v$version.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager enqueue error: ${e.message}")
        }
    }

    private fun compareSemVer(v1: String, v2: String): Int {
        val parts1 = v1.removePrefix("v").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.removePrefix("v").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p2 > p1) return 1
            if (p2 < p1) return -1
        }
        return 0
    }
}
