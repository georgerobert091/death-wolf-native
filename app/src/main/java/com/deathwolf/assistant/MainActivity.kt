package com.deathwolf.assistant
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val WolfBlack = Color(0xFF090510)
private val WolfPurple = Color(0xFF963FFF)
private val WolfLightPurple = Color(0xFFD5B0FF)
private val WolfCard = Color(0xFF1A1026)
private val WolfWhite = Color(0xFFF7F0FF)

data class WolfMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private val chatMessages = mutableStateListOf<WolfMessage>()
    private var assistantEnabled by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    private var voiceStatus by mutableStateOf("Ready for voice input")
    private var isListening by mutableStateOf(false)
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumeWakeWordAfterSpeech = false


    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) requestOverlayPermissionIfNeeded()
            else {
                assistantEnabled = false
                permissionDenied = true
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Android can still show foreground-service information in system task controls
            // when notification permission is denied, but we ask permission for the normal notice.
            activateAssistantService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistantEnabled = false
        getPreferences(MODE_PRIVATE).edit().putBoolean("assistant_enabled", false).apply()
        textToSpeech = TextToSpeech(this, this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = WolfPurple,
                background = WolfBlack,
                surface = WolfCard,
                onPrimary = Color.White,
                onBackground = WolfWhite,
                onSurface = WolfWhite
            )) {
                DeathWolfHome(
                    messages = chatMessages,
                    assistantEnabled = assistantEnabled,
                    permissionDenied = permissionDenied,
                    onAssistantChange = { enabled -> setAssistantMode(enabled) },
                    onSend = { sendChatMessage(it) },
                    onVoiceInput = { startVoiceInput() },
                    onStopVoiceInput = { stopVoiceInput() },
                    voiceStatus = voiceStatus,
                    isListening = isListening
                )
            }
        }
    }

    private fun setAssistantMode(enabled: Boolean) {
        if (!enabled) {
            stopService(Intent(this, WolfListeningService::class.java))
            stopService(Intent(this, WolfOverlayService::class.java).setAction(WolfOverlayService.ACTION_HIDE))
            assistantEnabled = false
            permissionDenied = false
            getPreferences(MODE_PRIVATE).edit().putBoolean("assistant_enabled", false).apply()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        requestOverlayPermissionIfNeeded()
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
            voiceStatus = "Allow Death Wolf AI to display over other apps, then return and enable Assistant Mode."
            return
        }
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            activateAssistantService()
        }
    }

    private fun activateAssistantService() {
        val serviceIntent = Intent(this, WolfListeningService::class.java)
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            assistantEnabled = true
            permissionDenied = false
            getPreferences(MODE_PRIVATE).edit().putBoolean("assistant_enabled", true).apply()
        } catch (_: Exception) {
            assistantEnabled = false
            permissionDenied = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (!getPreferences(MODE_PRIVATE).getBoolean("assistant_enabled", false)) {
            assistantEnabled = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            voiceStatus = "Overlay permission is available."
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            voiceStatus = "Allow microphone access, then tap the microphone again."
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus = "No speech recognition service is available. Install or enable Google's speech recognition service."
            isListening = false
            return
        }

        // OpenWakeWord and Android SpeechRecognizer cannot reliably capture the microphone
        // at the same time. Pause the wake-word service before opening speech recognition.
        resumeWakeWordAfterSpeech = assistantEnabled
        if (resumeWakeWordAfterSpeech) {
            try {
                startService(
                    Intent(this, WolfListeningService::class.java)
                        .setAction(WolfListeningService.ACTION_PAUSE_FOR_SPEECH)
                )
            } catch (_: Exception) {
                voiceStatus = "Could not pause wake-word listening. Turn Assistant Mode off and try the microphone again."
                resumeWakeWordAfterSpeech = false
                return
            }
        }

        stopVoiceInput()
        // Give the service a moment to release AudioRecord before SpeechRecognizer starts.
        mainHandler.postDelayed({
            beginSpeechRecognition()
        }, if (resumeWakeWordAfterSpeech) 500L else 0L)
    }

    private fun beginSpeechRecognition() {
        if (isFinishing || isDestroyed) {
            resumeWakeWordDetectionIfNeeded()
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    voiceStatus = "Wolf is listening… Speak clearly now."
                }

                override fun onBeginningOfSpeech() {
                    voiceStatus = "Hearing you…"
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level callback; the platform handles the microphone stream.
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    voiceStatus = "Processing your words…"
                }

                override fun onError(error: Int) {
                    isListening = false
                    voiceStatus = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed. Check microphone access and close other apps using the mic."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was interrupted. Tap the microphone and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Android denied microphone access. Allow it in Death Wolf AI app permissions."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a working internet connection. Check your connection and try again."
                        SpeechRecognizer.ERROR_NO_MATCH -> "I heard audio but couldn't recognize the words. Try speaking closer and more clearly."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Wait a moment and try again."
                        SpeechRecognizer.ERROR_SERVER -> "Google's speech recognition service returned an error. Try again shortly."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't detect speech. Tap the microphone and speak after the listening message."
                        else -> "Voice recognition stopped (error $error). Tap the microphone to try again."
                    }
                    releaseSpeechRecognizer()
                    resumeWakeWordDetectionIfNeeded()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    releaseSpeechRecognizer()

                    if (!spoken.isNullOrBlank()) {
                        voiceStatus = "Heard: $spoken"
                        resumeWakeWordDetectionIfNeeded()
                        sendChatMessage(spoken)
                    } else {
                        voiceStatus = "No words were returned. Please try again."
                        resumeWakeWordDetectionIfNeeded()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        voiceStatus = "Hearing: $partial"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }

            isListening = true
            voiceStatus = "Starting microphone… Speak after you hear the listening prompt."
            recognizer.startListening(speechIntent)
        } catch (e: Exception) {
            isListening = false
            voiceStatus = "Couldn't start voice recognition: ${e.message ?: "unknown error"}"
            releaseSpeechRecognizer()
            resumeWakeWordDetectionIfNeeded()
        }
    }

    private fun stopVoiceInput() {
        mainHandler.removeCallbacksAndMessages(null)
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        releaseSpeechRecognizer()
        isListening = false
        if (voiceStatus.contains("listening", ignoreCase = true) ||
            voiceStatus.contains("Hearing", ignoreCase = true)) {
            voiceStatus = "Listening stopped."
        }
        resumeWakeWordDetectionIfNeeded()
    }

    private fun releaseSpeechRecognizer() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun resumeWakeWordDetectionIfNeeded() {
        if (!resumeWakeWordAfterSpeech) return
        resumeWakeWordAfterSpeech = false
        if (!assistantEnabled) return
        try {
            startService(
                Intent(this, WolfListeningService::class.java)
                    .setAction(WolfListeningService.ACTION_RESUME_AFTER_SPEECH)
            )
        } catch (_: Exception) {
            voiceStatus = "Voice input finished, but wake-word listening could not resume. Toggle Assistant Mode off and on."
        }
    }

    private fun sendChatMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        chatMessages.add(WolfMessage(text, true))

        val response = handleLocalCommand(text)
        chatMessages.add(WolfMessage(response, false))
        speak(response)
    }

    private fun handleLocalCommand(command: String): String {
        val lower = command.lowercase(Locale.ROOT).trim()
        if (lower == "list apps" || lower == "show apps" || lower == "what apps can you open") {
            val labels = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            ).map { it.loadLabel(packageManager).toString() }.distinct().sorted()
            return if (labels.isEmpty()) "I couldn't find any launchable apps." else
                "Apps I can launch include: " + labels.take(60).joinToString(", ") +
                    if (labels.size > 60) " and more." else "."
        }

        val isLaunchRequest = lower.startsWith("open ") || lower.startsWith("launch ") ||
            lower.startsWith("start ") || lower.startsWith("run ")
        if (isLaunchRequest) {
            val appName = command.replace(
                Regex("(?i)^(please\\s+)?(open|launch|start|run)\\s+"), ""
            ).trim()
            val apps = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            )
            val match = apps.firstOrNull {
                it.loadLabel(packageManager).toString().equals(appName, true)
            } ?: apps.firstOrNull {
                it.loadLabel(packageManager).toString().contains(appName, true)
            }

            if (match != null) {
                val launch = packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
                if (launch != null) {
                    return try {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launch)
                        "Opening ${match.loadLabel(packageManager)}."
                    } catch (_: Exception) {
                        "I couldn't open that app. Please try from your app drawer."
                    }
                }
            }
            return "I couldn't find a launchable app called \"$appName\". Try “list apps”."
        }

        return "I received your message: \"$command\". The full online AI connection will be added in a later stage."
    }

    private fun speak(message: String) {
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "WOLF_REPLY")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(0.85f)
            textToSpeech?.setSpeechRate(0.95f)
        }
    }

    override fun onDestroy() {
        stopVoiceInput()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun DeathWolfHome(
    messages: List<WolfMessage>,
    assistantEnabled: Boolean,
    permissionDenied: Boolean,
    onAssistantChange: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    voiceStatus: String,
    isListening: Boolean
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Surface(Modifier.fillMaxSize(), color = WolfBlack) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier.fillMaxWidth().background(WolfCard).padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.wolf_logo),
                        contentDescription = "Death Wolf logo",
                        modifier = Modifier.size(54.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DEATH WOLF AI", color = WolfWhite, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Text("YOUR WORLD. YOUR WOLF.", color = WolfLightPurple, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                    Text("●", color = if (assistantEnabled) Color(0xFF65E69A) else Color.Gray, fontSize = 15.sp)
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WolfBlack).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("VOICE ASSISTANT MODE", color = WolfWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (assistantEnabled) "Enabled • say Hey Jarvis to summon Wolf"
                            else "Off • Wolf will not listen",
                            color = if (assistantEnabled) WolfLightPurple else Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(checked = assistantEnabled, onCheckedChange = onAssistantChange)
                }
                if (permissionDenied) {
                    Text("Microphone permission is needed to enable Assistant Mode.", color = Color(0xFFFFA5A5), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            if (messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.wolf_logo),
                            contentDescription = "Death Wolf wolf emblem",
                            modifier = Modifier.size(150.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(22.dp))
                        Text("HELLO, I'M WOLF", color = WolfWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your personal assistant. Chat with me, or try a command such as “open WhatsApp” or “list apps”.",
                            color = Color.LightGray, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        Text("SAY HEY JARVIS TO SUMMON WOLF", color = WolfLightPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 1.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (message.isUser) WolfPurple else WolfCard,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 310.dp)
                            ) {
                                Text(message.text, color = WolfWhite, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().background(WolfCard).padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isListening) {
                    Surface(
                        color = WolfBlack,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(10.dp).background(Color(0xFFB875FF), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(voiceStatus, color = WolfLightPurple, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = onStopVoiceInput) { Text("STOP", color = Color(0xFFFFA5A5)) }
                        }
                    }
                } else {
                    Text(voiceStatus, color = WolfLightPurple, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Wolf...", color = Color.Gray) },
                    shape = RoundedCornerShape(15.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = WolfBlack,
                        unfocusedContainerColor = WolfBlack,
                        focusedTextColor = WolfWhite,
                        unfocusedTextColor = WolfWhite,
                        cursorColor = WolfPurple,
                        focusedIndicatorColor = WolfPurple,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) { onSend(input); input = "" }
                    })
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.size(48.dp).background(if (isListening) Color(0xFFB875FF) else WolfPurple, CircleShape)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Speak to Wolf", tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { if (input.isNotBlank()) { onSend(input); input = "" } },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WolfPurple),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("SEND", color = Color.White, fontWeight = FontWeight.Bold)
                }
                }
            }
        }
    }
}
