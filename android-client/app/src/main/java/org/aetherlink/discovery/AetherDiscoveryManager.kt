package org.aetherlink.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AetherDiscoveryManager provides ultra-resilient zero-config local network discovery
 * using dual-channel lookup:
 * 1. Ultra-fast UDP broadcast query (sub-50ms) to Mac's UDPDiscoveryResponder.
 * 2. Standard Android NsdManager Bonjour / mDNS service discovery for _aetherlink._tcp.
 */
object AetherDiscoveryManager {

    private const val TAG = "AetherDiscoveryManager"
    private const val UDP_PORT = 8444
    private const val SERVICE_TYPE = "_aetherlink._tcp."

    private val isDiscovering = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var activeCallback: ((name: String, ip: String, port: Int) -> Unit)? = null

    fun startDiscovery(
        context: Context,
        onMacFound: (name: String, ip: String, port: Int) -> Unit
    ) {
        if (!isDiscovering.compareAndSet(false, true)) {
            Log.d(TAG, "Discovery already active.")
            return
        }

        activeCallback = onMacFound
        Log.i(TAG, "Initiating dual-channel (UDP Broadcast + mDNS) Mac discovery...")

        // Acquire multicast lock so OEM ROMs don't filter broadcast packets
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("AetherDiscoveryLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
        }

        // Channel 1: Fast UDP Broadcast Query
        scope.launch {
            broadcastUdpDiscoveryQuery(context)
        }

        // Channel 2: mDNS NsdManager Discovery
        scope.launch(Dispatchers.Main) {
            startNsdDiscovery(context)
        }

        // Auto-stop discovery after 15 seconds to conserve battery
        scope.launch {
            delay(15000)
            if (isDiscovering.get()) {
                Log.d(TAG, "Discovery timed out after 15s. Stopping.")
                stopDiscovery()
            }
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering.compareAndSet(true, false)) return
        Log.i(TAG, "Stopping all discovery channels.")

        activeCallback = null

        // Release MulticastLock
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null

        // Stop mDNS
        val listener = discoveryListener
        val mgr = nsdManager
        if (listener != null && mgr != null) {
            try {
                mgr.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping NSD discovery: ${e.message}")
            }
        }
        discoveryListener = null
        nsdManager = null
    }

    private suspend fun broadcastUdpDiscoveryQuery(context: Context) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 2500

            val queryMsg = "AETHER_DISCOVER_MAC"
            val queryBytes = queryMsg.toByteArray(Charsets.UTF_8)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")

            val packet = DatagramPacket(queryBytes, queryBytes.size, broadcastAddr, UDP_PORT)

            // Send 3 probe bursts spaced by 150ms
            for (i in 1..3) {
                if (!isDiscovering.get()) break
                socket.send(packet)
                Log.d(TAG, "Sent UDP discovery probe #$i to 255.255.255.255:$UDP_PORT")
                delay(150)
            }

            val recvBuffer = ByteArray(2048)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

            val startTime = System.currentTimeMillis()
            while (isDiscovering.get() && (System.currentTimeMillis() - startTime < 4000)) {
                try {
                    socket.receive(recvPacket)
                    val responseStr = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8)
                    if (responseStr.contains("AETHER_ANNOUNCE_MAC") || responseStr.contains("AETHER_WAKE_BEACON")) {
                        val json = JsonParser.parseString(responseStr).asJsonObject
                        val ip = json.get("ip")?.asString ?: recvPacket.address.hostAddress ?: ""
                        val port = json.get("port")?.asInt ?: 8443
                        val macName = json.get("macName")?.asString ?: "MacBook Pro"

                        if (ip.isNotBlank() && ip != "127.0.0.1") {
                            Log.i(TAG, "UDP Probe SUCCESS: Discovered '$macName' at $ip:$port")
                            handleDeviceFound(context, macName, ip, port)
                            break
                        }
                    }
                } catch (_: Exception) {
                    // Timeout hit on receive
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP discovery socket exception: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun startNsdDiscovery(context: Context) {
        try {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS NSD discovery started: $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS service found: ${service.serviceName}")
                    if (service.serviceType.contains("_aetherlink._tcp")) {
                        resolveNsdService(context, mgr, service)
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS service lost: ${service.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS NSD discovery stopped: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "mDNS start failed: $errorCode")
                    stopDiscovery()
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "mDNS stop failed: $errorCode")
                }
            }

            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed initiating NsdManager: ${e.message}")
        }
    }

    private fun resolveNsdService(context: Context, mgr: NsdManager, service: NsdServiceInfo) {
        mgr.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName

                if (host.isNotBlank() && host != "127.0.0.1") {
                    Log.i(TAG, "mDNS Resolve SUCCESS: Discovered '$name' at $host:$port")
                    handleDeviceFound(context, name, host, port)
                }
            }
        })
    }

    private fun handleDeviceFound(context: Context, name: String, ip: String, port: Int) {
        // Save discovered IP to preferences for instant reconnect on future launches
        try {
            val prefs = context.getSharedPreferences("aetherlink_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("last_mac_ip", ip).apply()
        } catch (_: Exception) {}

        val cb = activeCallback
        stopDiscovery()
        cb?.invoke(name, ip, port)
    }
}
