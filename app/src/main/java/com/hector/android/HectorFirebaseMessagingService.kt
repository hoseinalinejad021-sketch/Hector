package com.hector.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives Firebase push messages. Delivery requires a configured Firebase project and sender. */
class HectorFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        getSharedPreferences("hector_private", MODE_PRIVATE)
            .edit().putString("fcm_installation_id", installationId).apply()
        // Bind the Firebase Installation ID to the signed-in owner. Sending messages
        // still requires a trusted server using Firebase's supported send API.
        if (CloudMemorySync.isConfigured(this)) {
            CloudMemorySync.registerDeviceInstallationId(this, installationId) { /* best-effort registration */ }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "هیکتور"
        val body = message.notification?.body ?: message.data["body"] ?: "اعلان جدید داری."
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "اعلان‌های هیکتور", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.take(80))
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(500)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "hector_push_notifications"
    }
}
