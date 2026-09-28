package org.aetherlink.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.aetherlink.service.AetherCoreService
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CallAudioRelayManager provides ultra-low latency real-time bidirectional call audio streaming
 * between the Android device and MacBook Pro over local Wi-Fi UDP.
 */
object CallAudioRelayManager {
    private const val TAG = "CallAudioRelayManager"

    private const val SAMPLE_RATE = 16000
    private const val FRAME_MS = 20
    private const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_MS / 1000 // 320 samples
    private const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 640 bytes (16-bit)
    private const val PACKET_MAGIC: Short = 0xAECA.toShort()

    private const val DEFAULT_MAC_PORT = 8444
    private const val DEFAULT_LOCAL_MIC_PORT = 8445

    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var txThread: Thread? = null
    private var rxThread: Thread? = null
    private var txSocket: DatagramSocket? = null
    private var rxSocket: DatagramSocket? = null

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Synchronized
    fun start(context: Context, macIp: String? = null) {
        if (isRunning.get()) {
            Log.d(TAG, "Audio relay already active.")
            return
        }

        val targetIp = macIp ?: run {
            val prefs = context.getSharedPreferences("aetherlink_prefs", Context.MODE_PRIVATE)
            prefs.getString("last_mac_ip", "192.168.1.15") ?: "192.168.1.15"
        }

        if (targetIp.isBlank() || targetIp == "127.0.0.1") {
            Log.w(TAG, "Target Mac IP is invalid ($targetIp), skipping audio relay.")
            return
        }

        Log.i(TAG, "Starting ultra-low latency call audio relay to Mac ($targetIp:$DEFAULT_MAC_PORT)...")
        isRunning.set(true)

        // 1. Configure Android AudioManager for optimal hands-free communication
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
                audioManager.isBluetoothScoOn = false
                Log.i(TAG, "AudioManager configured: MODE_IN_COMMUNICATION, speakerphone=ON")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting audio manager mode: ${e.message}")
        }

        // 2. Notify Mac to initialize its playback and microphone receiver
        val startPayload = JsonObject().apply {
            addProperty("action", "start")
            addProperty("sampleRate", SAMPLE_RATE)
            addProperty("udpPort", DEFAULT_MAC_PORT)
            addProperty("macMicPort", DEFAULT_LOCAL_MIC_PORT)
            addProperty("phoneIp", "")
            addProperty("timestamp", System.currentTimeMillis().toDouble())
        }
        AetherCoreService.instance?.sendMessage("CALL_AUDIO_START", startPayload)

        // 3. Start Audio Transmitter (Phone Mic/Call -> Mac Speakers)
        startXmitThread(targetIp, DEFAULT_MAC_PORT)

        // 4. Start Audio Receiver (Mac Mic -> Phone Call AudioTrack)
        startRecvThread(DEFAULT_LOCAL_MIC_PORT)
    }

    @Synchronized
    fun stop(context: Context? = null) {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping call audio relay...")

        // Interrupt threads
        txThread?.interrupt()
        rxThread?.interrupt()

        // Close sockets
        try { txSocket?.close() } catch (_: Exception) {}
        try { rxSocket?.close() } catch (_: Exception) {}
        txSocket = null
        rxSocket = null

        // Release hardware audio effects
        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null

        // Restore AudioManager
        context?.let { ctx ->
            try {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
            } catch (_: Exception) {}
        }

        // Notify Mac to tear down its audio engine
        val stopPayload = JsonObject().apply {
            addProperty("action", "stop")
            addProperty("timestamp", System.currentTimeMillis().toDouble())
        }
        AetherCoreService.instance?.sendMessage("CALL_AUDIO_STOP", stopPayload)

        Log.i(TAG, "Call audio relay stopped.")
    }

    @SuppressLint("MissingPermission")
    private fun startXmitThread(targetIp: String, targetPort: Int) {
        txThread = Thread {
            var audioRecord: AudioRecord? = null
            try {
                val minBufSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufSize, BYTES_PER_FRAME * 4)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    return@Thread
                }

                // Enable hardware Acoustic Echo Cancellation and Noise Suppression if supported
                val sessionId = audioRecord.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    Log.i(TAG, "Hardware AcousticEchoCanceler enabled")
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    Log.i(TAG, "Hardware NoiseSuppressor enabled")
                }

                audioRecord.startRecording()
                Log.i(TAG, "AudioRecord started recording at $SAMPLE_RATE Hz")

                txSocket = DatagramSocket()
                val targetAddress = InetAddress.getByName(targetIp)

                val readBuffer = ByteArray(BYTES_PER_FRAME)
                val packetData = ByteArray(8 + BYTES_PER_FRAME)
                var sequence: Int = 0

                // Magic header: 0xAECA
                packetData[0] = ((PACKET_MAGIC.toInt() shr 8) and 0xFF).toByte()
                packetData[1] = (PACKET_MAGIC.toInt() and 0xFF).toByte()
                packetData[6] = ((BYTES_PER_FRAME shr 8) and 0xFF).toByte()
                packetData[7] = (BYTES_PER_FRAME and 0xFF).toByte()

                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    val bytesRead = audioRecord.read(readBuffer, 0, BYTES_PER_FRAME)
                    if (bytesRead > 0) {
                        // Sequence number
                        packetData[2] = ((sequence shr 24) and 0xFF).toByte()
                        packetData[3] = ((sequence shr 16) and 0xFF).toByte()
                        packetData[4] = ((sequence shr 8) and 0xFF).toByte()
                        packetData[5] = (sequence and 0xFF).toByte()
                        sequence++

                        System.arraycopy(readBuffer, 0, packetData, 8, bytesRead)

                        val dgram = DatagramPacket(packetData, 8 + bytesRead, targetAddress, targetPort)
                        txSocket?.send(dgram)
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.w(TAG, "TX audio thread exception: ${e.message}")
                }
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                Log.i(TAG, "TX audio thread exited.")
            }
        }.apply {
            name = "org.aetherlink.audio.tx"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun startRecvThread(localPort: Int) {
        rxThread = Thread {
            var audioTrack: AudioTrack? = null
            try {
                rxSocket = DatagramSocket(localPort)
                rxSocket?.reuseAddress = true

                val minBufSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufSize, BYTES_PER_FRAME * 4)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()
                Log.i(TAG, "AudioTrack started playback at $SAMPLE_RATE Hz (listening on UDP :$localPort)")

                val recvBuffer = ByteArray(2048)
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    val packet = DatagramPacket(recvBuffer, recvBuffer.size)
                    rxSocket?.receive(packet)

                    val len = packet.length
                    if (len >= 8) {
                        val magic = ((recvBuffer[0].toInt() and 0xFF) shl 8) or (recvBuffer[1].toInt() and 0xFF)
                        if (magic.toShort() == PACKET_MAGIC) {
                            val pcmLen = len - 8
                            if (pcmLen > 0) {
                                audioTrack.write(recvBuffer, 8, pcmLen)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.w(TAG, "RX audio thread exception: ${e.message}")
                }
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
                Log.i(TAG, "RX audio thread exited.")
            }
        }.apply {
            name = "org.aetherlink.audio.rx"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }
}
