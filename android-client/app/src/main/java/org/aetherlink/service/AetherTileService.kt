package org.aetherlink.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import org.aetherlink.ui.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class AetherTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val hasInstance = AetherCoreService.instance != null
        val isConnected = AetherCoreService.isConnectedState.value

        if (hasInstance && isConnected) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "AetherLink"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Bağlı"
            }
        } else if (hasInstance) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "AetherLink"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Aranıyor..."
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "AetherLink"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Kapalı"
            }
        }
        tile.updateTile()
    }
}
