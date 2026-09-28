package org.aetherlink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.aetherlink.service.AetherCoreService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("BootReceiver", "Boot or package update detected ($action). Auto-starting AetherCoreService and Watchdog...")
            AetherCoreService.start(context)
            AetherWatchdogReceiver.scheduleWatchdog(context)
        }
    }
}
