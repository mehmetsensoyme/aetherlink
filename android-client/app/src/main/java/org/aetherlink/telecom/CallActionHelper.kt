package org.aetherlink.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

object CallActionHelper {
    private const val TAG = "CallActionHelper"

    @SuppressLint("MissingPermission")
    fun answerCall(context: Context) {
        try {
            // Method 1: TelecomManager (Android 8+)
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecom != null) {
                telecom.acceptRingingCall()
                Log.i(TAG, "Accepted call using TelecomManager.acceptRingingCall()")
                routeAudioToBluetooth(context)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager answer failed: ${e.message}")
        }

        // Method 2: Media button headset hook emulation fallback
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
            }
            val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
            }
            context.sendOrderedBroadcast(down, null)
            context.sendOrderedBroadcast(up, null)
            Log.i(TAG, "Answered call using KEYCODE_HEADSETHOOK broadcast")
            routeAudioToBluetooth(context)
        } catch (e: Exception) {
            Log.e(TAG, "Media button hook fallback failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun endCall(context: Context) {
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecom != null) {
                val ended = telecom.endCall()
                Log.i(TAG, "Ended call using TelecomManager: $ended")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager endCall failed: ${e.message}")
        }
    }

    fun routeAudioToBluetooth(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isBluetoothScoOn = true
            audioManager.startBluetoothSco()
            audioManager.isSpeakerphoneOn = false
            Log.i(TAG, "Audio routed to Bluetooth SCO for Mac voice continuity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed routing audio to Bluetooth SCO: ${e.message}")
        }
    }
}
