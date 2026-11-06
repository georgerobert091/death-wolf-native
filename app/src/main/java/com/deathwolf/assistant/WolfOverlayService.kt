package com.deathwolf.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs

class WolfOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var wolfIcon: ImageView? = null

    companion object {
        const val ACTION_SHOW = "com.deathwolf.assistant.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.deathwolf.assistant.HIDE_OVERLAY"
        const val EXTRA_OPEN_MICROPHONE = "death_wolf_open_microphone"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingWolf()
            ACTION_HIDE -> {
                removeFloatingWolf()
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
                setColor(android.graphics.Color.rgb(25, 12, 40))
                setStroke((2 * density).toInt(), android.graphics.Color.rgb(160, 75, 255))
            }
            clipToOutline = true
            setPadding((4 * density).toInt(), (4 * density).toInt(),
                (4 * density).toInt(), (4 * density).toInt())
            contentDescription = "Open Death Wolf AI"
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
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
                        try {
                            windowManager.updateViewLayout(icon, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openDeathWolf()
                    true
                }
                else -> true
            }
        }
    }

    private fun openDeathWolf() {
        removeFloatingWolf()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_MICROPHONE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            startActivity(launchIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Death Wolf AI.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFloatingWolf() {
        wolfIcon?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        wolfIcon = null
    }

    override fun onDestroy() {
        removeFloatingWolf()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
