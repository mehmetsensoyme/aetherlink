package org.aetherlink.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService

object DeviceTelemetryManager {

    fun collectTelemetry(context: Context): JsonObject {
        val payload = JsonObject()

        // 1. Device Hardware & OS Info
        payload.addProperty("model", Build.MODEL ?: "Galaxy S25 Ultra")
        payload.addProperty("manufacturer", Build.MANUFACTURER ?: "Samsung")
        payload.addProperty("androidVersion", Build.VERSION.RELEASE ?: "15")
        payload.addProperty("sdkLevel", Build.VERSION.SDK_INT)

        // 2. Battery & Temperature
        val batteryData = AetherCoreService.phoneBatteryState.value
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        var level = batteryData.level
        var isCharging = batteryData.isCharging || batteryData.isPluggedIn
        var tempCelsius = batteryData.temperatureCelsius
        var healthStr = "İyi"

        if (batteryIntent != null) {
            val rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (rawLevel >= 0 && scale > 0) {
                level = ((rawLevel / scale.toFloat()) * 100).toInt()
            }
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val isPluggedIn = plugged != 0
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    (isPluggedIn && status != BatteryManager.BATTERY_STATUS_DISCHARGING)

            val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            if (temp > 0) {
                tempCelsius = temp / 10.0
            }

            val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            healthStr = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "İyi"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Aşırı Isınmış"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Tükenmiş"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Yüksek Voltaj"
                else -> "Normal"
            }
        }

        payload.addProperty("batteryLevel", level)
        payload.addProperty("isCharging", isCharging)
        payload.addProperty("batteryTempCelsius", tempCelsius)
        payload.addProperty("batteryHealth", healthStr)

        // 3. RAM / Memory Info
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        if (actManager != null) {
            actManager.getMemoryInfo(memInfo)
            val totalMB = (memInfo.totalMem / (1024 * 1024)).toInt()
            val freeMB = (memInfo.availMem / (1024 * 1024)).toInt()
            val usedMB = totalMB - freeMB
            payload.addProperty("ramTotalMB", totalMB)
            payload.addProperty("ramUsedMB", usedMB)
            payload.addProperty("ramFreeMB", freeMB)
        } else {
            payload.addProperty("ramTotalMB", 12288)
            payload.addProperty("ramUsedMB", 6144)
            payload.addProperty("ramFreeMB", 6144)
        }

        // 4. Storage Info (GB)
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availBlocks = stat.availableBlocksLong

            val totalGB = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
            val freeGB = (availBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
            val usedGB = totalGB - freeGB

            payload.addProperty("storageTotalGB", Math.round(totalGB * 10.0) / 10.0)
            payload.addProperty("storageUsedGB", Math.round(usedGB * 10.0) / 10.0)
            payload.addProperty("storageFreeGB", Math.round(freeGB * 10.0) / 10.0)
        } catch (_: Exception) {
            payload.addProperty("storageTotalGB", 512.0)
            payload.addProperty("storageUsedGB", 120.0)
            payload.addProperty("storageFreeGB", 392.0)
        }

        // 5. Network & Operator
        var wifiSSID: String? = null
        var linkSpeed = 0
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            wifiSSID = wifiInfo?.ssid?.replace("\"", "")
            linkSpeed = wifiInfo?.linkSpeed ?: 0
        } catch (_: Exception) {}

        var operatorName: String? = null
        try {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            operatorName = telephony?.networkOperatorName
        } catch (_: Exception) {}

        payload.addProperty("wifiSSID", wifiSSID)
        payload.addProperty("wifiLinkSpeedMbps", linkSpeed)
        payload.addProperty("cellularOperator", operatorName ?: "Mobil Veri")

        // 6. Uptime
        val uptimeHours = SystemClock.elapsedRealtime() / (1000.0 * 3600.0)
        payload.addProperty("uptimeHours", Math.round(uptimeHours * 100.0) / 100.0)
        payload.addProperty("timestamp", System.currentTimeMillis())

        return payload
    }

    fun dispatchTelemetry(context: Context) {
        val payload = collectTelemetry(context)
        AetherCoreService.instance?.sendMessage("DEVICE_TELEMETRY", payload)
    }
}
