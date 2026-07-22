package com.example.ui.screens

import com.example.ui.screens.player.*

import com.example.viewmodel.*
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ripple
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.manager.ExoPlayerHandler
import com.example.ui.theme.Primary
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

@Composable
fun TvRutubeVideoPlayer(
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
    val latestIsMiniPlayer by rememberUpdatedState(isMiniPlayer)
    val latestOnToggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    val downloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
    val offlineFile = java.io.File(downloadFolder, "$videoId.mp4")

    val globalQuality by viewModel.playerQuality.collectAsStateWithLifecycle()
    var selectedQuality by remember(globalQuality) { mutableStateOf(globalQuality) }

    var hlsUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var subtitles by remember(videoId) { mutableStateOf<List<com.example.data.SubtitleTrack>>(emptyList()) }
    var activeSubtitleLanguage by remember(videoId) { mutableStateOf<String?>(null) }
    var subtitleDelayMs by remember(videoId) { mutableStateOf(0L) }

    var isLoading by remember(videoId) { mutableStateOf(true) }
    var loadError by remember(videoId) { mutableStateOf<String?>(null) }
    var useEmbedPlayer by remember(videoId) { mutableStateOf(false) }
    var isLiveStreamReady by remember { mutableStateOf(false) }
    
    val isPlayingFromViewModel by viewModel.isPlaying.collectAsStateWithLifecycle()
    var isPlayingState by remember { mutableStateOf(viewModel.isPlaying.value) }

    LaunchedEffect(isPlayingFromViewModel) {
        isPlayingState = isPlayingFromViewModel
    }

    LaunchedEffect(isPlayingState) {
        viewModel.setPlayingState(isPlayingState)
    }
    var controlsVisible by remember { mutableStateOf(!isMiniPlayer) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(isMiniPlayer) {
        if (!isMiniPlayer) {
            controlsVisible = true
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    var currentPos by remember(videoId) { mutableStateOf(viewModel.getVideoPosition(videoId)) }
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

    // ============================================================
    // УНИВЕРСАЛЬНАЯ ЗАГРУЗКА: HLS для всех, embed как fallback
    // ============================================================
    LaunchedEffect(videoId, selectedQuality) {
        isLoading = true
        loadError = null
        useEmbedPlayer = false
        isLiveStreamReady = false

        try {
            // 1. Проверяем офлайн-файл
            if (offlineFile.exists()) {
                hlsUrl = offlineFile.absolutePath
                isLoading = false
                return@LaunchedEffect
            }

            // 2. Пробуем получить HLS поток (работает и для LIVE, и для обычных видео)
            val url = viewModel.fetchHlsStreamUrl(videoId, selectedQuality)

            if (url != null && url.isNotBlank() && url.contains(".m3u8")) {
                hlsUrl = url
                if (isLive) isLiveStreamReady = true
                isLoading = false
            } else {
                // 3. HLS не получен — пробуем embed-плеер
                useEmbedPlayer = true
                isLoading = false
            }
        } catch (e: Exception) {
            // 4. Ошибка — пробуем embed-плеер
            android.util.Log.e("TvVideoPlayer", "HLS fetch error", e)
            useEmbedPlayer = true
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
            exoPlayerHandler.initialize(
                hlsUrl = hlsUrl,
                offlineFile = offlineFile,
                subtitles = subtitles,
                isPlayingState = isPlayingState,
                initialPosition = viewModel.getVideoPosition(videoId),
                isLive = isLive
            )
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
                if (pos > 0L && !isLive) {
                    viewModel.saveVideoPosition(videoId, pos, dur)
                }
            }
            exoPlayerHandler.release()
        }
    }

    var isBufferingState by remember { mutableStateOf(false) }

    LaunchedEffect(isPlayingState, exoPlayer) {
        exoPlayer?.playWhenReady = isPlayingState
    }

    LaunchedEffect(exoPlayer) {
        if (exoPlayer != null) {
            isBufferingState = true
            exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBufferingState = playbackState == androidx.media3.common.Player.STATE_BUFFERING ||
                                       playbackState == androidx.media3.common.Player.STATE_IDLE
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED && !isLive) {
                        val currentVideo = viewModel.playerManager.currentSelectedVideo.value
                        if (currentVideo != null) {
                            viewModel.markAsWatched(currentVideo, exoPlayer.duration)
                        }
                        viewModel.playerManager.playNext()
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("TvVideoPlayer", "ExoPlayer error", error)
                    loadError = "Ошибка воспроизведения: ${error.message}"
                }
            })
        } else {
            isBufferingState = false
        }
    }

    LaunchedEffect(isPlayingState) {
        if (!isPlayingState) {
            exoPlayer?.let { player ->
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0L)
                if (pos > 0L && !isLive) {
                    viewModel.saveVideoPosition(videoId, pos, dur)
                }
            }
        }
    }

    LaunchedEffect(exoPlayer, isPlayingState) {
        var lastSavedPos = 0L
        while (isPlayingState && exoPlayer != null) {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (pos > 0L) {
                currentPos = pos
                totalDuration = dur
                if (Math.abs(pos - lastSavedPos) >= 5000L && !isLive) {
                    viewModel.saveVideoPosition(videoId, pos, dur)
                    lastSavedPos = pos
                }
            }
            delay(1000)
        }
    }
    
    LaunchedEffect(controlsVisible, isMiniPlayer, exoPlayer, isLoading) {
        if (!isMiniPlayer) {
            delay(150)
            if (controlsVisible && !isLoading) {
                try {
                    playPauseFocusRequester.requestFocus()
                } catch (e: Exception) {
                    try {
                        playerFocusRequester.requestFocus()
                    } catch (ex: Exception) {}
                }
            } else if (!controlsVisible) {
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
                    onDoubleTap = {
                        if (latestIsMiniPlayer) {
                            latestOnToggleFullscreen()
                        } else {
                            controlsVisible = !controlsVisible
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    },
                    onTap = {
                        if (latestIsMiniPlayer) {
                            latestOnToggleFullscreen()
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
                } else Modifier.focusProperties { canFocus = false }
            )
    ) {
        // Если это стрим и мы ещё не получили HLS — показываем индикатор загрузки
        if (isLive && !isLiveStreamReady && !useEmbedPlayer && isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Подключение к прямому эфиру...",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = videoTitle,
                        color = GreyText,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else if (isLive && isLiveStreamReady && hlsUrl != null) {
            // Стрим воспроизводится через ExoPlayer
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
                        if (isMiniPlayer) {
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.keepScreenOn = true
                    playerView.resizeMode = when (aspectMode) {
                        VlcAspectRatio.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VlcAspectRatio.BEST_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    if (isMiniPlayer) {
                        playerView.isFocusable = false
                        playerView.isFocusableInTouchMode = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isMiniPlayer) Modifier.focusProperties { canFocus = false } else Modifier)
            )
            
            // LIVE indicator — маленький красный кружок
            if (isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (latestIsMiniPlayer) {
                                    latestOnToggleFullscreen()
                                } else {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            },
                            onDoubleTap = {
                                if (latestIsMiniPlayer) {
                                    latestOnToggleFullscreen()
                                } else {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        )
                    }
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
                            val onBackClick = { 
                                if (currentPos > 0 && !isLive) {
                                    viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
                                }
                                onToggleFullscreen() 
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .sleekTvFocus(CircleShape, onEnter = onBackClick)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onBackClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (offlineFile.exists()) "$videoTitle (Офлайн)" else videoTitle,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            // ❌ УДАЛЁН БЛОК С "LIVE" РЯДОМ С НАЗВАНИЕМ
                        }
                        
                        val isVkOrDzen = videoId.startsWith("vk_") || videoId.startsWith("plugin_Дзен_")
                        if (!offlineFile.exists() && !isVkOrDzen) {
                            val onQualityClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val qualities = listOf("Авто", "1080p", "720p", "480p")
                                val nextIdx = (qualities.indexOf(selectedQuality) + 1) % qualities.size
                                selectedQuality = qualities[nextIdx]
                                viewModel.setPlayerQuality(selectedQuality)
                            }
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .sleekTvFocus(RoundedCornerShape(8.dp), onEnter = onQualityClick)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onQualityClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(selectedQuality, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        if (subtitles.isNotEmpty() && !isLive) {
                            val onSubtitleClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val options = listOf(null) + subtitles.map { it.language }
                                val nextIdx = (options.indexOf(activeSubtitleLanguage) + 1) % options.size
                                activeSubtitleLanguage = options[nextIdx]
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (activeSubtitleLanguage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .sleekTvFocus(RoundedCornerShape(8.dp), onEnter = onSubtitleClick)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onSubtitleClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Subtitles, "Субтитры", tint = if (activeSubtitleLanguage != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(activeSubtitleLanguage ?: "Выкл", color = if (activeSubtitleLanguage != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))

                            if (activeSubtitleLanguage != null) {
                                val onDelayDecrease = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    subtitleDelayMs -= 500L
                                }
                                val onDelayIncrease = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    subtitleDelayMs += 500L
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .sleekTvFocus(RoundedCornerShape(4.dp), onEnter = onDelayDecrease)
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = ripple(bounded = true),
                                                onClick = onDelayDecrease
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("-0.5с", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Text(
                                        text = "${if (subtitleDelayMs > 0L) "+" else ""}${String.format(java.util.Locale.US, "%.1f", subtitleDelayMs / 1000f)}с",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .sleekTvFocus(RoundedCornerShape(4.dp), onEnter = onDelayIncrease)
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = ripple(bounded = true),
                                                onClick = onDelayIncrease
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+0.5с", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }

                        val onAspectClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            val modes = VlcAspectRatio.values()
                            val next = modes[(aspectMode.ordinal + 1) % modes.size]
                            onChangeAspectRatio(next)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onAspectClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onAspectClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AspectRatio, "Формат", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                        
                        if (!isFullscreen) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .sleekTvFocus(CircleShape, onEnter = onToggleFullscreen)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onToggleFullscreen
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Fullscreen, "Полный экран", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Center Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val onPrevClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            viewModel.playerManager.playPrevious()
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onPrevClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onPrevClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Предыдущее видео", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }

                        val onRewindClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            exoPlayer?.let {
                                val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                it.seekTo(newPos)
                                currentPos = newPos
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onRewindClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onRewindClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FastRewind, "Назад", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }

                        val onPlayPauseClick: () -> Unit = {
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
                        }
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .focusRequester(playPauseFocusRequester)
                                .sleekTvFocus(CircleShape, onEnter = onPlayPauseClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onPlayPauseClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                                    .align(Alignment.Center)
                            )
                        }

                        val onForwardClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            exoPlayer?.let {
                                val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                                it.seekTo(newPos)
                                currentPos = newPos
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onForwardClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onForwardClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FastForward, "Вперед", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }

                        val onNextClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            viewModel.playerManager.playNext()
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onNextClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onNextClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SkipNext, "Следующее видео", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }
                    }

                    // Bottom Bar (Timeline) — для LIVE только время
                    var isTimelineFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!isLive) {
                            Text(
                                text = formatMillis(currentPos),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(0.dp))
                            
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .onFocusChanged { isTimelineFocused = it.isFocused }
                                    .focusable()
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionLeft -> {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    exoPlayer?.let { player ->
                                                        val seekStep = if (player.duration > 0) (player.duration / 100).coerceIn(5000L, 30000L) else 10000L
                                                        val newPos = (player.currentPosition - seekStep).coerceAtLeast(0L)
                                                        player.seekTo(newPos)
                                                        currentPos = newPos
                                                    }
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    exoPlayer?.let { player ->
                                                        val seekStep = if (player.duration > 0) (player.duration / 100).coerceIn(5000L, 30000L) else 10000L
                                                        val newPos = (player.currentPosition + seekStep).coerceAtMost(player.duration)
                                                        player.seekTo(newPos)
                                                        currentPos = newPos
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
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
                                                exoPlayer?.let { player ->
                                                    player.seekTo(currentPos)
                                                }
                                            }
                                        )
                                    }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val progress = if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isTimelineFocused) 8.dp else 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Gray.copy(alpha = 0.5f))
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .height(if (isTimelineFocused) 8.dp else 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Primary)
                                )
                                
                                if (isTimelineFocused) {
                                    val thumbOffset = maxWidth * progress - 8.dp
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = thumbOffset.coerceAtLeast(0.dp))
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(2.dp, Primary, CircleShape)
                                    )
                                }
                            }
                            
                            Text(
                                text = formatMillis(totalDuration),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            // Для LIVE — только время, без "● LIVE"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatMillis(currentPos),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(64.dp))
            }
        } else if (useEmbedPlayer) {
            // ============================================================
            // EMBED ПЛЕЕР
            // ============================================================
            val coroutineScope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
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
                                mediaPlaybackRequiresUserGesture = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(false)
                                builtInZoomControls = false
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var style = document.getElementById('tv-player-style');
                                            if (!style) {
                                                style = document.createElement('style');
                                                style.id = 'tv-player-style';
                                                document.head.appendChild(style);
                                            }
                                            style.innerHTML = `
                                                * { margin: 0 !important; padding: 0 !important; }
                                                html, body, iframe, video, object, embed, 
                                                .player-container, .video-player, #player, 
                                                [id*="player"], [class*="player"],
                                                [id*="video"], [class*="video"],
                                                [id*="embed"], [class*="embed"] {
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    max-width: 100% !important;
                                                    max-height: 100% !important;
                                                    min-width: 100% !important;
                                                    min-height: 100% !important;
                                                    position: absolute !important;
                                                    top: 0 !important;
                                                    left: 0 !important;
                                                    margin: 0 !important;
                                                    padding: 0 !important;
                                                    border: none !important;
                                                    overflow: hidden !important;
                                                }
                                                body { 
                                                    background: #000 !important;
                                                    display: flex !important;
                                                    align-items: center !important;
                                                    justify-content: center !important;
                                                }
                                            `;
                                            window.dispatchEvent(new Event('resize'));
                                            
                                            var playAttempts = 0;
                                            function tryPlay() {
                                                var video = document.querySelector('video');
                                                if (video && !video.paused) {
                                                    return;
                                                }
                                                if (video) {
                                                    video.muted = false;
                                                    video.play().catch(function(e) {});
                                                }
                                                var btns = document.querySelectorAll('[class*="play"], [id*="play"], .wdp-play-button, .video_box_prep');
                                                for (var i = 0; i < btns.length; i++) {
                                                    var btn = btns[i];
                                                    var text = (btn.className + " " + btn.id).toLowerCase();
                                                    if (text.indexOf('pause') === -1) {
                                                        try { btn.click(); } catch(e) {}
                                                    }
                                                }
                                                if (playAttempts < 10) {
                                                    playAttempts++;
                                                    setTimeout(tryPlay, 600);
                                                }
                                            }
                                            setTimeout(tryPlay, 400);
                                        })();
                                        """.trimIndent(), null
                                    )
                                }
                            }
                            if (isMiniPlayer) {
                                isFocusable = false
                                isFocusableInTouchMode = false
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
                                viewModel.getPluginPageUrl(videoId) ?: "about:blank"
                            } else {
                                "https://rutube.ru/play/embed/$videoId/?autoplay=1"
                            }
                            loadUrl(embedUrl)
                        }
                    },
                    update = { webView ->
                        if (isMiniPlayer) {
                            webView.isFocusable = false
                            webView.isFocusableInTouchMode = false
                        }
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
                                    var style = document.getElementById('tv-player-style');
                                    if (!style) {
                                        style = document.createElement('style');
                                        style.id = 'tv-player-style';
                                        document.head.appendChild(style);
                                    }
                                    style.innerHTML = `
                                        * { margin: 0 !important; padding: 0 !important; }
                                        html, body, iframe, video, object, embed, 
                                        .player-container, .video-player, #player, 
                                        [id*="player"], [class*="player"],
                                        [id*="video"], [class*="video"],
                                        [id*="embed"], [class*="embed"] {
                                            width: 100% !important;
                                            height: 100% !important;
                                            max-width: 100% !important;
                                            max-height: 100% !important;
                                            min-width: 100% !important;
                                            min-height: 100% !important;
                                            position: absolute !important;
                                            top: 0 !important;
                                            left: 0 !important;
                                            margin: 0 !important;
                                            padding: 0 !important;
                                            border: none !important;
                                            overflow: hidden !important;
                                        }
                                        body { 
                                            background: #000 !important;
                                            display: flex !important;
                                            align-items: center !important;
                                            justify-content: center !important;
                                        }
                                    `;
                                    window.dispatchEvent(new Event('resize'));
                                })();
                                """.trimIndent(), null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isMiniPlayer) Modifier.focusProperties { canFocus = false } else Modifier)
                )
                
                // LIVE indicator для embed
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                }
                
                // Кнопка переключения на HLS
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    IconButton(
                        onClick = {
                            useEmbedPlayer = false
                            hlsUrl = null
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val url = viewModel.fetchHlsStreamUrl(videoId, selectedQuality)
                                    if (url != null && url.isNotBlank() && url.contains(".m3u8")) {
                                        hlsUrl = url
                                        if (isLive) isLiveStreamReady = true
                                    } else {
                                        useEmbedPlayer = true
                                    }
                                } catch (e: Exception) {
                                    useEmbedPlayer = true
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .size(48.dp)
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Попробовать HLS",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (latestIsMiniPlayer) {
                                        latestOnToggleFullscreen()
                                    } else {
                                        controlsVisible = !controlsVisible
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                },
                                onDoubleTap = {
                                    if (latestIsMiniPlayer) {
                                        latestOnToggleFullscreen()
                                    } else {
                                        controlsVisible = !controlsVisible
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                }
                            )
                        }
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
                                .sleekTvFocus(CircleShape, onEnter = onToggleFullscreen)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Полный экран",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        } else if (hlsUrl != null && !isLive) {
            // Обычное видео (не стрим)
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
                        if (isMiniPlayer) {
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.keepScreenOn = true
                    playerView.resizeMode = when (aspectMode) {
                        VlcAspectRatio.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VlcAspectRatio.BEST_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    if (isMiniPlayer) {
                        playerView.isFocusable = false
                        playerView.isFocusableInTouchMode = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isMiniPlayer) Modifier.focusProperties { canFocus = false } else Modifier)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (latestIsMiniPlayer) {
                                    latestOnToggleFullscreen()
                                } else {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            },
                            onDoubleTap = {
                                if (latestIsMiniPlayer) {
                                    latestOnToggleFullscreen()
                                } else {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        )
                    }
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
                            val onBackClick = { 
                                if (currentPos > 0) viewModel.saveVideoPosition(videoId, currentPos, totalDuration)
                                onToggleFullscreen() 
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .sleekTvFocus(CircleShape, onEnter = onBackClick)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onBackClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        
                        Text(
                            text = if (offlineFile.exists()) "$videoTitle (Офлайн)" else videoTitle,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        
                        val isVkOrDzen = videoId.startsWith("vk_") || videoId.startsWith("plugin_Дзен_")
                        if (!offlineFile.exists() && !isVkOrDzen) {
                            val onQualityClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val qualities = listOf("Авто", "1080p", "720p", "480p")
                                val nextIdx = (qualities.indexOf(selectedQuality) + 1) % qualities.size
                                selectedQuality = qualities[nextIdx]
                                viewModel.setPlayerQuality(selectedQuality)
                            }
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .sleekTvFocus(RoundedCornerShape(8.dp), onEnter = onQualityClick)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onQualityClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(selectedQuality, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        if (subtitles.isNotEmpty()) {
                            val onSubtitleClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val options = listOf(null) + subtitles.map { it.language }
                                val nextIdx = (options.indexOf(activeSubtitleLanguage) + 1) % options.size
                                activeSubtitleLanguage = options[nextIdx]
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (activeSubtitleLanguage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .sleekTvFocus(RoundedCornerShape(8.dp), onEnter = onSubtitleClick)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onSubtitleClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Subtitles, "Субтитры", tint = if (activeSubtitleLanguage != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(activeSubtitleLanguage ?: "Выкл", color = if (activeSubtitleLanguage != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))

                            if (activeSubtitleLanguage != null) {
                                val onDelayDecrease = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    subtitleDelayMs -= 500L
                                }
                                val onDelayIncrease = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    subtitleDelayMs += 500L
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .sleekTvFocus(RoundedCornerShape(4.dp), onEnter = onDelayDecrease)
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = ripple(bounded = true),
                                                onClick = onDelayDecrease
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("-0.5с", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Text(
                                        text = "${if (subtitleDelayMs > 0L) "+" else ""}${String.format(java.util.Locale.US, "%.1f", subtitleDelayMs / 1000f)}с",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .sleekTvFocus(RoundedCornerShape(4.dp), onEnter = onDelayIncrease)
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = ripple(bounded = true),
                                                onClick = onDelayIncrease
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+0.5с", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }

                        val onAspectClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            val modes = VlcAspectRatio.values()
                            val next = modes[(aspectMode.ordinal + 1) % modes.size]
                            onChangeAspectRatio(next)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onAspectClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onAspectClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AspectRatio, "Формат", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                        
                        if (!isFullscreen) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .sleekTvFocus(CircleShape, onEnter = onToggleFullscreen)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onToggleFullscreen
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Fullscreen, "Полный экран", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Center Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val onPrevClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            viewModel.playerManager.playPrevious()
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onPrevClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onPrevClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Предыдущее видео", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }

                        val onRewindClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            exoPlayer?.let {
                                val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                it.seekTo(newPos)
                                currentPos = newPos
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onRewindClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onRewindClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FastRewind, "Назад", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }

                        val onPlayPauseClick: () -> Unit = {
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
                        }
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .focusRequester(playPauseFocusRequester)
                                .sleekTvFocus(CircleShape, onEnter = onPlayPauseClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onPlayPauseClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                                    .align(Alignment.Center)
                            )
                        }

                        val onForwardClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            exoPlayer?.let {
                                val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                                it.seekTo(newPos)
                                currentPos = newPos
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onForwardClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onForwardClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FastForward, "Вперед", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }

                        val onNextClick: () -> Unit = {
                            lastInteractionTime = System.currentTimeMillis()
                            viewModel.playerManager.playNext()
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .sleekTvFocus(CircleShape, onEnter = onNextClick)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onNextClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SkipNext, "Следующее видео", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }
                    }

                    // Bottom Bar (Timeline)
                    var isTimelineFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMillis(currentPos),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .onFocusChanged { isTimelineFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionLeft -> {
                                                lastInteractionTime = System.currentTimeMillis()
                                                exoPlayer?.let { player ->
                                                    val seekStep = if (player.duration > 0) (player.duration / 100).coerceIn(5000L, 30000L) else 10000L
                                                    val newPos = (player.currentPosition - seekStep).coerceAtLeast(0L)
                                                    player.seekTo(newPos)
                                                    currentPos = newPos
                                                }
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                lastInteractionTime = System.currentTimeMillis()
                                                exoPlayer?.let { player ->
                                                    val seekStep = if (player.duration > 0) (player.duration / 100).coerceIn(5000L, 30000L) else 10000L
                                                    val newPos = (player.currentPosition + seekStep).coerceAtMost(player.duration)
                                                    player.seekTo(newPos)
                                                    currentPos = newPos
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
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
                                            exoPlayer?.let { player ->
                                                player.seekTo(currentPos)
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val progress = if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isTimelineFocused) 8.dp else 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Gray.copy(alpha = 0.5f))
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(if (isTimelineFocused) 8.dp else 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Primary)
                            )
                            
                            if (isTimelineFocused) {
                                val thumbOffset = maxWidth * progress - 8.dp
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = thumbOffset.coerceAtLeast(0.dp))
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(2.dp, Primary, CircleShape)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = formatMillis(totalDuration),
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