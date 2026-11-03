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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val WolfBlack = Color(0xFF090510)
private val WolfPurple = Color(0xFF9B4DFF)
private val WolfLightPurple = Color(0xFFD3A8FF)
private val WolfCard = Color(0xFF191024)
private val WolfWhite = Color(0xFFF5EDFF)

data class WolfMessage(
    val text: String,
    val isUser: Boolean
)
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val handler = Handler(Looper.getMainLooper())

    private var sessionActive by mutableStateOf(false)
    private var isListening by mutableStateOf(false)
    private var waitingForWakeWord by mutableStateOf(false)

    private var status by mutableStateOf("Ready when you are.")
    private var lastMessage by mutableStateOf("Tap below and say Wolf.")

    private var showChat by mutableStateOf(false)

    private val chatMessages = mutableStateListOf<WolfMessage>()

    private var retryCount = 0

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startSession()
            } else {
                status = "Microphone permission required"
                lastMessage = "Allow microphone access to use Wolf."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this, this)

        setContent {
            MaterialTheme {
                if (showChat) {
                    WolfChatScreen(
                        messages = chatMessages,
                        onSend = { sendChatMessage(it) },
                        onBack = { showChat = false }
                    )
                } else {
                    WolfScreen(
                        status = status,
                        message = lastMessage,
                        active = sessionActive,
                        onActivate = { checkPermissionAndListen() },
                        onStop = { stopSession() },
                        onOpenChat = { showChat = true }
                    )
                }
            }
        }
    }
    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(0.85f)
            textToSpeech?.setSpeechRate(0.95f)
        }
    }

    private fun speak(message: String) {
        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "WOLF_SPEECH"
        )
    }

    private fun addMessage(text: String, isUser: Boolean) {
        chatMessages.add(
            WolfMessage(
                text = text,
                isUser = isUser
            )
        )
    }

    private fun sendChatMessage(message: String) {
        val text = message.trim()

        if (text.isEmpty()) return

        addMessage(text, true)

        val response =
            "I received your message: \"$text\". My online AI connection is not connected yet. Once connected, I'll be able to have intelligent conversations with you."

        addMessage(response, false)
        speak(response)
    }
    private fun checkPermissionAndListen() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startSession()
        } else {
            requestPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startSession() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "Speech recognition unavailable"
            lastMessage = "Check your phone's speech recognition service."
            return
        }

        handler.removeCallbacksAndMessages(null)

        sessionActive = true
        waitingForWakeWord = true
        retryCount = 0

        status = "Listening for Wolf"
        lastMessage = "Say Wolf to activate your assistant."

        startRecognition()
    }

    private fun startRecognition() {
        if (!sessionActive) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSession()
            status = "Speech recognition unavailable"
            return
        }
        try {
            speechRecognizer?.destroy()

            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true

                        status = if (waitingForWakeWord) {
                            "Listening for Wolf"
                        } else {
                            "Wolf is listening"
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        status = "Processing..."
                    }

                    override fun onError(error: Int) {
                        isListening = false

                        if (sessionActive) {
                            scheduleRetry()
                        }
                    }
                                        override fun onResults(results: Bundle?) {
                        isListening = false

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val spokenText = matches
                            ?.firstOrNull()
                            ?.trim()
                            ?.lowercase(Locale.US)
                            .orEmpty()

                        handleSpeech(spokenText)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
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
                    Locale.US.toLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

            speechRecognizer?.startListening(intent)

        } catch (exception: Exception) {
            isListening = false
            status = "Could not start listening"
            lastMessage = exception.localizedMessage ?: "Please try again."
        }
    }
    private fun handleSpeech(text: String) {
        if (!sessionActive) return

        if (text.isBlank()) {
            scheduleRetry()
            return
        }

        if (waitingForWakeWord) {

            if (
                text.contains("wolf") ||
                text.contains("death wolf")
            ) {
                waitingForWakeWord = false
                retryCount = 0

                status = "Wolf activated"
                lastMessage = "I'm listening. What do you need?"

                speak("I'm listening. What do you need?")

                handler.postDelayed({
                    if (sessionActive) {
                        startRecognition()
                    }
                }, 1600)

            } else {
                lastMessage = "I heard: $text. Say Wolf to activate."
                scheduleRetry()
            }

        } else {
            addMessage(text, true)
            lastMessage = "You said: $text"
            processCommand(text)
        }
    }

    private fun processCommand(command: String) {
        val text = command.lowercase(Locale.US)

        when {

            text.contains("stop listening") ||
            text.contains("go to sleep") -> {
                stopSession()
                speak("Okay. Wolf is going to sleep.")
            }

            text.contains("open whatsapp business") ||
            text.contains("open business whatsapp") -> {
                openApp(
                    "com.whatsapp.w4b",
                    "Opening WhatsApp Business"
                )
            }
            text.contains("open whatsapp") ||
            text.contains("open what's app") -> {
                openApp(
                    "com.whatsapp",
                    "Opening WhatsApp"
                )
            }

            text.contains("open instagram") -> {
                openApp(
                    "com.instagram.android",
                    "Opening Instagram"
                )
            }

            text.contains("open youtube") ||
            text.contains("open you tube") -> {
                openApp(
                    "com.google.android.youtube",
                    "Opening YouTube"
                )
            }

            text.contains("open chrome") ||
            text.contains("open browser") -> {
                openApp(
                    "com.android.chrome",
                    "Opening Chrome"
                )
            }

            text.contains("open free fire") ||
            text.contains("open freefire") -> {
                openApp(
                    "com.dts.freefireth",
                    "Opening Free Fire"
                )
            }

            else -> {
                status = "Command received"

                val response =
                    "I heard your command: $command. My online AI connection is not installed yet."

                lastMessage = response
                addMessage(response, false)
                speak(response)
            }
        }
    }
    private fun openApp(
        packageName: String,
        response: String
    ) {
        try {
            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                stopSession()
                speak(response)

                handler.postDelayed({
                    try {
                        startActivity(launchIntent)
                    } catch (exception: Exception) {
                        status = "Could not open application"
                    }
                }, 500)

            } else {
                status = "Application not found"
                lastMessage = "That application may not be installed."
                speak("I could not find that application.")
            }

        } catch (exception: Exception) {
            status = "Unable to open application"
            lastMessage = exception.localizedMessage ?: "Please try again."
        }
    }

    private fun scheduleRetry() {
        if (!sessionActive) return

        retryCount++

        status = if (waitingForWakeWord) {
            "Waiting for Wolf..."
        } else {
            "Waiting for your command..."
        }

        val delay = when {
            retryCount <= 3 -> 1200L
            retryCount <= 6 -> 2000L
            else -> 3000L
        }

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            if (sessionActive) {
                startRecognition()
            }
        }, delay)
    }

    private fun stopSession() {
        sessionActive = false
        isListening = false
        waitingForWakeWord = false

        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        status = "Wolf is sleeping"
        lastMessage = "Tap Activate Wolf when you need me."
    }

    override fun onDestroy() {
        stopSession()

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
@Composable
fun WolfScreen(
    status: String,
    message: String,
    active: Boolean,
    onActivate: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WolfBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Image(
                painter = painterResource(
                    id = R.drawable.wolf_logo
                ),
                contentDescription = "Death Wolf AI logo",
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "DEATH WOLF",
                color = WolfWhite,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )

            Text(
                text = "AI ASSISTANT",
                color = WolfPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WolfCard
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "WOLF STATUS",
                        color = WolfLightPurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = status,
                        color = WolfWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = message,
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp
                    )
                }
            }
                        Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onActivate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WolfPurple
                )
            ) {
                Text(
                    text = if (active) {
                        "WOLF IS ACTIVE"
                    } else {
                        "ACTIVATE WOLF"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = WolfLightPurple
                )
            ) {
                Text(
                    text = "STOP LISTENING",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WolfCard
                )
            ) {
                Text(
                    text = "OPEN WOLF CHAT",
                    color = WolfLightPurple,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
                        Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "YOUR WORLD. YOUR WOLF.",
                color = WolfLightPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun WolfChatScreen(
    messages: List<WolfMessage>,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        color = WolfBlack
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WolfCard)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                TextButton(onClick = onBack) {
                    Text(
                        text = "BACK",
                        color = WolfLightPurple,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "DEATH WOLF",
                        color = WolfWhite,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = "AI CHAT",
                        color = WolfPurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }
            }
                        if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(
                                id = R.drawable.wolf_logo
                            ),
                            contentDescription = "Wolf logo",
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "WELCOME TO WOLF CHAT",
                            color = WolfLightPurple,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Your conversation starts here.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.isUser) {
                                Arrangement.End
                            } else {
                                Arrangement.Start
                            }
                        ) {
                            Card(
                                modifier = Modifier.widthIn(max = 300.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (message.isUser) {
                                        WolfPurple
                                    } else {
                                        WolfCard
                                    }
                                )
                            ) {
                               Text(
                                    text = message.text,
                                    modifier = Modifier.padding(14.dp),
                                    color = WolfWhite,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WolfCard)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Message Wolf...", color = Color.Gray)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = WolfBlack,
                        unfocusedContainerColor = WolfBlack,
                        focusedTextColor = WolfWhite,
                        unfocusedTextColor = WolfWhite,
                        cursorColor = WolfPurple,
                        focusedIndicatorColor = WolfPurple,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.isNotBlank()) {
                                onSend(input)
                                input = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSend(input)
                            input = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WolfPurple
                    )
                ) {
                    Text(
                        text = "SEND",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
