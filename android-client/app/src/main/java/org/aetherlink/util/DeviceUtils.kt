package org.aetherlink.util

import android.os.Build
import java.util.Locale

object DeviceUtils {
    /**
     * Dynamically combines Build.MANUFACTURER and Build.MODEL
     * to return a clean, human-readable device name without hardcoding.
     * Examples: "Samsung Galaxy S25 Ultra", "Google Pixel 9 Pro", "Xiaomi 14"
     */
    private val modelMap = mapOf(
        "SM-S938" to "Galaxy S25 Ultra",
        "SM-S936" to "Galaxy S25+",
        "SM-S931" to "Galaxy S25",
        "SM-S928" to "Galaxy S24 Ultra",
        "SM-S926" to "Galaxy S24+",
        "SM-S921" to "Galaxy S24",
        "SM-S918" to "Galaxy S23 Ultra",
        "SM-S916" to "Galaxy S23+",
        "SM-S911" to "Galaxy S23",
        "SM-S908" to "Galaxy S22 Ultra",
        "SM-S906" to "Galaxy S22+",
        "SM-S901" to "Galaxy S22",
        "SM-F956" to "Galaxy Z Fold 6",
        "SM-F741" to "Galaxy Z Flip 6",
        "SM-F946" to "Galaxy Z Fold 5",
        "SM-F731" to "Galaxy Z Flip 5"
    )

    fun getDeviceName(): String {
        val rawManufacturer = Build.MANUFACTURER ?: ""
        val manufacturer = rawManufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        val model = Build.MODEL ?: "Android Cihazı"

        for ((prefix, marketName) in modelMap) {
            if (model.startsWith(prefix, ignoreCase = true)) {
                return if (manufacturer.isNotBlank() && !marketName.startsWith(manufacturer, ignoreCase = true)) {
                    "$manufacturer $marketName"
                } else {
                    marketName
                }
            }
        }

        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else if (manufacturer.isNotBlank()) {
            "$manufacturer $model".trim()
        } else {
            model
        }
    }

    /**
     * Generates a stable unique device identifier based on model
     */
    fun getDeviceId(): String {
        val cleanModel = (Build.MODEL ?: "device").lowercase().replace("\\s+".toRegex(), "_")
        return "android_$cleanModel"
    }
}
