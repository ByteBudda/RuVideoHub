package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.MainActivity
import com.example.ui.theme.Primary
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BackgroundPlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "SleekVideoHubPlaybackChannel"
        const val NOTIFICATION_ID = 4059

        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.action.PREVIOUS"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_SHOW_OVERLAY = "com.example.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.action.HIDE_OVERLAY"

        var serviceViewModel: VideoViewModel? = null
        var isRunning = false
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayUiView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Sleek Video Hub", "Медиаплеер активен", false))

        // Watch state of current selected video and isPlaying to sync the notification
        serviceScope.launch {
            serviceViewModel?.currentSelectedVideo?.collectLatest { video ->
                val isPlaying = serviceViewModel?.isPlaying?.value ?: false
                updateNotification(video?.title ?: "Sleek Video Hub", video?.channel ?: "В эфире", isPlaying)
                updateOverlayContent()
            }
        }

        serviceScope.launch {
            serviceViewModel?.isPlaying?.collectLatest { isPlaying ->
                val video = serviceViewModel?.currentSelectedVideo?.value
                updateNotification(video?.title ?: "Sleek Video Hub", video?.channel ?: "В эфире", isPlaying)
                updateOverlayContent()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        android.util.Log.d("BackgroundPlayerService", "Received action: $action")
        when (action) {
            ACTION_PLAY -> serviceViewModel?.togglePlayPause()
            ACTION_PAUSE -> serviceViewModel?.togglePlayPause()
            ACTION_NEXT -> serviceViewModel?.playNextVideo()
            ACTION_PREVIOUS -> serviceViewModel?.playPreviousVideo()
            ACTION_STOP -> {
                stopFloatingOverlay()
                stopSelf()
            }
            ACTION_SHOW_OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    showFloatingOverlay()
                }
            }
            ACTION_HIDE_OVERLAY -> {
                stopFloatingOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleek Video Hub Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления фонового воспроизведения плеера"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, channel: String, isPlaying: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, channel, isPlaying))
    }

    private fun buildNotification(title: String, channel: String, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, flag)

        // Previous Action
        val prevIntent = Intent(this, BackgroundPlayerService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, flag or PendingIntent.FLAG_UPDATE_CURRENT)

        // Play Pause Action
        val playPauseIntent = Intent(this, BackgroundPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(this, 2, playPauseIntent, flag or PendingIntent.FLAG_UPDATE_CURRENT)

        // Next Action
        val nextIntent = Intent(this, BackgroundPlayerService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, flag or PendingIntent.FLAG_UPDATE_CURRENT)

        // Stop Action
        val stopIntent = Intent(this, BackgroundPlayerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, flag or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(channel)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFFF253E.toInt())

        // Add Next/Prev/Play buttons
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", playPausePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playPausePendingIntent)
        }
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)

        return builder.build()
    }

    private fun showFloatingOverlay() {
        if (overlayUiView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 30
            y = 100
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        // Dynamic custom view created programmatically to be highly robust and avoid needing XML assets
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val radius = 16f
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE1E1E1E.toInt())
                cornerRadius = 45f
                setStroke(2, 0x44FFFFFF.toInt())
            }
            background = bgDrawable
            setPadding(35, 20, 35, 20)
        }

        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Pulse / visualizer or small icon
        val icon = android.widget.ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(0xFFFF253E.toInt())
            val size = 50
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                rightMargin = 15
            }
        }

        val titleTv = android.widget.TextView(this).apply {
            text = "Ничего не воспроизводится"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            ellipsize = android.text.TextUtils.TruncateAt.END
            isSingleLine = true
            layoutParams = android.widget.LinearLayout.LayoutParams(250, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            }
        }

        val playBtn = android.widget.ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(android.graphics.Color.WHITE)
            setOnClickListener {
                serviceViewModel?.togglePlayPause()
            }
            val size = 60
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                rightMargin = 15
            }
        }

        val closeBtn = android.widget.ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(android.graphics.Color.GRAY)
            setOnClickListener {
                stopFloatingOverlay()
            }
            val size = 48
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
        }

        row.addView(icon)
        row.addView(titleTv)
        row.addView(playBtn)
        row.addView(closeBtn)
        container.addView(row)

        overlayUiView = container

        // Enable Drag to Reposition
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt() // invert logic slightly to feel standard on layout edge
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayUiView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Launch full app if clicked quickly (short click)
                        val elapsedX = kotlin.math.abs(event.rawX - initialTouchX)
                        val elapsedY = kotlin.math.abs(event.rawY - initialTouchY)
                        if (elapsedX < 10 && elapsedY < 10) {
                            val openAppIntent = Intent(this@BackgroundPlayerService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(openAppIntent)
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(overlayUiView, params)
        updateOverlayContent()
    }

    private fun updateOverlayContent() {
        val view = overlayUiView ?: return
        val currentVideo = serviceViewModel?.currentSelectedVideo?.value
        val isPlaying = serviceViewModel?.isPlaying?.value ?: false

        val rootRow = (view as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.LinearLayout ?: return
        val titleTv = rootRow.getChildAt(1) as? TextView
        val playBtn = rootRow.getChildAt(2) as? ImageView
        val icon = rootRow.getChildAt(0) as? ImageView

        titleTv?.text = currentVideo?.title ?: "Sleek Hub Playing"
        if (isPlaying) {
            playBtn?.setImageResource(android.R.drawable.ic_media_pause)
            icon?.setImageResource(android.R.drawable.ic_media_play)
        } else {
            playBtn?.setImageResource(android.R.drawable.ic_media_play)
            icon?.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun stopFloatingOverlay() {
        if (overlayUiView != null) {
            windowManager?.removeView(overlayUiView)
            overlayUiView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopFloatingOverlay()
        serviceScope.cancel()
    }
}
