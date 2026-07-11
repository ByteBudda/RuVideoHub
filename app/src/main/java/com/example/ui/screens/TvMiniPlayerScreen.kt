package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.PrimaryContainer
import com.example.viewmodel.VideoViewModel
import com.example.ui.screens.home.components.VideoThumbnail
import kotlinx.coroutines.launch

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvMiniPlayerScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentVideo by viewModel.currentSelectedVideo.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val dynamicVideos by viewModel.dynamicVideos.collectAsStateWithLifecycle()
    val localIsFullscreen by viewModel.isTvMiniFullscreen.collectAsStateWithLifecycle()
    
    val firstItemFocusRequester = remember { FocusRequester() }
    
    // Filter out non-playable items like folders/channels/playlists
    val playableVideos = remember(dynamicVideos, currentVideo) {
        val filtered = dynamicVideos.filter {
            it.duration != "ПАПКА" && it.duration != "СЕРИАЛ" && 
            it.duration != "КАНАЛ" && it.duration != "ПЛЕЙЛИСТ" && 
            it.duration != "ПРОМО" && it.duration != "КАТАЛОГ"
        }
        val current = currentVideo
        if (current != null && filtered.none { it.id == current.id }) {
            listOf(current) + filtered
        } else {
            filtered
        }
    }
    
    val playableVideosCount = playableVideos.size
    LaunchedEffect(currentVideo, localIsFullscreen, playableVideosCount) {
        if (currentVideo != null && !localIsFullscreen) {
            kotlinx.coroutines.delay(350)
            try {
                firstItemFocusRequester.requestFocus()
            } catch(e: Exception) {}
        }
    }
    
    // Auto-select first video if none is selected
    LaunchedEffect(playableVideos) {
        if (currentVideo == null && playableVideos.isNotEmpty()) {
            viewModel.selectVideo(playableVideos.first())
        }
    }

    var selectedAspectRatio by remember { mutableStateOf(VlcAspectRatio.FIT) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setTvMiniFullscreen(false)
        }
    }

    // D-pad back handler
    androidx.activity.compose.BackHandler(enabled = true) {
        if (localIsFullscreen) {
            viewModel.setTvMiniFullscreen(false)
        } else {
            viewModel.selectVideo(null)
        }
    }

    var playerRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            }
    ) {
        // 1. SPLIT SCREEN LAYOUT (ONLY drawn when NOT fullscreen)
        if (!localIsFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Left Column: Video feed list (D-pad focusable)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = "ТВ Плейлист",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    if (playableVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Нет доступных видео",
                                color = GreyText,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(playableVideos.size) { index ->
                                val video = playableVideos[index]
                                val isCurrent = currentVideo?.id == video.id
                                val isFirst = index == 0
                                val shouldAttachRequester = isCurrent || (currentVideo == null && isFirst)
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (shouldAttachRequester) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                        .sleekTvFocus(
                                            shape = RoundedCornerShape(12.dp),
                                            focusColor = Primary,
                                            onEnter = { viewModel.selectVideo(video) }
                                        )
                                        .clickable { viewModel.selectVideo(video) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) Primary.copy(alpha = 0.15f) else Color(0xFF1E1C29)
                                    ),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isCurrent) Primary else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Thumbnail or placeholder
                                        Box(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .height(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            VideoThumbnail(
                                                id = video.id,
                                                duration = video.duration,
                                                thumbnailUrl = video.thumbnailUrl,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = video.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = video.channel,
                                                fontSize = 10.sp,
                                                color = GreyText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Right Column: Mini Player box & Controls
                Column(
                    modifier = Modifier
                        .weight(1.8f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentVideo != null) {
                        val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
                        val activeDownload = remember(activeDownloads, currentVideo) { currentVideo?.let { activeDownloads[it.id] } }

                        Text(
                            text = "ТВ Мини-плеер (Управление пультом)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        // Transparent layout placeholder box (tracked dynamically to draw the player over it)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    val size = coordinates.size
                                    playerRect = androidx.compose.ui.geometry.Rect(
                                        left = position.x,
                                        top = position.y,
                                        right = position.x + size.width,
                                        bottom = position.y + size.height
                                    )
                                }
                                .background(Color.Transparent)
                        )
                        
                        // TV Optimized Control Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play / Pause Button
                            Row(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(44.dp)
                                    .background(Color(0xFF2C2A3A), RoundedCornerShape(20.dp))
                                    .then(if (playableVideos.isEmpty()) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .sleekTvFocus(RoundedCornerShape(20.dp), onEnter = { viewModel.togglePlayPause() })
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = { viewModel.togglePlayPause() }
                                    ),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Плей Пауза",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isPlaying) "Пауза" else "Старт", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            // Full Screen Button
                            Row(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(44.dp)
                                    .background(Color(0xFF2C2A3A), RoundedCornerShape(20.dp))
                                    .sleekTvFocus(RoundedCornerShape(20.dp), onEnter = { viewModel.setTvMiniFullscreen(true) })
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = { viewModel.setTvMiniFullscreen(true) }
                                    ),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Во весь экран",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Развернуть", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            // Download Button
                            val isDownloaded = currentVideo?.isDownloaded == true
                            val downloadContainerColor = when {
                                isDownloaded -> Color(0xFF10B981)
                                activeDownload != null -> Color.Red.copy(alpha = 0.2f)
                                else -> Color(0xFF2C2A3A)
                            }
                            val downloadContentColor = when {
                                isDownloaded -> Color.White
                                activeDownload != null -> Color.Red
                                else -> Color.White
                            }
                            val onDownloadClick = {
                                val video = currentVideo
                                if (video != null) {
                                    if (video.isDownloaded) {
                                        viewModel.toggleDownload(video)
                                    } else {
                                        if (activeDownload == null) {
                                            viewModel.toggleDownload(video)
                                        } else {
                                            viewModel.cancelDownload(video.id)
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1.0f)
                                    .height(44.dp)
                                    .background(downloadContainerColor, RoundedCornerShape(20.dp))
                                    .sleekTvFocus(RoundedCornerShape(20.dp), onEnter = onDownloadClick)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = onDownloadClick
                                    ),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when {
                                        activeDownload != null -> Icons.Default.Close
                                        isDownloaded -> Icons.Default.DownloadDone
                                        else -> Icons.Default.Download
                                    },
                                    contentDescription = "Скачать",
                                    tint = downloadContentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isDownloaded -> "Скачано"
                                        activeDownload != null -> "${(activeDownload.progress * 100).toInt()}%"
                                        else -> "Скачать"
                                    },
                                    color = downloadContentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Close Player Button
                            Row(
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(44.dp)
                                    .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .sleekTvFocus(RoundedCornerShape(20.dp), onEnter = { viewModel.selectVideo(null) })
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = { viewModel.selectVideo(null) }
                                    ),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Закрыть", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        // Metadata Box
                        val metadataScrollState = rememberScrollState()
                        val metadataScope = rememberCoroutineScope()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .sleekTvFocus(RoundedCornerShape(16.dp))
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                if (metadataScrollState.value < metadataScrollState.maxValue) {
                                                    metadataScope.launch {
                                                        metadataScrollState.animateScrollTo((metadataScrollState.value + 120).coerceAtMost(metadataScrollState.maxValue))
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            Key.DirectionUp -> {
                                                if (metadataScrollState.value > 0) {
                                                    metadataScope.launch {
                                                        metadataScrollState.animateScrollTo((metadataScrollState.value - 120).coerceAtLeast(0))
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .verticalScroll(metadataScrollState)
                                .background(Color(0xFF14131F), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = currentVideo!!.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${currentVideo!!.channel} • ${currentVideo!!.views} • ${currentVideo!!.timeAgo}",
                                fontSize = 11.sp,
                                color = GreyText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentVideo!!.description.ifBlank { "Описание отсутствует" },
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        // Placeholder card when no video is selected/playing
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF14131F), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF2C2A3A), RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "ТВ плеер",
                                    tint = Primary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "ТВ Мини-плеер готов к работе",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Выберите любое видео из списка слева, чтобы запустить его воспроизведение в мини-плеере. Все элементы управления оптимизированы для пульта управления Android TV.",
                                    fontSize = 12.sp,
                                    color = GreyText,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.widthIn(max = 360.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. REAL VIDEO PLAYER NODE (Rendered at the root level, positioned exactly over placeholder or fullscreen)
        if (currentVideo != null) {
            val currentVid = currentVideo
            val isLiveStream = currentVid != null && (currentVid.duration == "ЭФИР" || 
                              currentVid.duration == "00:00" || 
                              currentVid.duration.isBlank() || 
                              currentVid.duration.contains(":") == false ||
                              currentVid.duration.equals("трансляция", ignoreCase = true) || 
                              currentVid.duration.equals("live", ignoreCase = true))

            val playerModifier = if (localIsFullscreen) {
                Modifier.fillMaxSize()
            } else {
                if (playerRect != androidx.compose.ui.geometry.Rect.Zero) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    with(density) {
                        Modifier
                            .size(width = playerRect.width.toDp(), height = playerRect.height.toDp())
                            .offset(x = playerRect.left.toDp(), y = playerRect.top.toDp())
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .border(2.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .focusProperties { canFocus = false }
                    }
                } else {
                    Modifier.size(0.dp)
                }
            }

            Box(
                modifier = playerModifier,
                contentAlignment = Alignment.Center
            ) {
                TvRutubeVideoPlayer(
                    videoId = currentVideo!!.id,
                    viewModel = viewModel,
                    videoTitle = currentVideo!!.title,
                    aspectMode = selectedAspectRatio,
                    isFullscreen = localIsFullscreen,
                    isMiniPlayer = !localIsFullscreen,
                    isLive = isLiveStream,
                    onToggleFullscreen = { viewModel.setTvMiniFullscreen(!localIsFullscreen) },
                    onChangeAspectRatio = { selectedAspectRatio = it },
                    onShare = { shareVideo(context, currentVideo!!) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
