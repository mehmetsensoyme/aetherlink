package org.aetherlink.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*
import org.aetherlink.AetherLinkApplication
import org.aetherlink.clipboard.ClipboardSyncManager
import org.aetherlink.telecom.AetherInCallService
import org.aetherlink.ui.MainActivity
import java.util.concurrent.TimeUnit

class AetherCoreService : Service() {

    companion object {
        private const val TAG = "AetherCoreService"
        private const val NOTIFICATION_ID = 1001

        var instance: AetherCoreService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, AetherCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var macIpAddress: String = "127.0.0.1" // Local loopback via ADB reverse or Wi-Fi IP

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { dispatchBatteryUpdate(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundWithType()
        registerBatteryMonitoring()
        ClipboardSyncManager.init(this)
        connectToMacWebSocket()
        Log.i(TAG, "AetherCoreService started.")
    }

    private fun startForegroundWithType() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent.createChooser(Intent(this, MainActivity::class.java), null),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, AetherLinkApplication.CHANNEL_CORE_SERVICE)
            .setContentTitle("AetherLink Süreklilik Aktif")
            .setContentText("MacBook ile güvenli yerel bağlantı sürdürülüyor.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ Strict Foreground Service Types
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerBatteryMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun dispatchBatteryUpdate(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100

        val payload = JsonObject().apply {
            addProperty("batteryLevel", batteryPct)
            addProperty("isCharging", isCharging)
            addProperty("powerSaveMode", false)
        }

        sendMessage("BATTERY_UPDATE", payload)
    }

    fun connectToMacWebSocket(ip: String = macIpAddress) {
        this.macIpAddress = ip
        serviceScope.launch {
            try {
                val request = Request.Builder()
                    .url("ws://$macIpAddress:8443")
                    .build()

                webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        isConnected = true
                        Log.i(TAG, "Connected to macOS AetherLink listener at $macIpAddress:8443")
                        org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this@AetherCoreService)
                        startPeriodicTelemetry()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleIncomingMacMessage(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        isConnected = false
                        Log.w(TAG, "WebSocket closing: $reason")
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        isConnected = false
                        Log.w(TAG, "WebSocket connection failed: ${t.message}. Retrying in 5 seconds...")
                        serviceScope.launch {
                            delay(5000)
                            connectToMacWebSocket(macIpAddress)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating WebSocket: ${e.message}")
            }
        }
    }

    private var telemetryJob: Job? = null
    private fun startPeriodicTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            while (isActive && isConnected) {
                delay(30000)
                org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this@AetherCoreService)
            }
        }
    }

    fun disconnect(forget: Boolean = false) {
        val payload = JsonObject().apply {
            addProperty("reason", if (forget) "unpair" else "user_requested")
            addProperty("shouldForget", forget)
            addProperty("timestamp", System.currentTimeMillis())
        }
        sendMessage("DISCONNECT", payload)
        isConnected = false
        telemetryJob?.cancel()
        org.aetherlink.screen.ScreenStreamManager.stopCapture()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        Log.i(TAG, "Disconnected from Mac (forget: $forget)")
    }

    private fun handleIncomingMacMessage(jsonString: String) {
        try {
            val json = gson.fromJson(jsonString, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            val payload = json.getAsJsonObject("payload") ?: return

            when (type) {
                "DEVICE_TELEMETRY_REQUEST" -> {
                    org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this)
                }
                "SCREEN_STREAM_CONTROL" -> {
                    val action = payload.get("action")?.asString ?: ""
                    if (action == "start") {
                        org.aetherlink.screen.ScreenStreamManager.startCapture()
                    } else if (action == "stop") {
                        org.aetherlink.screen.ScreenStreamManager.stopCapture()
                    }
                }
                "DISCONNECT" -> {
                    val shouldForget = payload.get("shouldForget")?.asBoolean ?: false
                    disconnect(shouldForget)
                }
                "CALL_ACTION" -> {
                    val callId = payload.get("callId")?.asString ?: ""
                    val action = payload.get("action")?.asString ?: ""
                    AetherInCallService.handleRemoteAction(callId, action)
                }
                "NOTIFICATION_REPLY" -> {
                    val key = payload.get("notificationKey")?.asString ?: ""
                    val replyText = payload.get("replyText")?.asString ?: ""
                    AetherNotificationListener.instance?.sendReply(key, replyText)
                }
                "CLIPBOARD_SYNC" -> {
                    val text = payload.get("data")?.asString ?: ""
                    val hash = payload.get("sha256Hash")?.asString ?: ""
                    val source = payload.get("sourceDevice")?.asString ?: ""
                    if (source == "macos") {
                        ClipboardSyncManager.writeToClipboard(text, hash)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming Mac payload: ${e.message}")
        }
    }

    fun sendMessage(type: String, payload: JsonObject) {
        val envelope = JsonObject().apply {
            addProperty("type", type)
            add("payload", payload)
        }
        webSocket?.send(envelope.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        webSocket?.close(1000, "Service stopping")
        serviceScope.cancel()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
