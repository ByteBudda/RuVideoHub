package com.example.ui.screens.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.liquidGlass
import com.example.ui.screens.sleekTvFocus

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HeroVideoCard(
    video: Video,
    onVideoClick: () -> Unit,
    onDownloadToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    onChannelClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onSaveToDeviceClick: (() -> Unit)? = null,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val customTheme = com.example.ui.theme.LocalCustomTheme.current
    val cardCornerRadius = customTheme?.cardCornerRadius ?: 16
    val cardShape = RoundedCornerShape(cardCornerRadius.dp)
    val isContainer = video.duration == com.example.utils.VideoType.PLAYLIST ||
            video.duration == com.example.utils.VideoType.SERIES ||
            video.duration == com.example.utils.VideoType.CHANNEL ||
            video.duration == com.example.utils.VideoType.FOLDER ||
            video.duration == com.example.utils.VideoType.CATALOG ||
            video.duration == com.example.utils.VideoType.PROMO ||
            video.duration == "ПЛЕЙЛИСТ" ||
            video.id.startsWith("channel_")

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .sleekTvFocus(
                shape = cardShape,
                onEnter = onVideoClick,
                onLongEnter = { showMenu = true }
            )
            .liquidGlass(cardShape, borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .combinedClickable(
                onClick = onVideoClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Column {
            // Thumbnail
            VideoThumbnail(
                id = video.id,
                duration = video.duration,
                thumbnailUrl = video.thumbnailUrl,
                isWatched = video.isWatched,
                playbackProgress = video.playbackProgress,
                viewsAndTimeAgo = if (video.views.isNotBlank() || video.timeAgo.isNotBlank()) "${video.views} • ${video.timeAgo}".trim(' ', '•') else null,
                shape = RoundedCornerShape(
                    topStart = cardCornerRadius.dp,
                    topEnd = cardCornerRadius.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .aspectRatio(16f / 9f)
            )

            // Info Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Text labels
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.channel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (onChannelClick != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                var channelModifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                
                                if (!isTvOptimized) {
                                    channelModifier = Modifier
                                        .clickable(onClick = onChannelClick)
                                        .then(channelModifier)
                                }
                                
                                Text(
                                    text = video.channel,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .then(channelModifier)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = video.channel,
                                    color = GreyText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showMenu) {
        // Флаг, который блокирует нажатия, пока мы не увидим отпускание кнопки от долгого тапа
        var ignoreInitialRelease by remember { mutableStateOf(true) }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showMenu = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 320.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (ignoreInitialRelease) {
                                // Если поймали отпускание кнопки - снимаем блокировку
                                if (event.type == KeyEventType.KeyUp) {
                                    ignoreInitialRelease = false
                                }
                                // Поглощаем всё (и спам KeyDown, и финальный KeyUp от долгого нажатия)
                                return@onPreviewKeyEvent true 
                            }
                        }
                        false // Пропускаем остальные кнопки (например, стрелки) и нормальные клики
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Опции", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val btnModifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).sleekTvFocus(RoundedCornerShape(8.dp))
                    
                    if (onChannelClick != null) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onChannelClick()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text("Перейти в канал")
                        }
                    }
                    
                    Button(
                        onClick = { 
                            showMenu = false
                            onBookmarkToggle()
                        },
                        modifier = btnModifier,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Text(if (video.isBookmarked) "Убрать из избранного" else "В избранное")
                    }
                    
                    if (!isContainer) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onDownloadToggle()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text(if (video.isDownloaded) "Удалить" else "Скачать")
                        }
                    }
                    
                    if (video.isDownloaded && onSaveToDeviceClick != null) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onSaveToDeviceClick()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Text("Сохранить на устройство")
                        }
                    }
                    
                    if (onDeleteClick != null) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onDeleteClick()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text("Удалить из истории")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SleekVideoGridItem(
    video: Video,
    onVideoClick: () -> Unit,
    onDownloadToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    onChannelClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onSaveToDeviceClick: (() -> Unit)? = null,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val customTheme = com.example.ui.theme.LocalCustomTheme.current
    val cardCornerRadius = customTheme?.cardCornerRadius ?: 16
    val cardShape = RoundedCornerShape(cardCornerRadius.dp)
    val isContainer = video.duration == com.example.utils.VideoType.PLAYLIST ||
            video.duration == com.example.utils.VideoType.SERIES ||
            video.duration == com.example.utils.VideoType.CHANNEL ||
            video.duration == com.example.utils.VideoType.FOLDER ||
            video.duration == com.example.utils.VideoType.CATALOG ||
            video.duration == com.example.utils.VideoType.PROMO ||
            video.duration == "ПЛЕЙЛИСТ" ||
            video.id.startsWith("channel_")

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .sleekTvFocus(
                shape = cardShape,
                onEnter = onVideoClick,
                onLongEnter = { showMenu = true }
            )
            .liquidGlass(cardShape, borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .combinedClickable(
                onClick = onVideoClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Column {
            // Thumbnail
            VideoThumbnail(
                id = video.id,
                duration = video.duration,
                thumbnailUrl = video.thumbnailUrl,
                isWatched = video.isWatched,
                playbackProgress = video.playbackProgress,
                shape = RoundedCornerShape(
                    topStart = cardCornerRadius.dp,
                    topEnd = cardCornerRadius.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // Info Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = video.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.channel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onChannelClick != null) {
                            var channelModifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                            
                            if (!isTvOptimized) {
                                channelModifier = Modifier
                                    .clickable(onClick = onChannelClick)
                                    .then(channelModifier)
                            }
                            
                            Text(
                                text = video.channel,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .then(channelModifier)
                            )
                        } else {
                            Text(
                                text = video.channel,
                                color = GreyText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMenu) {
        var ignoreInitialRelease by remember { mutableStateOf(true) }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showMenu = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 320.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (ignoreInitialRelease) {
                                if (event.type == KeyEventType.KeyUp) {
                                    ignoreInitialRelease = false
                                }
                                return@onPreviewKeyEvent true 
                            }
                        }
                        false
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Опции", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val btnModifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).sleekTvFocus(RoundedCornerShape(8.dp))
                    
                    if (onChannelClick != null) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onChannelClick()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text("Перейти в канал")
                        }
                    }
                    
                    Button(
                        onClick = { 
                            showMenu = false
                            onBookmarkToggle()
                        },
                        modifier = btnModifier,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Text(if (video.isBookmarked) "Убрать из избранного" else "В избранное")
                    }
                    
                    if (!isContainer) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onDownloadToggle()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text(if (video.isDownloaded) "Удалить" else "Скачать")
                        }
                    }
                    
                    if (video.isDownloaded && onSaveToDeviceClick != null) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onSaveToDeviceClick()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Text("Сохранить на устройство")
                        }
                    }
                    
                    if (onDeleteClick != null) {
                        Button(
                            onClick = { 
                                showMenu = false
                                onDeleteClick()
                            },
                            modifier = btnModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text("Удалить из истории")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecondaryVideoItemRow(
    video: Video,
    onVideoClick: () -> Unit,
    onDownloadToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    onChannelClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onSaveToDeviceClick: (() -> Unit)? = null,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val customTheme = com.example.ui.theme.LocalCustomTheme.current
    val cardCornerRadius = customTheme?.cardCornerRadius ?: 16
    val cardShape = RoundedCornerShape(cardCornerRadius.dp)
    val isContainer = video.duration == com.example.utils.VideoType.PLAYLIST ||
            video.duration == com.example.utils.VideoType.SERIES ||
            video.duration == com.example.utils.VideoType.CHANNEL ||
            video.duration == com.example.utils.VideoType.FOLDER ||
            video.duration == com.example.utils.VideoType.CATALOG ||
            video.duration == com.example.utils.VideoType.PROMO ||
            video.duration == "ПЛЕЙЛИСТ" ||
            video.id.startsWith("channel_")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .sleekTvFocus(shape = cardShape, onEnter = onVideoClick)
            .clickable(onClick = onVideoClick)
            .liquidGlass(cardShape, borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Left
        VideoThumbnail(
            id = video.id,
            duration = video.duration,
            thumbnailUrl = video.thumbnailUrl,
            isWatched = video.isWatched,
            playbackProgress = video.playbackProgress,
            modifier = Modifier
                .width(128.dp)
                .height(78.dp)
        )

        // Text Right
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.channel.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onChannelClick != null) {
                        Text(
                            text = video.channel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .sleekTvFocus(shape = RoundedCornerShape(8.dp), scaleAmount = 1.18f, onEnter = onChannelClick)
                                .clickable(onClick = onChannelClick)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = video.channel,
                            fontSize = 10.sp,
                            color = GreyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // Quick mini controls
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier.size(24.dp)
                        .sleekTvFocus(CircleShape, scaleAmount = 1.18f)
                        .testTag("quick_bookmark_${video.id}")
                ) {
                    Icon(
                        imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Закладка",
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                if (!isContainer) {
                    IconButton(
                        onClick = onDownloadToggle,
                        modifier = Modifier.size(24.dp)
                            .sleekTvFocus(CircleShape, scaleAmount = 1.18f)
                            .testTag("quick_download_${video.id}")
                    ) {
                        Icon(
                            imageVector = if (video.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Скачать",
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (video.isDownloaded && onSaveToDeviceClick != null) {
                        IconButton(
                            onClick = onSaveToDeviceClick,
                            modifier = Modifier.size(24.dp)
                                .sleekTvFocus(CircleShape, scaleAmount = 1.18f)
                                .testTag("save_to_device_${video.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = "Сохранить на устройство",
                                tint = Primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                            .sleekTvFocus(CircleShape, scaleAmount = 1.18f)
                            .testTag("delete_item_${video.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить из списка",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }


            }
        }
    }
}
