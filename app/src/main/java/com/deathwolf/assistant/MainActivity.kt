package com.deathwolf.assistant

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
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

    private val speechInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                voiceStatus = "Heard: $spoken"
                sendChatMessage(spoken)
            } else {
                voiceStatus = "I didn't catch that. Try again."
            }
        } else {
            voiceStatus = "Voice input closed. Try again when ready."
        }
    }

    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) requestNotificationPermissionIfNeeded()
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
                    voiceStatus = voiceStatus
                )
            }
        }
    }

    private fun setAssistantMode(enabled: Boolean) {
        if (enabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionIfNeeded()
            } else {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            stopService(Intent(this, WolfListeningService::class.java))
            assistantEnabled = false
            permissionDenied = false
            getPreferences(MODE_PRIVATE).edit().putBoolean("assistant_enabled", false).apply()
        }
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
        // If Android stopped the service after screen-off, reflect that in the switch.
        if (!getPreferences(MODE_PRIVATE).getBoolean("assistant_enabled", false)) {
            assistantEnabled = false
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Death Wolf AI")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechInput.launch(intent)
            voiceStatus = "Listening through Android speech recognition..."
        } catch (_: Exception) {
            voiceStatus = "No speech recognition service is available on this phone."
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
    voiceStatus: String
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
                            if (assistantEnabled) "Enabled • wake-word setup is next"
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
                        Text("VOICE WAKE-WORD INTEGRATION COMES NEXT", color = WolfLightPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 1.sp)
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
                Text(voiceStatus, color = WolfLightPurple, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
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
                    modifier = Modifier.size(48.dp).background(WolfPurple, CircleShape)
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
