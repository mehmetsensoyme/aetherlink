package org.aetherlink.volume

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.aetherlink.service.AetherCoreService
import kotlin.math.roundToInt

object RemoteVolumeManager {
    private const val TAG = "RemoteVolumeManager"

    private val _phoneVolume = MutableStateFlow(50)
    val phoneVolume: StateFlow<Int> = _phoneVolume

    private val _macVolume = MutableStateFlow(50)
    val macVolume: StateFlow<Int> = _macVolume

    fun getPhoneVolumePercent(context: Context): Int {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) ((cur.toFloat() / max) * 100).roundToInt() else 50
        } catch (_: Exception) {
            50
        }
    }

    fun setPhoneVolumeFromMac(context: Context, volumePercent: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val clamped = volumePercent.coerceIn(0, 100)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((clamped / 100f) * max).roundToInt()

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            _phoneVolume.value = clamped
            Log.i(TAG, "Set phone volume to $clamped% (level $target/$max)")

            // Send back confirmation
            sendPhoneVolumeUpdate(clamped)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting phone volume: ${e.message}")
        }
    }

    fun sendPhoneVolumeUpdate(volumePercent: Int) {
        val payload = JsonObject().apply {
            addProperty("volume", volumePercent)
            addProperty("stream", "media")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("PHONE_VOLUME_UPDATE", payload)
    }

    // Android adjusts Mac Volume
    fun setMacVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        _macVolume.value = clamped
        val payload = JsonObject().apply {
            addProperty("volume", clamped)
            addProperty("stream", "master")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("SET_MAC_VOLUME", payload)
        Log.i(TAG, "Sent SET_MAC_VOLUME: $clamped%")
    }

    fun handleMacVolumeUpdate(volumePercent: Int) {
        _macVolume.value = volumePercent
    }
}
