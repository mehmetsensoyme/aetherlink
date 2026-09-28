package org.aetherlink.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoNr
import android.telephony.CellInfoLte
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService

object ConnectivityReportManager {
    private const val TAG = "ConnectivityReportMgr"

    fun dispatchConnectivityReport(context: Context) {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return

            // 1. Operator Name
            var operatorName = telephonyManager.networkOperatorName
            if (operatorName.isNullOrBlank()) {
                operatorName = telephonyManager.simOperatorName
            }
            if (operatorName.isNullOrBlank()) {
                operatorName = "Hücresel Ağ"
            }

            // 2. Network Type (5G, LTE, 3G, 2G, Wi-Fi)
            var networkType = "Mobil"
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = connMgr?.activeNetwork
            val caps = connMgr?.getNetworkCapabilities(activeNet)

            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                networkType = "Wi-Fi"
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Check for 5G / NR or LTE
                val dataNetworkType = try {
                    telephonyManager.dataNetworkType
                } catch (_: SecurityException) {
                    TelephonyManager.NETWORK_TYPE_UNKNOWN
                }

                networkType = when (dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                    else -> "4G/5G"
                }
            } else {
                networkType = "LTE"
            }

            // 3. Signal Strength (0 to 4 bars)
            var signalStrengthBars = 3
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val ss = telephonyManager.signalStrength
                    if (ss != null) {
                        signalStrengthBars = ss.level // 0..4
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not query signal strength level: ${e.message}")
                }
            }

            // 4. Roaming
            val isRoaming = try {
                telephonyManager.isNetworkRoaming
            } catch (_: Exception) {
                false
            }

            val payload = JsonObject().apply {
                addProperty("operatorName", operatorName)
                addProperty("networkType", networkType)
                addProperty("signalStrength", signalStrengthBars)
                addProperty("isRoaming", isRoaming)
                addProperty("timestamp", System.currentTimeMillis())
            }

            AetherCoreService.currentInstance?.sendMessage("CONNECTIVITY_REPORT", payload)
            Log.i(TAG, "Dispatched CONNECTIVITY_REPORT: $operatorName, $networkType ($signalStrengthBars/4 bars)")
        } catch (e: Exception) {
            Log.w(TAG, "Error dispatching connectivity report: ${e.message}")
        }
    }
}
