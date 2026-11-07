package com.deathwolf.assistant

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.abs

class WolfOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var wolfIcon: ImageView? = null
    private var dock: LinearLayout? = null
    private var dockStatus: TextView? = null
    private var statusReceiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(WolfVoiceService.EXTRA_STATUS) ?: return
            dockStatus?.text = message
        }
    }

    companion object {
        const val ACTION_SHOW = "com.deathwolf.assistant.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.deathwolf.assistant.HIDE_OVERLAY"
        const val EXTRA_OPEN_MICROPHONE = "death_wolf_open_microphone"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerStatusReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingWolf()
            ACTION_HIDE -> {
                removeDock()
                removeFloatingWolf()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun showFloatingWolf() {
        if (wolfIcon != null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Death Wolf AI to display over other apps.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val density = resources.displayMetrics.density
        val size = (64 * density).toInt()
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.wolf_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(25, 12, 40))
                setStroke((2 * density).toInt(), Color.rgb(160, 75, 255))
            }
            clipToOutline = true
            setPadding((4 * density).toInt(), (4 * density).toInt(),
                (4 * density).toInt(), (4 * density).toInt())
            contentDescription = "Open Death Wolf microphone dock"
        }

        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - size - (16 * density).toInt()).coerceAtLeast(0)
            y = (150 * density).toInt()
        }

        setupDragging(icon, params)
        try {
            windowManager.addView(icon, params)
            wolfIcon = icon
        } catch (_: Exception) {
            Toast.makeText(this, "Could not display the floating Wolf icon.", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun setupDragging(icon: ImageView, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        val threshold = 8 * resources.displayMetrics.density

        icon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > threshold || abs(dy) > threshold) moved = true
                    if (moved) {
                        params.x = (startX + dx).toInt().coerceAtLeast(0)
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        try { windowManager.updateViewLayout(icon, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleDock()
                    true
                }
                else -> true
            }
        }
    }

    private fun toggleDock() {
        if (dock == null) showDock() else removeDock()
    }

    private fun showDock() {
        if (dock != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 12, 32))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(150, 65, 245))
            }
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.wolf_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(35, 18, 55))
                setStroke(dp(1), Color.rgb(165, 95, 255))
            }
            clipToOutline = true
            contentDescription = "Death Wolf"
        }
        panel.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }
        textColumn.addView(TextView(this).apply {
            text = "DEATH WOLF"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        dockStatus = TextView(this).apply {
            text = "Tap the purple mic to speak"
            setTextColor(Color.rgb(210, 180, 255))
            textSize = 10f
            maxLines = 2
        }
        textColumn.addView(dockStatus)
        panel.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val mic = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(145, 55, 235))
                setStroke(dp(1), Color.rgb(205, 155, 255))
            }
            contentDescription = "Start voice recognition"
            setOnClickListener {
                dockStatus?.text = "Starting microphone…"
                val serviceIntent = Intent(this@WolfOverlayService, WolfVoiceService::class.java)
                    .setAction(WolfVoiceService.ACTION_START)
                try {
                    ContextCompat.startForegroundService(this@WolfOverlayService, serviceIntent)
                } catch (_: Exception) {
                    dockStatus?.text = "Could not start microphone. Open the app and check permissions."
                }
            }
        }
        panel.addView(mic, LinearLayout.LayoutParams(dp(46), dp(46)))

        val close = TextView(this).apply {
            text = "×"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 195, 255))
            setPadding(dp(8), 0, dp(4), 0)
            contentDescription = "Close microphone dock"
            setOnClickListener { removeDock() }
        }
        panel.addView(close, LinearLayout.LayoutParams(dp(36), dp(46)))

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(240)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(28)
        }

        try {
            windowManager.addView(panel, params)
            dock = panel
        } catch (_: Exception) {
            dockStatus = null
            Toast.makeText(this, "Could not open the microphone dock.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeDock() {
        dock?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        dock = null
        dockStatus = null
    }

    private fun removeFloatingWolf() {
        wolfIcon?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        wolfIcon = null
    }

    private fun registerStatusReceiver() {
        if (statusReceiverRegistered) return
        val filter = IntentFilter(WolfVoiceService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        statusReceiverRegistered = true
    }

    override fun onDestroy() {
        removeDock()
        removeFloatingWolf()
        if (statusReceiverRegistered) {
            try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
            statusReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
