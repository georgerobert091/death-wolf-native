package com.deathwolf.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class FloatingWolfService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
    }

    private fun showFloatingButton() {
        if (floatingView != null) return

        val density = resources.displayMetrics.density
        val size = (60 * density).toInt()

        val root = FrameLayout(this)

        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(92, 35, 145))
                setStroke(
                    (2 * density).toInt(),
                    Color.rgb(205, 155, 255)
                )
            }
            elevation = 12f
        }

        val wolf = ImageView(this).apply {
            setImageResource(R.drawable.wolf_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Open Death Wolf AI"

            val pad = (7 * density).toInt()
            setPadding(pad, pad, pad, pad)
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

        val closeSize = (20 * density).toInt()

        val closeButton = TextView(this).apply {
            text = "×"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(190, 45, 65))
            }

            setOnClickListener {
                stopSelf()
            }
        }

        root.addView(
            closeButton,
            FrameLayout.LayoutParams(
                closeSize,
                closeSize,
                Gravity.TOP or Gravity.END
            )
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

            x = (resources.displayMetrics.widthPixels - size - 18)
                .coerceAtLeast(0)

            y = (resources.displayMetrics.heightPixels / 3)
                .coerceAtLeast(0)
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
                    params.x =
                        startX + (event.rawX - touchX).toInt()

                    params.y =
                        startY + (event.rawY - touchY).toInt()

                    windowManager.updateViewLayout(root, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val movedX =
                        kotlin.math.abs(event.rawX - touchX)

                    val movedY =
                        kotlin.math.abs(event.rawY - touchY)

                    if (movedX < 10 && movedY < 10) {
                        val openApp =
                            Intent(this, MainActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }

                        startActivity(openApp)
                    }

                    true
                }

                else -> false
            }
        }

        floatingView = root
        windowManager.addView(root, params)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }

        floatingView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
