package org.aetherlink.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

data class MacBatteryData(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isPluggedIn: Boolean = false,
    val statusDescription: String = "Pilde"
)

data class PhoneBatteryData(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isPluggedIn: Boolean = false,
    val temperatureCelsius: Double = 28.0,
    val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN
)

class AetherCoreService : Service() {

    companion object {
        private const val TAG = "AetherCoreService"
        private const val NOTIFICATION_ID = 1001

        var instance: AetherCoreService? = null
            private set

        val macBatteryState = kotlinx.coroutines.flow.MutableStateFlow<MacBatteryData?>(null)
        val phoneBatteryState = kotlinx.coroutines.flow.MutableStateFlow(PhoneBatteryData())

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

    fun startForegroundWithType(includeMediaProjection: Boolean = false) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent.createChooser(Intent(this, MainActivity::class.java), null),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (includeMediaProjection) "AetherLink Canlı Ekran Yansıtma" else "AetherLink Süreklilik Aktif"
        val desc = if (includeMediaProjection) "Telefon ekranı Mac'e canlı olarak aktarılıyor." else "MacBook ile güvenli yerel bağlantı sürdürülüyor."

        val notification: Notification = NotificationCompat.Builder(this, AetherLinkApplication.CHANNEL_CORE_SERVICE)
            .setContentTitle(title)
            .setContentText(desc)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ Strict Foreground Service Types
            val baseType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            val finalType = if (includeMediaProjection) {
                baseType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                baseType
            }
            startForeground(NOTIFICATION_ID, notification, finalType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun startScreenCaptureWithProjection(resultCode: Int, data: Intent) {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mpManager == null) {
                Log.e(TAG, "MediaProjectionManager not available")
                return
            }

            // Must promote foreground service to mediaProjection before calling getMediaProjection on Android 14+
            startForegroundWithType(includeMediaProjection = true)

            val projection = mpManager.getMediaProjection(resultCode, data)
            if (projection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                startForegroundWithType(includeMediaProjection = false)
                return
            }

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    org.aetherlink.screen.ScreenStreamManager.stopCapture()
                    startForegroundWithType(includeMediaProjection = false)
                }
            }, Handler(Looper.getMainLooper()))

            val dm = resources.displayMetrics
            // Capture at smooth 540p or 720p proportional resolution
            val targetWidth = 540
            val targetHeight = (540 * dm.heightPixels) / dm.widthPixels
            org.aetherlink.screen.ScreenStreamManager.setupMediaProjection(
                projection,
                targetWidth,
                targetHeight,
                dm.densityDpi
            )
            Log.i(TAG, "Screen projection started at ${targetWidth}x${targetHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen projection: ${e.message}", e)
            startForegroundWithType(includeMediaProjection = false)
        }
    }

    fun stopScreenCapture() {
        org.aetherlink.screen.ScreenStreamManager.stopCapture()
        startForegroundWithType(includeMediaProjection = false)
    }

    private fun registerBatteryMonitoring() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val sticky = registerReceiver(batteryReceiver, filter)
        if (sticky != null) {
            dispatchBatteryUpdate(sticky)
        }
    }

    private fun dispatchBatteryUpdate(intent: Intent) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280)
        val temp = if (tempRaw > 0) tempRaw / 10.0 else 28.0

        val batteryPct = if (rawLevel >= 0 && scale > 0) ((rawLevel / scale.toFloat()) * 100).toInt() else 100
        val isPluggedIn = plugged != 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                (isPluggedIn && status != BatteryManager.BATTERY_STATUS_DISCHARGING)

        val data = PhoneBatteryData(
            level = batteryPct,
            isCharging = isCharging,
            isPluggedIn = isPluggedIn,
            temperatureCelsius = temp,
            status = status
        )
        phoneBatteryState.value = data

        val payload = JsonObject().apply {
            addProperty("batteryLevel", batteryPct)
            addProperty("isCharging", isCharging)
            addProperty("isPluggedIn", isPluggedIn)
            addProperty("temperatureCelsius", temp)
            addProperty("powerSaveMode", false)
        }

        sendMessage("BATTERY_UPDATE", payload)
        Log.i(TAG, "Dispatched Phone Battery: $batteryPct%, isCharging: $isCharging, isPlugged: $isPluggedIn, temp: $temp°C")
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
                        requestMacBattery()
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
            var counter = 0
            while (isActive && isConnected) {
                delay(5000)
                counter++
                requestMacBattery()
                val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (sticky != null) {
                    dispatchBatteryUpdate(sticky)
                }
                if (counter % 6 == 0) {
                    org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this@AetherCoreService)
                }
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
                "MAC_BATTERY_UPDATE" -> {
                    val level = payload.get("batteryLevel")?.asInt ?: 100
                    val isCharging = payload.get("isCharging")?.asBoolean ?: false
                    val isPluggedIn = payload.get("isPluggedIn")?.asBoolean ?: false
                    val desc = payload.get("statusDescription")?.asString ?: "Pilde"
                    macBatteryState.value = MacBatteryData(level, isCharging, isPluggedIn, desc)
                    Log.i(TAG, "Received Mac battery: $level%, isCharging: $isCharging ($desc)")
                }
                "DEVICE_TELEMETRY_REQUEST" -> {
                    org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this)
                }
                "SCREEN_STREAM_CONTROL" -> {
                    val action = payload.get("action")?.asString ?: ""
                    if (action == "start") {
                        if (!org.aetherlink.screen.ScreenStreamManager.isStreaming) {
                            val act = MainActivity.instance
                            if (act != null) {
                                act.launchScreenCapturePrompt()
                            } else {
                                val intent = Intent(this, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra(MainActivity.EXTRA_REQUEST_SCREEN_CAPTURE, true)
                                }
                                startActivity(intent)
                            }
                        }
                    } else if (action == "stop") {
                        stopScreenCapture()
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

    fun requestMacBattery() {
        sendMessage("MAC_BATTERY_REQUEST", JsonObject())
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
