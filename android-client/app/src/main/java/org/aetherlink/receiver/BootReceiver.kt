package org.aetherlink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.aetherlink.service.AetherCoreService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i("BootReceiver", "Device boot detected. Auto-starting AetherCoreService...")
            AetherCoreService.start(context)
        }
    }
}
