package org.aetherlink.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AetherDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("AetherDisconnectReceiver", "ACTION_DISCONNECT broadcast received")
        AetherCoreService.instance?.disconnect(userInitiated = true, forget = false)
    }
}
