package org.aetherlink.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import kotlinx.coroutines.launch
import org.aetherlink.service.AetherCoreService
import org.aetherlink.updater.AndroidUpdateChecker
import org.aetherlink.updater.UpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var macIpInput by remember { mutableStateOf("192.168.1.100") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

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
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isCheckingUpdate = true
                            val result = AndroidUpdateChecker.check()
                            isCheckingUpdate = false
                            if (result.hasUpdate) {
                                updateInfo = result
                            }
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

                    Button(
                        onClick = {
                            AetherCoreService.instance?.connectToMacWebSocket(macIpInput)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mac'e Bağlan / Yenile")
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
                "AetherLink v${AndroidUpdateChecker.CURRENT_VERSION} • Açık Kaynaklı Süreklilik Köprüsü",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Update Dialog Modal
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(info.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Yeni Sürüm: v${info.latestVersion} (Mevcut: v${info.currentVersion})", fontWeight = FontWeight.Bold)
                    Divider()
                    Text("Yenilikler (Changelog):", fontWeight = FontWeight.SemiBold)
                    Text(info.changelog, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    AndroidUpdateChecker.downloadApk(context, info.downloadUrl, info.latestVersion)
                    updateInfo = null
                }) {
                    Text("İndir ve Güncelle (.apk)")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("Daha Sonra")
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
