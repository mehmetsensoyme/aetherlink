package org.aetherlink.screen

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.google.gson.JsonObject
import org.aetherlink.service.AetherCoreService
import java.io.ByteArrayOutputStream

object ScreenStreamManager {
    private const val TAG = "ScreenStreamManager"

    var isStreaming = false
        private set

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var frameIndex = 0
    private var lastFrameTimestamp = 0L
    private const val MIN_FRAME_INTERVAL_MS = 33L // ~30 FPS max

    private var reusableBitmap: Bitmap? = null
    private var reusableCroppedBitmap: Bitmap? = null

    fun setupMediaProjection(projection: MediaProjection, width: Int, height: Int, densityDpi: Int) {
        stopCapture()
        this.mediaProjection = projection
        startCapture(width, height, densityDpi)
    }

    fun startCapture(width: Int = 540, height: Int = 1170, densityDpi: Int = 320) {
        if (isStreaming) return
        val projection = mediaProjection ?: run {
            Log.w(TAG, "Cannot start capture: MediaProjection is null")
            return
        }

        isStreaming = true
        frameIndex = 0
        lastFrameTimestamp = 0L

        try {
            val thread = HandlerThread("AetherScreenCaptureThread").apply { start() }
            handlerThread = thread
            val handler = Handler(thread.looper)
            captureHandler = handler

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            imageReader = reader

            virtualDisplay = projection.createVirtualDisplay(
                "AetherLinkMirrorDisplay",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            reader.setOnImageAvailableListener({ ir ->
                if (!isStreaming) return@setOnImageAvailableListener
                val now = System.currentTimeMillis()
                if (now - lastFrameTimestamp < MIN_FRAME_INTERVAL_MS) {
                    // Drop frame to preserve bandwidth and maintain smooth 30fps
                    ir.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }

                var image: Image? = null
                try {
                    image = ir.acquireLatestImage()
                    if (image != null) {
                        lastFrameTimestamp = now
                        processImageFrame(image, width, height)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring image frame: ${e.message}")
                } finally {
                    image?.close()
                }
            }, handler)

            Log.i(TAG, "Real MediaProjection VirtualDisplay started at ${width}x${height} (${densityDpi}dpi)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VirtualDisplay capture: ${e.message}", e)
            stopCapture()
        }
    }

    private fun processImageFrame(image: Image, width: Int, height: Int) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val rawWidth = width + rowPadding / pixelStride
        var rawBmp = reusableBitmap
        if (rawBmp == null || rawBmp.isRecycled || rawBmp.width != rawWidth || rawBmp.height != height) {
            rawBmp = Bitmap.createBitmap(rawWidth, height, Bitmap.Config.ARGB_8888)
            reusableBitmap = rawBmp
        }

        buffer.rewind()
        rawBmp.copyPixelsFromBuffer(buffer)

        val outputBitmap: Bitmap = if (rowPadding > 0) {
            var cropped = reusableCroppedBitmap
            if (cropped == null || cropped.isRecycled || cropped.width != width || cropped.height != height) {
                cropped = Bitmap.createBitmap(rawBmp, 0, 0, width, height)
                reusableCroppedBitmap = cropped
            } else {
                val canvas = android.graphics.Canvas(cropped)
                val srcRect = android.graphics.Rect(0, 0, width, height)
                val dstRect = android.graphics.Rect(0, 0, width, height)
                canvas.drawBitmap(rawBmp, srcRect, dstRect, null)
            }
            cropped
        } else {
            rawBmp
        }

        dispatchFrame(outputBitmap, width, height)
    }

    private fun dispatchFrame(bitmap: Bitmap, width: Int, height: Int) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos)
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
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VirtualDisplay: ${e.message}")
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ImageReader: ${e.message}")
        }

        try {
            handlerThread?.quitSafely()
            handlerThread = null
            captureHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping handler thread: ${e.message}")
        }

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection: ${e.message}")
        }

        reusableBitmap?.recycle()
        reusableBitmap = null
        reusableCroppedBitmap?.recycle()
        reusableCroppedBitmap = null

        Log.i(TAG, "Screen capture completely stopped.")
    }
}
