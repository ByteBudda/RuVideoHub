package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SurfaceVariant
import com.example.viewmodel.VideoViewModel

@Composable
fun SleekPlayerDetailOverlay(
    video: Video,
    viewModel: VideoViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.playProgress.collectAsStateWithLifecycle()
    val allVideos by viewModel.allVideos.collectAsStateWithLifecycle()

    val currentEpList by androidx.compose.runtime.produceState<List<Video>>(initialValue = emptyList(), video, allVideos) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            getSortedEpisodes(video, allVideos)
        }
    }
    val currentIndex = remember(currentEpList, video.id) { currentEpList.indexOfFirst { it.id == video.id } }

    val formattedElapsed = viewModel.getFormattedElapsedTime(video.duration, progress)

    var isFullscreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var isPlayerActive by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(video.id) {
        kotlinx.coroutines.delay(450)
        isPlayerActive = true
    }
    var selectedAspectRatio by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(VlcAspectRatio.FIT) }
    var showDownloadOptionsDialog by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember(video.id) { mutableStateOf(false) }

    val closeButtonFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val readGranted = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        if (readGranted || writeGranted || android.os.Build.VERSION.SDK_INT >= 29) {
            viewModel.saveToDevice(video, context) { success, message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Разрешение на работу с файлами отклонено", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val isTv = remember(context) {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Immersive mode orientation and system bar control
    val activity = context.findActivity()
    LaunchedEffect(isFullscreen, isTv) {
        val window = activity?.window
        if (isFullscreen) {
            if (!isTv) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            window?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    it.insetsController?.hide(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
                }
            }
        } else {
            if (!isTv) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            window?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    it.insetsController?.show(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    DisposableEffect(Unit, isTv) {
        onDispose {
            if (!isTv) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val window = activity?.window
            window?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    it.insetsController?.show(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    androidx.activity.compose.BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onDismiss()
        }
    }

    if (isLandscape && !isFullscreen) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .focusGroup()
        ) {
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .focusGroup()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("dismiss_player")
                            .focusRequester(closeButtonFocusRequester)
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть плеер",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "${video.category} • Воспроизведение",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        letterSpacing = 0.5.sp
                    )

                    Box(modifier = Modifier.size(24.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying && isPlayerActive) {
                        RutubeVideoPlayer(
                            videoId = video.id,
                            viewModel = viewModel,
                            videoTitle = video.title,
                            aspectMode = selectedAspectRatio,
                            isFullscreen = false,
                            onToggleFullscreen = { isFullscreen = true },
                            onChangeAspectRatio = { selectedAspectRatio = it },
                            onShare = { shareVideo(context, video) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            VideoThumbnail(
                                id = video.id,
                                duration = video.duration,
                                thumbnailUrl = video.thumbnailUrl,
                                hasPlayOverlay = !isPlaying,
                                isPlaying = false,
                                onPlayClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isPlaying && !isPlayerActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = Primary,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Подготовка проигрывателя...",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .focusGroup()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
                ) {
                    if (!video.authorId.isNullOrBlank()) {
                        Text(
                            text = video.channel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .sleekTvFocus(RoundedCornerShape(4.dp))
                                .clickable {
                                    onDismiss()
                                    val channelDummy = Video(
                                        id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                        title = video.channel,
                                        channel = video.channel,
                                        views = "",
                                        timeAgo = "",
                                        duration = "КАНАЛ",
                                        category = video.category,
                                        description = ""
                                    )
                                    viewModel.selectVideo(channelDummy)
                                }
                        )
                        Text(
                            text = " • ${video.views} • ${video.timeAgo}",
                            fontSize = 11.sp,
                            color = GreyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                            fontSize = 11.sp,
                            color = GreyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
                val activeDownload = activeDownloads[video.id]

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (video.isDownloaded) {
                                showDownloadOptionsDialog = true
                            } else {
                                if (activeDownload == null) {
                                    viewModel.toggleDownload(video)
                                } else {
                                    viewModel.cancelDownload(video.id)
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                video.isDownloaded -> Color(0xFF10B981)
                                activeDownload != null -> Color.Red.copy(alpha = 0.2f)
                                else -> PrimaryContainer
                            },
                            contentColor = when {
                                video.isDownloaded -> Color.White
                                activeDownload != null -> Color.Red
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(40.dp)
                            .testTag("player_action_download_land")
                            .sleekTvFocus(RoundedCornerShape(20.dp))
                    ) {
                        if (activeDownload != null) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отмена",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Red
                            )
                        } else {
                            Icon(
                                imageVector = if (video.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                video.isDownloaded -> "Скачано"
                                activeDownload != null -> "Отмена (${(activeDownload.progress * 100).toInt()}%)"
                                else -> "Скачать"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { viewModel.toggleBookmark(video) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (video.isBookmarked) Primary else SurfaceVariant,
                            contentColor = if (video.isBookmarked) Color.White else GreyText
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("player_action_bookmark_land")
                            .sleekTvFocus(RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (video.isBookmarked) "Сохранено" else "Избранное",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Quality Selection Pill in Landscape Mode
                    if (!video.isDownloaded) {
                        var qualityMenuExpanded by remember { mutableStateOf(false) }
                        val currentQuality by viewModel.playerQuality.collectAsStateWithLifecycle()
                        val availableQualities by viewModel.currentAvailableQualities.collectAsStateWithLifecycle()

                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { qualityMenuExpanded = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("player_action_quality_land")
                                    .sleekTvFocus(RoundedCornerShape(20.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hd,
                                    contentDescription = "Качество",
                                    modifier = Modifier.size(16.dp),
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentQuality,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DropdownMenu(
                                expanded = qualityMenuExpanded,
                                onDismissRequest = { qualityMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF0F0F1A))
                            ) {
                                availableQualities.forEach { q ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = q,
                                                color = if (currentQuality == q) Primary else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = if (currentQuality == q) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setPlayerQuality(q)
                                            qualityMenuExpanded = false
                                        },
                                        modifier = Modifier.sleekTvFocus(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }

                    // Share button in Landscape Mode
                    IconButton(
                        onClick = { shareVideo(context, video) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(SurfaceVariant, CircleShape)
                            .testTag("player_action_share_land")
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryBackground.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .sleekTvFocus(RoundedCornerShape(16.dp), onEnter = { isDescriptionExpanded = !isDescriptionExpanded })
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isDescriptionExpanded) "Описание медиафайла" else "Показать описание...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Icon(
                                imageVector = if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isDescriptionExpanded) "Скрыть" else "Развернуть",
                                tint = GreyText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isDescriptionExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = video.description.ifBlank { "Описание отсутствует." },
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = GreyText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (currentEpList.size > 1) "Смотреть далее" else "Рекомендуем далее",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentEpList.forEach { ep ->
                        val isActive = ep.id == video.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sleekTvFocus(RoundedCornerShape(12.dp), onEnter = { viewModel.selectVideo(ep) })
                                .clickable { viewModel.selectVideo(ep) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else SurfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    VideoThumbnail(
                                        id = ep.id,
                                        duration = ep.duration,
                                        thumbnailUrl = ep.thumbnailUrl,
                                        modifier = Modifier
                                            .width(72.dp)
                                            .height(44.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ep.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // Player header bar - collapses during fullscreen to avoid layout structure alterations
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullscreen) 0.dp else 56.dp)
         ) {
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("dismiss_player")
                            .focusRequester(closeButtonFocusRequester)
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть плеер",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "${video.category} • Воспроизведение",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        letterSpacing = 0.5.sp
                    )

                    Box(modifier = Modifier.size(24.dp)) // empty spacer for symmetry
                }
            }
        }

        // Active Player Canvas Render Box (16:9 Standard Aspect ratio or Fill Screen when Landscape Fullscreen)
        Box(
            modifier = if (isFullscreen) {
                Modifier.weight(1f).fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            }.background(Color.Black)
        ) {
            if (isPlaying && isPlayerActive) {
                RutubeVideoPlayer(
                    videoId = video.id,
                    viewModel = viewModel,
                    videoTitle = video.title,
                    aspectMode = selectedAspectRatio,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onChangeAspectRatio = { selectedAspectRatio = it },
                    onShare = { shareVideo(context, video) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VideoThumbnail(
                        id = video.id,
                        duration = video.duration,
                        thumbnailUrl = video.thumbnailUrl,
                        hasPlayOverlay = !isPlaying,
                        isPlaying = false,
                        onPlayClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isPlaying && !isPlayerActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Подготовка проигрывателя...",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isFullscreen) {
            Spacer(modifier = Modifier.height(12.dp))

            // Detail descriptions and channels metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .focusGroup()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Title descriptor
                Text(
                    text = video.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Stats info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
                ) {
                    if (!video.authorId.isNullOrBlank()) {
                        Text(
                            text = video.channel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .sleekTvFocus(RoundedCornerShape(4.dp))
                                .clickable {
                                    onDismiss()
                                    val channelDummy = Video(
                                        id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                        title = video.channel,
                                        channel = video.channel,
                                        views = "",
                                        timeAgo = "",
                                        duration = "КАНАЛ",
                                        category = video.category,
                                        description = ""
                                    )
                                    viewModel.selectVideo(channelDummy)
                                }
                        )
                        Text(
                            text = " • ${video.views} • ${video.timeAgo}",
                            fontSize = 11.sp,
                            color = GreyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                            fontSize = 11.sp,
                            color = GreyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Direct active action layout pills
                val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
                val activeDownload = activeDownloads[video.id]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download action state button
                    Button(
                        onClick = {
                            if (video.isDownloaded) {
                                showDownloadOptionsDialog = true
                            } else {
                                if (activeDownload == null) {
                                    viewModel.toggleDownload(video)
                                } else {
                                    viewModel.cancelDownload(video.id)
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                video.isDownloaded -> Color(0xFF10B981)
                                activeDownload != null -> Color.Red.copy(alpha = 0.2f)
                                else -> PrimaryContainer
                            },
                            contentColor = when {
                                video.isDownloaded -> Color.White
                                activeDownload != null -> Color.Red
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(40.dp)
                            .testTag("player_action_download")
                            .sleekTvFocus(RoundedCornerShape(20.dp))
                    ) {
                        if (activeDownload != null) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отмена",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Red
                            )
                        } else {
                            Icon(
                                imageVector = if (video.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                video.isDownloaded -> "Скачано"
                                activeDownload != null -> "Отмена (${(activeDownload.progress * 100).toInt()}%)"
                                else -> "Скачать"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Bookmark action state button
                    Button(
                        onClick = { viewModel.toggleBookmark(video) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (video.isBookmarked) Primary else SurfaceVariant,
                            contentColor = if (video.isBookmarked) Color.White else GreyText
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("player_action_bookmark")
                            .sleekTvFocus(RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (video.isBookmarked) "Сохранено" else "Избранное",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Quality Selection Pill in Portrait Mode
                    if (!video.isDownloaded) {
                        var qualityMenuExpanded by remember { mutableStateOf(false) }
                        val currentQuality by viewModel.playerQuality.collectAsStateWithLifecycle()
                        val availableQualities by viewModel.currentAvailableQualities.collectAsStateWithLifecycle()

                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { qualityMenuExpanded = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("player_action_quality")
                                    .sleekTvFocus(RoundedCornerShape(20.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hd,
                                    contentDescription = "Качество",
                                    modifier = Modifier.size(16.dp),
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentQuality,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DropdownMenu(
                                expanded = qualityMenuExpanded,
                                onDismissRequest = { qualityMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF0F0F1A))
                            ) {
                                availableQualities.forEach { q ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = q,
                                                color = if (currentQuality == q) Primary else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = if (currentQuality == q) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setPlayerQuality(q)
                                            qualityMenuExpanded = false
                                        },
                                        modifier = Modifier.sleekTvFocus(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }

                    // Share action state button (as compact circular icon button)
                    IconButton(
                        onClick = { shareVideo(context, video) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(SurfaceVariant, CircleShape)
                            .testTag("player_action_share")
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Beautiful visual active download card info below buttons
                if (activeDownload != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("active_download_progress_card"),
                        colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Скачивание во внутреннюю память...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = activeDownload.status,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { activeDownload.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Скорость: ${activeDownload.speed}",
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                                Text(
                                    text = "Осталось: ${activeDownload.eta}",
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                            }
                        }
                    }
                }

                // Download options Alert Dialog
                if (showDownloadOptionsDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDownloadOptionsDialog = false },
                        title = {
                            Text(
                                "Управление скачанным файлом",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                "Файл сохранен во внутреннем медиа-кэше приложения. Вы можете экспортировать его в папку 'Загрузки' вашего устройства или очистить кэш.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = GreyText
                            )
                        },
                        confirmButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        showDownloadOptionsDialog = false
                                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                                            viewModel.saveToDevice(video, context) { success, message ->
                                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                )
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Сохранить на устройство", fontSize = 11.sp)
                                }

                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showDownloadOptionsDialog = false
                                        viewModel.deleteDownload(video)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Удалить из кэша", fontSize = 11.sp)
                                }

                                androidx.compose.material3.TextButton(
                                    onClick = { showDownloadOptionsDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Отмена", fontSize = 11.sp)
                                }
                            }
                        },
                        dismissButton = {}
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Expandable Description Box card (spoiler style)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryBackground.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .sleekTvFocus(RoundedCornerShape(16.dp), onEnter = { isDescriptionExpanded = !isDescriptionExpanded })
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isDescriptionExpanded) "Описание медиафайла" else "Показать описание видео...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Icon(
                                imageVector = if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isDescriptionExpanded) "Скрыть" else "Развернуть",
                                tint = GreyText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isDescriptionExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = video.description.ifBlank { "Описание отсутствует." },
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = GreyText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Related videos segment
                Text(
                    text = if (currentEpList.size > 1) "Смотреть далее" else "Рекомендуем далее",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentEpList.forEach { ep ->
                        val isActive = ep.id == video.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sleekTvFocus(RoundedCornerShape(12.dp), onEnter = { viewModel.selectVideo(ep) })
                                .clickable { viewModel.selectVideo(ep) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else SurfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    VideoThumbnail(
                                        id = ep.id,
                                        duration = ep.duration,
                                        thumbnailUrl = ep.thumbnailUrl,
                                        modifier = Modifier
                                            .width(80.dp)
                                            .height(48.dp)
                                    )
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .width(80.dp)
                                                .height(48.dp)
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Играет сейчас",
                                                tint = Primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    val epInfo = parseEpisode(ep.title)
                                    Text(
                                        text = ep.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (epInfo.hasEpisodeInfo) {
                                                "Сезон ${epInfo.season} • Серия ${epInfo.episode}"
                                            } else {
                                                "${ep.views} • ${ep.timeAgo}"
                                            },
                                            fontSize = 9.sp,
                                            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else GreyText
                                        )
                                        if (isActive) {
                                            Text(
                                                text = "Воспроизведение",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Primary,
                                                modifier = Modifier
                                                    .background(Primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}

data class EpisodeInfo(
    val baseTitle: String,
    val season: Int,
    val episode: Int,
    val rawNum: Int,
    val hasEpisodeInfo: Boolean = false
)

fun parseEpisode(title: String): EpisodeInfo {
    val lower = title.lowercase()
    
    var season = 1
    var episode = 1
    var rawNum = -1
    var matched = false
    
    // 1. Unified S01E08, S1 Ep 8, 1x08 patterns
    val sExeRegex = Regex("""s\s*(\d+)\s*e\s*(\d+)""", RegexOption.IGNORE_CASE)
    val sEpRegex = Regex("""s\s*(\d+)\s*ep\s*(\d+)""", RegexOption.IGNORE_CASE)
    val xRegex = Regex("""(\d+)\s*x\s*(\d+)""", RegexOption.IGNORE_CASE)
    
    val sexMatch = sExeRegex.find(lower)
    val sEpMatch = sEpRegex.find(lower)
    val xMatch = xRegex.find(lower)
    
    if (sexMatch != null) {
        season = sexMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        episode = sexMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
        rawNum = episode
        matched = true
    } else if (sEpMatch != null) {
        season = sEpMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        episode = sEpMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
        rawNum = episode
        matched = true
    } else if (xMatch != null) {
        season = xMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        episode = xMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
        rawNum = episode
        matched = true
    }
    
    if (!matched) {
        // 2. Russian Combined patterns:
        val ruComb1 = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон\w*\s*[,.\s-]*\s*(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")
        val ruComb2 = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\s*[,.\s-]*\s*(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)""")
        val ruComb3 = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон\w*\s*[,.\s-]*\s*(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)""")
        val ruComb4 = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\s*[,.\s-]*\s*(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")

        val c1 = ruComb1.find(lower)
        val c2 = ruComb2.find(lower)
        val c3 = ruComb3.find(lower)
        val c4 = ruComb4.find(lower)
        
        if (c1 != null) {
            season = c1.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c1.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (c2 != null) {
            season = c2.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c2.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (c3 != null) {
            season = c3.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c3.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (c4 != null) {
            season = c4.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c4.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        }
    }
    
    if (!matched) {
        val seasonSuffixRegex = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон""")
        val seasonPrefixRegex = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\b""")
        
        val sSfxMatch = seasonSuffixRegex.find(lower)
        val sPfxMatch = seasonPrefixRegex.find(lower)
        
        var seasonFound = false
        if (sSfxMatch != null) {
            season = sSfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            seasonFound = true
        } else if (sPfxMatch != null) {
            season = sPfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            seasonFound = true
        }
        
        val epSuffixRegex = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")
        val epPrefixRegex = Regex("""(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)\b""")
        
        val epSfxMatch = epSuffixRegex.find(lower)
        val epPfxMatch = epPrefixRegex.find(lower)
        
        var episodeFound = false
        if (epSfxMatch != null) {
            episode = epSfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            rawNum = episode
            episodeFound = true
        } else if (epPfxMatch != null) {
            episode = epPfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            rawNum = episode
            episodeFound = true
        }
        
        if (seasonFound || episodeFound) {
            matched = true
        }
        
        if (rawNum == -1) {
            val digitRegex = Regex("""\d+""")
            val matches = digitRegex.findAll(lower).toList()
            if (matches.isNotEmpty()) {
                val lastNum = matches.last().groupValues.getOrNull(0)?.toIntOrNull() ?: 1
                if (matches.size == 1 && (sSfxMatch != null || sPfxMatch != null)) {
                    episode = 1
                } else {
                    episode = lastNum
                    rawNum = lastNum
                }
            }
        }
    }
    
    var baseTitle = title
        .replace(Regex("""(?i)\bs\d+e\d+\b"""), "")
        .replace(Regex("""(?i)\bs\d+ep\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+x\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*сезон\w*\b"""), "")
        .replace(Regex("""(?i)\bсезон\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*сери\w*\b"""), "")
        .replace(Regex("""(?i)\bсери\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*эпизод\w*\b"""), "")
        .replace(Regex("""(?i)\bэпизод\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*выпуск\w*\b"""), "")
        .replace(Regex("""(?i)\bвыпуск\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*част\w*\b"""), "")
        .replace(Regex("""(?i)\bчаст\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        
    if (baseTitle.length < 3) {
        baseTitle = title.take(15)
    }
    
    return EpisodeInfo(baseTitle, season, episode, if (rawNum != -1) rawNum else 1, matched)
}

fun getSortedEpisodes(currentVideo: Video, allVideos: List<Video>): List<Video> {
    val currentInfo = parseEpisode(currentVideo.title)
    
    val matching = allVideos.filter { item ->
        val itemInfo = parseEpisode(item.title)
        val shareBaseTitle = itemInfo.baseTitle.lowercase().split(" ").filter { it.length > 3 }
            .any { word -> currentInfo.baseTitle.lowercase().contains(word) }
        
        shareBaseTitle || item.channel == currentVideo.channel
    }
    
    val sorted = matching.distinctBy { it.id }.sortedWith(compareBy<Video> { 
        val info = parseEpisode(it.title)
        info.season
    }.thenBy { 
        val info = parseEpisode(it.title)
        info.episode
    })
    
    if (sorted.size > 1) {
        return sorted
    }
    
    val categoryVideos = allVideos.filter { it.category == currentVideo.category }
    if (categoryVideos.size > 1) {
        return categoryVideos
    }
    
    return allVideos.take(10)
}
