package com.deathwolf.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val handler = Handler(Looper.getMainLooper())

    private var sessionActive = false
    private var waitingForWakeWord = true

    private var status by mutableStateOf("Ready for you")
    private var listening by mutableStateOf(false)
    private var lastMessage by mutableStateOf(
        "Tap the microphone and say Wolf."
    )

    private var retryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        setContent {
            WolfScreen(
                status = status,
                listening = listening,
                message = lastMessage,
                onStart = { checkPermissionAndListen() },
                onStop = { stopListening() }
            )
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.85f)
            tts?.setSpeechRate(0.95f)
        }
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startSession()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startSession()
        } else {
            status = "Microphone permission required"
        }
    }

    private fun startSession() {
        stopRecognizerOnly()

        sessionActive = true
        waitingForWakeWord = true
        retryCount = 0

        lastMessage = "Say Wolf to activate your assistant."
        startRecognition()
    }

    private fun startRecognition() {
        if (!sessionActive || isFinishing) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            listening = false
            status = "Speech recognition unavailable"
            lastMessage = "Check your phone's speech recognition service."
            return
        }

        stopRecognizerOnly()

        try {
            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        status = if (waitingForWakeWord) {
                            "Waiting for Wolf..."
                        } else {
                            "Wolf is listening..."
                        }
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        status = "Processing..."
                    }

                    override fun onError(error: Int) {
                        listening = false

                        if (sessionActive) {
                            status = "Trying again..."
                            scheduleRetry()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false

                        if (!sessionActive) return

                        val words = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val spoken = words
                            ?.firstOrNull()
                            ?.lowercase(Locale.US)
                            ?.trim()

                        if (spoken.isNullOrBlank()) {
                            scheduleRetry()
                            return
                        }

                        retryCount = 0

                        if (waitingForWakeWord) {
                            if (spoken.contains("wolf")) {
                                waitingForWakeWord = false

                                lastMessage =
                                    "I'm listening. What do you need?"

                                status = "Wolf activated"

                                speak(
                                    "I'm listening. What do you need?"
                                )

                                handler.postDelayed({
                                    if (sessionActive) {
                                        startRecognition()
                                    }
                                }, 1700)

                            } else {
                                lastMessage =
                                    "I didn't recognize Wolf. Try again."

                                status = "Waiting for Wolf..."
                                scheduleRetry()
                            }

                        } else {
                            handleCommand(spoken)
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-US"
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

            speechRecognizer?.startListening(intent)

        } catch (exception: Exception) {
            listening = false
            status = "Trying again..."
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (!sessionActive) return

        handler.removeCallbacksAndMessages(null)

        retryCount++

        val delay = when {
            retryCount <= 3 -> 1200L
            retryCount <= 6 -> 2000L
            else -> 3000L
        }

        handler.postDelayed({
            if (sessionActive) {
                startRecognition()
            }
        }, delay)
    }

    private fun handleCommand(command: String) {
        val normalized = command.lowercase(Locale.US)

        when {
            normalized.contains("stop listening") ||
            normalized.contains("go to sleep") ||
            normalized == "sleep" -> {
                stopListening()
                speak("Wolf is going to sleep.")
                lastMessage = "Wolf is sleeping."
                return
            }

            normalized.contains("whatsapp") -> {
                finishCommand("Opening WhatsApp.") {
                    openApp("com.whatsapp", "WhatsApp")
                }
            }

            normalized.contains("youtube") -> {
                finishCommand("Opening YouTube.") {
                    openApp(
                        "com.google.android.youtube",
                        "YouTube"
                    )
                }
            }

            normalized.contains("chrome") -> {
                finishCommand("Opening Chrome.") {
                    openApp("com.android.chrome", "Chrome")
                }
            }

            normalized.contains("free fire") -> {
                finishCommand("Opening Free Fire.") {
                    openApp("com.dts.freefireth", "Free Fire")
                }
            }

            normalized.contains("your name") -> {
                finishCommand(
                    "I am Death Wolf, your personal assistant."
                )
            }

            else -> {
                finishCommand(
                    "I heard you. My AI connection will be added later."
                )
            }
        }
    }

    private fun finishCommand(
        reply: String,
        action: (() -> Unit)? = null
    ) {
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        stopRecognizerOnly()

        lastMessage = reply
        status = "Ready for you"
        listening = false

        speak(reply)

        action?.let {
            handler.postDelayed(it, 500)
        }
    }

    private fun openApp(
        packageName: String,
        appName: String
    ) {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
            } catch (exception: Exception) {
                lastMessage = "Couldn't open $appName."
            }
        } else {
            lastMessage = "$appName is not installed."
            speak("$appName is not installed.")
        }
    }

    private fun speak(text: String) {
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "wolf_reply"
        )
    }

    private fun stopRecognizerOnly() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listening = false
    }

    private fun stopListening() {
        sessionActive = false
        handler.removeCallbacksAndMessages(null)

        stopRecognizerOnly()
        tts?.stop()

        status = "Wolf is sleeping"
        lastMessage = "Tap ACTIVATE WOLF when you need me."
    }

    override fun onDestroy() {
        sessionActive = false
        handler.removeCallbacksAndMessages(null)

        stopRecognizerOnly()

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }
}

@Composable
fun WolfScreen(
    status: String,
    listening: Boolean,
    message: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val background = Color(0xFF08050D)
    val purple = Color(0xFF9B4DFF)

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(35.dp))

                Text(
                    text = "DEATH WOLF",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "YOUR PERSONAL AI ASSISTANT",
                    color = purple,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .background(
                            purple.copy(alpha = 0.13f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.size(125.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = purple
                        )
                    ) {
                        Image(
                            painter = painterResource(
                                id = R.drawable.wolf_logo
                            ),
                            contentDescription = "Death Wolf logo",
                            modifier = Modifier
                                .size(85.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = status,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purple
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("ACTIVATE WOLF")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "STOP LISTENING",
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
