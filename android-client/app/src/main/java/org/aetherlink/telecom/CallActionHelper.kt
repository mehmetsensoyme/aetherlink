package org.aetherlink.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import org.aetherlink.service.AetherNotificationListener

object CallActionHelper {
    private const val TAG = "CallActionHelper"

    @SuppressLint("MissingPermission")
    fun answerCall(context: Context) {
        Log.i(TAG, "Executing multi-tier answerCall strategy...")
        var answered = false

        // Strategy 1: Dialer Notification PendingIntent (High reliability on Samsung One UI & Android 14/15)
        val answerIntent = AetherNotificationListener.activeCallAnswerIntent
        if (answerIntent != null) {
            try {
                answerIntent.send()
                answered = true
                Log.i(TAG, "Strategy 1: Answered call via dialer notification PendingIntent")
            } catch (e: Exception) {
                Log.w(TAG, "Strategy 1 failed: ${e.message}")
            }
        }

        // Strategy 2: Media Key Dispatch via AudioManager (Standard Headset Hook / Call Key)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val hookDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
                val hookUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
                audioManager.dispatchMediaKeyEvent(hookDown)
                audioManager.dispatchMediaKeyEvent(hookUp)

                val callDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CALL)
                val callUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CALL)
                audioManager.dispatchMediaKeyEvent(callDown)
                audioManager.dispatchMediaKeyEvent(callUp)

                Log.i(TAG, "Strategy 2: Dispatched KEYCODE_HEADSETHOOK & KEYCODE_CALL to AudioManager")
                answered = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 2 media key dispatch failed: ${e.message}")
        }

        // Strategy 3: TelecomManager.acceptRingingCall()
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecom != null) {
                telecom.acceptRingingCall()
                Log.i(TAG, "Strategy 3: Accepted call using TelecomManager.acceptRingingCall()")
                answered = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 3 TelecomManager answer failed: ${e.message}")
        }

        routeAudioToBluetooth(context)
        org.aetherlink.audio.CallAudioRelayManager.start(context)
    }

    @SuppressLint("MissingPermission")
    fun endCall(context: Context) {
        Log.i(TAG, "Executing multi-tier endCall/rejectCall strategy...")

        // Strategy 1: Dialer Notification PendingIntent (Direct rejection of ringing call)
        val rejectIntent = AetherNotificationListener.activeCallRejectIntent
        if (rejectIntent != null) {
            try {
                rejectIntent.send()
                Log.i(TAG, "Strategy 1: Rejected call via dialer notification PendingIntent")
                AetherNotificationListener.clearActiveCallIntents()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Strategy 1 failed: ${e.message}")
            }
        }

        // Strategy 2: Media Key Dispatch via AudioManager (KEYCODE_ENDCALL & KEYCODE_HEADSETHOOK)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val endDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL)
                val endUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL)
                audioManager.dispatchMediaKeyEvent(endDown)
                audioManager.dispatchMediaKeyEvent(endUp)

                val hookDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
                val hookUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
                audioManager.dispatchMediaKeyEvent(hookDown)
                audioManager.dispatchMediaKeyEvent(hookUp)

                Log.i(TAG, "Strategy 2: Dispatched KEYCODE_ENDCALL & KEYCODE_HEADSETHOOK to AudioManager")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 2 media key dispatch failed: ${e.message}")
        }

        // Strategy 3: TelecomManager.endCall()
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecom != null) {
                val ended = telecom.endCall()
                Log.i(TAG, "Strategy 3: Ended call using TelecomManager: $ended")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 3 TelecomManager endCall failed: ${e.message}")
        }

        AetherNotificationListener.clearActiveCallIntents()
        org.aetherlink.audio.CallAudioRelayManager.stop(context)
    }

    fun routeAudioToBluetooth(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            // Only route SCO if Bluetooth is bonded with Mac
            if (org.aetherlink.bluetooth.BluetoothAudioManager.isPairedWithMac.value) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
                audioManager.isSpeakerphoneOn = false
                Log.i(TAG, "Audio routed to Bluetooth SCO for Mac voice continuity")
            } else {
                Log.d(TAG, "Mac not bonded via Bluetooth, skipping SCO routing")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed routing audio to Bluetooth SCO: ${e.message}")
        }
    }
}
