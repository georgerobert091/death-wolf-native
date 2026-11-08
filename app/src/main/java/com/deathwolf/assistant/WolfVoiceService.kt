package com.deathwolf.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

class WolfVoiceService : Service(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var finished = false
    private var ttsReady = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingSpeech: String? = null
    private var afterSpeech: (() -> Unit)? = null
    private var speechCompleted = false

    private val speechTimeout = Runnable {
        completeSpeech()
    }

    companion object {
        const val ACTION_START =
            "com.deathwolf.assistant.START_VOICE_SESSION"

        const val ACTION_STATUS =
            "com.deathwolf.assistant.VOICE_STATUS"

        const val EXTRA_STATUS = "voice_status_text"

        private const val CHANNEL_ID = "death_wolf_voice_session"
        private const val NOTIFICATION_ID = 4210
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_START) {
            finished = false
            startVoiceForeground()
            beginRecognition()
        }

        return START_NOT_STICKY
    }

    private fun startVoiceForeground() {
        val notification =
            buildNotification("Microphone session is starting")

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

    private fun beginRecognition() {

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            publishStatus(
                "Microphone permission is missing. Open Death Wolf AI and allow it."
            )
            finishSession()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            publishStatus(
                "Speech recognition is unavailable. Check your phone's speech services."
            )
            finishSession()
            return
        }

        try {
            recognizer?.destroy()

            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(
                        params: android.os.Bundle?
                    ) {
                        publishStatus("Listening… speak now")
                        updateNotification("Listening for your command")
                    }

                    override fun onBeginningOfSpeech() {
                        publishStatus("Hearing you…")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        publishStatus("Processing your words…")
                    }

                    override fun onError(error: Int) {

                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO ->
                                "Audio input problem. Check the microphone."

                            SpeechRecognizer.ERROR_CLIENT ->
                                "Speech service stopped. Tap the mic and try again."

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission was denied."

                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Speech service network problem. Check your connection."

                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "I didn't catch that. Tap the mic and try again."

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Speech service is busy. Wait a moment and try again."

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "I didn't hear speech. Tap the mic and try again."

                            else ->
                                "Voice recognition failed (error $error). Try again."
                        }

                        publishStatus(message)
                        finishSession()
                    }

                    override fun onResults(
                        results: android.os.Bundle?
                    ) {

                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                        if (spoken.isBlank()) {
                            publishStatus(
                                "No words were recognised. Tap the mic to try again."
                            )
                            finishSession()
                        } else {
                            publishStatus("You said: $spoken")
                            handleCommand(spoken)
                        }
                    }

                    override fun onPartialResults(
                        partialResults: android.os.Bundle?
                    ) {

                        val partial = partialResults
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                        if (!partial.isNullOrBlank()) {
                            publishStatus("Hearing: $partial")
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: android.os.Bundle?
                    ) {}
                }
            )

            val request =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        Locale.getDefault().toLanguageTag()
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                        Locale.getDefault().toLanguageTag()
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        true
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS,
                        1
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_CALLING_PACKAGE,
                        packageName
                    )
                }

            recognizer?.startListening(request)

            publishStatus("Opening microphone…")

        } catch (_: SecurityException) {

            publishStatus(
                "Microphone access was blocked. Check app permissions."
            )

            finishSession()

        } catch (_: Exception) {

            publishStatus(
                "Could not start speech recognition. Check your speech services."
            )

            finishSession()
        }
    }

    private fun handleCommand(spoken: String) {

        val lower = spoken.lowercase(Locale.ROOT).trim()

        // LIST INSTALLED APPS

        if (
            lower == "list apps" ||
            lower == "show apps" ||
            lower == "what apps can you open"
        ) {

            val apps = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            )
                .map {
                    it.loadLabel(packageManager).toString()
                }
                .distinct()
                .sorted()

            val reply =
                if (apps.isEmpty()) {
                    "I couldn't find any launchable apps."
                } else {
                    "Apps I can launch include: " +
                        apps.take(35).joinToString(", ") +
                        if (apps.size > 35) " and more." else "."
                }

            publishStatus(reply)

            speakThen(reply)

            return
        }

        // OPEN AN APP

        val launchRequest =
            lower.startsWith("open ") ||
            lower.startsWith("launch ") ||
            lower.startsWith("start ") ||
            lower.startsWith("run ")

        if (launchRequest) {

            val appName = spoken.replace(
                Regex("(?i)^(please\\s+)?(open|launch|start|run)\\s+"),
                ""
            ).trim()

            val activities = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            )

            val match = activities.firstOrNull {
                it.loadLabel(packageManager)
                    .toString()
                    .equals(appName, true)
            } ?: activities.firstOrNull {
                it.loadLabel(packageManager)
                    .toString()
                    .contains(appName, true)
            }

            if (match != null) {

                val launchIntent =
                    packageManager.getLaunchIntentForPackage(
                        match.activityInfo.packageName
                    )

                if (launchIntent != null) {

                    val reply =
                        "Opening ${match.loadLabel(packageManager)}."

                    publishStatus(reply)

                    // SPEAK FIRST, THEN OPEN THE APP

                    speakThen(reply) {

                        try {
                            launchIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )

                            startActivity(launchIntent)

                        } catch (_: Exception) {

                            publishStatus(
                                "I recognised the app, but Android did not allow it to open."
                            )
                        }
                    }

                } else {

                    val reply = "I couldn't open that app."

                    publishStatus(reply)
                    speakThen(reply)
                }

            } else {

                val reply =
                    "I couldn't find $appName. Try saying list apps."

                publishStatus(reply)
                speakThen(reply)
            }

            return
        }

        // OTHER VOICE COMMANDS

        val reply =
            "I heard: $spoken. App launching commands are available; the online AI conversation connection is not configured yet."

        publishStatus(reply)
        speakThen(reply)
    }

    // SPEAK AND WAIT UNTIL SPEECH FINISHES

    private fun speakThen(
        message: String,
        action: (() -> Unit)? = null
    ) {

        if (finished) return

        pendingSpeech = message
        afterSpeech = action
        speechCompleted = false

        mainHandler.removeCallbacks(speechTimeout)

        // Fallback in case the speech engine does not respond.
        mainHandler.postDelayed(speechTimeout, 20000)

        if (ttsReady) {
            startPendingSpeech()
        }
    }

    private fun startPendingSpeech() {

        val message = pendingSpeech ?: return

        pendingSpeech = null

        val result = tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "DEATH_WOLF_REPLY"
        )

        if (result == null || result == TextToSpeech.ERROR) {
            completeSpeech()
        }
    }

    private fun completeSpeech() {

        if (speechCompleted || finished) return

        speechCompleted = true

        mainHandler.removeCallbacks(speechTimeout)

        val action = afterSpeech
        afterSpeech = null

        try {
            action?.invoke()
        } catch (_: Exception) {
            publishStatus("I couldn't complete that command.")
        }

        if (!finished) {
            finishSession()
        }
    }

    // TEXT TO SPEECH INITIALIZATION

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val languageResult = tts?.setLanguage(Locale.US)

            tts?.setPitch(0.85f)
            tts?.setSpeechRate(0.95f)

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            completeSpeech()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            completeSpeech()
                        }
                    }

                    override fun onError(
                        utteranceId: String?,
                        errorCode: Int
                    ) {
                        mainHandler.post {
                            completeSpeech()
                        }
                    }
                }
            )

            ttsReady =
                languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED

            if (ttsReady && pendingSpeech != null) {
                startPendingSpeech()
            } else if (!ttsReady && pendingSpeech != null) {
                completeSpeech()
            }

        } else {

            ttsReady = false

            if (pendingSpeech != null) {
                completeSpeech()
            }
        }
    }

    private fun publishStatus(message: String) {

        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, message)
        )

        updateNotification(message)
    }

    private fun finishSession() {

        if (finished) return

        finished = true

        mainHandler.removeCallbacks(speechTimeout)

        pendingSpeech = null
        afterSpeech = null

        try {
            recognizer?.cancel()
        } catch (_: Exception) {}

        try {
            recognizer?.destroy()
        } catch (_: Exception) {}

        recognizer = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Death Wolf microphone",
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "Shows when Death Wolf is using voice recognition"

                setShowBadge(false)
            }

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Death Wolf AI")
            .setContentText(message.take(100))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(message: String) {

        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                buildNotification(message)
            )
    }

    override fun onDestroy() {

        mainHandler.removeCallbacks(speechTimeout)

        try {
            recognizer?.cancel()
        } catch (_: Exception) {}

        try {
            recognizer?.destroy()
        } catch (_: Exception) {}

        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
