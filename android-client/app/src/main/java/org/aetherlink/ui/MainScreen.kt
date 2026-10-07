package org.aetherlink.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.aetherlink.discovery.AetherNsdDiscovery
import org.aetherlink.permission.AetherPermissionManager
import org.aetherlink.permission.PermissionOnboardingDialog
import org.aetherlink.permission.PermissionStatusBanner
import org.aetherlink.screen.ScreenStreamManager
import org.aetherlink.service.AetherCoreService
import org.aetherlink.service.MacBatteryData
import org.aetherlink.service.MacTelemetryData
import org.aetherlink.service.PhoneBatteryData
import org.aetherlink.telemetry.DeviceTelemetryManager
import org.aetherlink.updater.AndroidUpdateChecker
import org.aetherlink.updater.UpdateCheckResult
import org.aetherlink.updater.UpdateInfo
import org.aetherlink.findmyphone.FindMyPhoneManager
import org.aetherlink.volume.RemoteVolumeManager
import org.aetherlink.lock.RemoteLockManager
import org.aetherlink.ping.PingManager
import org.aetherlink.util.AppThemeMode
import org.aetherlink.util.ThemePreferences

enum class AetherTab(val title: String) {
    DASHBOARD("Genel"),
    CONTINUITY("Süreklilik"),
    REMOTE("Kumanda"),
    SETTINGS("Ayarlar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onReplayOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AetherTab.DASHBOARD) }
    val prefs = remember { context.getSharedPreferences("aetherlink_prefs", Context.MODE_PRIVATE) }
    var macIpInput by remember {
        mutableStateOf(prefs.getString("last_mac_ip", null) ?: "192.168.1.15")
    }
    var discoveredMacName by remember { mutableStateOf<String?>(null) }
    var discoveredMacIp by remember { mutableStateOf<String?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var showAdvancedConnection by remember { mutableStateOf(false) }

    var isScreenStreaming by remember { mutableStateOf(ScreenStreamManager.isStreaming) }
    var isCameraStreaming by remember { mutableStateOf(org.aetherlink.service.AetherCameraService.isStreaming) }
    var cameraLens by remember { mutableStateOf(org.aetherlink.service.AetherCameraService.currentLens) }
    var isTorchOn by remember { mutableStateOf(org.aetherlink.service.AetherCameraService.isTorchOn) }

    // Dialog & Sheet states
    var showPairingDialog by remember { mutableStateOf(false) }
    var showTelemetryDialog by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("482 915") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingApk by remember { mutableStateOf(false) }
    var apkDownloadProgress by remember { mutableStateOf(0f) }
    var apkDownloadError by remember { mutableStateOf<String?>(null) }

    val macBattery by AetherCoreService.macBatteryState.collectAsState()
    val macTelemetry by AetherCoreService.macTelemetryState.collectAsState()
    val phoneBattery by AetherCoreService.phoneBatteryState.collectAsState()
    val isServiceConnected by AetherCoreService.isConnectedState.collectAsState()

    // KDE Connect / Continuity Feature States
    val isPhoneRinging by FindMyPhoneManager.isPhoneRinging.collectAsState()
    val isMacRinging by FindMyPhoneManager.isMacRinging.collectAsState()
    val pingLatency by PingManager.pingLatencyMs.collectAsState()
    val isPinging by PingManager.isPinging.collectAsState()
    val macVolume by RemoteVolumeManager.macVolume.collectAsState()
    val isCaffeinateActive by org.aetherlink.caffeinate.CaffeinateManager.isCaffeinateActive.collectAsState()
    val isBtPaired by org.aetherlink.bluetooth.BluetoothAudioManager.isPairedWithMac.collectAsState()

    // Bottom Sheets
    var showTrackpadSheet by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showPresenterSheet by remember { mutableStateOf(false) }
    var showCommandsSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (cameraGranted) {
            org.aetherlink.service.AetherCameraService.start(
                context = context,
                mic = audioGranted
            )
            isCameraStreaming = true
            Toast.makeText(context, "Süreklilik Kamerası başlatıldı", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Kamera izni verilmedi. Ayarlardan izin verebilirsiniz.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Category Expand/Collapse States (Useful & simple organization)
    var isPowerSectionExpanded by remember { mutableStateOf(true) }
    var isRemoteSectionExpanded by remember { mutableStateOf(true) }
    var isMediaSectionExpanded by remember { mutableStateOf(true) }
    var isSystemSectionExpanded by remember { mutableStateOf(true) }
    var isPermissionsExpanded by remember { mutableStateOf(false) }

    // Automatic update check on app launch
    LaunchedEffect(Unit) {
        val result = AndroidUpdateChecker.check(context)
        if (result is UpdateCheckResult.Available) {
            if (!AndroidUpdateChecker.isVersionDismissed(context, result.info.latestVersion)) {
                updateInfo = result.info
            }
        }
    }

    // Runtime Permission State
    var permissionStatus by remember { mutableStateOf(AetherPermissionManager.checkAllPermissions(context)) }
    var showPermissionDialog by remember { mutableStateOf(!permissionStatus.allCoreGranted) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionStatus = AetherPermissionManager.checkAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            isScreenStreaming = ScreenStreamManager.isStreaming
            isCameraStreaming = org.aetherlink.service.AetherCameraService.isStreaming
            cameraLens = org.aetherlink.service.AetherCameraService.currentLens
            isTorchOn = org.aetherlink.service.AetherCameraService.isTorchOn
            delay(500)
        }
    }

    // mDNS Discovery for local Mac
    DisposableEffect(Unit) {
        val discovery = AetherNsdDiscovery(context) { name, host, _ ->
            discoveredMacName = name
            discoveredMacIp = host
            macIpInput = host
        }
        discovery.startDiscovery()
        isDiscovering = true
        onDispose {
            discovery.stopDiscovery()
            isDiscovering = false
        }
    }

    if (showPermissionDialog) {
        PermissionOnboardingDialog(
            status = permissionStatus,
            onDismiss = { showPermissionDialog = false },
            onRefresh = {
                permissionStatus = AetherPermissionManager.checkAllPermissions(context)
                if (permissionStatus.allCoreGranted) {
                    showPermissionDialog = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "AetherLink",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isServiceConnected) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (isServiceConnected) (discoveredMacName ?: "Mac'e Bağlı") else "Bağlantı Yok",
                                    fontSize = 11.sp,
                                    color = if (isServiceConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!permissionStatus.allCoreGranted) {
                        IconButton(onClick = { showPermissionDialog = true }) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = Color(0xFFEF4444))
                                }
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = "Eksik İzinler",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }

                    IconButton(onClick = onReplayOnboarding) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Rehber ve Tanıtım",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                tonalElevation = 8.dp
            ) {
                val tabItems = listOf(
                    Triple(AetherTab.DASHBOARD, "Genel", Icons.Default.Dashboard),
                    Triple(AetherTab.CONTINUITY, "Süreklilik", Icons.Default.CastConnected),
                    Triple(AetherTab.REMOTE, "Kumanda", Icons.Default.Devices),
                    Triple(AetherTab.SETTINGS, "Ayarlar", Icons.Default.Settings)
                )
                tabItems.forEach { (tab, label, icon) ->
                    val isSelected = (selectedTab == tab)
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(icon, contentDescription = label)
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "tab_content_animation"
        ) { currentTab ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (currentTab) {
                    AetherTab.DASHBOARD -> {
                        // Priority Alert Banner: Find My Phone Ringing
                        if (isPhoneRinging) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFBBF24)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            "Mac Telefonunuzu Çaldırıyor!",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Button(
                                        onClick = { FindMyPhoneManager.stopRinging(context, notifyMac = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Sustur", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Optional Missing Permission Banner (Only if missing)
                        if (!permissionStatus.allCoreGranted) {
                            PermissionStatusBanner(
                                status = permissionStatus,
                                onClick = { showPermissionDialog = true }
                            )
                        }

                        // BÖLÜM 1: 🔗 BAĞLANTI & EŞLEŞTİRME
                        ConnectionCategoryCard(
                            isConnected = isServiceConnected,
                            discoveredMacName = discoveredMacName,
                            discoveredMacIp = discoveredMacIp,
                            macIpInput = macIpInput,
                            onMacIpChange = { macIpInput = it },
                            showAdvanced = showAdvancedConnection,
                            onToggleAdvanced = { showAdvancedConnection = !showAdvancedConnection },
                            onConnect = {
                                val ip = discoveredMacIp ?: macIpInput.trim()
                                AetherCoreService.instance?.connectToMacWebSocket(ip)
                            },
                            onDisconnect = {
                                AetherCoreService.instance?.disconnect(userInitiated = true, forget = false)
                            },
                            onForgetPairing = {
                                AetherCoreService.instance?.disconnect(userInitiated = true, forget = true)
                            },
                            onStartPairing = {
                                pairingCode = String.format("%03d %03d", (100..999).random(), (100..999).random())
                                val payload = JsonObject().apply {
                                    addProperty("deviceId", org.aetherlink.util.DeviceUtils.getDeviceId())
                                    addProperty("deviceName", org.aetherlink.util.DeviceUtils.getDeviceName())
                                    addProperty("confirmationCode", pairingCode)
                                    addProperty("timestamp", System.currentTimeMillis())
                                }
                                AetherCoreService.instance?.sendMessage("PAIRING_REQUEST", payload)
                                showPairingDialog = true
                            },
                            onScanQr = {
                                MainActivity.scanQrCode { raw ->
                                    try {
                                        val uri = Uri.parse(raw)
                                        if (uri.scheme == "aetherlink" && uri.host == "pair") {
                                            val ip = uri.getQueryParameter("ip") ?: macIpInput
                                            val code = uri.getQueryParameter("code") ?: "123456"
                                            val name = uri.getQueryParameter("name") ?: "MacBook"
                                            macIpInput = ip
                                            AetherCoreService.instance?.connectToMacWebSocket(ip)
                                            val payload = JsonObject().apply {
                                                addProperty("deviceId", org.aetherlink.util.DeviceUtils.getDeviceId())
                                                addProperty("deviceName", org.aetherlink.util.DeviceUtils.getDeviceName())
                                                addProperty("confirmationCode", code)
                                                addProperty("timestamp", System.currentTimeMillis())
                                            }
                                            AetherCoreService.instance?.sendMessage("PAIRING_REQUEST", payload)
                                            Toast.makeText(context, "$name QR Kodu Başarıyla Eşleşildi!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Tanınmayan QR formatı: $raw", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "QR İşleme Hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        // BÖLÜM 2: ⚡ GÜÇ & PİL YÖNETİMİ
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "Güç & Pil Durumu",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Mac ve telefon şarj telemetrisi",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                    if (isServiceConnected) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Canlı Veri",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                PowerManagementContent(
                                    isConnected = isServiceConnected,
                                    macBattery = macBattery,
                                    macTelemetry = macTelemetry,
                                    phoneBattery = phoneBattery,
                                    isCaffeinateActive = isCaffeinateActive,
                                    onToggleCaffeinate = {
                                        org.aetherlink.caffeinate.CaffeinateManager.toggleCaffeinate()
                                    },
                                    onRefresh = {
                                        AetherCoreService.instance?.requestMacBattery()
                                        AetherCoreService.instance?.requestMacTelemetry()
                                    }
                                )
                            }
                        }

                        // BÖLÜM 3: ⚡ HIZLI EYLEMLER (2x2 Grid)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Hızlı Araçlar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Ağ Gecikmesi (Ping RTT)
                                ActionButtonTile(
                                    title = pingLatency?.let { "${it.toInt()} ms" } ?: (if (isServiceConnected) "Ping Testi" else "Bağlantı Yok"),
                                    subtitle = "Ağ Gecikmesi",
                                    icon = Icons.Default.Speed,
                                    iconTint = Color(0xFF06B6D4),
                                    enabled = isServiceConnected,
                                    onClick = { PingManager.sendPing() },
                                    modifier = Modifier.weight(1f)
                                )

                                // Kafein Modu
                                ActionButtonTile(
                                    title = if (isCaffeinateActive) "Kafein Açık" else "Kafein Modu",
                                    subtitle = if (isCaffeinateActive) "Uyanık Tutuluyor" else "Standart Uyku",
                                    icon = if (isCaffeinateActive) Icons.Default.Coffee else Icons.Default.CoffeeMaker,
                                    iconTint = if (isCaffeinateActive) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                    enabled = true,
                                    onClick = { org.aetherlink.caffeinate.CaffeinateManager.toggleCaffeinate() },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Mac'i Çaldır
                                ActionButtonTile(
                                    title = if (isMacRinging) "Mac'i Sustur" else "Mac'i Çaldır",
                                    subtitle = "Cihazı Bul",
                                    icon = if (isMacRinging) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                                    iconTint = if (isMacRinging) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                    enabled = isServiceConnected,
                                    onClick = { FindMyPhoneManager.toggleRingMac() },
                                    modifier = Modifier.weight(1f)
                                )

                                // Mac'i Kilitle
                                ActionButtonTile(
                                    title = "Mac'i Kilitle",
                                    subtitle = "Ekranı Kapat",
                                    icon = Icons.Default.Lock,
                                    iconTint = Color(0xFFEF4444),
                                    enabled = isServiceConnected,
                                    onClick = {
                                        RemoteLockManager.lockMac()
                                        Toast.makeText(context, "Mac ekranı kilitlendi", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    AetherTab.CONTINUITY -> {
                        // SÜREKLİLİK & MEDYA KÖPRÜSÜ
                        MediaAndScreenContent(
                            isConnected = isServiceConnected,
                            isScreenStreaming = isScreenStreaming,
                            onToggleScreenStream = {
                                if (ScreenStreamManager.isStreaming) {
                                    AetherCoreService.instance?.stopScreenCapture()
                                    isScreenStreaming = false
                                } else {
                                    MainActivity.requestScreenCapture()
                                }
                            },
                            isCameraStreaming = isCameraStreaming,
                            isTorchOn = isTorchOn,
                            onToggleCameraStream = {
                                if (isCameraStreaming) {
                                    org.aetherlink.service.AetherCameraService.stop(context)
                                    isCameraStreaming = false
                                } else {
                                    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                    val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (hasCamera) {
                                        org.aetherlink.service.AetherCameraService.start(
                                            context = context,
                                            mic = hasAudio
                                        )
                                        isCameraStreaming = true
                                    } else {
                                        cameraPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                        )
                                    }
                                }
                            },
                            onSwitchCameraLens = {
                                org.aetherlink.service.AetherCameraService.switchLens(context)
                                cameraLens = org.aetherlink.service.AetherCameraService.currentLens
                            },
                            onToggleTorch = {
                                org.aetherlink.service.AetherCameraService.toggleTorch(context)
                                isTorchOn = org.aetherlink.service.AetherCameraService.isTorchOn
                            },
                            isBtPaired = isBtPaired,
                            onManageBluetooth = {
                                org.aetherlink.bluetooth.BluetoothAudioManager.initiateBonding(context)
                                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                context.startActivity(intent)
                            },
                            onOpenAetherDrop = { showShareSheet = true }
                        )
                    }

                    AetherTab.REMOTE -> {
                        // UZAKTAN KONTROL & SUNUM
                        if (!isServiceConnected) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Mac'e bağlandığınızda Touchpad, Sunum ve Sistem komutları aktifleşir.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        RemoteControlContent(
                            isEnabled = isServiceConnected,
                            isMacRinging = isMacRinging,
                            onToggleRingMac = { FindMyPhoneManager.toggleRingMac() },
                            onOpenTrackpad = { showTrackpadSheet = true },
                            onLockMac = {
                                RemoteLockManager.lockMac()
                                Toast.makeText(context, "Mac ekranı kilitlendi", Toast.LENGTH_SHORT).show()
                            },
                            onOpenVolume = { showVolumeDialog = true },
                            onOpenPresenter = { showPresenterSheet = true },
                            onOpenCommands = { showCommandsSheet = true }
                        )
                    }

                    AetherTab.SETTINGS -> {
                        // 1. Theme Selection Card
                        ThemeSelectionCard(context = context)

                        // 2. System & Network Telemetry Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF06B6D4).copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Speed,
                                                    contentDescription = null,
                                                    tint = Color(0xFF06B6D4),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "Sistem & Donanım Telemetrisi",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Ağ gecikmesi ve ısı sensörleri",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                            )
                                        }
                                    }

                                    pingLatency?.let {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF06B6D4).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${it.toInt()} ms",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF06B6D4),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                SystemAndNetworkContent(
                                    isConnected = isServiceConnected,
                                    pingLatency = pingLatency,
                                    isPinging = isPinging,
                                    onSendPing = { PingManager.sendPing() },
                                    phoneBattery = phoneBattery,
                                    macTelemetry = macTelemetry,
                                    onOpenDetails = { showTelemetryDialog = true },
                                    onRefreshSensors = {
                                        DeviceTelemetryManager.dispatchTelemetry(context)
                                        AetherCoreService.instance?.requestMacBattery()
                                        AetherCoreService.instance?.requestMacTelemetry()
                                    }
                                )
                            }
                        }

                        // 3. System Permissions Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = (if (permissionStatus.allCoreGranted) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Shield,
                                                    contentDescription = null,
                                                    tint = if (permissionStatus.allCoreGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "Sistem İzinleri & Güvenlik",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Süreklilik köprüsü için gereken izinler",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = (if (permissionStatus.allCoreGranted) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (permissionStatus.allCoreGranted) "Tam Yetkili" else "İzin Gerekli",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (permissionStatus.allCoreGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                PermissionsContent(
                                    allGranted = permissionStatus.allCoreGranted,
                                    onOpenNotificationListener = {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    },
                                    onOpenAppSettings = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    },
                                    onOpenBatteryOptimization = {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    onOpenAccessibility = {
                                        AetherPermissionManager.openAccessibilitySettings(context)
                                    }
                                )
                            }
                        }

                        // 4. Hakkında & Sürüm Bilgisi
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "AetherLink v${AndroidUpdateChecker.getAppVersion(context)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Açık Kaynaklı Süreklilik Köprüsü",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isCheckingUpdate = true
                                            when (val result = AndroidUpdateChecker.check(context)) {
                                                is UpdateCheckResult.Available -> {
                                                    updateInfo = result.info
                                                }
                                                is UpdateCheckResult.UpToDate -> {
                                                    Toast.makeText(context, "AetherLink güncel (v${result.currentVersion}).", Toast.LENGTH_SHORT).show()
                                                }
                                                is UpdateCheckResult.Error -> {
                                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            isCheckingUpdate = false
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    if (isCheckingUpdate) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Güncelle", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // =========================================================================
    // MODAL DIALOGS & BOTTOM SHEETS
    // =========================================================================

    // Pairing Confirmation Dialog Modal
    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Eşleştirme Onay Kodu", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mac ekranınızda beliren kod ile aşağıdaki kod uyuşuyor mu?")
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = pairingCode,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                    Text(
                        "Kodlar eşleşiyorsa Mac uygulamasından 'Onayla' butonuna tıklayın.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPairingDialog = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    // Update Dialog Modal
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingApk) {
                    updateInfo = null
                    apkDownloadError = null
                }
            },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(info.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "Mevcut: v${info.currentVersion}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Yeni: v${info.latestVersion}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text("Neler Yeni?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 180.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                info.changelog.ifBlank { "Performans iyileştirmeleri ve hata düzeltmeleri yapıldı." },
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (isDownloadingApk) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "İndiriliyor: %${(apkDownloadProgress * 100).toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = { apkDownloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    if (apkDownloadError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    apkDownloadError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isDownloadingApk,
                    onClick = {
                        if (!AetherPermissionManager.canRequestPackageInstalls(context)) {
                            Toast.makeText(context, "Güncellemeyi yükleyebilmek için lütfen 'Bilinmeyen Uygulamaları Yükle' iznini verin.", Toast.LENGTH_LONG).show()
                            AetherPermissionManager.openInstallPermissionSettings(context)
                        } else {
                            isDownloadingApk = true
                            apkDownloadError = null
                            coroutineScope.launch {
                                AndroidUpdateChecker.downloadAndInstallApk(
                                    context = context,
                                    downloadUrl = info.downloadUrl,
                                    version = info.latestVersion,
                                    onProgress = { progress -> apkDownloadProgress = progress },
                                    onSuccess = {
                                        isDownloadingApk = false
                                        updateInfo = null
                                    },
                                    onError = { err ->
                                        isDownloadingApk = false
                                        apkDownloadError = err
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(if (isDownloadingApk) "İndiriliyor..." else "Şimdi Güncelle (.apk)")
                }
            },
            dismissButton = {
                if (!isDownloadingApk) {
                    TextButton(onClick = {
                        AndroidUpdateChecker.dismissVersion(context, info.latestVersion)
                        updateInfo = null
                    }) {
                        Text("Daha Sonra")
                    }
                }
            }
        )
    }

    // Hardware & Thermal Telemetry Details Dialog Modal
    if (showTelemetryDialog) {
        val telemetry = remember(showTelemetryDialog) { DeviceTelemetryManager.collectTelemetry(context) }
        AlertDialog(
            onDismissRequest = { showTelemetryDialog = false },
            icon = { Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Donanım & Isı Telemetrisi", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mac Hardware Section
                    Text("Mac Durumu", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("İşlemci / Donanım Isısı", fontSize = 12.sp, color = Color.Gray)
                                val macTemp = macTelemetry?.temperatureCelsius
                                Text(
                                    if (macTemp != null) "${String.format(java.util.Locale.US, "%.1f", macTemp)}°C" else "Bağlantı Yok",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Termal Durum", fontSize = 12.sp, color = Color.Gray)
                                Text(macTelemetry?.thermalStatus ?: "NORMAL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Pil Seviyesi & Şarj", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    if (macBattery != null) "%${macBattery?.level} (${if (macBattery?.isCharging == true) "Şarjda" else "Pilde"})" else "--",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Phone Hardware Section
                    Text("Telefon Durumu (Bu Cihaz)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Model", fontSize = 12.sp, color = Color.Gray)
                                Text("${Build.MANUFACTURER} ${Build.MODEL}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Batarya Isısı", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    "${String.format(java.util.Locale.US, "%.1f", phoneBattery.temperatureCelsius)}°C",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Termal Durum", fontSize = 12.sp, color = Color.Gray)
                                Text(AetherCoreService.currentThermalStatus, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            val ramUsed = telemetry.get("ramUsedMB")?.asInt?.div(1024.0)
                            val ramTotal = telemetry.get("ramTotalMB")?.asInt?.div(1024.0)
                            if (ramUsed != null && ramTotal != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("RAM Kullanımı", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        "${String.format(java.util.Locale.US, "%.1f", ramUsed)} GB / ${String.format(java.util.Locale.US, "%.1f", ramTotal)} GB",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            val storageFree = telemetry.get("storageFreeGB")?.asDouble
                            val storageTotal = telemetry.get("storageTotalGB")?.asDouble
                            if (storageFree != null && storageTotal != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Depolama Alanı", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        "${String.format(java.util.Locale.US, "%.1f", storageFree)} GB Boş (${String.format(java.util.Locale.US, "%.1f", storageTotal)} GB)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    DeviceTelemetryManager.dispatchTelemetry(context)
                    AetherCoreService.instance?.requestMacBattery()
                    AetherCoreService.instance?.requestMacTelemetry()
                    Toast.makeText(context, "Sensör verileri yenilendi", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sensörleri Yenile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTelemetryDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    // Virtual Trackpad Bottom Sheet
    if (showTrackpadSheet) {
        RemoteTrackpadSheet(onDismiss = { showTrackpadSheet = false })
    }

    // Remote Volume Dialog
    if (showVolumeDialog) {
        var localMacVol by remember { mutableStateOf(macVolume.toFloat()) }
        var localPhoneVol by remember { mutableStateOf(RemoteVolumeManager.getPhoneVolumePercent(context).toFloat()) }

        AlertDialog(
            onDismissRequest = { showVolumeDialog = false },
            title = { Text("Uzaktan Ses Denetimi", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("MacBook Ses Düzeyi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("%${localMacVol.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = localMacVol,
                            onValueChange = {
                                localMacVol = it
                                RemoteVolumeManager.setMacVolume(it.toInt())
                            },
                            valueRange = 0f..100f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Telefon Medya Düzeyi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("%${localPhoneVol.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = localPhoneVol,
                            onValueChange = {
                                localPhoneVol = it
                                RemoteVolumeManager.setPhoneVolumeFromMac(context, it.toInt())
                            },
                            valueRange = 0f..100f
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showVolumeDialog = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    if (showPresenterSheet) {
        RemotePresenterSheet(onDismiss = { showPresenterSheet = false })
    }

    if (showCommandsSheet) {
        RemoteCommandsSheet(onDismiss = { showCommandsSheet = false })
    }

    if (showShareSheet) {
        AetherShareSheet(onDismiss = { showShareSheet = false })
    }
}

// =============================================================================
// REUSABLE CATEGORY COMPONENTS (Sade, Dikkat Dağıtmayan Gruplandırma)
// =============================================================================

@Composable
fun CategorySection(
    title: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    isExpanded: Boolean = true,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Header Row (Clickable to collapse/expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    content()
                }
            }
        }
    }
}

// 1. Connection Card (Bağlantı ve Eşleştirme)
@Composable
fun ConnectionCategoryCard(
    isConnected: Boolean,
    discoveredMacName: String?,
    discoveredMacIp: String?,
    macIpInput: String,
    onMacIpChange: (String) -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onForgetPairing: () -> Unit,
    onStartPairing: () -> Unit,
    onScanQr: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Connection Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                CircleShape
                            )
                    )
                    Column {
                        Text(
                            text = if (isConnected) (discoveredMacName ?: "Mac'e Bağlı") else "Mac Bağlantısı Bekleniyor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isConnected) "Yerel Ağ Eşitlemesi Aktif • AES-256" else "Bağlantı kurulduğunda araçlar otomatik aktifleşir",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kes", fontSize = 12.sp)
                    }
                }
            }

            // If local Mac is discovered via mDNS and not yet connected, show quick 1-tap connect banner
            if (!isConnected && discoveredMacName != null && discoveredMacIp != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LaptopMac,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(discoveredMacName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Ağda Bulundu ($discoveredMacIp)", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Button(
                            onClick = onConnect,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Bağlan", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick Connect / QR Actions (Always easy to access)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onScanQr,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mac QR Tara", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                if (!isConnected) {
                    OutlinedButton(
                        onClick = onConnect,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bağlan", fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartPairing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eşleştir", fontSize = 13.sp)
                    }
                }
            }

            // Advanced / Manual IP toggle (Keeps UI clean and uncluttered)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleAdvanced() }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Manuel IP ve Gelişmiş Ayarlar",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = macIpInput,
                        onValueChange = onMacIpChange,
                        label = { Text("Mac Yerel IP Adresi", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onForgetPairing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Eşleşmeyi Sil", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onConnect,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("IP ile Bağlan", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// 2. Power & Battery Content
@Composable
fun PowerManagementContent(
    isConnected: Boolean,
    macBattery: MacBatteryData?,
    macTelemetry: MacTelemetryData?,
    phoneBattery: PhoneBatteryData,
    isCaffeinateActive: Boolean,
    onToggleCaffeinate: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // MacBook Pro Battery Row
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Laptop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text("MacBook Pro", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        val desc = if (!isConnected || macBattery == null) {
                            "Bağlantı Bekleniyor..."
                        } else if (macBattery.isCharging) {
                            "Şarj Oluyor (Prize Takılı)"
                        } else if (macBattery.isPluggedIn) {
                            "Prize Takılı (Tam Dolu)"
                        } else {
                            "Pilde Çalışıyor"
                        }
                        Text(
                            desc,
                            fontSize = 11.sp,
                            color = if (macBattery?.isCharging == true) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val macTemp = macTelemetry?.temperatureCelsius
                    if (macTemp != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (if (macTemp < 55.0) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.15f)
                        ) {
                            Text(
                                String.format(java.util.Locale.US, "%.1f°C", macTemp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (macTemp < 55.0) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        if (macBattery != null) "%${macBattery.level}" else "--",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (macBattery?.isCharging == true) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        if (macBattery?.isCharging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (macBattery?.isCharging == true) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Phone Battery Row
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "${org.aetherlink.util.DeviceUtils.getDeviceName()} (Bu Cihaz)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val phoneDesc = if (phoneBattery.isCharging) {
                            "Şarj Oluyor"
                        } else if (phoneBattery.isPluggedIn) {
                            "Prize Takılı (%80 Koruma)"
                        } else {
                            "Pilde Çalışıyor"
                        }
                        Text(
                            phoneDesc,
                            fontSize = 11.sp,
                            color = if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val phoneTemp = phoneBattery.temperatureCelsius
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (phoneTemp < 38.0) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.15f)
                    ) {
                        Text(
                            String.format(java.util.Locale.US, "%.1f°C", phoneTemp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (phoneTemp < 38.0) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        "%${phoneBattery.level}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Quick Caffeinate & Refresh Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleCaffeinate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCaffeinateActive) Color(0xFFF59E0B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isCaffeinateActive) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isCaffeinateActive) Color(0xFFD97706) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    if (isCaffeinateActive) Icons.Default.Coffee else Icons.Default.CoffeeMaker,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isCaffeinateActive) "Kafein Açık (Uyanık)" else "Kafein Modu", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(42.dp)
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// 3. Remote Control Content (2x3 Grid)
@Composable
fun RemoteControlContent(
    isEnabled: Boolean,
    isMacRinging: Boolean,
    onToggleRingMac: () -> Unit,
    onOpenTrackpad: () -> Unit,
    onLockMac: () -> Unit,
    onOpenVolume: () -> Unit,
    onOpenPresenter: () -> Unit,
    onOpenCommands: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Touchpad
            ActionButtonTile(
                title = "Touchpad",
                subtitle = "İmleç & Klavye",
                icon = Icons.Default.TouchApp,
                iconTint = MaterialTheme.colorScheme.primary,
                enabled = isEnabled,
                onClick = onOpenTrackpad,
                modifier = Modifier.weight(1f)
            )

            // Sunum Kumandası
            ActionButtonTile(
                title = "Sunum",
                subtitle = "Keynote / PPT",
                icon = Icons.Default.CoPresent,
                iconTint = Color(0xFF8B5CF6),
                enabled = isEnabled,
                onClick = onOpenPresenter,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mac Komutları
            ActionButtonTile(
                title = "Mac Komutları",
                subtitle = "Terminal İşlemleri",
                icon = Icons.Default.Terminal,
                iconTint = Color(0xFF06B6D4),
                enabled = isEnabled,
                onClick = onOpenCommands,
                modifier = Modifier.weight(1f)
            )

            // Ses Denetimi
            ActionButtonTile(
                title = "Ses Denetimi",
                subtitle = "Hoparlör & Medya",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconTint = Color(0xFF10B981),
                enabled = isEnabled,
                onClick = onOpenVolume,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mac'i Çaldır / Sustur
            ActionButtonTile(
                title = if (isMacRinging) "Mac'i Sustur" else "Mac'i Çaldır",
                subtitle = "Cihazı Bul",
                icon = if (isMacRinging) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                iconTint = if (isMacRinging) Color(0xFFEF4444) else Color(0xFFF59E0B),
                enabled = isEnabled,
                onClick = onToggleRingMac,
                modifier = Modifier.weight(1f)
            )

            // Mac'i Kilitle
            ActionButtonTile(
                title = "Mac'i Kilitle",
                subtitle = "Ekranı Kilitle",
                icon = Icons.Default.Lock,
                iconTint = Color(0xFFEF4444),
                enabled = isEnabled,
                onClick = onLockMac,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// 4. Media & Screen Content
@Composable
fun MediaAndScreenContent(
    isConnected: Boolean,
    isScreenStreaming: Boolean,
    onToggleScreenStream: () -> Unit,
    isCameraStreaming: Boolean,
    isTorchOn: Boolean,
    onToggleCameraStream: () -> Unit,
    onSwitchCameraLens: () -> Unit,
    onToggleTorch: () -> Unit,
    isBtPaired: Boolean,
    onManageBluetooth: () -> Unit,
    onOpenAetherDrop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Feature 1: Screen Mirroring (Scrcpy 60 FPS)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = (if (isScreenStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CastConnected,
                                contentDescription = null,
                                tint = if (isScreenStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Ekran Yansıtma", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (isScreenStreaming) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text("60 FPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text("Mac ekranına ultra düşük gecikmeli canlı akış", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                    }
                }
                Button(
                    onClick = onToggleScreenStream,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScreenStreaming) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        if (isScreenStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isScreenStreaming) "Durdur" else "Yansıt", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Feature 2: Continuity Camera & Studio Mic
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = (if (isCameraStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = if (isCameraStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Süreklilik Kamerası", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = (if (isCameraStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)
                                ) {
                                    Text(if (isCameraStreaming) "1080p Yayında" else "1080p HD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isCameraStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                            Text("Mac için kablosuz web kamera & stüdyo mikrofonu", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                        }
                    }

                    Button(
                        onClick = onToggleCameraStream,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCameraStreaming) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (isCameraStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isCameraStreaming) "Durdur" else "Başlat", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isCameraStreaming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSwitchCameraLens,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lens Değiştir", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onToggleTorch,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isTorchOn) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTorchOn) "Flaş Açık" else "Flaş", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Feature 3: Universal Clipboard Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF06B6D4).copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Evrensel Pano", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text("Canlı Senkron", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        Text("Mac ve telefon arasında iki yönlü anlık metin kopyalama", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                    }
                }
            }
        }

        // Feature 3.5: Zero-ADB AetherAudio Wireless Audio & Call Bridge Card
        val context = androidx.compose.ui.platform.LocalContext.current
        val isAudioStreaming = org.aetherlink.service.AetherAudioService.streamingState.value
        val audioMode = org.aetherlink.service.AetherAudioService.modeState.value

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (isAudioStreaming) Color(0xFF8B5CF6).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = (if (isAudioStreaming) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = if (isAudioStreaming) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("AetherAudio Ses Köprüsü", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = (if (isAudioStreaming) Color(0xFF8B5CF6) else Color(0xFF10B981)).copy(alpha = 0.15f)
                                ) {
                                    Text(if (isAudioStreaming) "Canlı Akış" else "Sıfır-ADB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isAudioStreaming) Color(0xFF8B5CF6) else Color(0xFF10B981), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                            Text("Kablosuz medya ses aktarımı ve Mac'ten telefon görüşmesi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                        }
                    }

                    Switch(
                        checked = isAudioStreaming,
                        onCheckedChange = { enable ->
                            if (enable) {
                                org.aetherlink.service.AetherAudioService.start(context, audioMode)
                            } else {
                                org.aetherlink.service.AetherAudioService.stop(context)
                            }
                        }
                    )
                }

                if (isAudioStreaming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        org.aetherlink.service.AetherAudioService.AudioMode.values().forEach { mode ->
                            val isSelected = (audioMode == mode)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        org.aetherlink.service.AetherAudioService.setMode(context, mode)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF8B5CF6) else Color.Transparent)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feature 4: Bluetooth Audio & AetherDrop Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bluetooth Audio Sync
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onManageBluetooth() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text("Bluetooth Ses", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (isBtPaired) "Ses Aktif" else "Eşleşme Ayarla", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                    }
                }
            }

            // AetherDrop Share
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenAetherDrop() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text("AetherDrop", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Dosya & Paylaşım", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}


// 5. System & Network Content
@Composable
fun SystemAndNetworkContent(
    isConnected: Boolean,
    pingLatency: Double?,
    isPinging: Boolean,
    onSendPing: () -> Unit,
    phoneBattery: PhoneBatteryData,
    macTelemetry: MacTelemetryData?,
    onOpenDetails: () -> Unit,
    onRefreshSensors: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Ping Test Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text("Yerel Ağ Gecikmesi (RTT)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        val pingDesc = if (isPinging) {
                            "Ping ölçülüyor..."
                        } else if (pingLatency != null) {
                            String.format(java.util.Locale.US, "%.1f ms gecikme", pingLatency)
                        } else {
                            "Ölçüm yapılmadı"
                        }
                        Text(pingDesc, fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Button(
                    onClick = onSendPing,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (isPinging) "..." else "Ping Testi", fontSize = 12.sp)
                }
            }
        }

        // Hardware Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Telefon Isısı", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${String.format(java.util.Locale.US, "%.1f", phoneBattery.temperatureCelsius)}°C",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Mac Isısı", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    val macTemp = macTelemetry?.temperatureCelsius
                    Text(
                        if (macTemp != null) "${String.format(java.util.Locale.US, "%.1f", macTemp)}°C" else "--",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Action Buttons: Detaylar ve Sensörleri Yenile
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenDetails,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tüm Detaylar", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onRefreshSensors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sensörleri Yenile", fontSize = 12.sp)
            }
        }
    }
}

// 6. Permissions Content
@Composable
fun PermissionsContent(
    allGranted: Boolean,
    onOpenNotificationListener: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermissionRow(
            title = "Bildirim Dinleyici",
            desc = "WhatsApp/SMS mesajlarını Mac'e iletir",
            icon = Icons.Default.Notifications,
            onClick = onOpenNotificationListener
        )

        PermissionRow(
            title = "Telefon ve Arama Yönetimi",
            desc = "Gelen aramaları Mac üzerinden yanıtlama",
            icon = Icons.Default.Call,
            onClick = onOpenAppSettings
        )

        PermissionRow(
            title = "Pil Tasarrufu Muafiyeti",
            desc = "Arka planda kesintisiz bağlantı sağlar",
            icon = Icons.Default.BatteryChargingFull,
            onClick = onOpenBatteryOptimization
        )

        PermissionRow(
            title = "Erişilebilirlik Servisi",
            desc = "Evrensel Pano senkronu ve ekran kilidi",
            icon = Icons.Default.ContentPaste,
            onClick = onOpenAccessibility
        )
    }
}

@Composable
fun PermissionRow(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                }
            }

            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text("Ayarla", fontSize = 12.sp)
            }
        }
    }
}


// Reusable Action Tile for Grid
@Composable
fun ActionButtonTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.55f else 0.25f)
        ),
        shadowElevation = if (enabled) 1.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (enabled) {
                    onClick()
                } else {
                    Toast.makeText(context, "$title için önce Mac'e bağlanmalısınız.", Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = if (enabled) 0.16f else 0.08f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.85f else 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ThemeSelectionCard(context: Context) {
    val currentTheme = ThemePreferences.themeModeState.value

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Uygulama Teması",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Arayüz rengini ve kontrastını özelleştirin",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.values().forEach { mode ->
                    val isSelected = currentTheme == mode
                    val icon = when (mode) {
                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                        AppThemeMode.DARK -> Icons.Default.DarkMode
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                ThemePreferences.setThemeMode(context, mode)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 11.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = mode.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

