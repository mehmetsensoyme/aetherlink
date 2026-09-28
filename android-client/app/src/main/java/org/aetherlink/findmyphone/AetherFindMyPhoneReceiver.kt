package org.aetherlink.findmyphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AetherFindMyPhoneReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP_ALARM = "org.aetherlink.action.STOP_FIND_MY_PHONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_ALARM) {
            Log.i("AetherFindMyPhoneReceiver", "Stopping alarm via notification action...")
            FindMyPhoneManager.stopRinging(context, notifyMac = true)
        }
    }
}
