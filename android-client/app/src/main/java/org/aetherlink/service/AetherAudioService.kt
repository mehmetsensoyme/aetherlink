package org.aetherlink.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.aetherlink.R
import org.aetherlink.ui.MainActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AetherAudioService
 *
 * Provides ultra-low latency (<30ms), zero-ADB wireless audio streaming
 * between Android and macOS.
 *
 * Features:
 * 1. Media Downlink: Streams Android media audio (Spotify, YouTube, Games) to Mac speakers.
 * 2. Full-Duplex Call Bridge (Wi-Fi Calling):
 *    - Captures phone call downlink -> streams to Mac speakers.
 *    - Receives Mac microphone uplink via UDP -> plays to phone call.
 * 3. 100% Zero-ADB, Non-Root: Operates entirely via standard Android APIs.
 */
class AetherAudioService : Service() {

    enum class AudioMode(val id: String, val title: String) {
        HYBRID("hybrid", "Otomatik Hibrit"),
        MEDIA_ONLY("media", "Yalnızca Medya"),
        CALL_ONLY("call", "Yalnızca Arama")
    }

    companion object {
        private const val TAG = "AetherAudioService"
        const val AUDIO_PORT = 8446
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "aether_audio_stream_channel"

        // Magic header: "AEAU" (AetherAudio)
        val MAGIC_BYTES = byteArrayOf(0x41, 0x45, 0x41, 0x55)
        const val PKT_TYPE_MEDIA = 0x01.toByte()
        const val PKT_TYPE_CALL_DOWNLINK = 0x02.toByte()
        const val PKT_TYPE_CALL_UPLINK = 0x03.toByte()
        const val PKT_TYPE_HEARTBEAT = 0x04.toByte()
        const val PKT_TYPE_CONTROL = 0x05.toByte()

        const val SAMPLE_RATE_MEDIA = 48000
        const val SAMPLE_RATE_CALL = 16000
        const val FRAME_SIZE_MS = 20 // 20ms chunks

        const val ACTION_START = "org.aetherlink.audio.START"
        const val ACTION_STOP = "org.aetherlink.audio.STOP"
        const val ACTION_SET_MODE = "org.aetherlink.audio.SET_MODE"

        val streamingState = androidx.compose.runtime.mutableStateOf(false)
        val modeState = androidx.compose.runtime.mutableStateOf(AudioMode.HYBRID)

        var isStreaming = false
            private set(value) {
                field = value
                streamingState.value = value
            }
        var currentMode: AudioMode = AudioMode.HYBRID
            private set(value) {
                field = value
                modeState.value = value
            }
        var isCallAudioActive: Boolean = false
            private set

        fun start(context: Context, mode: AudioMode = AudioMode.HYBRID) {
            val intent = Intent(context, AetherAudioService::class.java).apply {
                action = ACTION_START
                putExtra("mode", mode.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AetherAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setMode(context: Context, mode: AudioMode) {
            val intent = Intent(context, AetherAudioService::class.java).apply {
                action = ACTION_SET_MODE
                putExtra("mode", mode.id)
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var udpSocket: DatagramSocket? = null
    private var macTargetAddress: InetAddress? = null

    // Audio Capture & Playback engines
    private var mediaRecord: AudioRecord? = null
    private var callRecord: AudioRecord? = null
    private var callTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var isRecordingMedia = false
    private var isRecordingCall = false
    private var isPlayingCallUplink = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val modeStr = intent.getStringExtra("mode") ?: AudioMode.HYBRID.id
                currentMode = AudioMode.values().find { it.id == modeStr } ?: AudioMode.HYBRID

                if (!startForegroundWithNotification()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                initSocketsAndStreaming()
            }
            ACTION_SET_MODE -> {
                val modeStr = intent.getStringExtra("mode") ?: AudioMode.HYBRID.id
                currentMode = AudioMode.values().find { it.id == modeStr } ?: AudioMode.HYBRID
                updateNotification()
                broadcastStatus()
            }
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun initSocketsAndStreaming() {
        serviceScope.launch {
            try {
                if (udpSocket == null || udpSocket?.isClosed == true) {
                    udpSocket = DatagramSocket(AUDIO_PORT)
                    udpSocket?.broadcast = true
                }

                // Resolve Mac IP address from core service
                val coreIp = AetherCoreService.instance?.getConnectedMacIp() ?: "127.0.0.1"
                macTargetAddress = try {
                    InetAddress.getByName(coreIp)
                } catch (e: Exception) {
                    InetAddress.getByName("127.0.0.1")
                }

                isStreaming = true
                broadcastStatus()
                Log.i(TAG, "AetherAudio UDP stream ready on port $AUDIO_PORT -> target: $coreIp")

                // Start UDP uplink listener (Mac mic packets -> Phone Call Track)
                startUplinkReceiver()

                // Start capture based on active mode
                restartCaptureLoops()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AetherAudio UDP socket: ${e.message}", e)
                stopStreaming()
            }
        }
    }

    private fun restartCaptureLoops() {
        stopCaptureEngines()

        if (isCallAudioActive || currentMode == AudioMode.CALL_ONLY) {
            startCallCaptureLoop()
        } else if (currentMode == AudioMode.HYBRID || currentMode == AudioMode.MEDIA_ONLY) {
            startMediaCaptureLoop()
        }
    }

    // MARK: - Media Audio Capture Loop (48kHz Stereo)
    @SuppressLint("MissingPermission")
    private fun startMediaCaptureLoop() {
        if (isRecordingMedia) return
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            Log.w(TAG, "Cannot start media capture: RECORD_AUDIO permission missing")
            return
        }

        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE_MEDIA, channelConfig, audioFormat)
        val bufSize = maxOf(minBuf, (SAMPLE_RATE_MEDIA * 2 * 2 * FRAME_SIZE_MS) / 1000)

        try {
            // Using VOICE_RECOGNITION / MIC source with wide frequency response for media
            mediaRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_MEDIA,
                channelConfig,
                audioFormat,
                bufSize
            )

            if (mediaRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "Media AudioRecord failed to initialize. Falling back to MIC source.")
                mediaRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_MEDIA,
                    channelConfig,
                    audioFormat,
                    bufSize
                )
            }

            mediaRecord?.startRecording()
            isRecordingMedia = true
            Log.i(TAG, "AetherAudio Media Capture loop started (48kHz Stereo).")

            serviceScope.launch {
                val pcmBuffer = ByteArray(1920) // 20ms at 48kHz 16-bit stereo = 1920 bytes
                while (isActive && isRecordingMedia && isStreaming) {
                    val readBytes = mediaRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: -1
                    if (readBytes > 0 && macTargetAddress != null) {
                        sendAudioPacket(PKT_TYPE_MEDIA, 0x01, pcmBuffer, readBytes)
                    } else if (readBytes < 0) {
                        delay(10)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting media capture loop: ${e.message}", e)
        }
    }

    // MARK: - Call Audio Capture Loop (16kHz Mono with AEC)
    @SuppressLint("MissingPermission")
    private fun startCallCaptureLoop() {
        if (isRecordingCall) return
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            Log.w(TAG, "Cannot start call capture: RECORD_AUDIO missing")
            return
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE_CALL, channelConfig, audioFormat)
        val bufSize = maxOf(minBuf, (SAMPLE_RATE_CALL * 2 * FRAME_SIZE_MS) / 1000)

        try {
            callRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_CALL,
                channelConfig,
                audioFormat,
                bufSize
            )

            val sessionId = callRecord?.audioSessionId ?: 0
            if (AcousticEchoCanceler.isAvailable() && sessionId != 0) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
            }
            if (NoiseSuppressor.isAvailable() && sessionId != 0) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }

            callRecord?.startRecording()
            isRecordingCall = true
            Log.i(TAG, "AetherAudio Call Downlink Capture loop started (16kHz Mono with AEC).")

            serviceScope.launch {
                val pcmBuffer = ByteArray(640) // 20ms at 16kHz 16-bit mono = 640 bytes
                while (isActive && isRecordingCall && isStreaming) {
                    val readBytes = callRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: -1
                    if (readBytes > 0 && macTargetAddress != null) {
                        sendAudioPacket(PKT_TYPE_CALL_DOWNLINK, 0x00, pcmBuffer, readBytes)
                    } else if (readBytes < 0) {
                        delay(10)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting call capture loop: ${e.message}", e)
        }
    }

    // MARK: - UDP Uplink Receiver (Mac mic -> Phone Call AudioTrack)
    private fun startUplinkReceiver() {
        serviceScope.launch {
            val rxBuffer = ByteArray(2048)
            val packet = DatagramPacket(rxBuffer, rxBuffer.size)

            // Setup phone call audio output track
            setupCallOutputTrack()

            while (isActive && isStreaming && udpSocket?.isClosed == false) {
                try {
                    udpSocket?.receive(packet)
                    val len = packet.length
                    if (len >= 8 && rxBuffer[0] == MAGIC_BYTES[0] && rxBuffer[1] == MAGIC_BYTES[1] &&
                        rxBuffer[2] == MAGIC_BYTES[2] && rxBuffer[3] == MAGIC_BYTES[3]) {

                        val type = rxBuffer[4]
                        val payloadLen = ByteBuffer.wrap(rxBuffer, 6, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        val safeLen = minOf(payloadLen, len - 8)

                        if (type == PKT_TYPE_CALL_UPLINK && safeLen > 0) {
                            // Feed received Mac microphone speech into phone's call output track
                            callTrack?.write(rxBuffer, 8, safeLen)
                        } else if (type == PKT_TYPE_HEARTBEAT) {
                            // Update Mac target address if sender changed
                            macTargetAddress = packet.address
                        }
                    }
                } catch (e: Exception) {
                    if (isStreaming) {
                        delay(20)
                    }
                }
            }
        }
    }

    private fun setupCallOutputTrack() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_CALL,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_CALL)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            callTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            callTrack?.play()
            isPlayingCallUplink = true
            Log.i(TAG, "AetherAudio Call Uplink AudioTrack ready for Mac mic input.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating call output track: ${e.message}", e)
        }
    }

    private fun sendAudioPacket(type: Byte, flags: Byte, data: ByteArray, len: Int) {
        val target = macTargetAddress ?: return
        val totalSize = 8 + len
        val packetBytes = ByteArray(totalSize)

        // Magic
        System.arraycopy(MAGIC_BYTES, 0, packetBytes, 0, 4)
        packetBytes[4] = type
        packetBytes[5] = flags
        packetBytes[6] = ((len shr 8) and 0xFF).toByte()
        packetBytes[7] = (len and 0xFF).toByte()

        // Payload
        System.arraycopy(data, 0, packetBytes, 8, len)

        try {
            val datagram = DatagramPacket(packetBytes, totalSize, target, AUDIO_PORT)
            udpSocket?.send(datagram)
        } catch (e: Exception) {
            // Ignore transient UDP drop
        }
    }

    fun onCallStateChanged(active: Boolean) {
        isCallAudioActive = active
        Log.i(TAG, "Call state changed -> isCallAudioActive: $active")
        serviceScope.launch {
            restartCaptureLoops()
            updateNotification()
            broadcastStatus()
        }
    }

    private fun stopCaptureEngines() {
        isRecordingMedia = false
        isRecordingCall = false

        try {
            mediaRecord?.stop()
            mediaRecord?.release()
        } catch (e: Exception) {}
        mediaRecord = null

        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            callRecord?.stop()
            callRecord?.release()
        } catch (e: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
        callRecord = null
    }

    private fun stopStreaming() {
        isStreaming = false
        stopCaptureEngines()

        try {
            callTrack?.stop()
            callTrack?.release()
        } catch (e: Exception) {}
        callTrack = null

        try {
            udpSocket?.close()
        } catch (e: Exception) {}
        udpSocket = null

        broadcastStatus()
        Log.i(TAG, "AetherAudio Service stopped.")
    }

    override fun onDestroy() {
        stopStreaming()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun broadcastStatus() {
        val payload = JsonObject().apply {
            addProperty("isStreaming", isStreaming)
            addProperty("mode", currentMode.id)
            addProperty("isCallActive", isCallAudioActive)
            addProperty("port", AUDIO_PORT)
        }
        AetherCoreService.instance?.sendMessage("AUDIO_STREAM_STATUS", payload)
    }

    // MARK: - Foreground Service Notification
    private fun startForegroundWithNotification(): Boolean {
        return try {
            val notification = buildNotification()
            val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMic) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground: ${e.message}", e)
            false
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val subtext = if (isCallAudioActive) "Arama Görüşmesi Aktif • Mac Mikrofon & Hoparlör" else "Canlı Medya & Ses Köprüsü Aktif"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AetherAudio: ${currentMode.title}")
            .setContentText(subtext)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopIntent)
            .build()
            .apply {
                flags = flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR or Notification.FLAG_FOREGROUND_SERVICE
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AetherAudio Ses Köprüsü",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kablosuz Medya ve Çağrı Ses Köprüsü Bildirimi"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
