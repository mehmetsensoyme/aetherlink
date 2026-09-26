package org.aetherlink.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aetherlink.service.AetherCoreService

object BluetoothAudioManager {
    private const val TAG = "BluetoothAudioManager"

    private val _isPairedWithMac = MutableStateFlow(false)
    val isPairedWithMac = _isPairedWithMac.asStateFlow()

    private var targetMacBluetoothAddress: String = "50:F2:65:F1:47:63"
    private var targetMacName: String = "MacBook Pro"

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                if (device?.address.equals(targetMacBluetoothAddress, ignoreCase = true)) {
                    val bonded = bondState == BluetoothDevice.BOND_BONDED
                    _isPairedWithMac.value = bonded
                    Log.i(TAG, "Bond state changed for Mac ($targetMacBluetoothAddress): $bondState (bonded: $bonded)")
                }
            }
        }
    }

    fun init(context: Context) {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(bondReceiver, filter)
        checkCurrentBondState(context)
    }

    @SuppressLint("MissingPermission")
    fun checkCurrentBondState(context: Context) {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return
            val bondedDevices = adapter.bondedDevices ?: return

            val found = bondedDevices.any { 
                it.address.equals(targetMacBluetoothAddress, ignoreCase = true) ||
                (it.name != null && it.name.contains("MacBook", ignoreCase = true))
            }
            _isPairedWithMac.value = found
            Log.d(TAG, "Current Bluetooth bonded state with Mac: $found")
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Bluetooth bond state: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun handleIncomingBluetoothHandshake(payload: JsonObject, context: Context) {
        val macAddress = payload.get("macBluetoothAddress")?.asString
        val macName = payload.get("macBluetoothName")?.asString
        if (!macAddress.isNullOrBlank()) {
            targetMacBluetoothAddress = macAddress
        }
        if (!macName.isNullOrBlank()) {
            targetMacName = macName
        }

        checkCurrentBondState(context)
        if (!_isPairedWithMac.value) {
            Log.i(TAG, "Mac requested Bluetooth handshake. Initiating auto-bond with: $targetMacName ($targetMacBluetoothAddress)")
            initiateBonding(context)
        } else {
            Log.i(TAG, "Already bonded with Mac ($targetMacName). Confirming audio ready.")
            sendHandshakeResponse(isBonded = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun initiateBonding(context: Context) {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "Bluetooth adapter not enabled")
                return
            }

            val device = adapter.getRemoteDevice(targetMacBluetoothAddress)
            if (device != null) {
                val bondResult = device.createBond()
                Log.i(TAG, "createBond called for $targetMacBluetoothAddress, initiated: $bondResult")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Bluetooth bond: ${e.message}", e)
        }
    }

    fun sendHandshakeResponse(isBonded: Boolean) {
        val payload = JsonObject().apply {
            addProperty("isBonded", isBonded)
            addProperty("deviceModel", Build.MODEL)
            addProperty("status", if (isBonded) "ready_for_audio" else "bonding_pending")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.instance?.sendMessage("BLUETOOTH_HANDSHAKE_RESPONSE", payload)
    }
}
