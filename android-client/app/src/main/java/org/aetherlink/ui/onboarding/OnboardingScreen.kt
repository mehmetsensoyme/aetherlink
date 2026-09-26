package org.aetherlink.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.aetherlink.discovery.AetherNsdDiscovery
import org.aetherlink.permission.AetherPermissionManager
import org.aetherlink.service.AetherCoreService
import org.aetherlink.ui.MainActivity

object OnboardingPreferences {
    private const val PREFS_NAME = "aetherlink_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "is_onboarding_completed"

    fun isOnboardingCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}

// Colors inspired by macOS Big Sur / Sonoma dark titanium & nebula glass
private val BgGradientTop = Color(0xFF090D18)
private val BgGradientBottom = Color(0xFF13182C)
private val CardSurface = Color(0xFF1C2237).copy(alpha = 0.70f)
private val CardBorder = Color.White.copy(alpha = 0.10f)
private val AccentViolet = Color(0xFF8B5CF6)
private val AccentCyan = Color(0xFF06B6D4)
private val AccentEmerald = Color(0xFF10B981)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    var showPermissionWarning by remember { mutableStateOf(false) }

    // Re-check permissions automatically on resume
    var hasNotificationPermission by remember {
        mutableStateOf(AetherPermissionManager.isNotificationPermissionGranted(context))
    }
    var hasCallAndContactsPermission by remember {
        mutableStateOf(AetherPermissionManager.isCallAndContactsGranted(context))
    }
    var hasBatteryOptimizationExemption by remember {
        mutableStateOf(AetherPermissionManager.isBatteryOptimizationIgnored(context))
    }

    val refreshPermissions = {
        hasNotificationPermission = AetherPermissionManager.isNotificationPermissionGranted(context)
        hasCallAndContactsPermission = AetherPermissionManager.isCallAndContactsGranted(context)
        hasBatteryOptimizationExemption = AetherPermissionManager.isBatteryOptimizationIgnored(context)
        if (hasNotificationPermission && hasCallAndContactsPermission && hasBatteryOptimizationExemption) {
            showPermissionWarning = false
        }
    }

    // Lifecycle observer to re-check when returning from system dialogs/settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgGradientTop, BgGradientBottom)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar: Step indicator and Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // macOS Style Pill Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 3) {
                        val isSelected = pagerState.currentPage == i
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            label = "indicator_width"
                        )
                        val color = if (isSelected) AccentViolet else Color.White.copy(alpha = 0.25f)
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                if (pagerState.currentPage < 2) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        }
                    ) {
                        Text(
                            "Atla",
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomeStep()
                    1 -> OnboardingPermissionsStep(
                        hasNotification = hasNotificationPermission,
                        hasCallAndContacts = hasCallAndContactsPermission,
                        hasBatteryExemption = hasBatteryOptimizationExemption,
                        showWarning = showPermissionWarning,
                        onPermissionsUpdated = { refreshPermissions() }
                    )
                    2 -> OnboardingPairingStep(
                        onComplete = onComplete
                    )
                }
            }

            // Bottom Navigation Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Geri")
                    }
                } else {
                    Spacer(modifier = Modifier.width(80.dp))
                }

                // Next or Complete Button
                when (pagerState.currentPage) {
                    0 -> {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                        ) {
                            Text("Devam Et", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    1 -> {
                        val allGranted = hasNotificationPermission && hasCallAndContactsPermission && hasBatteryOptimizationExemption
                        Button(
                            onClick = {
                                if (allGranted) {
                                    showPermissionWarning = false
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(2)
                                    }
                                } else {
                                    showPermissionWarning = true
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (allGranted) AccentEmerald else AccentViolet
                            )
                        ) {
                            Text("İleri", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    2 -> {
                        // Handled within Step 3 directly for instant feedback and finishing
                    }
                }
            }
        }
    }
}

// MARK: - Step 1: Welcome & Features
@Composable
private fun OnboardingWelcomeStep() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Hero Badge / Logo
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(AccentViolet.copy(alpha = 0.35f), AccentCyan.copy(alpha = 0.35f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Devices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "AetherLink'e Hoş Geldiniz",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Telefonunuz ve Mac'iniz arasında sınırları kaldıran yüksek hızlı ekosistem köprüsü.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Feature 1: Universal Clipboard
        FeatureShowcaseCard(
            icon = Icons.Default.ContentPaste,
            iconTint = AccentCyan,
            title = "Evrensel Pano",
            description = "Telefonunuzda kopyaladığınız her metin anında Mac panosuna aktarılır; Mac'te kopyaladıklarınız telefona gelir."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Feature 2: Wireless Screen Mirroring
        FeatureShowcaseCard(
            icon = Icons.Default.CastConnected,
            iconTint = AccentViolet,
            title = "Düşük Gecikmeli Ekran",
            description = "Scrcpy destekli donanım hızlandırma ile 60 FPS akıcı ve ultra düşük ses gecikmesiyle canlı ekran yansıtma."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Feature 3: Calls & Continuity
        FeatureShowcaseCard(
            icon = Icons.Default.PhoneInTalk,
            iconTint = AccentEmerald,
            title = "Arama ve Medya Köprüsü",
            description = "Gelen aramaları Mac'teki kayan panelden anında yanıtlayın veya reddedin. Spotify ve Apple Music senkronize çalsın."
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureShowcaseCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// MARK: - Step 2: Permission Management
@Composable
private fun OnboardingPermissionsStep(
    hasNotification: Boolean,
    hasCallAndContacts: Boolean,
    hasBatteryExemption: Boolean,
    showWarning: Boolean,
    onPermissionsUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Activity Result Launchers
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onPermissionsUpdated()
    }

    val callAndContactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsUpdated()
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionsUpdated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AccentViolet.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Gerekli İzinler",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Kesintisiz arama, bildirim aktarımı ve arka plan bağlantısı için sistem izinlerini onaylayın.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Warning banner if user tried to advance prematurely
        AnimatedVisibility(
            visible = showWarning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Lütfen sonraki adıma geçmeden önce tüm izinleri onaylayın.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Permission Card 1: Notifications
        InteractivePermissionCard(
            title = "Bildirim Erişimi",
            description = "Mac'e anlık gelen bildirimleri ve medya durumunu senkronize etmek için gereklidir.",
            icon = Icons.Default.NotificationsActive,
            isGranted = hasNotification,
            onGrantClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    AetherPermissionManager.openAppSettings(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Permission Card 2: Calls & Contacts
        InteractivePermissionCard(
            title = "Arama ve Rehber Yönetimi",
            description = "Mac üzerinden gelen çağrıları yanıtlama, reddetme ve arayan kişi kimliğini göstermek için gereklidir.",
            icon = Icons.Default.ContactPhone,
            isGranted = hasCallAndContacts,
            onGrantClick = {
                callAndContactsLauncher.launch(AetherPermissionManager.getCallAndContactsPermissions())
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Permission Card 3: Battery Optimization Exemption
        InteractivePermissionCard(
            title = "Pil Tasarrufu Muafiyeti",
            description = "Ekran kapalıyken Android sisteminin Wi-Fi bağlantısını uyutmasını engeller ve Mac ile köprüyü canlı tutar.",
            icon = Icons.Default.BatteryChargingFull,
            isGranted = hasBatteryExemption,
            onGrantClick = {
                val intent = AetherPermissionManager.createBatteryOptimizationIntent(context)
                try {
                    batteryLauncher.launch(intent)
                } catch (e: Exception) {
                    AetherPermissionManager.openAppSettings(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InteractivePermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(
            1.dp,
            if (isGranted) AccentEmerald.copy(alpha = 0.40f) else CardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isGranted) AccentEmerald.copy(alpha = 0.15f) else AccentViolet.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isGranted) AccentEmerald else AccentViolet,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.60f),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action button
            if (isGranted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentEmerald.copy(alpha = 0.12f))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "İzin Verildi",
                        color = AccentEmerald,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                ) {
                    Text("İzin Ver", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

// MARK: - Step 3: Initial Pairing Step
@Composable
private fun OnboardingPairingStep(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isConnected by AetherCoreService.isConnectedState.collectAsState()

    var discoveredMacName by remember { mutableStateOf<String?>(null) }
    var discoveredMacIp by remember { mutableStateOf<String?>(null) }
    var manualIpInput by remember { mutableStateOf("127.0.0.1") }
    var isScanning by remember { mutableStateOf(true) }

    // Start mDNS Network Discovery
    DisposableEffect(Unit) {
        val discovery = AetherNsdDiscovery(context) { name, host, _ ->
            discoveredMacName = name
            discoveredMacIp = host
            manualIpInput = host
            isScanning = false
        }
        discovery.startDiscovery()
        onDispose {
            discovery.stopDiscovery()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isConnected) AccentEmerald.copy(alpha = 0.20f) else AccentCyan.copy(alpha = 0.20f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isConnected) Icons.Default.CheckCircle else Icons.Default.LaptopMac,
                contentDescription = null,
                tint = if (isConnected) AccentEmerald else AccentCyan,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isConnected) "Mac ile Bağlantı Kuruldu!" else "İlk Eşleştirme",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isConnected)
                "Harika! Telefonunuz ve Mac'iniz başarıyla eşleşti. AetherLink kontrol paneline geçebilirsiniz."
            else
                "Aynı yerel Wi-Fi ağına bağlı Mac'inizi otomatik tarayın veya Mac uygulamasındaki QR kodu okutun.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isConnected) {
            // Success Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = AccentEmerald.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.40f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Celebration,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Kusursuz Süreklilik Aktif",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Cihazınız Mac ile canlı veri senkronizasyonuna hazır.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.70f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
            ) {
                Text(
                    "AetherLink'i Başlat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        } else {
            // Scan / Discovery Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Yerel Ağ Taraması (mDNS)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        if (isScanning && discoveredMacName == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (discoveredMacName != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = AccentCyan
                                    )
                                    Column {
                                        Text(
                                            discoveredMacName ?: "MacBook",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            discoveredMacIp ?: "127.0.0.1",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        discoveredMacIp?.let { ip ->
                                            AetherCoreService.instance?.connectToMacWebSocket(ip)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                                ) {
                                    Text("Bağlan", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Text(
                            "Ağdaki AetherLink Mac dinleyicisi aranıyor...",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Code Scanner Action
                    OutlinedButton(
                        onClick = {
                            MainActivity.scanQrCode { scanned ->
                                try {
                                    if (scanned.contains("ip") && scanned.contains("{")) {
                                        val json = com.google.gson.JsonParser.parseString(scanned).asJsonObject
                                        val ip = json.get("ip")?.asString ?: "127.0.0.1"
                                        AetherCoreService.instance?.connectToMacWebSocket(ip)
                                    } else if (scanned.isNotBlank()) {
                                        AetherCoreService.instance?.connectToMacWebSocket(scanned.trim())
                                    }
                                    Toast.makeText(context, "QR Kod okundu, bağlanılıyor...", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    AetherCoreService.instance?.connectToMacWebSocket(scanned.trim())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mac QR Kodunu Tara", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Manual IP fallback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualIpInput,
                            onValueChange = { manualIpInput = it },
                            label = { Text("Manuel Mac IP", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (manualIpInput.isNotBlank()) {
                                    AetherCoreService.instance?.connectToMacWebSocket(manualIpInput.trim())
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                        ) {
                            Text("Bağlan", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Skip/Finish without active pairing
            TextButton(
                onClick = onComplete
            ) {
                Text(
                    "Şimdi Değil, Kontrol Paneline Geç",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
