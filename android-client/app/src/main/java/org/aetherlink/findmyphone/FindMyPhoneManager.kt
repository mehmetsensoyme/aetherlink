package org.aetherlink.findmyphone

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.aetherlink.AetherLinkApplication
import org.aetherlink.service.AetherCoreService

object FindMyPhoneManager {
    private const val TAG = "FindMyPhoneManager"
    private const val NOTIFICATION_ID = 8899

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoStopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isPhoneRinging = MutableStateFlow(false)
    val isPhoneRinging: StateFlow<Boolean> = _isPhoneRinging

    private val _isMacRinging = MutableStateFlow(false)
    val isMacRinging: StateFlow<Boolean> = _isMacRinging

    private var originalAlarmVolume: Int = -1

    fun startRinging(context: Context) {
        if (_isPhoneRinging.value) return
        _isPhoneRinging.value = true
        Log.i(TAG, "Starting Find My Phone alarm at max volume...")

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            // Vibration pattern
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator = vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 300, 800, 300, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }

            // Post Heads-up Notification with "Sustur" button
            showAlarmNotification(context)

            // Notify Mac that ringing started
            sendPhoneStatus(isRinging = true)

            // Auto-stop after 60 seconds
            autoStopJob?.cancel()
            autoStopJob = scope.launch {
                delay(60000)
                if (_isPhoneRinging.value) {
                    stopRinging(context, notifyMac = true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm: ${e.message}", e)
        }
    }

    fun stopRinging(context: Context, notifyMac: Boolean = true) {
        if (!_isPhoneRinging.value) return
        _isPhoneRinging.value = false
        Log.i(TAG, "Stopping Find My Phone alarm...")

        autoStopJob?.cancel()
        autoStopJob = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media player: ${e.message}")
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        // Restore original alarm volume if was altered
        if (originalAlarmVolume >= 0) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            } catch (_: Exception) {}
            originalAlarmVolume = -1
        }

        // Cancel Notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        if (notifyMac) {
            sendPhoneStatus(isRinging = false)
        }
    }

    private fun showAlarmNotification(context: Context) {
        val stopIntent = Intent(context, AetherFindMyPhoneReceiver::class.java).apply {
            action = AetherFindMyPhoneReceiver.ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AetherLinkApplication.CHANNEL_FIND_MY_PHONE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 Mac'iniz Telefonunuzu Arıyor!")
            .setContentText("Cihazı bulmak için ses çalınıyor. Durdurmak için tıklayın.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Sustur", stopPendingIntent)
            .setContentIntent(stopPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendPhoneStatus(isRinging: Boolean) {
        val payload = JsonObject().apply {
            addProperty("action", if (isRinging) "ring" else "stop")
            addProperty("sourceDevice", "android")
            addProperty("isRinging", isRinging)
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("FIND_MY_PHONE_STATUS", payload)
    }

    // MARK: - Mac Ringing (Android -> Mac)
    fun toggleRingMac() {
        if (_isMacRinging.value) {
            stopRingMac()
        } else {
            ringMac()
        }
    }

    fun ringMac() {
        _isMacRinging.value = true
        val payload = JsonObject().apply {
            addProperty("action", "ring")
            addProperty("sourceDevice", "android")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("FIND_MY_MAC_REQUEST", payload)
        Log.i(TAG, "Sent FIND_MY_MAC_REQUEST: ring")
    }

    fun stopRingMac() {
        _isMacRinging.value = false
        val payload = JsonObject().apply {
            addProperty("action", "stop")
            addProperty("sourceDevice", "android")
            addProperty("timestamp", System.currentTimeMillis())
        }
        AetherCoreService.currentInstance?.sendMessage("FIND_MY_MAC_REQUEST", payload)
        Log.i(TAG, "Sent FIND_MY_MAC_REQUEST: stop")
    }

    fun handleMacResponse(action: String) {
        if (action == "stop") {
            _isMacRinging.value = false
        }
    }
}
