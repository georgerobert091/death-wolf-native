package com.deathwolf.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openwakeword.OpenWakeWord

class WolfListeningService : Service() {

    private val channelId = "death_wolf_assistant"
    private val notificationId = 4201

    private var screenReceiverRegistered = false
    private var detector: OpenWakeWord? = null
    private var overlayShown = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("assistant_enabled", false)
                    .apply()

                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenOffReceiver()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_OVERLAY_CLOSED) {
            overlayShown = false
            startWakeWordDetection()
            return START_STICKY
        }

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(notificationId, notification)
        }

        startWakeWordDetection()

        return START_NOT_STICKY
    }

    private fun startWakeWordDetection() {
        if (detector != null || overlayShown) return

        try {
            detector = OpenWakeWord.Builder(this)
                .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .setThreshold(0.5f)
                .build()

            detector?.start { score ->
                Log.d("DeathWolf", "Wake word detected: $score")

                if (!overlayShown) {
                    overlayShown = true
                    stopWakeWordDetection()

                    val overlayIntent = Intent(
                        this,
                        WolfOverlayService::class.java
                    ).setAction(WolfOverlayService.ACTION_SHOW)

                    startService(overlayIntent)
                }
            }

        } catch (e: Exception) {
            Log.e("DeathWolf", "Wake-word startup failed", e)
            stopWakeWordDetection()
        }
    }

    private fun stopWakeWordDetection() {
        try {
            detector?.stop()
            detector?.release()
        } catch (e: Exception) {
            Log.e("DeathWolf", "Detector shutdown failed", e)
        }

        detector = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Death Wolf Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Death Wolf Assistant Mode"
                setShowBadge(false)
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val stopService = PendingIntent.getService(
            this,
            1,
            Intent(this, WolfListeningService::class.java)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Death Wolf Assistant Mode is ON")
            .setContentText("Listening for Hey Jarvis")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Turn off", stopService)
            .build()
    }

    private fun registerScreenOffReceiver() {
        if (screenReceiverRegistered) return

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                screenReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }

        screenReceiverRegistered = true
    }

    override fun onDestroy() {
        stopWakeWordDetection()

        try {
            stopService(
                Intent(this, WolfOverlayService::class.java)
                    .setAction(WolfOverlayService.ACTION_HIDE)
            )
        } catch (_: Exception) {
        }

        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
            screenReceiverRegistered = false
        }

        getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("assistant_enabled", false)
            .apply()

        getSystemService(NotificationManager::class.java)
            .cancel(notificationId)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP =
            "com.deathwolf.assistant.STOP_LISTENING"

        const val ACTION_OVERLAY_CLOSED =
            "com.deathwolf.assistant.OVERLAY_CLOSED"
    }
}
