package org.aetherlink.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.aetherlink.screen.ScreenStreamManager
import org.aetherlink.service.AetherCoreService

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_REQUEST_SCREEN_CAPTURE = "extra_request_screen_capture"
        var instance: MainActivity? = null
            private set

        fun requestScreenCapture() {
            instance?.launchScreenCapturePrompt()
        }

        fun scanQrCode(onScanned: (String) -> Unit) {
            instance?.launchQrCameraScanner(onScanned)
        }
    }

    fun launchQrCameraScanner(onScanned: (String) -> Unit) {
        try {
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue
                    if (!raw.isNullOrBlank()) {
                        Log.i(TAG, "QR Code scanned successfully: $raw")
                        onScanned(raw)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "QR Scanner failed: ${e.message}")
                    Toast.makeText(this, "Kamera taraması başarısız: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "GmsBarcodeScanner not available: ${e.message}", e)
            Toast.makeText(this, "Kamera açılırken hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.i(TAG, "MediaProjection permission granted by user")
            AetherCoreService.instance?.startScreenCaptureWithProjection(
                result.resultCode,
                result.data!!
            )
            Toast.makeText(this, "Ekran Mac'e canlı aktarılıyor", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "MediaProjection permission rejected or cancelled")
            Toast.makeText(this, "Ekran yansıtma izni verilmedi", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchScreenCapturePrompt() {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager != null) {
                val intent = projectionManager.createScreenCaptureIntent()
                screenCaptureLauncher.launch(intent)
            } else {
                Log.e(TAG, "MediaProjectionManager service not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch screen capture intent: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // Automatically start the background foreground service on launch
        AetherCoreService.start(this)

        if (intent?.getBooleanExtra(EXTRA_REQUEST_SCREEN_CAPTURE, false) == true) {
            launchScreenCapturePrompt()
        }

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_SCREEN_CAPTURE, false)) {
            launchScreenCapturePrompt()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }
}
