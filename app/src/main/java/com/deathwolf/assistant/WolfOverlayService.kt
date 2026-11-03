package com.deathwolf.assistant

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class WolfOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var minimized = false

    companion object {
        const val ACTION_SHOW = "com.deathwolf.assistant.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.deathwolf.assistant.HIDE_OVERLAY"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showPanel()
            ACTION_HIDE -> {
                removePanel()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showPanel() {
        if (panel != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Death Wolf AI to display over other apps.", Toast.LENGTH_LONG).show()
            notifyOverlayClosed()
            stopSelf()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 18)
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 12, 35))
                cornerRadius = 28f
                setStroke(3, Color.rgb(145, 65, 255))
            }
        }

        val heading = TextView(this).apply {
            text = "🐺  DEATH WOLF AI"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "HEY JARVIS DETECTED"
            textSize = 13f
            setTextColor(Color.rgb(213, 176, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 14)
        }

        val openButton = makeButton("OPEN DEATH WOLF")
        val minimizeButton = makeButton("MINIMIZE")
        val closeButton = makeButton("CLOSE")

        openButton.setOnClickListener {
            removePanel()
            notifyOverlayClosed()
            val launch = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                startActivity(launch)
            } catch (_: Exception) {
                Toast.makeText(this, "Tap the Death Wolf notification to open the app.", Toast.LENGTH_LONG).show()
            }
            stopSelf()
        }
        minimizeButton.setOnClickListener { minimizePanel() }
        closeButton.setOnClickListener {
            removePanel()
            notifyOverlayClosed()
            stopSelf()
        }

        root.addView(heading)
        root.addView(status)
        root.addView(openButton)
        root.addView(minimizeButton)
        root.addView(closeButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            width = (resources.displayMetrics.widthPixels * 0.88f).toInt()
        }

        try {
            windowManager.addView(root, params)
            panel = root
        } catch (_: Exception) {
            Toast.makeText(this, "Could not display Death Wolf panel.", Toast.LENGTH_SHORT).show()
            notifyOverlayClosed()
            stopSelf()
        }
    }

    private fun makeButton(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(12, 15, 12, 15)
        background = GradientDrawable().apply {
            setColor(Color.rgb(75, 30, 125))
            cornerRadius = 18f
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 5, 0, 5) }
    }

    private fun minimizePanel() {
        val current = panel ?: return
        if (minimized) return
        minimized = true
        current.removeAllViews()
        val restore = makeButton("🐺  DEATH WOLF — TAP TO EXPAND")
        restore.setOnClickListener {
            removePanel()
            showPanel()
        }
        current.addView(restore)
    }

    private fun notifyOverlayClosed() {
        try {
            startService(
                Intent(this, WolfListeningService::class.java)
                    .setAction(WolfListeningService.ACTION_OVERLAY_CLOSED)
            )
        } catch (_: Exception) {
        }
    }

    private fun removePanel() {
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panel = null
        minimized = false
    }

    override fun onDestroy() {
        removePanel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
