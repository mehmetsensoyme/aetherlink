package org.aetherlink

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AetherLinkApplication : Application() {

    companion object {
        const val CHANNEL_CORE_SERVICE = "aetherlink_core_channel"
        const val CHANNEL_CALL_RELAY = "aetherlink_call_channel"
        const val CHANNEL_ALERTS = "aetherlink_alerts_channel"
        const val CHANNEL_FIND_MY_PHONE = "aetherlink_find_my_phone_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Foreground persistent service channel
            val coreChannel = NotificationChannel(
                CHANNEL_CORE_SERVICE,
                "AetherLink Süreklilik Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mac ile kesintisiz bağlantıyı ve senkronizasyonu sürdürür."
                setShowBadge(false)
            }

            // Call relay channel
            val callChannel = NotificationChannel(
                CHANNEL_CALL_RELAY,
                "AetherLink Arama Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gelen aramaları Mac ekranına yansıtır."
            }

            // Disconnect and alert channel
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "AetherLink Uyarı ve Durum Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Bağlantı kopması ve durum bildirimleri."
            }

            // Find my phone high-priority channel (bypasses DND)
            val findMyPhoneChannel = NotificationChannel(
                CHANNEL_FIND_MY_PHONE,
                "AetherLink Cihazımı Bul",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mac'inizden cihazınızı çaldırma uyarıları."
                enableVibration(true)
                setBypassDnd(true)
            }

            notificationManager.createNotificationChannel(coreChannel)
            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(findMyPhoneChannel)
        }
    }
}
