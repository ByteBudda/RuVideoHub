package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

// Импорты твоих кастомных компонентов плеера и ресурсов темы
import com.example.ui.player.VlcVideoView
import com.example.ui.player.VlcAspectRatio
import com.example.viewmodel.VideoViewModel
import com.example.data.Video
import com.example.data.SavedVideo
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.ProBadgeBg
import com.example.ui.theme.ProBadgeText

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
fun SleekVideoHubApp(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentSelectedVideo by viewModel.currentSelectedVideo.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            bottomBar = {
                SleekBottomNavigation(
                    selectedTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "tab_fade",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        "home" -> HomeTabScreen(viewModel = viewModel)
                        "explore" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Обзор", color = Color.White) }
                        "recents" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Недавние", color = Color.White) }
                        "downloads" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Загрузки", color = Color.White) }
                        "library" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Избранное", color = Color.White) }
                    }
                }
            }
        }

        // Оверлей плеера, который открывается поверх всего приложения
        AnimatedVisibility(
            visible = currentSelectedVideo != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220)
            )
        ) {
            currentSelectedVideo?.let { video ->
                var aspectMode by remember { mutableStateOf(VlcAspectRatio.FIT) }
                
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    RutubeVideoPlayer(
                        videoId = video.id,
                        viewModel = viewModel,
                        videoTitle = video.title,
                        aspectMode = aspectMode,
                        isFullscreen = false,
                        onToggleFullscreen = { viewModel.selectVideo(null) },
                        onChangeAspectRatio = { aspectMode = it },
                        onShare = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val isMicActive by viewModel.isMicrophoneActive.collectAsStateWithLifecycle()
        if (isMicActive) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Text("Слушаю...", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun SleekBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SecondaryBackground,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .border(width = 1.dp, color = SurfaceVariant, shape = androidx.compose.ui.graphics.RectangleShape),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomTabItem("Главная", Icons.Default.Home, selectedTab == "home", { onTabSelected("home") }, "tab_home")
            BottomTabItem("Обзор", Icons.Default.Explore, selectedTab == "explore", { onTabSelected("explore") }, "tab_explore")
            BottomTabItem("Недавние", Icons.Default.History, selectedTab == "recents", { onTabSelected("recents") }, "tab_recents")
            BottomTabItem("Загрузки", Icons.Default.Download, selectedTab == "downloads", { onTabSelected("downloads") }, "tab_downloads")
            BottomTabItem("Избранное", Icons.Default.Favorite, selectedTab == "library", { onTabSelected("library") }, "tab_library")
        }
    }
}

@Composable
fun RowScope.BottomTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (isActive) PrimaryContainer else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else GreyText,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.onBackground else GreyText
        )
    }
}

@Composable
fun HomeTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isMicActive by viewModel.isMicrophoneActive.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val apiSource by viewModel.apiSource.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        SleekHeader(
            searchQuery = searchQuery,
            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
            isMicActive = isMicActive,
            onMicToggle = { viewModel.toggleMicrophone(it) },
            apiSource = apiSource
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(filteredVideos, key = { it.id }) { video ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { viewModel.selectVideo(video) },
                        colors = CardDefaults.cardColors(containerColor = SecondaryBackground)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = video.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                            Text(text = video.channel, fontSize = 12.sp, color = GreyText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleekHeader(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isMicActive: Boolean,
    onMicToggle: (Boolean) -> Unit,
    apiSource: String
) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SecondaryBackground)
                .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "Поиск", tint = GreyText)
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) Text("Поиск на Rutube", color = GreyText, fontSize = 14.sp)
                    innerTextField()
                }
            )
            IconButton(onClick = { onMicToggle(!isMicActive) }) {
                Icon(imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicNone, contentDescription = "Микрофон", tint = Primary)
            }
        }
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
    val context = LocalContext.current
    val downloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
    val offlineFile = File(downloadFolder, "$videoId.mp4")

    var hlsUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var loadError by remember(videoId) { mutableStateOf<String?>(null) }
    var useEmbedPlayer by remember(videoId) { mutableStateOf(false) }

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Получение видеопотока...", color = Color.White, fontSize = 12.sp)
            }
        } else if (useEmbedPlayer) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mediaPlaybackRequiresUserGesture = false
                            }
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            loadUrl("https://rutube.ru/play/embed/$videoId/")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (hlsUrl != null) {
            AndroidView(
                factory = { ctx ->
                    VlcVideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        this.aspectMode = aspectMode
                        
                        if (offlineFile.exists()) {
                            setVideoPath(offlineFile.absolutePath)
                        } else {
                            val headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                                "Referer" to "https://rutube.ru/"
                            )
                            setVideoURI(android.net.Uri.parse(hlsUrl), headers)
                        }
                        
                        setOnPreparedListener { mediaPlayer ->
                            updateVideoSize(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                            totalDuration = mediaPlayer.duration.toLong()
                            val savedPos = viewModel.getVideoPosition(videoId)
                            if (savedPos > 0L && savedPos < totalDuration) {
                                seekTo(savedPos.toInt())
                                currentPos = savedPos
                            }
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

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = videoTitle, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = {
                                val nextMode = aspectMode.next()
                                hudMessage = "Соотношение: ${nextMode.displayName}"
                                onChangeAspectRatio(nextMode)
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                            }
                        }
                    }

                    // Center Controllers
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            videoViewRef?.let { view: VlcVideoView ->
                                val newPos = (view.currentPosition - 10000).coerceAtLeast(0)
                                view.seekTo(newPos.toInt())
                                currentPos = newPos.toLong()
                            }
                        }) {
                            Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(onClick = {
                            videoViewRef?.let { view: VlcVideoView ->
                                if (view.isPlaying) {
                                    view.pause()
                                    isPlayingState = false
                                } else {
                                    view.start()
                                    isPlayingState = true
                                }
                            }
                        }) {
                            Icon(if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        }

                        IconButton(onClick = {
                            videoViewRef?.let { view: VlcVideoView ->
                                val newPos = (view.currentPosition + 10000).coerceAtMost(view.duration)
                                view.seekTo(newPos.toInt())
                                currentPos = newPos.toLong()
                            }
                        }) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    // Bottom Bar Progress Slider
                    Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(formatMillis(currentPos), color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = currentPos.toFloat().coerceIn(0f, totalDuration.toFloat().coerceAtLeast(1f)),
                                onValueChange = { newValue ->
                                    currentPos = newValue.toLong()
                                    videoViewRef?.seekTo(newValue.toInt())
                                },
                                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.weight(1f)
                            )
                            Text(formatMillis(totalDuration), color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }

            hudMessage?.let { msg ->
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.8f)).padding(8.dp)) {
                    Text(msg, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
