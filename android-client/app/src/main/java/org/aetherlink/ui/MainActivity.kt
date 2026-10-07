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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.aetherlink.ui.onboarding.OnboardingPreferences
import org.aetherlink.ui.onboarding.OnboardingScreen

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

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val grantedCount = results.values.count { it }
        Log.i(TAG, "Runtime permissions request completed: $grantedCount / ${results.size} granted")
    }

    private fun requestMissingPermissionsOnLaunch() {
        val required = org.aetherlink.permission.AetherPermissionManager.getRequiredRuntimePermissions()
        val missing = required.filter { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(this, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Log.i(TAG, "Requesting missing permissions on startup: $missing")
            runtimePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // Automatically start the background foreground service on launch
        AetherCoreService.start(this)
        requestMissingPermissionsOnLaunch()
        org.aetherlink.util.ThemePreferences.init(this)

        if (intent?.getBooleanExtra(EXTRA_REQUEST_SCREEN_CAPTURE, false) == true) {
            launchScreenCapturePrompt()
        }

        setContent {
            val currentThemeMode by org.aetherlink.util.ThemePreferences.themeModeState
            val isDark = when (currentThemeMode) {
                org.aetherlink.util.AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                org.aetherlink.util.AppThemeMode.LIGHT -> false
                org.aetherlink.util.AppThemeMode.DARK -> true
            }
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                var isOnboardingCompleted by remember {
                    mutableStateOf(OnboardingPreferences.isOnboardingCompleted(this))
                }

                if (!isOnboardingCompleted) {
                    OnboardingScreen(
                        onComplete = {
                            OnboardingPreferences.setOnboardingCompleted(this, true)
                            isOnboardingCompleted = true
                        }
                    )
                } else {
                    MainScreen(
                        onReplayOnboarding = {
                            isOnboardingCompleted = false
                        }
                    )
                }
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
