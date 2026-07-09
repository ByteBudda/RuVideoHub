package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.viewmodel.VideoViewModel
import com.example.ui.theme.liquidGlass
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.OpenInFull

@Composable
fun SleekPlayerDetailOverlay(
    video: Video,
    viewModel: VideoViewModel,
    isDark: Boolean,
    isTvOptimized: Boolean,
    isMiniPlayer: Boolean = false,
    isInPipMode: Boolean = false,
    onRestore: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isFullscreen by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    val closeButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // viewModel.loadRelatedVideos(video)
        if (isTvOptimized) {
            try { closeButtonFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    LaunchedEffect(isFullscreen) {
        val activity = context.findActivity()
        if (activity != null) {
            val window = activity.window
            val decorView = window.decorView
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, decorView)
            if (isFullscreen) {
                if (!isTvOptimized) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                if (!isTvOptimized) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            if (activity != null) {
                if (!isTvOptimized) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                val window = activity.window
                val decorView = window.decorView
                val controller = androidx.core.view.WindowInsetsControllerCompat(window, decorView)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isPlayerActive = true
    var selectedAspectRatio by remember { mutableStateOf(VlcAspectRatio.FIT) }

    val currentEpList = viewModel.filteredVideos.collectAsStateWithLifecycle().value.take(5)
    var showDownloadOptionsDialog by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            viewModel.saveToDevice(video, context) { success, message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Отказано в доступе к хранилищу", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    androidx.activity.compose.BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onDismiss()
        }
    }

    val playerContent = remember(video.id) {
        androidx.compose.runtime.movableContentOf<Boolean, Boolean, VlcAspectRatio> { isMini, isFull, aspect ->
            val isLiveStream = video.duration == "ЭФИР" || 
                              video.duration == "00:00" || 
                              video.duration.isBlank() || 
                              video.duration.contains(":") == false ||
                              video.duration.equals("трансляция", ignoreCase = true) || 
                              video.duration.equals("live", ignoreCase = true)
            if (isTvOptimized) {
                TvRutubeVideoPlayer(
                    videoId = video.id,
                    viewModel = viewModel,
                    videoTitle = video.title,
                    aspectMode = aspect,
                    isFullscreen = isFull,
                    isMiniPlayer = isMini,
                    isLive = isLiveStream,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onShare = { shareVideoOverlay(context, video) },
                    onChangeAspectRatio = { selectedAspectRatio = it },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                RutubeVideoPlayer(
                    videoId = video.id,
                    viewModel = viewModel,
                    videoTitle = video.title,
                    aspectMode = aspect,
                    isFullscreen = isFull,
                    isMiniPlayer = isMini,
                    isLive = isLiveStream,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onShare = { shareVideoOverlay(context, video) },
                    onChangeAspectRatio = { selectedAspectRatio = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            playerContent(false, isFullscreen, selectedAspectRatio)
        }
        return
    }

    if (isMiniPlayer) {
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var widthDp by remember { mutableStateOf(300.dp) }
        val heightDp = remember(widthDp) { (widthDp.value / (16f / 9f)).dp }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = 80.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Card(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .width(widthDp)
                    .height(heightDp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .clickable { onRestore() }
                    .sleekTvFocus(RoundedCornerShape(12.dp), onEnter = onRestore),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isPlaying || isPlayerActive) {
                        playerContent(true, isFullscreen, selectedAspectRatio)
                    }
                    
                    // Controls inside mini player: Restore, Aspect Ratio / Size, Float, and Close (Spaced elegantly)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = onRestore,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Развернуть",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                widthDp = when (widthDp) {
                                    240.dp -> 320.dp
                                    320.dp -> 400.dp
                                    else -> 240.dp
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Размер окна",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (!isTvOptimized) IconButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val activity = context.findActivity()
                                    if (activity != null) {
                                        try {
                                            val params = android.app.PictureInPictureParams.Builder().build()
                                            activity.enterPictureInPictureMode(params)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SleekPlayerDetailOverlay", "Error entering PiP", e)
                                        }
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Режим картинка-в-картинке не поддерживается на этом устройстве", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Поверх всех окон",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть мини-плеер",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Size of window can be changed step-by-step using AspectRatio button in top-right
                }
            }
        }
        return
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
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Свернуть плеер",
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

                    if (!isTvOptimized) IconButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val activity = context.findActivity()
                                if (activity != null) {
                                    try {
                                        val params = android.app.PictureInPictureParams.Builder().build()
                                        activity.enterPictureInPictureMode(params)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SleekPlayerDetailOverlay", "Error entering PiP", e)
                                    }
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Режим картинка-в-картинке не поддерживается на этом устройстве", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Поверх всех окон",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying || isPlayerActive) {
                        playerContent(false, isFullscreen, selectedAspectRatio)
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
            ) {
                PlayerDetailsPanel(
                    video = video,
                    viewModel = viewModel,
                    currentEpList = currentEpList,
                    context = context,
                    isDark = isDark,
                    isTvOptimized = isTvOptimized,
                    onDismiss = onDismiss,
                    showDownloadOptionsDialog = { showDownloadOptionsDialog = it }
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
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
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Свернуть плеер",
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

                        if (!isTvOptimized) IconButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val activity = context.findActivity()
                                    if (activity != null) {
                                        try {
                                            val params = android.app.PictureInPictureParams.Builder().build()
                                            activity.enterPictureInPictureMode(params)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SleekPlayerDetailOverlay", "Error entering PiP", e)
                                        }
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Режим картинка-в-картинке не поддерживается на этом устройстве", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Поверх всех окон",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
            Box(
                modifier = if (isFullscreen) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }.background(Color.Black)
            ) {
                if (isPlaying || isPlayerActive) {
                    playerContent(false, isFullscreen, selectedAspectRatio)
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
                    }
                }
            }
            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .focusGroup()
                ) {
                    PlayerDetailsPanel(
                        video = video,
                        viewModel = viewModel,
                        currentEpList = currentEpList,
                        context = context,
                        isDark = isDark,
                        isTvOptimized = isTvOptimized,
                        onDismiss = onDismiss,
                        showDownloadOptionsDialog = { showDownloadOptionsDialog = it }
                    )
                }
            }
        }
    }

    if (showOverlayPermissionDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text("Поверх других окон") },
            text = { Text("Для отображения плавающего плеера поверх всех окон требуется разрешение. Пожалуйста, разрешите показ поверх других окон в настройках.") },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDialog = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            }
                        }
                    }
                ) {
                    Text("Настройки")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

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
}

private fun shareVideoOverlay(context: android.content.Context, video: Video) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        val prefix = if (video.duration == com.example.utils.VideoType.CHANNEL) "Канал" else "Видео"
        val url = if (video.duration == com.example.utils.VideoType.CHANNEL && !video.authorActionUrl.isNullOrBlank()) {
            video.authorActionUrl
        } else {
            "https://rutube.ru/video/${video.id}/"
        }
        putExtra(Intent.EXTRA_SUBJECT, "$prefix: ${video.title}")
        putExtra(Intent.EXTRA_TEXT, "Смотри '$prefix: ${video.title}' на Rutube: $url")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Поделиться..."))
}
