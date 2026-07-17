package com.example.ui.screens

import com.example.ui.screens.player.*

import com.example.viewmodel.*
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import com.example.viewmodel.VideoViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag
import com.example.manager.ExoPlayerHandler

@Composable
fun RutubeVideoPlayer(
    videoId: String,
    viewModel: VideoViewModel,
    videoTitle: String = "",
    aspectMode: VlcAspectRatio = VlcAspectRatio.BEST_FIT,
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
    var isTimelineDragging by remember { mutableStateOf(false) }
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
    var subtitles by remember(videoId) { mutableStateOf<List<com.example.data.SubtitleTrack>>(emptyList()) }
    var activeSubtitleLanguage by remember(videoId) { mutableStateOf<String?>(null) }
    var subtitleDelayMs by remember(videoId) { mutableStateOf(0L) }
    var subtitleMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(videoId, selectedQuality, retryTrigger) {
        if (isLive) {
            useEmbedPlayer = true
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        loadError = null
        useEmbedPlayer = false

        // Fetch subtitles and stream URL in parallel
        val subsDeferred = async {
            viewModel.fetchSubtitles(videoId)
        }
        val urlDeferred = async {
            if (offlineFile.exists()) {
                offlineFile.absolutePath
            } else {
                if (retryTrigger > 0) {
                    viewModel.clearHlsCache(videoId)
                }
                viewModel.fetchHlsStreamUrl(videoId, selectedQuality)
            }
        }

        val loadedSubs = try { subsDeferred.await() } catch (e: Exception) { emptyList() }
        val resolvedUrl = try { urlDeferred.await() } catch (e: Exception) { null }

        subtitles = loadedSubs
        if (resolvedUrl != null) {
            hlsUrl = resolvedUrl
            isLoading = false
        } else {
            loadError = "Видео недоступно или не может быть воспроизведено напрямую."
            isLoading = false
        }
    }

    fun getBcp47Language(lang: String): String {
        return when (lang.lowercase()) {
            "русский", "russian", "ru" -> "ru"
            "english", "английский", "en" -> "en"
            else -> "ru"
        }
    }

    val exoPlayerHandler = remember { ExoPlayerHandler(context) }
    
    val exoPlayer = remember(videoId, hlsUrl, subtitles) {
        if (hlsUrl == null) null else {
            exoPlayerHandler.initialize(hlsUrl, offlineFile, subtitles, isPlayingState, viewModel.getVideoPosition(videoId))
        }
    }

    LaunchedEffect(activeSubtitleLanguage, exoPlayer) {
        val track = subtitles.find { it.language == activeSubtitleLanguage }
        exoPlayerHandler.setSubtitleTrack(track)
    }

    LaunchedEffect(subtitleDelayMs, exoPlayer) {
        exoPlayerHandler.setSubtitleDelayMs(subtitleDelayMs)
    }

    LaunchedEffect(activeSubtitleLanguage) {
        if (activeSubtitleLanguage == null) {
            subtitleDelayMs = 0L
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            exoPlayer?.let { player ->
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0L)
                if (pos > 0L) {
                    viewModel.saveVideoPosition(videoId, pos, dur)
                }
            }
            exoPlayerHandler.release()
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
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    val currentVideo = viewModel.playerManager.currentSelectedVideo.value
                    if (currentVideo != null) {
                        viewModel.markAsWatched(currentVideo, exoPlayer?.duration ?: 0L)
                    }
                    viewModel.playerManager.playNext()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoPlayer", "ExoPlayer error", error)
                loadError = "Ошибка воспроизведения: ${error.message}"
            }
        })
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(4000)
            if (System.currentTimeMillis() - lastInteractionTime >= 4000) {
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

    LaunchedEffect(isPlayingState) {
        if (!isPlayingState) {
            exoPlayer?.let { player ->
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0L)
                if (pos > 0L) {
                    viewModel.saveVideoPosition(videoId, pos, dur)
                }
            }
        }
    }

    // Progress update loop
    LaunchedEffect(exoPlayer, isPlayingState, isTimelineDragging) {
        var lastSavedPos = 0L
        while (isPlayingState && exoPlayer != null) {
            if (exoPlayer.isPlaying && !isTimelineDragging) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                if (pos > 0L) {
                    currentPos = pos
                    totalDuration = dur
                    if (Math.abs(pos - lastSavedPos) >= 5000L) {
                        viewModel.saveVideoPosition(videoId, pos, dur)
                        lastSavedPos = pos
                    }
                }
            }
            kotlinx.coroutines.delay(1000)
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
            .background(Color.Black),
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
                            } else if (videoId.startsWith("plugin_")) {
                                viewModel.getPluginPageUrl(videoId) ?: "about:blank"
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

                PlayerGestureOverlay(
                    onDoubleTapLeft = {
                        exoPlayer?.let { player ->
                            val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                            player.seekTo(newPos)
                            currentPos = newPos
                            hudMessage = "-10 сек"
                        }
                    },
                    onDoubleTapRight = {
                        exoPlayer?.let { player ->
                            val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration ?: 0)
                            player.seekTo(newPos)
                            currentPos = newPos
                            hudMessage = "+10 сек"
                        }
                    },
                    currentPositionProvider = { exoPlayer?.currentPosition ?: 0L },
                    durationProvider = { exoPlayer?.duration ?: 0L },
                    onSeekCompleted = { targetPos ->
                        exoPlayer?.let { player ->
                            player.seekTo(targetPos)
                            currentPos = targetPos
                        }
                    },
                    onTap = {
                        lastInteractionTime = System.currentTimeMillis()
                        controlsVisible = !controlsVisible
                    }
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
                            VlcAspectRatio.BEST_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
                        VlcAspectRatio.BEST_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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

            PlayerGestureOverlay(
                onDoubleTapLeft = {
                    exoPlayer?.let { player ->
                        val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                        player.seekTo(newPos)
                        currentPos = newPos
                        hudMessage = "-10 сек"
                    }
                },
                onDoubleTapRight = {
                    exoPlayer?.let { player ->
                        val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration ?: 0)
                        player.seekTo(newPos)
                        currentPos = newPos
                        hudMessage = "+10 сек"
                    }
                },
                currentPositionProvider = { exoPlayer?.currentPosition ?: 0L },
                durationProvider = { exoPlayer?.duration ?: 0L },
                onSeekCompleted = { targetPos ->
                    exoPlayer?.let { player ->
                        player.seekTo(targetPos)
                        currentPos = targetPos
                    }
                },
                onTap = {
                    lastInteractionTime = System.currentTimeMillis()
                    controlsVisible = !controlsVisible
                }
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
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Выйти из полного экрана",
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = if (offlineFile.exists()) "$videoTitle • Офлайн" else videoTitle.ifBlank { "Онлайн-превью" },
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
                            val isVkOrDzen = videoId.startsWith("vk_") || videoId.startsWith("plugin_Дзен_")
                            if (!offlineFile.exists() && !isVkOrDzen) {
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
                                                        color = if (activeVideoQuality == q || selectedQuality == q) Primary else MaterialTheme.colorScheme.onBackground,
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

                            if (subtitles.isNotEmpty()) {
                                Box {
                                    Button(
                                        onClick = {
                                            lastInteractionTime = System.currentTimeMillis()
                                            subtitleMenuExpanded = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(28.dp).sleekTvFocus(RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = "Субтитры",
                                            tint = if (activeSubtitleLanguage != null) Primary else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(activeSubtitleLanguage ?: "Выкл", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    DropdownMenu(
                                        expanded = subtitleMenuExpanded,
                                        onDismissRequest = { subtitleMenuExpanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.background)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Выкл", color = if (activeSubtitleLanguage == null) Primary else MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, fontWeight = if (activeSubtitleLanguage == null) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                activeSubtitleLanguage = null
                                                subtitleMenuExpanded = false
                                                lastInteractionTime = System.currentTimeMillis()
                                            },
                                            modifier = Modifier.sleekTvFocus(RoundedCornerShape(4.dp))
                                        )
                                        subtitles.forEach { track ->
                                            DropdownMenuItem(
                                                text = { Text(track.language, color = if (activeSubtitleLanguage == track.language) Primary else MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, fontWeight = if (activeSubtitleLanguage == track.language) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    activeSubtitleLanguage = track.language
                                                    subtitleMenuExpanded = false
                                                    lastInteractionTime = System.currentTimeMillis()
                                                },
                                                modifier = Modifier.sleekTvFocus(RoundedCornerShape(4.dp))
                                            )
                                        }

                                        if (activeSubtitleLanguage != null) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
                                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = "Синхронизация: ${if (subtitleDelayMs > 0L) "+" else ""}${String.format(java.util.Locale.US, "%.1f", subtitleDelayMs / 1000f)} сек",
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                subtitleDelayMs -= 500L
                                                                lastInteractionTime = System.currentTimeMillis()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Remove,
                                                                contentDescription = "Быстрее",
                                                                tint = MaterialTheme.colorScheme.onBackground,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                        
                                                        Button(
                                                            onClick = {
                                                                subtitleDelayMs = 0L
                                                                lastInteractionTime = System.currentTimeMillis()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            shape = RoundedCornerShape(4.dp),
                                                            modifier = Modifier.height(20.dp)
                                                        ) {
                                                            Text("Сброс", color = MaterialTheme.colorScheme.onBackground, fontSize = 9.sp)
                                                        }
                                                        
                                                        IconButton(
                                                            onClick = {
                                                                subtitleDelayMs += 500L
                                                                lastInteractionTime = System.currentTimeMillis()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "Медленнее",
                                                                tint = MaterialTheme.colorScheme.onBackground,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
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
                                                    color = if (playbackSpeed == speed) Primary else MaterialTheme.colorScheme.onBackground,
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

                            // Cycle Aspect Ratio option like VLC (Icon only, keeping Fit, Fill, Stretch)
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val nextMode = aspectMode.next()
                                    hudMessage = "Соотношение: ${nextMode.displayName}"
                                    onChangeAspectRatio(nextMode)
                                },
                                modifier = Modifier.size(32.dp).sleekTvFocus(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Соотношение сторон",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Share video link
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    onShare()
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

                    // Center playback buttons (SkipPrev, Rewind, Play/Pause, Forward, SkipNext)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}, // consume clicks to avoid hiding controls
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                viewModel.playerManager.playPrevious()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Предыдущее видео",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

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

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                viewModel.playerManager.playNext()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Следующее видео",
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

                            var isTimelineFocused by remember { mutableStateOf(false) }
                            val isTimelineActive = isTimelineDragging || isTimelineFocused

                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .onFocusChanged { isTimelineFocused = it.isFocused }
                                    .focusable()
                                    .sleekTvFocus(RoundedCornerShape(14.dp))
                                    .pointerInput(totalDuration) {
                                        detectTapGestures(
                                            onTap = { offset ->
                                                if (totalDuration > 0) {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    exoPlayer?.let { player ->
                                                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                        val newPos = (totalDuration * fraction).toLong()
                                                        player.seekTo(newPos)
                                                        currentPos = newPos
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(totalDuration) {
                                        detectDragGestures(
                                            onDragStart = { _ ->
                                                lastInteractionTime = System.currentTimeMillis()
                                                isTimelineDragging = true
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                if (totalDuration > 0) {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                    val newPos = (totalDuration * fraction).toLong()
                                                    currentPos = newPos
                                                }
                                            },
                                            onDragEnd = {
                                                isTimelineDragging = false
                                                exoPlayer?.let { player ->
                                                    player.seekTo(currentPos)
                                                    viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
                                                }
                                            },
                                            onDragCancel = {
                                                isTimelineDragging = false
                                            }
                                        )
                                    }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val progress = if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f
                                
                                // Background track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isTimelineActive) 8.dp else 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.3f))
                                )
                                
                                // Active progress track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .height(if (isTimelineActive) 8.dp else 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Primary)
                                )
                                
                                // Glowing circle thumb
                                val thumbSize = if (isTimelineActive) 16.dp else 10.dp
                                val thumbOffset = maxWidth * progress - (thumbSize / 2)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = thumbOffset.coerceAtLeast(0.dp))
                                        .size(thumbSize)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(2.dp, Primary, CircleShape)
                                )
                            }

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

// Share Video Intent launcher
fun shareVideo(context: android.content.Context, video: Video) {
    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        val url = video.getShareUrl()
        putExtra(android.content.Intent.EXTRA_TEXT, "Смотрю видео в RuVideoHub: \"${video.title}\"\n\nПосмотреть: $url")
        type = "text/plain"
    }
    val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться видео")
    context.startActivity(shareIntent)
}

// Format duration helper

