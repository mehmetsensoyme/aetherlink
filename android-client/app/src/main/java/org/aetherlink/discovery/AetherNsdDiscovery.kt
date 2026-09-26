package org.aetherlink.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class AetherNsdDiscovery(
    private val context: Context,
    private val onDeviceFound: (name: String, host: String, port: Int) -> Unit
) {
    companion object {
        private const val TAG = "AetherNsdDiscovery"
        private const val SERVICE_TYPE = "_aetherlink._tcp."
    }

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var isDiscovering = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            isDiscovering = true
            Log.d(TAG, "Service discovery started for $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${service.serviceName}")
            if (service.serviceType.contains("_aetherlink._tcp")) {
                resolveService(service)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${service.serviceName}")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            isDiscovering = false
            Log.d(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed to start: Error code $errorCode")
            try {
                nsdManager?.stopServiceDiscovery(this)
            } catch (_: Exception) {}
            isDiscovering = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed to stop: Error code $errorCode")
            isDiscovering = false
        }
    }

    private fun resolveService(service: NsdServiceInfo) {
        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName
                Log.i(TAG, "Resolved AetherLink Mac: $name at $host:$port")
                onDeviceFound(name, host, port)
            }
        })
    }

    fun startDiscovery() {
        if (!isDiscovering) {
            try {
                nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start mDNS discovery: ${e.message}")
            }
        }
    }

    fun stopDiscovery() {
        if (isDiscovering) {
            try {
                nsdManager?.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop mDNS discovery: ${e.message}")
            }
        }
    }
}
