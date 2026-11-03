package com.deathwolf.assistant

import android.app.Service
import android.content.Intent
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
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.abs

class FloatingWolfService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var chatPanel: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var textToSpeech: TextToSpeech? = null

    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f

    private val messages = mutableListOf<Pair<String, Boolean>>()

    private val purple = Color.rgb(92, 35, 145)
    private val lightPurple = Color.rgb(205, 155, 255)
    private val background = Color.rgb(13, 8, 22)
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

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(
        text: String,
        size: Float,
        color: Int = white,
        bold: Boolean = false
    ) = TextView(this).apply {
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

        circle.addView(
            wolf,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            circle,
            FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        )

        val close = TextView(this).apply {
            text = "×"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(190, 45, 65))
            }
            setOnClickListener { stopSelf() }
        }
        root.addView(
            close,
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END)
        )

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - size - dp(18)).coerceAtLeast(0)
            y = resources.displayMetrics.heightPixels / 3
        }

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - touchX) < dp(8) &&
                        abs(event.rawY - touchY) < dp(8)
                    ) {
                        openChatPanel()
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = root
        bubbleParams = params
        windowManager.addView(root, params)
    }

    private fun openChatPanel() {
        if (chatPanel != null) return

        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(background, 24)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(12).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.wolf_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)))

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleColumn.addView(label("DEATH WOLF AI", 15f, white, true))
        titleColumn.addView(label("Your assistant", 11f, lightPurple))
        header.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val minimize = label("—", 24f, lightPurple, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { minimizeToBubble() }
        }
        header.addView(minimize)

        val close = label("×", 26f, white, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { closePanelToBubble() }
        }
        header.addView(close)
        outer.addView(header)

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(75, 48, 94))
        }
        val dividerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(10); bottomMargin = dp(10) }
        outer.addView(divider, dividerParams)

        val scroll = ScrollView(this)
        val conversation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(conversation)
        outer.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        if (messages.isEmpty()) {
            addBubble(conversation, "Hello. I'm Wolf. What would you like to do?", false)
        } else {
            messages.forEach { addBubble(conversation, it.first, it.second) }
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val input = EditText(this).apply {
            hint = "Message Wolf..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(white)
            textSize = 14f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = rounded(card, 22)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        val send = TextView(this).apply {
            text = "➤"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(purple)
            }
            setOnClickListener {
                sendMessage(input, conversation, scroll)
            }
        }
        val sendParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            leftMargin = dp(8)
        }
        inputRow.addView(send, sendParams)
        outer.addView(inputRow)

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(input, conversation, scroll)
                true
            } else false
        }

        val height = (resources.displayMetrics.heightPixels * 0.68f).toInt()
        val width = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(260))
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(28)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        chatPanel = outer
        panelParams = params
        windowManager.addView(outer, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(container: LinearLayout, text: String, fromUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(white)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(
                if (fromUser) purple else card,
                16
            )
        }
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
            addView(
                bubble,
                LinearLayout.LayoutParams(
                    (resources.displayMetrics.widthPixels * 0.72f).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        container.addView(row)
    }

    private fun sendMessage(
        input: EditText,
        conversation: LinearLayout,
        scroll: ScrollView
    ) {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        input.text.clear()
        messages.add(text to true)
        addBubble(conversation, text, true)

        val response =
            "I received your message: \"$text\". My online AI connection isn't connected yet. We can connect it later."
        messages.add(response to false)
        addBubble(conversation, response, false)

        textToSpeech?.speak(
            response,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "WOLF_OVERLAY_REPLY"
        )
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun minimizeToBubble() {
        removePanel()
        showFloatingButton()
    }

    private fun closePanelToBubble() {
        removePanel()
        showFloatingButton()
    }

    private fun removePanel() {
        chatPanel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        chatPanel = null
        panelParams = null
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int = START_STICKY

    override fun onDestroy() {
        removePanel()
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
