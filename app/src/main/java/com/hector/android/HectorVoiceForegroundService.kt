package com.hector.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Owns the live audio session independently of MainActivity, so the user can switch apps
 * while a user-started microphone session continues. Android still controls microphone
 * access, notification visibility, and foreground-service lifetime.
 */
class HectorVoiceForegroundService : Service() {
    private var session: RealtimeVoiceSession? = null
    private lateinit var notifications: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            stopSelf()
            return START_NOT_STICKY
        }

        // Android requires foreground promotion immediately after startForegroundService().
        startAsForeground(buildNotification("در حال آماده‌سازی گفت‌وگوی زنده"))

        // The emergency-stop latch is shared with MainActivity. Enforce it here too,
        // because a service/notification action must not bypass the user's stop choice.
        if (getSharedPreferences("hector_private", MODE_PRIVATE)
                .getBoolean("emergency_stop_engaged", false)) {
            updateNotification("توقف اضطراری فعال است")
            stopSelf()
            return START_NOT_STICKY
        }

        val keyBytes = SecureMemoryStore(this).read("api_key")
        val apiKey = try {
            keyBytes?.let { String(it, Charsets.UTF_8) }.orEmpty()
        } finally {
            keyBytes?.fill(0)
        }
        if (apiKey.isBlank()) {
            updateNotification("کلید API تنظیم نشده است")
            stopSelf()
            return START_NOT_STICKY
        }

        stopSession()
        session = RealtimeVoiceSession(applicationContext, apiKey) { status ->
            // Never put transcript text or other conversation content in the system notification.
            val safeStatus = when {
                status.contains("وصل شد") -> "گفت‌وگوی زنده فعال است"
                status.contains("متوقف") || status.contains("بسته شد") -> "گفت‌وگوی زنده متوقف شد"
                status.contains("خطا") || status.contains("نامعتبر") || status.contains("دسترسی") -> "اتصال صوتی نیاز به بررسی دارد"
                else -> "در حال اتصال به سرویس صوتی"
            }
            updateNotification(safeStatus)
        }
        session?.start()
        return START_NOT_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, 1,
            Intent(this, HectorVoiceForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("هیکتور | گفت‌وگوی زنده")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "پایان صدا", stopService)
            .build()
    }

    private fun updateNotification(message: String) {
        if (::notifications.isInitialized) notifications.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "گفت‌وگوی زنده هیکتور", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "اعلان دائمی هنگام استفاده از میکروفون در گفت‌وگوی زنده"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun stopSession() {
        session?.close()
        session = null
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        stopSession()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.hector.android.action.STOP_LIVE_VOICE"
        private const val CHANNEL_ID = "hector_live_voice"
        private const val NOTIFICATION_ID = 4102
    }
}
