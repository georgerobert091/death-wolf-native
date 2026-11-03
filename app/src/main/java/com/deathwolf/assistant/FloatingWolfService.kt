package com.deathwolf.assistant

import android.app.Service
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class FloatingWolfService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var chatPanel: View? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var conversation: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private var messageInput: EditText? = null

    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f
    private val messages = mutableListOf<Pair<String, Boolean>>()

    private val purple = Color.rgb(92, 35, 145)
    private val lightPurple = Color.rgb(205, 155, 255)
    private val backgroundColor = Color.rgb(13, 8, 22)
    private val card = Color.rgb(31, 20, 43)
    private val white = Color.rgb(248, 241, 255)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        textToSpeech = TextToSpeech(this, this)
        showFloatingButton()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(0.85f)
            textToSpeech?.setSpeechRate(0.95f)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(text: String, size: Float, color: Int = white, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }

    private fun showFloatingButton() {
        if (floatingView != null) return
        val size = dp(60)
        val root = FrameLayout(this)
        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(purple)
                setStroke(dp(2), lightPurple)
            }
            elevation = dp(8).toFloat()
        }
        val wolf = ImageView(this).apply {
            setImageResource(R.drawable.wolf_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Open Death Wolf AI"
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        circle.addView(wolf, FrameLayout.LayoutParams(-1, -1))
        root.addView(circle, FrameLayout.LayoutParams(size, size, Gravity.CENTER))

        val close = TextView(this).apply {
            text = "×"; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(190, 45, 65))
            }
            setOnClickListener { stopSelf() }
        }
        root.addView(close, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END))

        val params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - size - dp(18)).coerceAtLeast(0)
            y = resources.displayMetrics.heightPixels / 3
        }

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(root, params); true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - touchX) < dp(8) && abs(event.rawY - touchY) < dp(8)) openChatPanel()
                    true
                }
                else -> false
            }
        }
        floatingView = root
        windowManager.addView(root, params)
    }

    private fun openChatPanel() {
        if (chatPanel != null) return
        floatingView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        floatingView = null

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(backgroundColor, 24)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(12).toFloat()
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply { setImageResource(R.drawable.wolf_logo); scaleType = ImageView.ScaleType.CENTER_CROP }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)))
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        titleColumn.addView(label("DEATH WOLF AI", 15f, white, true))
        titleColumn.addView(label("Your assistant", 11f, lightPurple))
        header.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("—", 24f, lightPurple, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0); setOnClickListener { minimizeToBubble() }
        })
        header.addView(label("×", 26f, white, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(4), 0); setOnClickListener { closePanelToBubble() }
        })
        outer.addView(header)
        outer.addView(View(this).apply { setBackgroundColor(Color.rgb(75, 48, 94)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(10) })

        val scroll = ScrollView(this)
        val chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(chat)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        conversation = chat
        scrollView = scroll
        if (messages.isEmpty()) {
            addBubble(chat, "Hello. I'm Wolf. Type or tap the microphone and tell me what to do. Try: open WhatsApp.", false)
        } else messages.forEach { addBubble(chat, it.first, it.second) }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0)
        }
        val input = EditText(this).apply {
            hint = "Message Wolf..."; setHintTextColor(Color.LTGRAY); setTextColor(white); textSize = 14f
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_SEND
            background = rounded(card, 22); setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        messageInput = input
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        val mic = TextView(this).apply {
            text = "🎙"; textSize = 19f; setTextColor(white); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(card) }
            setOnClickListener { startVoiceInput() }
        }
        inputRow.addView(mic, LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(6) })
        val send = TextView(this).apply {
            text = "➤"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(purple) }
            setOnClickListener { sendMessage(input) }
        }
        inputRow.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(6) })
        outer.addView(inputRow)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(input); true } else false
        }

        val height = (resources.displayMetrics.heightPixels * 0.68f).toInt()
        val width = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(260))
        val params = WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(28); softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        chatPanel = outer
        windowManager.addView(outer, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(container: LinearLayout, text: String, fromUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(white)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(if (fromUser) purple else card, 16)
        }
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
            addView(bubble, LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.72f).toInt(), -2))
        }
        container.addView(row)
    }

    private fun showMessage(text: String, fromUser: Boolean) {
        messages.add(text to fromUser)
        conversation?.let { addBubble(it, text, fromUser) }
        scrollView?.post { scrollView?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage(input: EditText) {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        showMessage(text, true)
        val response = handleCommand(text)
        showMessage(response, false)
        speak(response)
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun handleCommand(raw: String): String {
        val command = raw.trim()
        val lower = command.lowercase(Locale.ROOT)
        if (lower in listOf("help", "what can you do", "commands")) {
            return "You can say or type: open WhatsApp, launch Instagram, start YouTube, or list apps. I can open apps available in your phone's app launcher."
        }
        if (lower == "list apps" || lower == "show apps" || lower == "what apps do i have") {
            val names = getLaunchableApps().map { it.loadLabel(packageManager).toString() }.sorted()
            return if (names.isEmpty()) "I couldn't find any launchable apps." else "Apps I can open: " + names.take(45).joinToString(", ") +
                if (names.size > 45) " ... and ${names.size - 45} more." else "."
        }

        val appName = command.replace(Regex("(?i)^(please\\s+)?(can you\\s+)?(open|launch|start|run)\\s+"), "")
            .replace(Regex("(?i)\\s+(for me|please)$"), "").trim()
        val isOpenRequest = lower.startsWith("open ") || lower.startsWith("launch ") ||
            lower.startsWith("start ") || lower.startsWith("run ") ||
            lower.startsWith("please open ") || lower.startsWith("can you open ")
        if (!isOpenRequest || appName.isBlank()) {
            return "I can handle app commands now. Try typing “open WhatsApp” or “list apps”. My online AI connection is not connected yet."
        }

        val apps = getLaunchableApps()
        val match = apps.firstOrNull {
            it.loadLabel(packageManager).toString().equals(appName, ignoreCase = true)
        } ?: apps.firstOrNull {
            val label = it.loadLabel(packageManager).toString()
            label.contains(appName, ignoreCase = true) || appName.contains(label, ignoreCase = true)
        }
        if (match == null) return "I couldn't find an app named \"$appName\". Type “list apps” to see the app names I can open."
        val launchIntent = packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "Android didn't provide an opening shortcut for ${match.loadLabel(packageManager)}."
        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            "Opening ${match.loadLabel(packageManager)}."
        } catch (_: Exception) {
            "I couldn't open ${match.loadLabel(packageManager)}. Please try opening it from your app drawer."
        }
    }

    private fun getLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is not available on this phone.")
            Toast.makeText(this, "Speech recognition isn't available.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {
                            Toast.makeText(this@FloatingWolfService, "Listening...", Toast.LENGTH_SHORT).show()
                        }
                        override fun onResults(results: android.os.Bundle?) {
                            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (!spoken.isNullOrBlank()) {
                                messageInput?.setText(spoken)
                                messageInput?.let { sendMessage(it) }
                            }
                        }
                        override fun onError(error: Int) {
                            Toast.makeText(this@FloatingWolfService, "Didn't catch that. Tap the mic and try again.", Toast.LENGTH_SHORT).show()
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: android.os.Bundle?) {}
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command for Wolf")
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't start voice input. Check microphone permission.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WOLF_REPLY")
    }

    private fun minimizeToBubble() { removePanel(); showFloatingButton() }
    private fun closePanelToBubble() { removePanel(); showFloatingButton() }

    private fun removePanel() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        chatPanel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        chatPanel = null
        conversation = null
        scrollView = null
        messageInput = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        removePanel()
        floatingView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        floatingView = null
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
