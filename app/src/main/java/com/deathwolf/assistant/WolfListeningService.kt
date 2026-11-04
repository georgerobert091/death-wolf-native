package com.deathwolf.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
    private var overlayVisible = false
    private var pausedForSpeech = false

    private var currentStatus = "Starting wake-word detector..."

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.i(TAG, "Screen turned off. Stopping Assistant Mode.")

                getSharedPreferences(
                    "MainActivity_preferences",
                    Context.MODE_PRIVATE
                ).edit()
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
        Log.i(TAG, "Listening service created.")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                Log.i(TAG, "Stop command received.")
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_OVERLAY_CLOSED -> {
                overlayVisible = false
                Log.i(TAG, "Overlay closed. Restarting detector.")
                startWakeWordDetection()
                return START_NOT_STICKY
            }

            ACTION_PAUSE_FOR_SPEECH -> {
                pausedForSpeech = true
                Log.i(TAG, "Pausing wake-word detector for speech recognition.")
                stopWakeWordDetection()
                updateNotification("Paused for voice input")
                return START_NOT_STICKY
            }

            ACTION_RESUME_AFTER_SPEECH -> {
                pausedForSpeech = false
                Log.i(TAG, "Resuming wake-word detection.")
                startWakeWordDetection()
                return START_NOT_STICKY
            }
        }

        currentStatus = "Checking microphone permission..."

        val notification = buildNotification(currentStatus)

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

        if (detector != null) {
            Log.d(TAG, "Detector is already running.")
            return
        }

        if (overlayVisible) {
            Log.d(TAG, "Detector not started because overlay is visible.")
            return
        }

        if (pausedForSpeech) {
            Log.d(TAG, "Detector paused for speech recognition.")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            currentStatus = "Microphone permission missing"
            Log.e(TAG, currentStatus)
            updateNotification(currentStatus)
            return
        }

        try {
            currentStatus = "Loading Hey Jarvis model..."
            updateNotification(currentStatus)

            Log.i(TAG, "Building Hey Jarvis detector.")

            val newDetector = OpenWakeWord.Builder(this)
                .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .setThreshold(0.5f)
                .build()

            detector = newDetector

            Log.i(TAG, "Detector built. Starting microphone detection.")

            newDetector.start { score ->

                Log.i(TAG, "Hey Jarvis detected. Score=$score")

                currentStatus = "Hey Jarvis detected!"
                updateNotification(currentStatus)

                if (!overlayVisible) {

                    overlayVisible = true

                    stopWakeWordDetection()

                    try {
                        Log.i(TAG, "Requesting Death Wolf overlay.")

                        startService(
                            Intent(
                                this,
                                WolfOverlayService::class.java
                            ).setAction(WolfOverlayService.ACTION_SHOW)
                        )

                    } catch (e: Exception) {

                        Log.e(TAG, "Could not show overlay.", e)

                        overlayVisible = false
                        startWakeWordDetection()
                    }
                }
            }

            currentStatus = "Detector started • Say Hey Jarvis"
            Log.i(TAG, currentStatus)
            updateNotification(currentStatus)

        } catch (e: SecurityException) {

            currentStatus = "Microphone access denied"
            Log.e(TAG, "Microphone security error.", e)
            updateNotification(currentStatus)
            stopWakeWordDetection()

        } catch (e: Exception) {

            currentStatus = "Detector failed: ${e.javaClass.simpleName}"
            Log.e(TAG, "Wake-word detector failed to start.", e)
            updateNotification(currentStatus)
            stopWakeWordDetection()
        }
    }

    private fun stopWakeWordDetection() {

        val current = detector ?: return

        detector = null

        try {
            current.stop()
            Log.i(TAG, "Detector stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Detector stop failed.", e)
        }

        try {
            current.release()
            Log.i(TAG, "Detector resources released.")
        } catch (e: Exception) {
            Log.e(TAG, "Detector release failed.", e)
        }
    }

    private fun updateNotification(message: String) {
        currentStatus = message

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.notify(
            notificationId,
            buildNotification(message)
        )
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

    private fun buildNotification(message: String): Notification {

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
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
            .setContentTitle("Death Wolf Assistant Mode")
            .setContentText(message)
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

        Log.i(TAG, "Listening service destroyed.")

        stopWakeWordDetection()

        try {
            stopService(
                Intent(this, WolfOverlayService::class.java)
                    .setAction(WolfOverlayService.ACTION_HIDE)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not hide overlay.", e)
        }

        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }

            screenReceiverRegistered = false
        }

        getSharedPreferences(
            "MainActivity_preferences",
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean("assistant_enabled", false)
            .apply()

        getSystemService(NotificationManager::class.java)
            .cancel(notificationId)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {

        private const val TAG = "DeathWolf"

        private const val ACTION_STOP =
            "com.deathwolf.assistant.STOP_LISTENING"

        const val ACTION_OVERLAY_CLOSED =
            "com.deathwolf.assistant.OVERLAY_CLOSED"

        const val ACTION_PAUSE_FOR_SPEECH =
            "com.deathwolf.assistant.PAUSE_FOR_SPEECH"

        const val ACTION_RESUME_AFTER_SPEECH =
            "com.deathwolf.assistant.RESUME_AFTER_SPEECH"
    }
}
