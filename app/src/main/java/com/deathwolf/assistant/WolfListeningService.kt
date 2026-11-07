package com.deathwolf.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Compatibility service retained because the existing MainActivity starts this class.
 * It no longer opens the microphone or waits for a wake word. Voice input is started
 * only when the user taps the purple microphone in the floating dock.
 */
class WolfListeningService : Service() {

    private val channelId = "death_wolf_assistant"
    private val notificationId = 4201

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            saveAssistantEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(notificationId, buildNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Death Wolf Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the user-enabled floating Wolf control available"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, 1,
            Intent(this, WolfListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Death Wolf Assistant Mode")
            .setContentText("Wake word is off. Tap the floating Wolf icon to speak.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Turn off", stopService)
            .build()
    }

    private fun saveAssistantEnabled(enabled: Boolean) {
        getSharedPreferences("MainActivity_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("assistant_enabled", enabled).apply()
    }

    override fun onDestroy() {
        getSystemService(NotificationManager::class.java).cancel(notificationId)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP = "com.deathwolf.assistant.STOP_LISTENING"
        const val ACTION_OVERLAY_CLOSED = "com.deathwolf.assistant.OVERLAY_CLOSED"
        const val ACTION_PAUSE_FOR_SPEECH = "com.deathwolf.assistant.PAUSE_FOR_SPEECH"
        const val ACTION_RESUME_AFTER_SPEECH = "com.deathwolf.assistant.RESUME_AFTER_SPEECH"
    }
}
