package com.example.engine

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HermesFloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        _isBubbleVisible.value = true
        showFloatingBubble()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBubble() {
        if (!FloatingOverlayHelper.canDrawOverlays(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 300
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xCC0D131A.toInt()) // Obsidian background
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE0D131A.toInt())
                setStroke(3, 0xFF00E5FF.toInt())
                cornerRadius = 60f
            }
            background = drawable
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_dialer)
            setColorFilter(0xFF00E5FF.toInt())
            layoutParams = LinearLayout.LayoutParams(90, 90)
        }
        container.addView(icon)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // Open Hermes Main Console
                        val intent = Intent(this@HermesFloatingService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        Toast.makeText(this@HermesFloatingService, "Hermes AI Assistant Activated", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = container
        try {
            windowManager?.addView(container, params)
        } catch (e: Exception) {
            _isBubbleVisible.value = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isBubbleVisible.value = false
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val _isBubbleVisible = MutableStateFlow(false)
        val isBubbleVisible = _isBubbleVisible.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, HermesFloatingService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HermesFloatingService::class.java)
            context.stopService(intent)
            _isBubbleVisible.value = false
        }
    }
}
