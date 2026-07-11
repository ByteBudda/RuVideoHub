package com.example.ui.screens

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import android.view.KeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.viewmodel.VideoViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag

@Composable
fun SimulatedPlaybackBars(modifier: Modifier = Modifier) {
    // Generate simple pulsing lines at the bottom of the black player background
    val transition = rememberInfiniteTransition(label = "audio_visualizer")
    val p1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "p2"
    )
    val p3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p3"
    )

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        val barCount = 12
        for (i in 0 until barCount) {
            val scale = when (i % 3) {
                0 -> p1
                1 -> p2
                else -> p3
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .fillMaxHeight(scale * 0.4f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Primary.copy(alpha = 0.35f))
            )
        }
    }
}

private fun forceResizeViewAndDescendants(v: android.view.View) {
    v.forceLayout()
    v.requestLayout()
    v.invalidate()
    if (v is android.view.ViewGroup) {
        for (i in 0 until v.childCount) {
            forceResizeViewAndDescendants(v.getChildAt(i))
        }
    }
}

private fun forceFullResize(view: android.view.View) {
    view.layoutParams = android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT
    )
    
    // Force layout on the view and all descendants
    forceResizeViewAndDescendants(view)
    
    // Walk up the parent chain and force layout on all ancestors
    var parent = view.parent as? android.view.ViewGroup
    while (parent != null) {
        parent.forceLayout()
        parent.requestLayout()
        parent.invalidate()
        parent = parent.parent as? android.view.ViewGroup
    }
}

@Composable
fun RutubeVideoPlayer(
    videoId: String,
    viewModel: VideoViewModel,
    videoTitle: String = "",
    aspectMode: VlcAspectRatio = VlcAspectRatio.FIT,
    isFullscreen: Boolean = false,
    isMiniPlayer: Boolean = false,
    isLive: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onChangeAspectRatio: (VlcAspectRatio) -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
    val offlineFile = java.io.File(downloadFolder, "$videoId.mp4")

    val globalQuality by viewModel.playerQuality.collectAsStateWithLifecycle()
    var selectedQuality by remember(globalQuality) { mutableStateOf(globalQuality) }

    var hlsUrl by remember(videoId, selectedQuality) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId, selectedQuality) { mutableStateOf(true) }
    var loadError by remember(videoId, selectedQuality) { mutableStateOf<String?>(null) }
    var useEmbedPlayer by remember(videoId, selectedQuality) { mutableStateOf(false) }

    // Position & duration states for custom controls
    val isPlayingFromViewModel by viewModel.isPlaying.collectAsStateWithLifecycle()
    var isPlayingState by remember { mutableStateOf(viewModel.isPlaying.value) }

    LaunchedEffect(isPlayingFromViewModel) {
        isPlayingState = isPlayingFromViewModel
    }

    LaunchedEffect(isPlayingState) {
        viewModel.setPlayingState(isPlayingState)
    }

    var currentPos by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playerFocusRequester = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (isTvOptimized) {
            controlsVisible = false
                                try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }
    val playPauseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(100)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
    
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // HUD message for aspect ratio cycle
    var hudMessage by remember { mutableStateOf<String?>(null) }

    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(videoId, selectedQuality, retryTrigger) {
        if (isLive) {
            useEmbedPlayer = true
            isLoading = false
            return@LaunchedEffect
        }
        if (offlineFile.exists()) {
            hlsUrl = offlineFile.absolutePath
            isLoading = false
        } else {
            isLoading = true
            loadError = null
            useEmbedPlayer = false
            // Evict cache on retry
            if (retryTrigger > 0) {
                viewModel.clearHlsCache(videoId)
            }
            val resolvedUrl = viewModel.fetchHlsStreamUrl(videoId, selectedQuality)
            if (resolvedUrl != null) {
                hlsUrl = resolvedUrl
                isLoading = false
            } else {
                useEmbedPlayer = true
                isLoading = false
            }
        }
    }

    val exoPlayer = remember(videoId, hlsUrl) {
        if (hlsUrl == null) null else {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(32000, 120000, 2500, 5000)
                .build()
                
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build().apply {
                playWhenReady = isPlayingState
                val uri = if (offlineFile.exists()) {
                    android.net.Uri.fromFile(offlineFile)
                } else {
                    android.net.Uri.parse(hlsUrl)
                }

                if (!offlineFile.exists() && hlsUrl!!.contains(".m3u8")) {
                    val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .setDefaultRequestProperties(mapOf(
                            "Accept" to "*/*",
                            "Referer" to "https://rutube.ru/"
                        ))
                        .setConnectTimeoutMs(5000)
                        .setReadTimeoutMs(5000)
                        .setAllowCrossProtocolRedirects(true)
                    val mediaSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(androidx.media3.common.MediaItem.fromUri(uri))
                    setMediaSource(mediaSource)
                } else {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                }

                prepare()

                val savedPos = viewModel.getVideoPosition(videoId)
                if (savedPos > 0L) {
                    seekTo(savedPos)
                    currentPos = savedPos
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    var isBufferingState by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer?.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(isPlayingState, exoPlayer) {
        exoPlayer?.playWhenReady = isPlayingState
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBufferingState = playbackState == androidx.media3.common.Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoPlayer", "ExoPlayer error, falling back to embed", error)
                useEmbedPlayer = true
            }
        })
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(4000)
            if (System.currentTimeMillis() - lastInteractionTime >= 4000) {
                controlsVisible = false
                controlsVisible = false
                                try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
            }
        }
    }

    // Clear HUD message after 1.5 seconds
    LaunchedEffect(hudMessage) {
        if (hudMessage != null) {
            kotlinx.coroutines.delay(1500)
            hudMessage = null
        }
    }

    // Progress update loop
    LaunchedEffect(exoPlayer, isPlayingState) {
        while (isPlayingState && exoPlayer != null) {
            if (exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition
                if (pos > 0L) {
                    currentPos = pos
                    val dur = exoPlayer.duration
                    if (dur > 0L) {
                        totalDuration = dur
                    }
                    viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
                }
            }
            kotlinx.coroutines.delay(250)
        }
    }

    Box(
        modifier = modifier
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    val wasVisible = controlsVisible
                    controlsVisible = true
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!wasVisible) {
                                controlsVisible = true
                                exoPlayer?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                    isPlayingState = it.isPlaying
                                }
                                true
                            } else {
                                false // let it pass to focused button if any
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                            exoPlayer?.let {
                                if (it.isPlaying) it.pause() else it.play()
                                isPlayingState = it.isPlaying
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            exoPlayer?.let {
                                val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                it.seekTo(newPos)
                                currentPos = newPos
                                hudMessage = "-10 сек"
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            exoPlayer?.let {
                                val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)
                                it.seekTo(newPos)
                                currentPos = newPos
                                hudMessage = "+10 сек"
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .background(Color.Black)
            .pointerInput(Unit) {
                val pWidth = size.width.toFloat()
                detectTapGestures(
                    onTap = {
                        lastInteractionTime = System.currentTimeMillis()
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        lastInteractionTime = System.currentTimeMillis()
                        if (offset.x < pWidth / 3) {
                            exoPlayer?.let { player ->
                                val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                                player.seekTo(newPos)
                                currentPos = newPos
                                hudMessage = "-10 сек"
                            }
                        } else if (offset.x > pWidth * 2 / 3) {
                            exoPlayer?.let { player ->
                                val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration ?: 0)
                                player.seekTo(newPos)
                                currentPos = newPos
                                hudMessage = "+10 сек"
                            }
                        }
                    }
                )
            }
            .pointerInput(isFullscreen) {
                val pWidth = size.width.toFloat()
                val pHeight = size.height.toFloat()
                if (isFullscreen) {
                    detectVerticalDragGestures(
                        onDragStart = { _ ->
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onDragEnd = { },
                        onVerticalDrag = { change, dragAmount ->
                            lastInteractionTime = System.currentTimeMillis()
                            val isRightSide = change.position.x > pWidth / 2f
                            
                            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            val activity = context.findActivity()
                            
                            if (isRightSide) {
                                val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                                val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                                val diff = -(dragAmount / pHeight) * maxVol * 1.5f
                                val newVol = (currentVol + diff).coerceIn(0f, maxVol)
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                                hudMessage = "Громкость: ${((newVol / maxVol) * 100).toInt()}%"
                            } else {
                                activity?.window?.let { window ->
                                    val attrs = window.attributes
                                    val currentBrightness = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                    val diff = -(dragAmount / pHeight) * 1.5f
                                    val newBrightness = (currentBrightness + diff).coerceIn(0f, 1f)
                                    attrs.screenBrightness = newBrightness
                                    window.attributes = attrs
                                    hudMessage = "Яркость: ${(newBrightness * 100).toInt()}%"
                                }
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Получение видеопотока...",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (useEmbedPlayer) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mediaPlaybackRequiresUserGesture = false
                                databaseEnabled = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Inject script that automatically triggers video playback and enforces responsive styling
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var style = document.getElementById('force-fullscreen-style');
                                            if (!style) {
                                                style = document.createElement('style');
                                                style.id = 'force-fullscreen-style';
                                                document.head.appendChild(style);
                                            }
                                            style.innerHTML = `
                                                html, body, iframe, video, object, embed, .player-container, .video-player, #player, [id*="player"], [class*="player"] {
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    max-width: 100% !important;
                                                    max-height: 100% !important;
                                                    position: absolute !important;
                                                    top: 0 !important;
                                                    left: 0 !important;
                                                    margin: 0 !important;
                                                    padding: 0 !important;
                                                }
                                            `;
                                            window.dispatchEvent(new Event('resize'));

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
                                                // Try to click any potential play buttons in the DOM (Rutube, VK, etc.)
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
                            keepScreenOn = true
                            val embedUrl = if (videoId.startsWith("vk_")) {
                                val parts = videoId.substringAfter("vk_").split("_")
                                if (parts.size >= 2) {
                                    val ownerId = parts[0]
                                    val vkId = parts[1]
                                    "https://vk.com/video_ext.php?oid=$ownerId&id=$vkId&autoplay=1"
                                } else {
                                    "https://vk.com/video_ext.php?oid=$videoId&id=$videoId&autoplay=1"
                                }
                            } else {
                                "https://rutube.ru/play/embed/$videoId/?autoplay=1"
                            }
                            loadUrl(embedUrl)
                        }
                    },
                    update = { webView ->
                        // Force layout parameters and measurement triggers on update to handle transition/resize perfectly
                        val parentGroup = webView.parent as? android.view.ViewGroup
                        parentGroup?.requestLayout()
                        
                        webView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webView.requestLayout()
                        webView.invalidate()
                        
                        webView.post {
                            webView.requestLayout()
                            webView.invalidate()
                            webView.evaluateJavascript(
                                """
                                (function() {
                                    var style = document.getElementById('force-fullscreen-style');
                                    if (!style) {
                                        style = document.createElement('style');
                                        style.id = 'force-fullscreen-style';
                                        document.head.appendChild(style);
                                    }
                                    style.innerHTML = `
                                        html, body, iframe, video, object, embed, .player-container, .video-player, #player, [id*="player"], [class*="player"] {
                                            width: 100% !important;
                                            height: 100% !important;
                                            max-width: 100% !important;
                                            max-height: 100% !important;
                                            position: absolute !important;
                                            top: 0 !important;
                                            left: 0 !important;
                                            margin: 0 !important;
                                            padding: 0 !important;
                                        }
                                    `;
                                    window.dispatchEvent(new Event('resize'));
                                    setTimeout(function() { window.dispatchEvent(new Event('resize')); }, 100);
                                    setTimeout(function() { window.dispatchEvent(new Event('resize')); }, 300);
                                    setTimeout(function() { window.dispatchEvent(new Event('resize')); }, 600);
                                })();
                                """.trimIndent(), null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay Controls inside embed player web layout
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(36.dp)
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Во весь экран",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { 
                            useEmbedPlayer = false 
                            hlsUrl = null 
                            loadError = "Переключен назад на стандартный плеер." 
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(36.dp)
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Стандартный плеер",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else if (loadError != null) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Ошибка",
                    tint = Color.Red,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = loadError ?: "Ошибка воспроизведения",
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isLoading = true
                            loadError = null
                            hlsUrl = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Повторить", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            useEmbedPlayer = true
                            loadError = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Embed-плеер", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        } else if (hlsUrl != null) {
            // Video View Container
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        player = exoPlayer
                        keepScreenOn = true
                        resizeMode = when (aspectMode) {
                            VlcAspectRatio.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            VlcAspectRatio.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }

                        addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                v.post {
                                    forceFullResize(v)
                                }
                                v.postDelayed({
                                    forceFullResize(v)
                                }, 100)
                                v.postDelayed({
                                    forceFullResize(v)
                                }, 300)
                                v.postDelayed({
                                    forceFullResize(v)
                                }, 600)
                            }
                            override fun onViewDetachedFromWindow(v: android.view.View) {}
                        })
                    }
                },
                update = { playerView ->
                    // Explicitly reference states to ensure update block is executed when they change
                    val mini = isMiniPlayer
                    val full = isFullscreen
                    val aspect = aspectMode

                    playerView.player = exoPlayer
                    playerView.keepScreenOn = true
                    playerView.resizeMode = when (aspect) {
                        VlcAspectRatio.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VlcAspectRatio.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }

                    forceFullResize(playerView)

                    playerView.post {
                        forceFullResize(playerView)
                    }
                    playerView.postDelayed({
                        forceFullResize(playerView)
                    }, 100)
                    playerView.postDelayed({
                        forceFullResize(playerView)
                    }, 300)
                    playerView.postDelayed({
                        forceFullResize(playerView)
                    }, 600)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Buffering progress indicator
            if (isBufferingState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Transparent overlay for Controls
            if (!isMiniPlayer) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .focusGroup()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                lastInteractionTime = System.currentTimeMillis()
                controlsVisible = false
                                try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                            }
                    ) {
                    // Top Bar Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isFullscreen) {
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (currentPos > 0) {
                                            viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
                                        }
                                        onToggleFullscreen()
                                    },
                                    modifier = Modifier.sleekTvFocus(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Выйти из полного экрана",
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = if (offlineFile.exists()) "$videoTitle • Offline" else videoTitle.ifBlank { "Онлайн-превью" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right actions (Aspect ratio, Quality & Share)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!offlineFile.exists()) {
                                var qualityMenuExpanded by remember { mutableStateOf(false) }
                                val availableQualities by viewModel.currentAvailableQualities.collectAsStateWithLifecycle()
                                val activeVideoQuality by viewModel.activeVideoQuality.collectAsStateWithLifecycle()

                                Box {
                                    Button(
                                        onClick = {
                                            lastInteractionTime = System.currentTimeMillis()
                                        qualityMenuExpanded = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(28.dp).sleekTvFocus(RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Hd,
                                            contentDescription = "Выбор качества",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(activeVideoQuality, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    DropdownMenu(
                                        expanded = qualityMenuExpanded,
                                        onDismissRequest = { qualityMenuExpanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.background)
                                    ) {
                                        availableQualities.forEach { q ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = q, 
                                                        color = if (activeVideoQuality == q || selectedQuality == q) Primary else Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (activeVideoQuality == q || selectedQuality == q) FontWeight.Bold else FontWeight.Normal
                                                    ) 
                                                },
                                                onClick = {
                                                    selectedQuality = q
                                                    qualityMenuExpanded = false
                                                    lastInteractionTime = System.currentTimeMillis()
                                                },
                                                modifier = Modifier.sleekTvFocus(RoundedCornerShape(4.dp))
                                            )
                                        }
                                    }
                                }
                            }

                            // Speed selection
                            var speedMenuExpanded by remember { mutableStateOf(false) }
                            val availableSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

                            Box {
                                Button(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        speedMenuExpanded = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp).sleekTvFocus(RoundedCornerShape(8.dp)).testTag("player_speed_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Скорость воспроизведения",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                DropdownMenu(
                                    expanded = speedMenuExpanded,
                                    onDismissRequest = { speedMenuExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                                ) {
                                    availableSpeeds.forEach { speed ->
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    text = if (speed == 1.0f) "Обычная" else "${speed}x", 
                                                    color = if (playbackSpeed == speed) Primary else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                                ) 
                                            },
                                            onClick = {
                                                playbackSpeed = speed
                                                speedMenuExpanded = false
                                                lastInteractionTime = System.currentTimeMillis()
                                            },
                                            modifier = Modifier.sleekTvFocus(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }

                            // Cycle Aspect Ratio option like VLC
                            Button(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val nextMode = aspectMode.next()
                                    hudMessage = "Соотношение: ${nextMode.displayName}"
                                    onChangeAspectRatio(nextMode)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp).sleekTvFocus(RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(aspectMode.displayName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Share video link
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    onToggleFullscreen()
                                },
                                modifier = Modifier.size(32.dp).sleekTvFocus(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Поделиться",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Center playback buttons (Rewind, Play/Pause, Forward)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}, // consume clicks to avoid hiding controls
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                                    player.seekTo(newPos)
                                    currentPos = newPos
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Назад 10с",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) {
                                        player.pause()
                                        isPlayingState = false
                                    } else {
                                        player.play()
                                        isPlayingState = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Primary.copy(alpha = 0.9f), CircleShape)
                                .focusRequester(playPauseFocusRequester)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Пауза/Пуск",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                    player.seekTo(newPos)
                                    currentPos = newPos
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Вперед 10с",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Bottom Bar Controls with Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(bottom = if (isFullscreen) 16.dp else 6.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {} // Consume touch events
                    ) {
                        // Progress Slider Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatMillis(currentPos),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            androidx.compose.material3.Slider(
                                value = currentPos.toFloat().coerceIn(0f, totalDuration.toFloat().coerceAtLeast(1f)),
                                onValueChange = { newValue ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    exoPlayer?.seekTo(newValue.toLong())
                                },
                                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = Primary,
                                    activeTrackColor = Primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .sleekTvFocus(RoundedCornerShape(14.dp))
                            )

                            Text(
                                text = formatMillis(totalDuration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Fullscreen Toggle action
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                        if (currentPos > 0) {
                                        viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
                                    }
                                    onToggleFullscreen()
                                },
                                modifier = Modifier.size(24.dp).sleekTvFocus(CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.Close else Icons.Default.AspectRatio,
                                    contentDescription = "Во весь экран",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            }

            // HUD notification for Aspect Ratio cycles, Volume, Brightness
            androidx.compose.animation.AnimatedVisibility(
                visible = hudMessage != null,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                hudMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(text = msg, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// VLC-style aspect ratios enum
enum class VlcAspectRatio(val displayName: String) {
    FIT("Вписать"),
    FILL("Заполнить"),
    STRETCH("Растянуть"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3");

    fun next(): VlcAspectRatio {
        val entries = values()
        return entries[(ordinal + 1) % entries.size]
    }
}

// Share Video Intent launcher
fun shareVideo(context: android.content.Context, video: Video) {
    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, "Смотрю видео в RuVideoHub: \"${video.title}\"\n\nПосмотреть: https://rutube.ru/video/${video.id}/")
        type = "text/plain"
    }
    val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться видео")
    context.startActivity(shareIntent)
}

// Format duration helper
fun formatMillis(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

// Helper extension function to unwrap Activity from any wrapped Context
fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
