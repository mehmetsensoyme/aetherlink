package org.aetherlink.service

import android.app.Notification
import android.app.NotificationManager
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
import org.aetherlink.util.DeviceUtils
import java.util.concurrent.TimeUnit

data class MacBatteryData(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isPluggedIn: Boolean = false,
    val statusDescription: String = "Pilde"
)

data class MacTelemetryData(
    val temperatureCelsius: Double = 41.0,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val thermalStatus: String = "NORMAL"
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
        const val ACTION_DISCONNECT = "org.aetherlink.action.DISCONNECT"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ALERT_ID = 1002

        var instance: AetherCoreService? = null
            private set

        val macBatteryState = kotlinx.coroutines.flow.MutableStateFlow<MacBatteryData?>(null)
        val macTelemetryState = kotlinx.coroutines.flow.MutableStateFlow<MacTelemetryData?>(null)
        val phoneBatteryState = kotlinx.coroutines.flow.MutableStateFlow(PhoneBatteryData())
        val isConnectedState = kotlinx.coroutines.flow.MutableStateFlow(false)
        var currentThermalStatus: String = "NORMAL"
            internal set

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
    private var connectedMacName: String = "MacBook"
    private var isMediaProjectionRunning: Boolean = false

    private var thermalListener: Any? = null
    private var lastDispatchedTemp: Double = 0.0
    private var lastDispatchedThermalStatus: String = "NORMAL"

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
        registerThermalStatusMonitoring()
        ClipboardSyncManager.init(this)
        org.aetherlink.bluetooth.BluetoothAudioManager.init(this)
        connectToMacWebSocket()
        Log.i(TAG, "AetherCoreService started.")
    }

    private fun registerThermalStatusMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
            val listener = android.os.PowerManager.OnThermalStatusChangedListener { status ->
                val statusStr = when (status) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                    android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                    android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                    else -> "NORMAL"
                }
                currentThermalStatus = statusStr
                Log.i(TAG, "Android Thermal Status changed: $statusStr ($status)")
                checkAndDispatchTelemetryIfChanged(force = true)
            }
            try {
                powerManager.addThermalStatusListener(mainExecutor, listener)
                thermalListener = listener
            } catch (e: Exception) {
                Log.w(TAG, "Could not add thermal status listener: ${e.message}")
            }
        }
    }

    fun checkAndDispatchTelemetryIfChanged(force: Boolean = false) {
        if (!isConnected) return
        val currentTemp = phoneBatteryState.value.temperatureCelsius
        val tempDiff = kotlin.math.abs(currentTemp - lastDispatchedTemp)
        val statusChanged = currentThermalStatus != lastDispatchedThermalStatus

        if (force || tempDiff >= 0.5 || statusChanged) {
            lastDispatchedTemp = currentTemp
            lastDispatchedThermalStatus = currentThermalStatus
            org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            Log.i(TAG, "ACTION_DISCONNECT received from notification")
            disconnect(userInitiated = true, forget = false)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildForegroundNotification(includeMediaProjection: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent.createChooser(Intent(this, MainActivity::class.java), null),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (includeMediaProjection) {
            "AetherLink Canlı Ekran Yansıtma"
        } else if (isConnected) {
            "AetherLink Aktif - Bağlı: $connectedMacName"
        } else {
            "AetherLink Aktif - Bağlantı Kesildi"
        }

        val desc = if (includeMediaProjection) {
            "Telefon ekranı Mac'e canlı olarak aktarılıyor."
        } else if (isConnected) {
            "Süreklilik köprüsü ve senkronizasyon devrede."
        } else {
            "Mac ile bağlantı sonlandırıldı veya bekleniyor."
        }

        val builder = NotificationCompat.Builder(this, AetherLinkApplication.CHANNEL_CORE_SERVICE)
            .setContentTitle(title)
            .setContentText(desc)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)

        if (isConnected) {
            val disconnectIntent = Intent(this, AetherDisconnectReceiver::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getBroadcast(
                this,
                1,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Bağlantıyı Kes",
                disconnectPendingIntent
            )
        }

        return builder.build()
    }

    fun startForegroundWithType(includeMediaProjection: Boolean = false) {
        isMediaProjectionRunning = includeMediaProjection
        val notification = buildForegroundNotification(includeMediaProjection)

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

    fun updateForegroundNotification(includeMediaProjection: Boolean = isMediaProjectionRunning) {
        isMediaProjectionRunning = includeMediaProjection
        val notification = buildForegroundNotification(includeMediaProjection)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    fun showDisconnectAlert(message: String = "Mac bağlantısı kesildi") {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val openIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val alertNotification = NotificationCompat.Builder(this, AetherLinkApplication.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("AetherLink")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
            notificationManager?.notify(NOTIFICATION_ALERT_ID, alertNotification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing disconnect alert: ${e.message}")
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
        checkAndDispatchTelemetryIfChanged(force = false)
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

                        val infoPayload = JsonObject().apply {
                            addProperty("deviceId", DeviceUtils.getDeviceId())
                            addProperty("deviceName", DeviceUtils.getDeviceName())
                            addProperty("model", Build.MODEL)
                            addProperty("manufacturer", Build.MANUFACTURER)
                            addProperty("androidVersion", Build.VERSION.RELEASE)
                            addProperty("sdkInt", Build.VERSION.SDK_INT)
                        }
                        sendMessage("DEVICE_INFO", infoPayload)

                        org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this@AetherCoreService)
                        requestMacBattery()
                        startPeriodicTelemetry()
                        isConnectedState.value = true
                        updateForegroundNotification()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleIncomingMacMessage(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        isConnected = false
                        isConnectedState.value = false
                        macBatteryState.value = null
                        updateForegroundNotification()
                        Log.w(TAG, "WebSocket closing: $reason")
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val wasConnected = isConnected
                        isConnected = false
                        isConnectedState.value = false
                        macBatteryState.value = null
                        updateForegroundNotification()
                        if (wasConnected) {
                            showDisconnectAlert("Mac bağlantısı kesildi")
                        }
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
                requestMacTelemetry()
                val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (sticky != null) {
                    dispatchBatteryUpdate(sticky)
                }
                org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this@AetherCoreService)
            }
        }
    }

    fun disconnect(userInitiated: Boolean = true, forget: Boolean = false) {
        if (isConnected) {
            val payload = JsonObject().apply {
                addProperty("source", "android")
                addProperty("reason", if (forget) "unpair" else if (userInitiated) "user_requested" else "connection_lost")
                addProperty("shouldForget", forget)
                addProperty("timestamp", System.currentTimeMillis())
            }
            sendMessage("DISCONNECT", payload)
        }
        isConnected = false
        isConnectedState.value = false
        macBatteryState.value = null
        macTelemetryState.value = null
        telemetryJob?.cancel()
        org.aetherlink.screen.ScreenStreamManager.stopCapture()
        isMediaProjectionRunning = false
        try {
            webSocket?.close(1000, if (userInitiated) "User disconnected" else "Disconnected")
        } catch (_: Exception) {}
        webSocket = null
        updateForegroundNotification()
        Log.i(TAG, "Disconnected from Mac (userInitiated: $userInitiated, forget: $forget)")
    }

    private fun handleRemoteDisconnect(shouldForget: Boolean) {
        isConnected = false
        isConnectedState.value = false
        macBatteryState.value = null
        macTelemetryState.value = null
        telemetryJob?.cancel()
        org.aetherlink.screen.ScreenStreamManager.stopCapture()
        isMediaProjectionRunning = false
        try {
            webSocket?.close(1000, "Remote Mac disconnected")
        } catch (_: Exception) {}
        webSocket = null
        updateForegroundNotification()
        Log.i(TAG, "Handled remote Mac disconnect (shouldForget: $shouldForget)")
    }

    private fun handleIncomingMacMessage(jsonString: String) {
        try {
            val json = gson.fromJson(jsonString, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            val payload = json.getAsJsonObject("payload") ?: return

            when (type) {
                "MAC_HELLO" -> {
                    val macName = payload.get("macName")?.asString
                    if (!macName.isNullOrBlank()) {
                        connectedMacName = macName
                    }
                    updateForegroundNotification()
                    Log.i(TAG, "Received MAC_HELLO from $connectedMacName")
                }
                "MAC_BATTERY_UPDATE" -> {
                    val level = payload.get("batteryLevel")?.asInt ?: 100
                    val isCharging = payload.get("isCharging")?.asBoolean ?: false
                    val isPluggedIn = payload.get("isPluggedIn")?.asBoolean ?: false
                    val desc = payload.get("statusDescription")?.asString ?: "Pilde"
                    macBatteryState.value = MacBatteryData(level, isCharging, isPluggedIn, desc)
                    Log.i(TAG, "Received Mac battery: $level%, isCharging: $isCharging ($desc)")
                }
                "MAC_TELEMETRY" -> {
                    val temp = payload.get("mac_temp")?.asDouble ?: 41.0
                    val batt = payload.get("mac_battery")?.asInt ?: 100
                    val charging = payload.get("is_charging")?.asBoolean ?: false
                    val thermal = payload.get("thermal_status")?.asString ?: "NORMAL"
                    macTelemetryState.value = MacTelemetryData(temp, batt, charging, thermal)
                    Log.i(TAG, "Received MAC_TELEMETRY: $temp°C, batt: $batt%, charging: $charging, thermal: $thermal")
                }
                "DEVICE_TELEMETRY_REQUEST" -> {
                    org.aetherlink.telemetry.DeviceTelemetryManager.dispatchTelemetry(this)
                    requestMacTelemetry()
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
                    val source = payload.get("source")?.asString ?: "macos"
                    if (source == "macos") {
                        showDisconnectAlert("Mac bağlantısı kesildi")
                    }
                    handleRemoteDisconnect(shouldForget)
                }
                "DEVICE_BUSY" -> {
                    val message = payload.get("message")?.asString ?: "Mac başka bir cihaza bağlı"
                    Log.w(TAG, "Mac is busy: $message")
                    showDisconnectAlert(message)
                    disconnect(userInitiated = false, forget = false)
                }
                "BLUETOOTH_HANDSHAKE" -> {
                    org.aetherlink.bluetooth.BluetoothAudioManager.handleIncomingBluetoothHandshake(payload, this)
                }
                "CALL_ACTION" -> {
                    val callId = payload.get("callId")?.asString ?: ""
                    val action = payload.get("action")?.asString ?: ""
                    if (action == "answer") {
                        org.aetherlink.telecom.CallActionHelper.answerCall(this)
                    } else if (action == "decline" || action == "hangup") {
                        org.aetherlink.telecom.CallActionHelper.endCall(this)
                    }
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
        sendMessage("MAC_TELEMETRY_REQUEST", JsonObject())
    }

    fun requestMacTelemetry() {
        sendMessage("MAC_TELEMETRY_REQUEST", JsonObject())
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
