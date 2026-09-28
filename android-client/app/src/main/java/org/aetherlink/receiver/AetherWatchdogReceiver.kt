package org.aetherlink.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.aetherlink.service.AetherCoreService

/**
 * AetherWatchdogReceiver ensures AetherCoreService remains active across aggressive OEM kills
 * (Samsung One UI, Xiaomi HyperOS, OnePlus) without consuming battery or CPU.
 */
class AetherWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AetherWatchdog"
        const val ACTION_WATCHDOG_PING = "org.aetherlink.action.WATCHDOG_PING"
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        fun scheduleWatchdog(context: Context) {
            try {
                val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, AetherWatchdogReceiver::class.java).apply {
                    action = ACTION_WATCHDOG_PING
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9911,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmMgr.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
                Log.d(TAG, "Scheduled next watchdog check in 15 mins")
            } catch (e: Exception) {
                Log.w(TAG, "Failed scheduling watchdog: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "Watchdog received action: $action")

        if (AetherCoreService.instance == null) {
            Log.i(TAG, "AetherCoreService is NOT running! Reviving service now...")
            AetherCoreService.start(context)
        } else {
            Log.d(TAG, "AetherCoreService is already active and healthy.")
        }

        // Reschedule for next 15-minute window
        scheduleWatchdog(context)
    }
}
