package com.orbistrade.dubaiai.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.orbistrade.dubaiai.core.AppRuntimeState

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updater = object : Runnable {
        override fun run() {
            val vision = AppRuntimeState.vision.value
            val indicators = vision.indicators
            val strategy = vision.strategy
            overlayView?.text = buildString {
                append("ORBIS Dubai V1\n")
                append(if (vision.graphDetected) "Gráfico: SIM" else "Procurando gráfico")
                append("\nCandles: ${vision.candleCount}")
                append("\nTendência: ${indicators.trend}")
                append("\nSinal: ${strategy.direction}")
                append("\nScore: ${strategy.score}/100")
                append("\nConfiança: ${strategy.confidence}")
            }
            handler.postDelayed(this, 600L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        showOverlay()
        handler.post(updater)
        AppRuntimeState.setOverlayRunning(true)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        AppRuntimeState.setOverlayRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = TextView(this).apply {
            text = "ORBIS Dubai V1\nInicializando..."
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xDD111827.toInt())
            setPadding(20, 14, 20, 14)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 160
        }
        windowManager.addView(overlayView, params)
    }

    private fun notification(): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Orbis Trade AI")
                .setContentText("Dubai V1 e alertas ativos")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Orbis Trade AI")
                .setContentText("Dubai V1 e alertas ativos")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true).build()
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1001
    }
}
