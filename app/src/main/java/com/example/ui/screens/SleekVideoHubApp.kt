package com.example.ui.screens // Твой package из логов гитхаба

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

// Если эти классы/цвета лежат в другом месте — допиши импорты
// import com.example.torrflix.ui.player.VlcVideoView 
// import com.example.torrflix.ui.player.VideoViewModel

val Primary = Color(0xFFE50914)

enum class VlcAspectRatio(val displayName: String) {
    FIT("Вписать"),
    FILL("Растянуть"),
    NONE("16:9"), // Переименовал во избежание конфликтов парсинга чисел в enum-константах
    FOUR_THREE("4:3");

    fun next(): VlcAspectRatio {
        val values = values()
        val nextOrdinal = (this.ordinal + 1) % values.size
        return values[nextOrdinal]
    }
}

fun formatMillis(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun RutubeVideoPlayer(
    videoId: String,
    viewModel: VideoViewModel,
    videoTitle: String = "",
    aspectMode: VlcAspectRatio = VlcAspectRatio.FIT,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onChangeAspectRatio: (VlcAspectRatio) -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
    val offlineFile = File(downloadFolder, "$videoId.mp4")

    var hlsUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var loadError by remember(videoId) { mutableStateOf<String?>(null) }
    var useEmbedPlayer by remember(videoId) { mutableStateOf(false) }

    // Явно задаем тип ссылки на плеер, чтобы компилятор не блуждал в трех соснах
    var videoViewRef by remember { mutableStateOf<VlcVideoView?>(null) }
    var isPlayingState by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }

    var hudMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId) {
        if (offlineFile.exists()) {
            hlsUrl = offlineFile.absolutePath
            isLoading = false
        } else {
            isLoading = true
            loadError = null
            useEmbedPlayer = false
            val resolvedUrl = viewModel.fetchHlsStreamUrl(videoId)
            if (resolvedUrl != null) {
                hlsUrl = resolvedUrl
                isLoading = false
            } else {
                useEmbedPlayer = true
                isLoading = false
            }
        }
    }

    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            delay(4000)
            if (System.currentTimeMillis() - lastInteractionTime >= 4000) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(hudMessage) {
        if (hudMessage != null) {
            delay(1500)
            hudMessage = null
        }
    }

    // Progress update loop с явным приведением типа в let
    LaunchedEffect(videoViewRef, isPlayingState) {
        while (isPlayingState && videoViewRef != null) {
            videoViewRef?.let { view: VlcVideoView ->
                if (view.isPlaying) {
                    currentPos = view.currentPosition.toLong()
                    val dur = view.duration.toLong()
                    if (dur > 0L) {
                        totalDuration = dur
                    }
                    viewModel.saveVideoPosition(videoId, currentPos)
                }
            }
            delay(250)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                lastInteractionTime = System.currentTimeMillis()
                controlsVisible = !controlsVisible
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
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            val embedUrl = "https://rutube.ru/play/embed/$videoId/"
                            loadUrl(embedUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

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
                            isLoading = true
                            hlsUrl = null 
                            loadError = null
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(36.dp)
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
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        useEmbedPlayer = true
                        loadError = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Включить через встроенный плеер", fontSize = 11.sp)
                }
            }
        } else if (hlsUrl != null) {
            AndroidView(
                factory = { ctx ->
                    VlcVideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.aspectMode = aspectMode
                        
                        if (offlineFile.exists()) {
                            setVideoPath(offlineFile.absolutePath)
                        } else {
                            val headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                                "Accept" to "*/*",
                                "Referer" to "https://rutube.ru/"
                            )
                            setVideoURI(android.net.Uri.parse(hlsUrl), headers)
                        }
                        
                        setOnPreparedListener { mediaPlayer ->
                            updateVideoSize(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                            mediaPlayer.isLooping = false
                            totalDuration = mediaPlayer.duration.toLong()
                            val savedPos = viewModel.getVideoPosition(videoId)
                            if (savedPos > 0L && savedPos < totalDuration) {
                                seekTo(savedPos.toInt())
                                currentPos = savedPos
                            }
                            // Тот самый вызов! Теперь он внутри контекста VlcVideoView, всё ок
                            start()
                        }
                        
                        setOnErrorListener { _, _, _ ->
                            useEmbedPlayer = true
                            hlsUrl = null
                            true
                        }
                        videoViewRef = this
                    }
                },
                update = { videoView ->
                    if (videoView.aspectMode != aspectMode) {
                        videoView.aspectMode = aspectMode
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            lastInteractionTime = System.currentTimeMillis()
                            controlsVisible = false
                        }
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isFullscreen) {
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        onToggleFullscreen()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Назад",
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = if (offlineFile.exists()) "$videoTitle • Offline" else videoTitle.ifBlank { "Rutube Онлайн-превью" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val nextMode = aspectMode.next()
                                    hudMessage = "Соотношение: ${nextMode.displayName}"
                                    onChangeAspectRatio(nextMode)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
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

                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    onShare()
                                },
                                modifier = Modifier.size(32.dp)
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

                    // Center Buttons (Явное указание типов во всех лямбдах)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                videoViewRef?.let { view: VlcVideoView ->
                                    val newPos = (view.currentPosition - 10000).coerceAtLeast(0)
                                    view.seekTo(newPos.toInt())
                                    currentPos = newPos.toLong()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
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
                                videoViewRef?.let { view: VlcVideoView ->
                                    if (view.isPlaying) {
                                        view.pause()
                                        isPlayingState = false
                                    } else {
                                        view.start()
                                        isPlayingState = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Primary.copy(alpha = 0.9f), CircleShape)
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
                                videoViewRef?.let { view: VlcVideoView ->
                                    val newPos = (view.currentPosition + 10000).coerceAtMost(view.duration)
                                    view.seekTo(newPos.toInt())
                                    currentPos = newPos.toLong()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Вперед 10с",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Bottom Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(bottom = if (isFullscreen) 16.dp else 6.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
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

                            Slider(
                                value = currentPos.toFloat().coerceIn(0f, totalDuration.toFloat().coerceAtLeast(1f)),
                                onValueChange = { newValue ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    currentPos = newValue.toLong()
                                    videoViewRef?.seekTo(newValue.toInt())
                                },
                                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Primary,
                                    activeTrackColor = Primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            )

                            Text(
                                text = formatMillis(totalDuration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    onToggleFullscreen()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.Close else Icons.Default.AspectRatio,
                                    contentDescription = "Экран",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            hudMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = msg, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
