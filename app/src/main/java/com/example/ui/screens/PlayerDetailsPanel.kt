package com.example.ui.screens

import com.example.ui.screens.player.*

import com.example.viewmodel.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Video
import com.example.utils.VideoType
import com.example.viewmodel.VideoViewModel
import com.example.ui.screens.home.components.VideoThumbnail
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.liquidGlass
import kotlinx.coroutines.launch

@Composable
fun PlayerDetailsPanel(
    video: Video,
    viewModel: VideoViewModel,
    currentEpList: List<Video>,
    context: android.content.Context,
    isDark: Boolean,
    isTvOptimized: Boolean,
    onDismiss: () -> Unit,
    showDownloadOptionsDialog: (Boolean) -> Unit
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                                duration = VideoType.CHANNEL,
                                category = video.category,
                                description = "",
                                thumbnailUrl = video.authorAvatarUrl,
                                authorAvatarUrl = video.authorAvatarUrl,
                                authorId = video.authorId,
                                authorActionUrl = video.authorActionUrl
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
        val isContainer = video.duration == VideoType.PLAYLIST ||
                video.duration == VideoType.SERIES ||
                video.duration == VideoType.CHANNEL ||
                video.duration == VideoType.FOLDER ||
                video.duration == VideoType.CATALOG ||
                video.duration == VideoType.PROMO ||
                video.duration == "ПЛЕЙЛИСТ"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download action state button
            if (!isContainer) {
                Button(
                    onClick = {
                        if (video.isDownloaded) {
                            showDownloadOptionsDialog(true)
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
            }

            // Bookmark Toggle 
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

            // Quality Selection Pill
            val isVkOrDzen = video.id.startsWith("vk_") || video.category == "VK Video" || video.category == "Дзен"
            if (!video.isDownloaded && !isContainer && !isVkOrDzen) {
                var qualityMenuExpanded by remember { mutableStateOf(false) }
                val activeVideoQuality by viewModel.activeVideoQuality.collectAsStateWithLifecycle()
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
                            text = activeVideoQuality,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = qualityMenuExpanded,
                        onDismissRequest = { qualityMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.background)
                    ) {
                        availableQualities.forEach { q ->
                            val isSelected = activeVideoQuality == q || (activeVideoQuality == "Авто" && currentQuality == "Авто" && q == "Авто") || (activeVideoQuality != "Авто" && currentQuality == q)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = q,
                                        color = if (activeVideoQuality == q || currentQuality == q) Primary else MaterialTheme.colorScheme.onBackground,
                                        fontSize = 11.sp,
                                        fontWeight = if (activeVideoQuality == q || currentQuality == q) FontWeight.Bold else FontWeight.Normal
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

            // External Player Action Trigger
            val coroutineScope = rememberCoroutineScope()
            var isFetchingExternal by remember { mutableStateOf(false) }

            if (!isContainer && !isVkOrDzen && !video.isDownloaded) {
                IconButton(
                    onClick = {
                        if (isFetchingExternal) return@IconButton
                        isFetchingExternal = true
                        coroutineScope.launch {
                            try {
                                val url = viewModel.fetchHlsStreamUrl(video.id, "Авто")
                                if (url != null && url.isNotBlank()) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(url), "video/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Открыть через..."))
                                } else {
                                    android.widget.Toast.makeText(context, "Не удалось получить ссылку на поток", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Ошибка открытия внешнего плеера", android.widget.Toast.LENGTH_SHORT).show()
                            } finally {
                                isFetchingExternal = false
                            }
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(SurfaceVariant, CircleShape)
                        .sleekTvFocus(CircleShape)
                ) {
                    if (isFetchingExternal) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Открыть во внешнем плеере",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (video.isDownloaded) {
                IconButton(
                    onClick = {
                        val downloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val offlineFile = java.io.File(downloadFolder, "${video.id}.mp4")
                        if (offlineFile.exists()) {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    offlineFile
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/mp4")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Открыть через..."))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Ошибка открытия скачанного файла", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(SurfaceVariant, CircleShape)
                        .sleekTvFocus(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Открыть во внешнем плеере",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Share action trigger
            IconButton(
                onClick = { shareVideo(context, video) },
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceVariant, CircleShape)
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

        // Advanced details description container
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .sleekTvFocus(RoundedCornerShape(16.dp), onEnter = { isDescriptionExpanded = !isDescriptionExpanded })
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
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

        // Episode context
        Text(
            text = if (currentEpList.size > 1) "Смотреть далее" else "Рекомендуем далее",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        val tvVideoColsSetting = LocalTvVideoGridColumns.current
        val isLargeCardsMode by viewModel.isLargeCardsMode.collectAsStateWithLifecycle()

        if (isTvOptimized) {
            val chunkedEpList = currentEpList.chunked(tvVideoColsSetting)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunkedEpList.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { ep ->
                            com.example.ui.screens.home.components.SleekVideoGridItem(
                                video = ep,
                                onVideoClick = { viewModel.selectVideo(ep) },
                                onDownloadToggle = { viewModel.toggleDownload(ep) },
                                onBookmarkToggle = { viewModel.toggleBookmark(ep) },
                                isDark = isDark,
                                onChannelClick = if (!ep.authorId.isNullOrBlank()) {
                                    {
                                        val channelDummy = Video(
                                            id = "channel_${ep.authorId}__${ep.authorActionUrl ?: ""}",
                                            title = ep.channel,
                                            channel = ep.channel,
                                            views = "",
                                            timeAgo = "",
                                            duration = VideoType.CHANNEL,
                                            category = ep.category,
                                            description = "",
                                            thumbnailUrl = ep.authorAvatarUrl,
                                            authorAvatarUrl = ep.authorAvatarUrl,
                                            authorId = ep.authorId,
                                            authorActionUrl = ep.authorActionUrl
                                        )
                                        viewModel.selectVideo(channelDummy)
                                    }
                                } else null,
                                isTvOptimized = isTvOptimized,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(tvVideoColsSetting - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLargeCardsMode) {
                    currentEpList.forEach { ep ->
                        com.example.ui.screens.home.components.HeroVideoCard(
                            video = ep,
                            onVideoClick = { viewModel.selectVideo(ep) },
                            onDownloadToggle = { viewModel.toggleDownload(ep) },
                            onBookmarkToggle = { viewModel.toggleBookmark(ep) },
                            isDark = isDark,
                            onChannelClick = if (!ep.authorId.isNullOrBlank()) {
                                {
                                    val channelDummy = Video(
                                        id = "channel_${ep.authorId}__${ep.authorActionUrl ?: ""}",
                                        title = ep.channel,
                                        channel = ep.channel,
                                        views = "",
                                        timeAgo = "",
                                        duration = VideoType.CHANNEL,
                                        category = ep.category,
                                        description = "",
                                        thumbnailUrl = ep.authorAvatarUrl,
                                        authorAvatarUrl = ep.authorAvatarUrl,
                                        authorId = ep.authorId,
                                        authorActionUrl = ep.authorActionUrl
                                    )
                                    viewModel.selectVideo(channelDummy)
                                }
                            } else null,
                            isTvOptimized = isTvOptimized,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    currentEpList.forEach { ep ->
                        com.example.ui.screens.home.components.SecondaryVideoItemRow(
                            video = ep,
                            onVideoClick = { viewModel.selectVideo(ep) },
                            onDownloadToggle = { viewModel.toggleDownload(ep) },
                            onBookmarkToggle = { viewModel.toggleBookmark(ep) },
                            isDark = isDark,
                            onChannelClick = if (!ep.authorId.isNullOrBlank()) {
                                {
                                    val channelDummy = Video(
                                        id = "channel_${ep.authorId}__${ep.authorActionUrl ?: ""}",
                                        title = ep.channel,
                                        channel = ep.channel,
                                        views = "",
                                        timeAgo = "",
                                        duration = VideoType.CHANNEL,
                                        category = ep.category,
                                        description = "",
                                        thumbnailUrl = ep.authorAvatarUrl,
                                        authorAvatarUrl = ep.authorAvatarUrl,
                                        authorId = ep.authorId,
                                        authorActionUrl = ep.authorActionUrl
                                    )
                                    viewModel.selectVideo(channelDummy)
                                }
                            } else null,
                            isTvOptimized = isTvOptimized,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        val isMoreLoading by viewModel.isMoreLoading.collectAsStateWithLifecycle()
        if (isMoreLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
