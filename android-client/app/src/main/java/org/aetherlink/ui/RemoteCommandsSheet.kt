package org.aetherlink.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.aetherlink.commands.RemoteCommandManager

private data class QuickCommandItem(
    val key: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val tint: Color = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteCommandsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val commands = listOf(
        QuickCommandItem("screenshot", "Ekran Görüntüsü", "Mac masaüstüne kaydeder", Icons.Default.CameraAlt, Color(0xFF4CAF50)),
        QuickCommandItem("sleep", "Mac'i Uyut", "Sistemi uyku moduna alır", Icons.Default.Bedtime, Color(0xFF2196F3)),
        QuickCommandItem("emptyTrash", "Çöpü Boşalt", "Finder çöp kutusunu temizler", Icons.Default.DeleteSweep, Color(0xFFFF9800)),
        QuickCommandItem("showDesktop", "Masaüstü", "Tüm pencereleri gizler", Icons.Default.DesktopWindows, Color(0xFF9C27B0)),
        QuickCommandItem("openDownloads", "İndirilenler", "İndirilenler klasörünü açar", Icons.Default.Folder, Color(0xFF00BCD4)),
        QuickCommandItem("openTerminal", "Terminal Aç", "macOS Terminal uygulamasını başlatır", Icons.Default.Terminal, Color(0xFFFFC107))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Hızlı Mac Komutları",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "Mac'inizde tek dokunuşla sistem eylemleri ve komutları çalıştırın.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(commands) { cmd ->
                    Card(
                        onClick = {
                            RemoteCommandManager.execute(cmd.key)
                            Toast.makeText(context, "${cmd.title} komutu Mac'e gönderildi", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(cmd.icon, contentDescription = null, tint = cmd.tint, modifier = Modifier.size(24.dp))
                            Text(cmd.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            Text(cmd.desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
