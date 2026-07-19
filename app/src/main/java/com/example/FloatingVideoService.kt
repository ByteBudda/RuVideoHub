package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

class FloatingVideoService : Service() {

    private lateinit var windowManager: WindowManager
    private var rootView: FrameLayout? = null
    private var webView: WebView? = null
    private var videoId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(12345, getNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("video_id")
        if (id != null && id != videoId) {
            videoId = id
            showFloatingWindow()
        }
        return START_NOT_STICKY
    }

    private fun showFloatingWindow() {
        if (rootView != null) {
            windowManager.removeView(rootView)
            rootView = null
        }

        val ctx = this
        val initialWidth = dpToPx(320)
        val initialHeight = dpToPx(180)

        val params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // Root container with rounded corners and border
        val root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dpToPxFloat(16)
                setStroke(dpToPx(2), 0xFF3F3B50.toInt())
            }
            clipToOutline = true
        }

        // 1. WebView
        val web = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var playAttempts = 0;
                            function tryPlay() {
                                var video = document.querySelector('video');
                                if (video && !video.paused) {
                                    console.log('Video is playing, stopping further autoplay attempts.');
                                    return;
                                }
                                if (video) {
                                    video.muted = false;
                                    var playPromise = video.play();
                                    if (playPromise !== undefined) {
                                        playPromise.then(function() {
                                            console.log('Autoplay started successfully');
                                        }).catch(function(error) {
                                            console.log('Autoplay playPromise failed: ' + error);
                                        });
                                    }
                                }
                                var playBtn = document.querySelector('.wdp-play-button') ||
                                              document.querySelector('.video_box_prep') ||
                                              document.querySelector('[class*="play-button"]') ||
                                              document.querySelector('[class*="play_btn"]') ||
                                              document.querySelector('[class*="playButton"]') ||
                                              document.querySelector('[id*="play"]') ||
                                              document.querySelector('[class*="play"]');
                                if (playBtn) {
                                    var btnText = (playBtn.className + " " + playBtn.id).toLowerCase();
                                    if (btnText.indexOf('pause') === -1) {
                                        try {
                                            playBtn.click();
                                        } catch(e) {
                                            console.log('Click failed', e);
                                        }
                                    }
                                }
                                if (playAttempts < 15) {
                                    playAttempts++;
                                    setTimeout(tryPlay, 800);
                                }
                            }
                            setTimeout(tryPlay, 500);
                        })();
                        """.trimIndent(), null
                    )
                }
            }
            webChromeClient = WebChromeClient()
        }

        val embedUrl = if (videoId.startsWith("vk_")) {
            val parts = videoId.substringAfter("vk_").split("_")
            if (parts.size >= 2) {
                val ownerId = parts[0]
                val vkId = parts[1]
                "https://vk.com/video_ext.php?oid=$ownerId&id=$vkId&autoplay=1"
            } else {
                "https://vk.com/video_ext.php?oid=$videoId&id=$videoId&autoplay=1"
            }
        } else if (videoId.startsWith("plugin_")) {
            "about:blank"
        } else {
            "https://rutube.ru/play/embed/$videoId/?autoplay=1"
        }
        web.loadUrl(embedUrl)
        this.webView = web

        // Add WebView to root
        val webParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(web, webParams)

        // 2. Drag / Top bar (semi-transparent, allows dragging to move the window)
        val dragBar = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xAA121118.toInt())
                cornerRadii = floatArrayOf(
                    dpToPxFloat(16), dpToPxFloat(16), // Top-left
                    dpToPxFloat(16), dpToPxFloat(16), // Top-right
                    0f, 0f,                           // Bottom-right
                    0f, 0f                            // Bottom-left
                )
            }
        }
        val dragBarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(44)
        ).apply {
            gravity = Gravity.TOP
        }
        root.addView(dragBar, dragBarParams)

        // Drag window touch listener on the dragBar
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        dragBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        // 3. Spaced Buttons inside the Drag Bar (Restore & Close)
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnContainerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            rightMargin = dpToPx(12)
        }
        dragBar.addView(buttonContainer, btnContainerParams)

        // Restore button
        val restoreBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_zoom) // Standard zoom icon
            setBackgroundResource(android.R.drawable.screen_background_dark_transparent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnClickListener {
                val restoreIntent = Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("restore_video_id", videoId)
                }
                ctx.startActivity(restoreIntent)
                stopSelf()
            }
        }
        val btnParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
            rightMargin = dpToPx(12) // Generous spacing!
        }
        buttonContainer.addView(restoreBtn, btnParams)

        // Close button
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Standard close icon
            setBackgroundResource(android.R.drawable.screen_background_dark_transparent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnClickListener {
                stopSelf()
            }
        }
        buttonContainer.addView(closeBtn, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)))

        // 4. Resize handle (Bottom-Right Corner)
        val resizeHandle = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop) // Standard crop/resize indicator icon
            setBackgroundResource(android.R.drawable.screen_background_dark_transparent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }
        val resizeParams = FrameLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dpToPx(4)
            bottomMargin = dpToPx(4)
        }
        root.addView(resizeHandle, resizeParams)

        // Resize window touch listener
        var initialWidthVal = params.width
        var initialHeightVal = params.height
        var initialResizeTouchX = 0f
        var initialResizeTouchY = 0f

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidthVal = params.width
                    initialHeightVal = params.height
                    initialResizeTouchX = event.rawX
                    initialResizeTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialResizeTouchX
                    val deltaY = event.rawY - initialResizeTouchY

                    var newWidth = (initialWidthVal + deltaX).toInt()
                    if (newWidth < dpToPx(200)) newWidth = dpToPx(200)
                    if (newWidth > dpToPx(600)) newWidth = dpToPx(600)
                    val newHeight = (newWidth / (16f / 9f)).toInt()

                    params.width = newWidth
                    params.height = newHeight
                    windowManager.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        this.rootView = root
        windowManager.addView(root, params)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun dpToPxFloat(dp: Int): Float {
        val density = resources.displayMetrics.density
        return dp * density
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_player_channel",
                "Плавающий плеер",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun getNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "floating_player_channel")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Плавающий плеер")
            .setContentText("Видео воспроизводится поверх других окон")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (rootView != null) {
            windowManager.removeView(rootView)
            rootView = null
        }
        webView?.destroy()
        webView = null
    }
}
