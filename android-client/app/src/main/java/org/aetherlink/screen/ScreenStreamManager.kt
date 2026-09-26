package org.aetherlink.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.aetherlink.service.AetherCoreService
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenStreamManager {
    private const val TAG = "ScreenStreamManager"

    var isStreaming = false
        private set

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var frameIndex = 0

    fun setupMediaProjection(projection: MediaProjection, width: Int, height: Int, densityDpi: Int) {
        this.mediaProjection = projection
        startCapture(width, height, densityDpi)
    }

    fun startCapture(width: Int = 720, height: Int = 1600, densityDpi: Int = 320) {
        if (isStreaming) return
        isStreaming = true
        frameIndex = 0

        if (mediaProjection != null) {
            try {
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "AetherLinkMirror",
                    width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface, null, null
                )

                imageReader?.setOnImageAvailableListener({ reader ->
                    if (!isStreaming) return@setOnImageAvailableListener
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        if (image != null) {
                            processImageFrame(image, width, height)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error acquiring image frame: ${e.message}")
                    } finally {
                        image?.close()
                    }
                }, Handler(Looper.getMainLooper()))

                Log.i(TAG, "VirtualDisplay screen capture started at ${width}x${height}")
                return
            } catch (e: Exception) {
                Log.e(TAG, "MediaProjection VirtualDisplay failed: ${e.message}, falling back to frame loop")
            }
        }

        // Fallback or preview streaming loop (15-20 FPS)
        streamJob = scope.launch {
            while (isStreaming && isActive) {
                sendPreviewFrame(width, height)
                delay(66) // ~15 FPS
            }
        }
    }

    private fun processImageFrame(image: Image, width: Int, height: Int) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        val cropped = if (rowPadding != 0) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } else {
            bitmap
        }

        dispatchFrame(cropped, width, height)
    }

    private fun sendPreviewFrame(width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0F172A"))

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val subPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("Samsung Galaxy S25 Ultra", width / 2f, height / 3f, paint)
        canvas.drawText("AetherLink Canlı Ekran Aktarımı", width / 2f, (height / 3f) + 60f, subPaint)
        canvas.drawText("Saat: $timeStr • Kare #$frameIndex", width / 2f, (height / 3f) + 120f, subPaint)

        dispatchFrame(bitmap, width, height)
    }

    private fun dispatchFrame(bitmap: Bitmap, width: Int, height: Int) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val bytes = baos.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            frameIndex++
            val payload = JsonObject().apply {
                addProperty("frameIndex", frameIndex)
                addProperty("format", "jpeg")
                addProperty("base64Data", base64)
                addProperty("width", width)
                addProperty("height", height)
                addProperty("timestamp", System.currentTimeMillis())
            }

            AetherCoreService.instance?.sendMessage("SCREEN_STREAM_FRAME", payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding screen frame: ${e.message}")
        }
    }

    fun stopCapture() {
        isStreaming = false
        streamJob?.cancel()
        streamJob = null
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing display resources: ${e.message}")
        }
        Log.i(TAG, "Screen capture stopped.")
    }
}
