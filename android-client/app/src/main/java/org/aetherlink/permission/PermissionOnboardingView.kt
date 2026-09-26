package org.aetherlink.permission

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat

@Composable
fun PermissionOnboardingDialog(
    status: PermissionStatusReport,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasRequestedOnce = true
        onRefresh()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                "İzin Gereksinimleri",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat")
                        }
                    }

                    Text(
                        "AetherLink'in Mac ile kesintisiz arama, bildirim ve bağlantı senkronizasyonu sunabilmesi için aşağıdaki sistem izinleri gereklidir:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Permission Cards
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PermissionRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Bildirimler ve Süreklilik Servisi",
                        description = "Mac bağlantı durumu, kesinti uyarıları ve arka plan köprüsünün stabil kalması için gereklidir.",
                        isGranted = status.notificationsGranted
                    )

                    PermissionRow(
                        icon = Icons.Default.PhoneCallback,
                        title = "Telefon Çağrıları ve Arama Yönetimi",
                        description = "Gelen ve giden aramaların Mac ekranına yansıtılması ve bilgisayardan konuşulabilmesi için gereklidir.",
                        isGranted = status.phoneCallsGranted
                    )

                    PermissionRow(
                        icon = Icons.Default.Sensors,
                        title = "Yakındaki Cihazlar & Bluetooth",
                        description = "MacBook ile ultra düşük gecikmeli Bluetooth ses köprüsü ve yerel eşleşme için gereklidir.",
                        isGranted = status.nearbyDevicesGranted
                    )

                    PermissionRow(
                        icon = Icons.Default.Wifi,
                        title = "Yerel Ağ Bağlantısı (Wi-Fi)",
                        description = "Mac ile aynı yerel ağda yüksek hızlı ve şifreli soket bağlantısı kurmak için gereklidir.",
                        isGranted = status.networkAvailable
                    )

                    PermissionRow(
                        icon = Icons.Default.MarkChatRead,
                        title = "Bildirim Aynalama & Hızlı Yanıt (Özel)",
                        description = "WhatsApp ve SMS bildirimlerini Mac'e aktarıp oradan yanıtlayabilmek için gereklidir.",
                        isGranted = status.notificationListenerGranted,
                        onSpecialAction = {
                            AetherPermissionManager.openNotificationListenerSettings(context)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!status.allCoreGranted) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(AetherPermissionManager.getRequiredRuntimePermissions())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gerekli İzinleri Ver", fontWeight = FontWeight.SemiBold)
                        }

                        if (hasRequestedOnce) {
                            OutlinedButton(
                                onClick = {
                                    AetherPermissionManager.openAppSettings(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uygulama Ayarlarını Aç (Manuel İzin)")
                            }
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tüm Temel İzinler Tamam - Devam Et", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onSpecialAction: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isGranted) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFE65100).copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "İzin Verildi",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(22.dp)
                )
            } else if (onSpecialAction != null) {
                TextButton(
                    onClick = onSpecialAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Aç", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            } else {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "İzin Gerekli",
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionStatusBanner(
    status: PermissionStatusReport,
    onClick: () -> Unit
) {
    if (!status.allCoreGranted) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF3E0),
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(22.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Eksik Sistem İzinleri Var",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        "Mac ile arama ve bildirimlerin çalışabilmesi için izinleri tamamlayın.",
                        fontSize = 11.sp,
                        color = Color(0xFF5D4037)
                    )
                }

                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("İzin Ver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
