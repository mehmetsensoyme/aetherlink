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
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.aetherlink.R
import org.aetherlink.ui.MainActivity
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

/**
 * AetherCameraService
 * 
 * Provides ultra-low latency (<35ms), hardware-accelerated Continuity Camera
 * and Studio Microphone streaming from Android to macOS.
 * Uses Camera2 hardware JPEG ISP capture and 48kHz 16-bit PCM AudioRecord.
 */
class AetherCameraService : Service() {

    companion object {
        private const val TAG = "AetherCameraService"
        const val STREAM_PORT = 8445
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "aether_camera_stream_channel"

        const val ACTION_START = "org.aetherlink.camera.START"
        const val ACTION_STOP = "org.aetherlink.camera.STOP"
        const val ACTION_SWITCH = "org.aetherlink.camera.SWITCH"
        const val ACTION_TORCH = "org.aetherlink.camera.TORCH"
        const val ACTION_MIC_TOGGLE = "org.aetherlink.camera.MIC_TOGGLE"

        var isStreaming = false
            private set
        var currentLens: String = "back" // "back" or "front"
            private set
        var isTorchOn: Boolean = false
            private set
        var isMicActive: Boolean = true
            private set
        var currentResolution: String = "1080p" // "1080p" or "720p"
            private set

        fun start(
            context: Context,
            lens: String = "back",
            resolution: String = "1080p",
            torch: Boolean = false,
            mic: Boolean = true
        ) {
            val intent = Intent(context, AetherCameraService::class.java).apply {
                action = ACTION_START
                putExtra("lens", lens)
                putExtra("resolution", resolution)
                putExtra("torch", torch)
                putExtra("mic", mic)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AetherCameraService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun switchLens(context: Context) {
            val intent = Intent(context, AetherCameraService::class.java).apply {
                action = ACTION_SWITCH
            }
            context.startService(intent)
        }

        fun toggleTorch(context: Context) {
            val intent = Intent(context, AetherCameraService::class.java).apply {
                action = ACTION_TORCH
            }
            context.startService(intent)
        }

        fun toggleMic(context: Context) {
            val intent = Intent(context, AetherCameraService::class.java).apply {
                action = ACTION_MIC_TOGGLE
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Camera2 state
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    // Audio state
    private var audioRecord: AudioRecord? = null
    private var isAudioRecording = false
    private var audioThread: Thread? = null

    // TCP Streaming Server
    private var serverSocket: ServerSocket? = null
    private val clientSockets = java.util.concurrent.CopyOnWriteArrayList<Socket>()
    private val clientStreams = java.util.concurrent.CopyOnWriteArrayList<DataOutputStream>()

    // Framing & FPS Metrics
    private var frameCount = 0L
    private var lastFpsCalculationTime = 0L
    private var currentFps = 30

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        try {
            when (action) {
                ACTION_START -> {
                    val reqLens = intent?.getStringExtra("lens") ?: currentLens
                    val reqRes = intent?.getStringExtra("resolution") ?: currentResolution
                    val reqTorch = intent?.getBooleanExtra("torch", false) ?: false
                    val reqMic = intent?.getBooleanExtra("mic", true) ?: true

                    currentLens = reqLens
                    currentResolution = reqRes
                    isTorchOn = reqTorch
                    isMicActive = reqMic

                    if (startForegroundServiceWithNotification()) {
                        startStreamingPipeline()
                    }
                }
                ACTION_STOP -> {
                    stopStreamingPipeline()
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {}
                    stopSelf()
                }
                ACTION_SWITCH -> {
                    currentLens = if (currentLens == "back") "front" else "back"
                    isTorchOn = false // Torch only on back camera
                    restartCameraCapture()
                    broadcastStatus()
                }
                ACTION_TORCH -> {
                    if (currentLens == "back") {
                        isTorchOn = !isTorchOn
                        updateTorchState()
                        broadcastStatus()
                    }
                }
                ACTION_MIC_TOGGLE -> {
                    isMicActive = !isMicActive
                    if (isMicActive) {
                        startAudioCapture()
                    } else {
                        stopAudioCapture()
                    }
                    broadcastStatus()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled exception in onStartCommand: ${e.message}", e)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification(): Boolean {
        return try {
            val notification = buildServiceNotification()
            val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

            if (!hasCamera) {
                Log.w(TAG, "Cannot start camera foreground service: CAMERA permission not granted!")
                stopSelf()
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMic && isMicActive) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: ${e.message}", e)
            stopSelf()
            false
        }
    }

    private fun buildServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherCameraService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val switchIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AetherCameraService::class.java).apply { action = ACTION_SWITCH },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AetherLink Süreklilik Kamerası")
            .setContentText("Mac'e $currentResolution Full HD görüntü ve stüdyo sesi aktarılıyor")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_camera, "Kamera Değiştir", switchIntent)
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
                "Süreklilik Kamerası Akışı",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mac ile Continuity Camera canlı akış bildirimi"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startStreamingPipeline() {
        if (isStreaming) return
        isStreaming = true
        Log.i(TAG, "Starting Continuity Camera streaming pipeline...")

        startTcpServer()
        startCameraThread()
        openCamera()
        if (isMicActive) {
            startAudioCapture()
        }
        broadcastStatus()
    }

    private fun stopStreamingPipeline() {
        isStreaming = false
        Log.i(TAG, "Stopping Continuity Camera streaming pipeline...")

        closeCamera()
        stopAudioCapture()
        stopCameraThread()
        stopTcpServer()
        broadcastStatus()
    }

    // MARK: - TCP Server & Packet Dispatcher
    private fun startTcpServer() {
        serviceScope.launch {
            try {
                serverSocket = ServerSocket(STREAM_PORT)
                Log.i(TAG, "Camera TCP Streaming Server listening on port $STREAM_PORT")

                while (isActive && isStreaming) {
                    val socket = serverSocket?.accept() ?: break
                    socket.tcpNoDelay = true
                    socket.sendBufferSize = 256 * 1024
                    val out = DataOutputStream(socket.getOutputStream())

                    clientSockets.add(socket)
                    clientStreams.add(out)
                    Log.i(TAG, "Mac connected to Camera TCP stream: ${socket.inetAddress}")

                    // Send initial handshake status
                    sendStreamPacket(out, 0x03, buildStatusPayload().toString().toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                if (isStreaming) {
                    Log.e(TAG, "Error in Camera TCP server: ${e.message}")
                }
            }
        }
    }

    private fun stopTcpServer() {
        try {
            for (socket in clientSockets) {
                try { socket.close() } catch (_: Exception) {}
            }
            clientSockets.clear()
            clientStreams.clear()
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera TCP server: ${e.message}")
        }
    }

    /**
     * Dispatch packet:
     * Header: 'AETH' (4 bytes)
     * Type: 1 byte (0x01 Video Frame, 0x02 Audio PCM, 0x03 Status)
     * Length: 4 bytes Int
     * Timestamp: 8 bytes Long
     * Payload: byte[]
     */
    private fun sendStreamPacket(out: DataOutputStream, type: Byte, data: ByteArray) {
        try {
            synchronized(out) {
                out.writeByte(0x41) // 'A'
                out.writeByte(0x45) // 'E'
                out.writeByte(0x54) // 'T'
                out.writeByte(0x48) // 'H'
                out.writeByte(type.toInt())
                out.writeInt(data.size)
                out.writeLong(System.currentTimeMillis())
                out.write(data)
                out.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client socket write error: ${e.message}")
            // Client will be cleaned up on next pass
        }
    }

    private fun broadcastFrame(frameData: ByteArray) {
        val staleClients = mutableListOf<DataOutputStream>()
        for (out in clientStreams) {
            try {
                sendStreamPacket(out, 0x01, frameData)
            } catch (e: Exception) {
                staleClients.add(out)
            }
        }
        if (staleClients.isNotEmpty()) {
            clientStreams.removeAll(staleClients)
        }
    }

    private fun broadcastAudio(audioData: ByteArray) {
        val staleClients = mutableListOf<DataOutputStream>()
        for (out in clientStreams) {
            try {
                sendStreamPacket(out, 0x02, audioData)
            } catch (e: Exception) {
                staleClients.add(out)
            }
        }
        if (staleClients.isNotEmpty()) {
            clientStreams.removeAll(staleClients)
        }
    }

    // MARK: - Camera2 Hardware Capture
    private fun startCameraThread() {
        val thread = HandlerThread("AetherCameraBackgroundThread").apply { start() }
        cameraThread = thread
        cameraHandler = Handler(thread.looper)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join(500)
        } catch (_: InterruptedException) {}
        cameraThread = null
        cameraHandler = null
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val manager = cameraManager ?: return
        val handler = cameraHandler ?: return

        // Verify camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot open camera: CAMERA permission not granted")
            return
        }

        try {
            val targetFacing = if (currentLens == "front") {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }

            var chosenCameraId: String? = null
            for (id in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    chosenCameraId = id
                    break
                }
            }

            val cameraId = chosenCameraId ?: manager.cameraIdList.firstOrNull() ?: run {
                Log.e(TAG, "No suitable camera device found")
                return
            }

            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val availableSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            val desiredWidth = if (currentResolution == "720p") 1280 else 1920
            val desiredHeight = if (currentResolution == "720p") 720 else 1080

            // Pick exact match, or closest 16:9, or highest resolution within bounds
            val targetSize = availableSizes.firstOrNull { it.width == desiredWidth && it.height == desiredHeight }
                ?: availableSizes.filter { it.width <= desiredWidth && it.height <= desiredHeight }
                    .maxByOrNull { it.width * it.height }
                ?: availableSizes.firstOrNull()
                ?: Size(1280, 720)

            Log.i(TAG, "Selected camera output size: ${targetSize.width}x${targetSize.height} for camera $cameraId")

            // ImageReader with JPEG format uses hardware ISP encoder directly
            // Maximum buffer size = 2 to ensure zero latency and instant frame dropping when delayed
            val reader = ImageReader.newInstance(targetSize.width, targetSize.height, ImageFormat.JPEG, 2)
            reader.setOnImageAvailableListener({ ir ->
                var image: Image? = null
                try {
                    image = ir.acquireLatestImage()
                    if (image != null && isStreaming) {
                        val planes = image.planes
                        if (planes.isNotEmpty()) {
                            val buffer = planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)

                            frameCount++
                            val now = System.currentTimeMillis()
                            if (now - lastFpsCalculationTime >= 1000) {
                                currentFps = (frameCount * 1000 / (now - lastFpsCalculationTime)).toInt()
                                frameCount = 0
                                lastFpsCalculationTime = now
                            }

                            broadcastFrame(bytes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring camera image: ${e.message}")
                } finally {
                    image?.close()
                }
            }, handler)

            imageReader = reader

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession(camera, reader)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    try { camera.close() } catch (_: Exception) {}
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera open error: $error")
                    try { camera.close() } catch (_: Exception) {}
                    cameraDevice = null
                    isStreaming = false
                    broadcastStatus()
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Exception opening camera: ${e.message}", e)
        }
    }

    private fun startCaptureSession(camera: CameraDevice, reader: ImageReader) {
        val handler = cameraHandler ?: return
        try {
            val surfaces = listOf(reader.surface)
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        requestBuilder.addTarget(reader.surface)

                        // Optimize for high-framerate, continuous autofocus
                        requestBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        )
                        requestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON
                        )
                        requestBuilder.set(
                            CaptureRequest.CONTROL_AWB_MODE,
                            CaptureRequest.CONTROL_AWB_MODE_AUTO
                        )

                        if (currentLens == "back" && isTorchOn) {
                            requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                        }

                        session.setRepeatingRequest(requestBuilder.build(), null, handler)
                        Log.i(TAG, "Camera capture session active at ${currentResolution} (Lens: $currentLens)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start repeating capture request: ${e.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera capture session configuration failed")
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting capture session: ${e.message}")
        }
    }

    private fun restartCameraCapture() {
        closeCamera()
        openCamera()
    }

    private fun updateTorchState() {
        val session = captureSession ?: return
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return
        val handler = cameraHandler ?: return

        try {
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            requestBuilder.addTarget(reader.surface)
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            if (currentLens == "back" && isTorchOn) {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            session.setRepeatingRequest(requestBuilder.build(), null, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling torch: ${e.message}")
        }
    }

    private fun closeCamera() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
        } catch (_: Exception) {}

        try {
            cameraDevice?.close()
            cameraDevice = null
        } catch (_: Exception) {}

        try {
            imageReader?.close()
            imageReader = null
        } catch (_: Exception) {}
    }

    // MARK: - Studio Microphone Capture (48kHz 16-bit PCM)
    @SuppressLint("MissingPermission")
    private fun startAudioCapture() {
        if (isAudioRecording) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Audio capture skipped: RECORD_AUDIO permission not granted")
            isAudioRecording = false
            return
        }

        isAudioRecording = true

        val sampleRate = 48000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 2048)

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            recorder.startRecording()
            audioRecord = recorder

            audioThread = Thread({
                val buffer = ByteArray(2048)
                while (isAudioRecording && isStreaming) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        broadcastAudio(chunk)
                    }
                }
            }, "AetherAudioCaptureThread").apply { start() }

            Log.i(TAG, "Studio Microphone audio capture started at ${sampleRate}Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}")
            isAudioRecording = false
        }
    }

    private fun stopAudioCapture() {
        isAudioRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}
        try {
            audioThread?.join(300)
            audioThread = null
        } catch (_: Exception) {}
    }

    // MARK: - Status Sync to Mac via AetherCoreService
    private fun buildStatusPayload(): JsonObject {
        return JsonObject().apply {
            addProperty("isStreaming", isStreaming)
            addProperty("lens", currentLens)
            addProperty("resolution", currentResolution)
            addProperty("isTorchOn", isTorchOn)
            addProperty("isMicActive", isMicActive)
            addProperty("fps", currentFps)
            addProperty("port", STREAM_PORT)
        }
    }

    private fun broadcastStatus() {
        val payload = buildStatusPayload()
        AetherCoreService.instance?.sendMessage("CONTINUITY_CAMERA_STATUS", payload)
    }

    override fun onDestroy() {
        stopStreamingPipeline()
        super.onDestroy()
    }
}
