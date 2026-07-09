package com.example.ui.screens

import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.delay

@Composable
fun TvTvSimulatedPlaybackBars(modifier: Modifier = Modifier) {
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
            .background(Color(0xFF0F0F1A))
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until 12) {
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
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            forceResizeViewAndDescendants(v.getChildAt(i))
        }
    }
}

private fun forceFullResize(view: android.view.View) {
    view.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    forceResizeViewAndDescendants(view)
    var parent = view.parent as? ViewGroup
    while (parent != null) {
        parent.forceLayout()
        parent.requestLayout()
        parent.invalidate()
        parent = parent.parent as? ViewGroup
    }
}

@Composable
fun TvRutubeVideoPlayer(
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
    
    val isPlayingFromViewModel by viewModel.isPlaying.collectAsStateWithLifecycle()
    var isPlayingState by remember { mutableStateOf(viewModel.isPlaying.value) }
    var controlsVisible by remember { mutableStateOf(!isMiniPlayer) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var currentPos by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val playerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(controlsVisible, lastInteractionTime, isPlayingState) {
        if (controlsVisible && isPlayingState && !isMiniPlayer) {
            delay(4000)
            if (System.currentTimeMillis() - lastInteractionTime >= 3500) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(videoId, selectedQuality) {
        if (isLive) {
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
                        .setUserAgent("Mozilla/5.0")
                        .setDefaultRequestProperties(mapOf(
                            "Accept" to "*/*",
                            "Referer" to "https://rutube.ru/"
                        ))
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
        onDispose { exoPlayer?.release() }
    }

    var isBufferingState by remember { mutableStateOf(false) }

    LaunchedEffect(isPlayingState, exoPlayer) {
        exoPlayer?.playWhenReady = isPlayingState
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBufferingState = playbackState == androidx.media3.common.Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("TvVideoPlayer", "ExoPlayer error", error)
                useEmbedPlayer = true
            }
        })
    }

    LaunchedEffect(exoPlayer, isPlayingState) {
        while (isPlayingState && exoPlayer != null) {
            currentPos = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration.coerceAtLeast(0)
            delay(1000)
        }
    }

    LaunchedEffect(currentPos) {
        if (currentPos > 0 && currentPos % 10000 < 1500) {
            viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
        }
    }
    
    LaunchedEffect(controlsVisible) {
        if (!isMiniPlayer) {
            delay(150)
            if (controlsVisible) {
                try {
                    playPauseFocusRequester.requestFocus()
                } catch (e: Exception) {
                    try {
                        playerFocusRequester.requestFocus()
                    } catch (ex: Exception) {}
                }
            } else {
                try {
                    playerFocusRequester.requestFocus()
                } catch (e: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (isMiniPlayer) {
                            onToggleFullscreen()
                        } else {
                            controlsVisible = !controlsVisible
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
                )
            }
            .then(
                if (!isMiniPlayer) {
                    Modifier
                        .focusRequester(playerFocusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                lastInteractionTime = System.currentTimeMillis()
                                val wasVisible = controlsVisible
                                
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (!wasVisible) {
                                            controlsVisible = true
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                                        exoPlayer?.let {
                                            if (it.isPlaying) {
                                                it.pause()
                                                isPlayingState = false
                                            } else {
                                                it.play()
                                                isPlayingState = true
                                            }
                                        }
                                        controlsVisible = true
                                        true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                        exoPlayer?.let {
                                            val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                            it.seekTo(newPos)
                                            currentPos = newPos
                                        }
                                        controlsVisible = true
                                        true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                        exoPlayer?.let {
                                            val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                                            it.seekTo(newPos)
                                            currentPos = newPos
                                        }
                                        controlsVisible = true
                                        true
                                    }
                                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                        if (controlsVisible && isFullscreen) {
                                            controlsVisible = false
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                } else Modifier
            )
    ) {
        if (isLive) {
            TvTvSimulatedPlaybackBars(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Прямой эфир: $videoTitle",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(64.dp))
            }
        } else if (useEmbedPlayer) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        val url = "https://rutube.ru/play/embed/$videoId/"
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            AnimatedVisibility(
                visible = controlsVisible && !isMiniPlayer,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(56.dp)
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Полный экран",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        } else if (hlsUrl != null) {
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
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.keepScreenOn = true
                    playerView.resizeMode = when (aspectMode) {
                        VlcAspectRatio.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VlcAspectRatio.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (isBufferingState) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(64.dp))
                }
            }

            AnimatedVisibility(
                visible = controlsVisible && !isMiniPlayer,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFullscreen) {
                            IconButton(
                                onClick = { 
                                    if (currentPos > 0) viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
                                    onToggleFullscreen() 
                                },
                                modifier = Modifier
                                    .background(Color.DarkGray, CircleShape)
                                    .size(48.dp)
                                    .sleekTvFocus(CircleShape)
                            ) {
                                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        
                        Text(
                            text = if (offlineFile.exists()) "$videoTitle (Offline)" else videoTitle,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val qualities = listOf("Авто", "1080p", "720p", "480p")
                                val nextIdx = (qualities.indexOf(selectedQuality) + 1) % qualities.size
                                selectedQuality = qualities[nextIdx]
                                viewModel.setPlayerQuality(selectedQuality)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.sleekTvFocus(RoundedCornerShape(8.dp))
                        ) {
                            Text(selectedQuality, color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val modes = VlcAspectRatio.values()
                                val next = modes[(aspectMode.ordinal + 1) % modes.size]
                                onChangeAspectRatio(next)
                            },
                            modifier = Modifier
                                .background(Color.DarkGray, CircleShape)
                                .size(48.dp)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(Icons.Default.AspectRatio, "Формат", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        
                        if (!isFullscreen) {
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier
                                    .background(Color.DarkGray, CircleShape)
                                    .size(48.dp)
                                    .sleekTvFocus(CircleShape)
                            ) {
                                Icon(Icons.Default.Fullscreen, "Полный экран", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Center Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let {
                                    val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                    it.seekTo(newPos)
                                    currentPos = newPos
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.DarkGray.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(Icons.Default.FastRewind, "Назад", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let {
                                    if (it.isPlaying) {
                                        it.pause()
                                        isPlayingState = false
                                    } else {
                                        it.play()
                                        isPlayingState = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(96.dp)
                                .background(Primary, CircleShape)
                                .focusRequester(playPauseFocusRequester)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let {
                                    val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                                    it.seekTo(newPos)
                                    currentPos = newPos
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.DarkGray.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(Icons.Default.FastForward, "Вперед", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    // Bottom Bar (Timeline)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTvMillis(currentPos),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Gray.copy(alpha = 0.5f))
                        ) {
                            val progress = if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(Primary)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = formatTvMillis(totalDuration),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatTvMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
