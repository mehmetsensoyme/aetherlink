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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var macIpInput by remember { mutableStateOf("127.0.0.1") }
    var discoveredMacName by remember { mutableStateOf<String?>(null) }
    var discoveredMacIp by remember { mutableStateOf<String?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var isScreenStreaming by remember { mutableStateOf(ScreenStreamManager.isStreaming) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("482 915") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingApk by remember { mutableStateOf(false) }
    var apkDownloadProgress by remember { mutableStateOf(0f) }
    var apkDownloadError by remember { mutableStateOf<String?>(null) }

    // Automatic update check on app launch (silent background check)
    LaunchedEffect(Unit) {
        val result = AndroidUpdateChecker.check(context)
        if (result is UpdateCheckResult.Available) {
            updateInfo = result.info
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
            val macBattery by AetherCoreService.macBatteryState.collectAsState()
            val phoneBattery by AetherCoreService.phoneBatteryState.collectAsState()

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

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Cihaz Donanım Bilgileri", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        TextButton(onClick = {
                            DeviceTelemetryManager.dispatchTelemetry(context)
                        }) {
                            Text("Mac'e Gönder", fontSize = 12.sp)
                        }
                    }

                    Text("${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Pil Durumu", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("${phoneBattery.level}% • ${String.format(java.util.Locale.US, "%.1f", phoneBattery.temperatureCelsius)}°C", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Column {
                            Text("RAM Kullanımı", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("7.2 GB / 12 GB", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Depolama", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("392 GB Boş", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                desc = "Samsung arka plan kısıtlamalarını aşarak kesintisiz bağlantı sağlar.",
                icon = Icons.Default.BatteryChargingFull,
                actionLabel = "Muaf Tut",
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
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
            title = { Text(info.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Yeni Sürüm: v${info.latestVersion} (Mevcut: v${info.currentVersion})", fontWeight = FontWeight.Bold)
                    Divider()
                    Text("Yenilikler (Changelog):", fontWeight = FontWeight.SemiBold)
                    Text(info.changelog, style = MaterialTheme.typography.bodySmall, maxLines = 8)

                    if (isDownloadingApk) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("İndiriliyor: %${(apkDownloadProgress * 100).toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        LinearProgressIndicator(
                            progress = { apkDownloadProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }

                    if (apkDownloadError != null) {
                        Text(apkDownloadError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    TextButton(onClick = { updateInfo = null }) {
                        Text("Daha Sonra")
                    }
                }
            }
        )
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
