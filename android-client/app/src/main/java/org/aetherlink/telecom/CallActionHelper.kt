package org.aetherlink.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.aetherlink.receiver.CallStateReceiver
import org.aetherlink.service.AetherNotificationListener

object CallActionHelper {
    private const val TAG = "CallActionHelper"

    @SuppressLint("MissingPermission")
    fun answerCall(context: Context) {
        Log.i(TAG, "Executing multi-tier answerCall strategy...")
        CallStateReceiver.markAnsweredFromMac()
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

        routeAudioForRemoteAnswer(context)
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

                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
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

        setSpeakerphone(context, false)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        AetherNotificationListener.clearActiveCallIntents()
        org.aetherlink.audio.CallAudioRelayManager.stop(context)
    }

    fun routeAudioForRemoteAnswer(context: Context) {
        Log.i(TAG, "Enforcing hands-free speakerphone routing on modern Android / Samsung One UI...")
        setSpeakerphone(context, true)

        // Multiple delayed passes to prevent Samsung One UI InCallUI / Telecom resetting to earpiece
        CoroutineScope(Dispatchers.Main).launch {
            delay(350)
            setSpeakerphone(context, true)
            delay(750)
            setSpeakerphone(context, true)
            delay(1200)
            setSpeakerphone(context, true)
        }
    }

    fun setSpeakerphone(context: Context, enabled: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            // If enabling, verify if a Bluetooth headset is actively connected
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val hasBtHeadset = audioManager.availableCommunicationDevices.any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    if (hasBtHeadset) {
                        Log.i(TAG, "Bluetooth headset detected, preserving headset route instead of speakerphone.")
                        return
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
                        Log.i(TAG, "Bluetooth headset detected (legacy), preserving headset route.")
                        return
                    }
                }
            }

            // InCallService route
            AetherInCallService.setSpeaker(enabled)

            audioManager.mode = AudioManager.MODE_IN_CALL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enabled) {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        val ok = audioManager.setCommunicationDevice(speakerDevice)
                        Log.i(TAG, "setCommunicationDevice(TYPE_BUILTIN_SPEAKER) result: $ok")
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                        Log.i(TAG, "Speaker device not found in availableCommunicationDevices, used isSpeakerphoneOn=true")
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                    Log.i(TAG, "Cleared communication device, speaker disabled.")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enabled
                Log.i(TAG, "Legacy isSpeakerphoneOn set to $enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring speakerphone route: ${e.message}")
        }
    }
}
