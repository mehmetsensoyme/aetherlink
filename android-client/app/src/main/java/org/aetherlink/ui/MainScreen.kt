package org.aetherlink.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.aetherlink.telemetry.DeviceTelemetryManager
import org.aetherlink.updater.AndroidUpdateChecker
import org.aetherlink.updater.UpdateCheckResult
import org.aetherlink.updater.UpdateInfo
import org.aetherlink.findmyphone.FindMyPhoneManager
import org.aetherlink.volume.RemoteVolumeManager
import org.aetherlink.lock.RemoteLockManager
import org.aetherlink.ping.PingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onReplayOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("aetherlink_prefs", android.content.Context.MODE_PRIVATE) }
    var macIpInput by remember {
        mutableStateOf(prefs.getString("last_mac_ip", null) ?: "192.168.1.15")
    }
    var discoveredMacName by remember { mutableStateOf<String?>(null) }
    var discoveredMacIp by remember { mutableStateOf<String?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var isScreenStreaming by remember { mutableStateOf(ScreenStreamManager.isStreaming) }
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

    // KDE Connect Ported Feature States
    val isPhoneRinging by FindMyPhoneManager.isPhoneRinging.collectAsState()
    val isMacRinging by FindMyPhoneManager.isMacRinging.collectAsState()
    val pingLatency by PingManager.pingLatencyMs.collectAsState()
    val isPinging by PingManager.isPinging.collectAsState()
    val macVolume by RemoteVolumeManager.macVolume.collectAsState()
    val isCaffeinateActive by org.aetherlink.caffeinate.CaffeinateManager.isCaffeinateActive.collectAsState()
    var showTrackpadSheet by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showPresenterSheet by remember { mutableStateOf(false) }
    var showCommandsSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    // Automatic update check on app launch (silent background check)
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

    // Re-check permissions on resume (e.g. returning from app settings)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionStatus = AetherPermissionManager.checkAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            isScreenStreaming = ScreenStreamManager.isStreaming
            delay(500)
        }
    }

    // Start mDNS Discovery
    DisposableEffect(Unit) {
        val discovery = AetherNsdDiscovery(context) { name, host, port ->
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AetherLink", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onReplayOnboarding) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Karşılama ve Tanıtım")
                    }

                    IconButton(onClick = { showPermissionDialog = true }) {
                        BadgedBox(
                            badge = {
                                if (!permissionStatus.allCoreGranted) {
                                    Badge(containerColor = Color(0xFFE65100))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "İzinler ve Güvenlik")
                        }
                    }

                    IconButton(onClick = {
                        coroutineScope.launch {
                            isCheckingUpdate = true
                            when (val result = AndroidUpdateChecker.check(context)) {
                                is UpdateCheckResult.Available -> {
                                    updateInfo = result.info
                                }
                                is UpdateCheckResult.UpToDate -> {
                                    Toast.makeText(context, "AetherLink en güncel sürümde (v${result.currentVersion}).", Toast.LENGTH_SHORT).show()
                                }
                                is UpdateCheckResult.Error -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                            isCheckingUpdate = false
                        }
                    }) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Güncellemeleri Denetle")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Status Warning Banner (Visible if any core permission is missing)
            PermissionStatusBanner(
                status = permissionStatus,
                onClick = { showPermissionDialog = true }
            )

            // Find My Phone Active Ringing Alert Banner
            if (isPhoneRinging) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEB3B)),
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mac Telefonunuzu Çaldırıyor!", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                        }
                        Button(
                            onClick = { FindMyPhoneManager.stopRinging(context, notifyMac = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text("Sustur", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Discovered Mac Banner (mDNS Bonjour)
            if (discoveredMacName != null && discoveredMacIp != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LaptopMac, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(discoveredMacName ?: "MacBook Pro", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Yerel Ağda Bulundu (${discoveredMacIp})", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Button(
                            onClick = {
                                macIpInput = discoveredMacIp ?: "127.0.0.1"
                                AetherCoreService.instance?.connectToMacWebSocket(macIpInput)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Bağlan", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Connection Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Column {
                            Text("Süreklilik Servisi: Aktif", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("MacBook ile Yerel Ağ Eşitlemesi", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }

                    OutlinedTextField(
                        value = macIpInput,
                        onValueChange = { macIpInput = it },
                        label = { Text("Mac Yerel IP Adresi (veya mDNS)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                AetherCoreService.instance?.connectToMacWebSocket(macIpInput)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bağlan")
                        }

                        OutlinedButton(
                            onClick = {
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
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eşleştir")
                        }
                    }

                    // Camera QR Code Scanner Button
                    Button(
                        onClick = {
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
                                        Toast.makeText(context, "$name QR Kodu Başarıyla Tarandı ve Eşleşildi!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Tanınmayan QR formatı: $raw", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "QR İşleme Hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kamera ile Mac QR Kodunu Tara")
                    }

                    // Live Connection State
                    val isServiceConnected by AetherCoreService.isConnectedState.collectAsState()

                    // Disconnect / Reconnect Actions
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isServiceConnected) {
                            OutlinedButton(
                                onClick = {
                                    AetherCoreService.instance?.disconnect(userInitiated = true, forget = false)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9800)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bağlantıyı Kes", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val ip = discoveredMacIp ?: macIpInput.trim()
                                    AetherCoreService.instance?.connectToMacWebSocket(ip)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Yeniden Bağlan", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                AetherCoreService.instance?.disconnect(userInitiated = true, forget = true)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Eşleşmeyi Sil", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Dual Battery & Power Status Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Karşılıklı Şarj & Güç Durumu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        IconButton(onClick = {
                            AetherCoreService.instance?.requestMacBattery()
                            AetherCoreService.instance?.requestMacTelemetry()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile", modifier = Modifier.size(20.dp))
                        }
                    }

                    // MacBook Pro Battery Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Laptop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("MacBook Pro", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                val desc = if (macBattery == null) {
                                    "Mac Bağlantısı Bekleniyor..."
                                } else if (macBattery?.isCharging == true) {
                                    "Şarj Oluyor (Prize Takılı)"
                                } else if (macBattery?.isPluggedIn == true) {
                                    "Prize Takılı (Şarj Dolu)"
                                } else {
                                    "Pilde Çalışıyor"
                                }
                                Text(
                                    desc,
                                    fontSize = 12.sp,
                                    color = if (macBattery?.isCharging == true) Color(0xFF4CAF50) else Color.Gray
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Mac Temperature Badge Chip
                            val macTemp = macTelemetry?.temperatureCelsius
                            if (macTemp != null) {
                                val tempColor = when {
                                    macTemp < 50.0 -> Color(0xFF4CAF50)
                                    macTemp <= 75.0 -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tempColor.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(end = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Thermostat,
                                            contentDescription = null,
                                            tint = tempColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            String.format(java.util.Locale.US, "%.1f°C", macTemp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tempColor
                                        )
                                    }
                                }
                            }

                            Text(
                                if (macBattery != null) "%${macBattery?.level}" else "--",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (macBattery?.isCharging == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                if (macBattery?.isCharging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = if (macBattery?.isCharging == true) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Dynamic Phone Battery Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("${org.aetherlink.util.DeviceUtils.getDeviceName()} (Bu Cihaz)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                val phoneDesc = if (phoneBattery.isCharging) {
                                    "Şarj Oluyor (Prize Takılı)"
                                } else if (phoneBattery.isPluggedIn) {
                                    "Prize Takılı (Pil Koruması %80 Limiti)"
                                } else {
                                    "Pilde Çalışıyor"
                                }
                                Text(
                                    phoneDesc,
                                    fontSize = 12.sp,
                                    color = if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Color(0xFF4CAF50) else Color.Gray
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Phone Temperature Chip
                            val phoneTemp = phoneBattery.temperatureCelsius
                            val phoneTempColor = when {
                                phoneTemp < 38.0 -> Color(0xFF4CAF50)
                                phoneTemp <= 44.0 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = phoneTempColor.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Thermostat,
                                        contentDescription = null,
                                        tint = phoneTempColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        String.format(java.util.Locale.US, "%.1f°C", phoneTemp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = phoneTempColor
                                    )
                                }
                            }

                            Text(
                                "%${phoneBattery.level}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = if (phoneBattery.isCharging || phoneBattery.isPluggedIn) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // KDE Connect Ported Quick Synergy Tools Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("KDE Connect Süreklilik Araçları", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (pingLatency != null) {
                            Badge(containerColor = Color(0xFF00BCD4)) {
                                Text("${pingLatency?.toInt()} ms", color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Text(
                        "Mac'inizi uzaktan yönetin, sesini kontrol edin veya sanal touchpad olarak kullanın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    // 2x2 Grid of Actions
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Tile 1: Mac'i Çaldır / Sustur
                        OutlinedButton(
                            onClick = { FindMyPhoneManager.toggleRingMac() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isMacRinging) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (isMacRinging) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMacRinging) "Mac'i Sustur" else "Mac'i Çaldır", fontSize = 12.sp, maxLines = 1)
                        }

                        // Tile 2: Sanal Touchpad
                        Button(
                            onClick = { showTrackpadSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Touchpad", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Tile 3: Mac'i Kilitle
                        OutlinedButton(
                            onClick = {
                                RemoteLockManager.lockMac()
                                Toast.makeText(context, "Mac ekranı kilitlendi", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mac'i Kilitle", fontSize = 12.sp, maxLines = 1)
                        }

                        // Tile 4: Uzaktan Ses Denetimi
                        OutlinedButton(
                            onClick = { showVolumeDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ses Denetimi", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Tile: Sunum Kumandası
                        OutlinedButton(
                            onClick = { showPresenterSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CoPresent, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sunum", fontSize = 12.sp, maxLines = 1)
                        }

                        // Tile: Kafein Modu
                        OutlinedButton(
                            onClick = { org.aetherlink.caffeinate.CaffeinateManager.toggleCaffeinate() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isCaffeinateActive) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                if (isCaffeinateActive) Icons.Default.Coffee else Icons.Default.CoffeeMaker,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isCaffeinateActive) "Kafein Açık" else "Kafein Modu", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Tile: Hızlı Mac Komutları
                        OutlinedButton(
                            onClick = { showCommandsSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mac Komutları", fontSize = 12.sp, maxLines = 1)
                        }

                        // Tile: AetherDrop Paylaşım
                        OutlinedButton(
                            onClick = { showShareSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AetherDrop", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    // Tile 5: Ping Testi Bar
                    OutlinedButton(
                        onClick = { PingManager.sendPing() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val pingText = if (isPinging) {
                            "Ağ Gecikmesi Ölçülüyor..."
                        } else if (pingLatency != null) {
                            String.format(java.util.Locale.US, "Anlık Wi-Fi Gecikmesi: %.1f ms (Testi Yenile)", pingLatency)
                        } else {
                            "Ping & Ağ Gecikme Testi Yap"
                        }
                        Text(pingText, fontSize = 12.sp)
                    }
                }
            }

            // Bluetooth Audio Sync (Android Auto style)
            val isBtPaired by org.aetherlink.bluetooth.BluetoothAudioManager.isPairedWithMac.collectAsState()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Bluetooth Ses Köprüsü", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Badge(containerColor = if (isBtPaired) Color(0xFF4CAF50) else Color(0xFFFF9800)) {
                            Text(if (isBtPaired) "Ses Aktif" else "Eşleşme Bekleniyor", color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }

                    Text(
                        "Telefon sesini ve aramalarını Android Auto tarzında kablosuz olarak Mac'e aktarır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Button(
                        onClick = {
                            org.aetherlink.bluetooth.BluetoothAudioManager.initiateBonding(context)
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBtPaired) "Bluetooth Ayarlarını Yönet" else "Mac ile Bluetooth Eşleşmesi Başlat")
                    }
                }
            }

            // Wireless Screen Mirroring Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CastConnected,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Kablosuz Ekran Aktarımı", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (isScreenStreaming) {
                            Badge(containerColor = Color(0xFF4CAF50)) {
                                Text("Canlı Yayında", color = Color.White, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }

                    Text(
                        "Telefon ekranını düşük gecikmeyle doğrudan Mac penceresine canlı olarak yansıtır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Button(
                        onClick = {
                            if (ScreenStreamManager.isStreaming) {
                                AetherCoreService.instance?.stopScreenCapture()
                                isScreenStreaming = false
                            } else {
                                MainActivity.requestScreenCapture()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScreenStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            if (isScreenStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isScreenStreaming) "Yayını Durdur" else "Ekranı Mac'e Yansıt")
                    }
                }
            }

            // Real-Time Device Telemetry Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Cihaz Donanım Bilgileri", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                showTelemetryDialog = true
                            }) {
                                Text("Detaylar", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                DeviceTelemetryManager.dispatchTelemetry(context)
                                AetherCoreService.instance?.requestMacBattery()
                                AetherCoreService.instance?.requestMacTelemetry()
                            }) {
                                Text("Yenile", fontSize = 12.sp)
                            }
                        }
                    }

                    Text("${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Telefon Isısı", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${String.format(java.util.Locale.US, "%.1f", phoneBattery.temperatureCelsius)}°C",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (AetherCoreService.currentThermalStatus == "NORMAL") Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        AetherCoreService.currentThermalStatus,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (AetherCoreService.currentThermalStatus == "NORMAL") Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Column {
                            Text("Bilgisayar Isısı (Mac)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            val macTemp = macTelemetry?.temperatureCelsius
                            if (macTemp != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${String.format(java.util.Locale.US, "%.1f", macTemp)}°C",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    val macThermal = macTelemetry?.thermalStatus ?: "NORMAL"
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (macThermal == "NORMAL") Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            macThermal,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (macThermal == "NORMAL") Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            } else {
                                Text("Bağlantı Yok", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                        Column {
                            Text("Pil Durumu", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("${phoneBattery.level}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Permissions Checklist
            Text("İzin & Güvenlik Durumu", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            PermissionCard(
                title = "Bildirim Okuma & Cevaplama",
                desc = "WhatsApp, Telegram ve SMS mesajlarını Mac'e iletir ve cevaplar.",
                icon = Icons.Default.Notifications,
                actionLabel = "İzin Ver",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )

            PermissionCard(
                title = "Arama Yönetimi & Telefon",
                desc = "Gelen aramaları Mac ekranına yansıtmak ve cevaplamak için gereklidir.",
                icon = Icons.Default.Call,
                actionLabel = "İzin Ayarları",
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            PermissionCard(
                title = "Pil Tasarrufu Muafiyeti",
                desc = "Samsung ve OEM arka plan kısıtlamalarını aşarak kesintisiz bağlantı sağlar.",
                icon = Icons.Default.BatteryChargingFull,
                actionLabel = "Muaf Tut",
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            PermissionCard(
                title = "Evrensel Pano & Ekran Kilidi",
                desc = "Android 10-16 arka plan pano kopyalamasını ve Mac'ten ekran kilitlemeyi sağlar.",
                icon = Icons.Default.ContentPaste,
                actionLabel = "Etkinleştir",
                onClick = {
                    AetherPermissionManager.openAccessibilitySettings(context)
                }
            )

            // Features Overview
            Text("Aktif Süreklilik Özellikleri", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            FeatureItem(Icons.Default.ContentPaste, "Evrensel Pano", "Mac'te kopyalanan metin anında telefona yansır.")
            FeatureItem(Icons.Default.Chat, "Hızlı Yanıt (RemoteInput)", "Mac bildirim kutusundan WhatsApp/Telegram yanıtlama.")
            FeatureItem(Icons.Default.PhoneCallback, "Arama Yansıtma (InCallService)", "Mac üzerinden çağrı açma ve kapatma.")
            FeatureItem(Icons.Default.Shield, "Sıfır-Bulut Gizliliği", "Tüm iletişim yerel ağda AES-256 ile şifrelidir.")

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "AetherLink v${AndroidUpdateChecker.getAppVersion(context)} • Açık Kaynaklı Süreklilik Köprüsü",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Pairing Confirmation Dialog Modal
    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Eşleştirme Onay Kodu") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("MacBook ekranınızda da aynı kod görünüyor mu?")
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = pairingCode,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    Text(
                        "Her iki ekranda kodlar uyuşuyorsa bağlantı onaylanmıştır.",
                        fontSize = 12.sp,
                        color = Color.Gray
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
                    // Version Tag Comparison Row
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
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
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

                    // Neler Yeni Section
                    Text("Neler Yeni?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 200.dp)
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
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.downloadUrl))
                                            browserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Tarayıcı açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tarayıcıda Doğrudan İndir (.apk)", fontSize = 11.sp)
                                }
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
                                    onProgress = { progress ->
                                        apkDownloadProgress = progress
                                    },
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
        RemoteTrackpadSheet(
            onDismiss = { showTrackpadSheet = false }
        )
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


@Composable
fun PermissionCard(title: String, desc: String, icon: ImageVector, actionLabel: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            TextButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun FeatureItem(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
