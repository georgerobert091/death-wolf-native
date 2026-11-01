package com.deathwolf.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var status by mutableStateOf("Ready for you")
    private var listening by mutableStateOf(false)
    private var lastMessage by mutableStateOf("Say Wolf to get started.")

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
        }
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            status = "Microphone permission required"
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "Speech recognition unavailable"
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                status = "Listening..."
                listening = true
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status = "Processing..."
            }

            override fun onError(error: Int) {
                listening = false
                status = "Tap the microphone to try again"
            }

            override fun onResults(results: Bundle?) {
                listening = false

                val words = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                val spoken = words?.firstOrNull()?.lowercase() ?: return

                if (spoken.contains("wolf")) {
                    speak("I'm listening. What do you need?")
                    lastMessage = "I'm listening. What do you need?"
                    startCommandListening()
                } else {
                    handleCommand(spoken)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun startCommandListening() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        android.os.Handler(mainLooper).postDelayed({
            startListeningForCommand()
        }, 1000)
    }

    private fun startListeningForCommand() {
        if (!listening && !isFinishing) {
            startListening()
        }
    }

    private fun handleCommand(command: String) {
        lastMessage = "You said: $command"

        when {
            command.contains("open whatsapp") ||
            command.contains("whatsapp") -> {
                openApp("com.whatsapp", "WhatsApp")
            }

            command.contains("open youtube") ||
            command.contains("youtube") -> {
                openApp("com.google.android.youtube", "YouTube")
            }

            command.contains("open chrome") ||
            command.contains("chrome") -> {
                openApp("com.android.chrome", "Chrome")
            }

            command.contains("free fire") -> {
                openApp("com.dts.freefireth", "Free Fire")
            }

            command.contains("your name") -> {
                speak("I am Death Wolf, your personal assistant.")
                lastMessage = "I am Death Wolf, your personal assistant."
            }

            command.contains("sleep") ||
            command.contains("stop listening") -> {
                stopListening()
                speak("Wolf is going to sleep.")
                lastMessage = "Wolf is sleeping."
            }

            else -> {
                speak("I heard you. AI conversations will be added in the next stage.")
                lastMessage = "AI connection coming in the next stage."
            }
        }

        status = "Ready for you"
    }

    private fun openApp(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            speak("Opening $appName.")
            startActivity(launchIntent)
        } else {
            speak("$appName is not installed.")
            lastMessage = "$appName is not installed."
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wolf_reply")
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listening = false
        status = "Wolf is sleeping"
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text("🐺", fontSize = 43.sp)
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purple
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("START LISTENING")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("STOP LISTENING", color = Color.White)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
