package org.aetherlink.util

import android.os.Build
import java.util.Locale

object DeviceUtils {
    /**
     * Dynamically combines Build.MANUFACTURER and Build.MODEL
     * to return a clean, human-readable device name without hardcoding.
     * Examples: "Samsung Galaxy S25 Ultra", "Google Pixel 9 Pro", "Xiaomi 14"
     */
    fun getDeviceName(): String {
        val rawManufacturer = Build.MANUFACTURER ?: ""
        val manufacturer = rawManufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        val model = Build.MODEL ?: "Android Cihazı"

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
