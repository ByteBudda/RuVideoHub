package com.example.ui.screens.library

import com.example.viewmodel.*
import androidx.compose.foundation.*
import com.example.ui.screens.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SavedVideo
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.liquidGlass
import com.example.viewmodel.VideoViewModel
import com.example.ui.screens.home.components.VideoThumbnail
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun RecentsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recentVideos by viewModel.recentSavedVideos.collectAsStateWithLifecycle()
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    // 0 = Видео, 1 = Сериалы и плейлисты
    var selectedTab by remember { mutableIntStateOf(0) }

    val allRecentVideosRuntime = remember(recentVideos) {
        recentVideos
            .filter { saved ->
                saved.duration != com.example.utils.VideoType.PLAYLIST &&
                saved.duration != "ПЛЕЙЛИСТ" &&
                saved.duration != com.example.utils.VideoType.SERIES &&
                saved.duration != "СЕРИАЛ"
            }
            .map { saved ->
                val progress = if (saved.lastDuration > 0L) {
                    (saved.lastProgress.toFloat() / saved.lastDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
                Video(
                    id = saved.id,
                    title = saved.title,
                    channel = saved.channel,
                    views = saved.views,
                    timeAgo = saved.timeAgo,
                    duration = saved.duration,
                    isPro = saved.isPro,
                    category = saved.category,
                    description = saved.description ?: "",
                    thumbnailUrl = saved.thumbnailUrl,
                    isDownloaded = saved.isDownloaded,
                    isBookmarked = saved.isBookmarked,
                    isWatched = progress >= 0.80f || saved.isWatched,
                    playbackProgress = progress,
                    authorId = saved.authorId,
                    authorAvatarUrl = saved.authorAvatarUrl,
                    originType = saved.originType,
                    originId = saved.originId,
                    originTitle = saved.originTitle,
                    pageUrl = saved.pageUrl,
                    lastProgress = saved.lastProgress,
                    lastDuration = saved.lastDuration
                )
            }
    }

    val playlistAndSeriesContainers = remember(recentVideos) {
        val containers = mutableListOf<Video>()
        val seenContainerIds = mutableSetOf<String>()

        recentVideos.forEach { saved ->
            val isSavedContainer = saved.duration == com.example.utils.VideoType.PLAYLIST ||
                    saved.duration == "ПЛЕЙЛИСТ" ||
                    saved.duration == com.example.utils.VideoType.SERIES ||
                    saved.duration == "СЕРИАЛ"

            if (isSavedContainer) {
                if (!seenContainerIds.contains(saved.id)) {
                    seenContainerIds.add(saved.id)
                    containers.add(
                        Video(
                            id = saved.id,
                            title = saved.title,
                            channel = saved.channel,
                            views = saved.views,
                            timeAgo = saved.timeAgo,
                            duration = saved.duration,
                            isPro = saved.isPro,
                            category = saved.category,
                            description = saved.description ?: if (saved.duration == com.example.utils.VideoType.SERIES || saved.duration == "СЕРИАЛ") "Сериал" else "Плейлист",
                            thumbnailUrl = saved.thumbnailUrl,
                            isDownloaded = saved.isDownloaded,
                            isBookmarked = saved.isBookmarked,
                            authorId = saved.authorId,
                            authorAvatarUrl = saved.authorAvatarUrl,
                            pageUrl = saved.pageUrl
                        )
                    )
                }
            } else {
                val oType = saved.originType
                val oId = saved.originId
                if (!oType.isNullOrBlank() && !oId.isNullOrBlank()) {
                    val isSeries = oType == com.example.utils.VideoType.SERIES || oType == "СЕРИАЛ"
                    val isPlaylist = oType == com.example.utils.VideoType.PLAYLIST || oType == "ПЛЕЙЛИСТ"
                    if (isSeries || isPlaylist) {
                        if (!seenContainerIds.contains(oId)) {
                            seenContainerIds.add(oId)
                            val containerType = if (isSeries) com.example.utils.VideoType.SERIES else com.example.utils.VideoType.PLAYLIST
                            val containerTitle = if (!saved.originTitle.isNullOrBlank()) saved.originTitle!! else saved.channel
                            containers.add(
                                Video(
                                    id = oId,
                                    title = containerTitle,
                                    channel = saved.channel,
                                    views = "",
                                    timeAgo = saved.timeAgo,
                                    duration = containerType,
                                    isPro = false,
                                    category = saved.category,
                                    description = if (isSeries) "Сериал" else "Плейлист",
                                    thumbnailUrl = saved.thumbnailUrl,
                                    isDownloaded = false,
                                    isBookmarked = false,
                                    authorId = saved.authorId,
                                    authorAvatarUrl = saved.authorAvatarUrl,
                                    pageUrl = saved.pageUrl
                                )
                            )
                        }
                    }
                }
            }
        }
        containers
    }

    val filteredVideos = remember(allRecentVideosRuntime, playlistAndSeriesContainers, selectedTab) {
        when (selectedTab) {
            1 -> playlistAndSeriesContainers
            else -> allRecentVideosRuntime
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Недавние",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "История просмотров",
                    fontSize = 11.sp,
                    color = GreyText
                )
            }

            if (filteredVideos.isNotEmpty()) {
                TextButton(
                    onClick = {
                        filteredVideos.forEach { videoRuntime ->
                            viewModel.deleteRecentItem(videoRuntime)
                        }
                        android.widget.Toast.makeText(context, "История очищена", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.sleekTvFocus(RoundedCornerShape(8.dp))
                ) {
                    Text("Очистить", color = Primary, fontSize = 12.sp)
                }
            }
        }

        // Sub-tabs / Filter Chips inside Recents
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("Видео", "Сериалы и плейлисты")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) Primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .sleekTvFocus(
                            shape = RoundedCornerShape(20.dp),
                            onEnter = { selectedTab = index }
                        )
                        .clickable { selectedTab = index }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (filteredVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = GreyText.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val emptyTitle = when (selectedTab) {
                        1 -> "Нет просмотренных сериалов или плейлистов"
                        else -> "Нет просмотренных видео"
                    }
                    val emptyHint = when (selectedTab) {
                        1 -> "Здесь будут отображаться сериалы и плейлисты, в которых вы посмотрели хотя бы одно видео."
                        else -> "Отдельные просмотренные видео будут появляться здесь."
                    }
                    Text(
                        text = emptyTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = emptyHint,
                        fontSize = 11.sp,
                        color = GreyText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val isHistoryLargeCardsMode by viewModel.isHistoryLargeCardsMode.collectAsStateWithLifecycle()
            val tvVideoColsSetting = com.example.ui.screens.LocalTvVideoGridColumns.current
            val mobileColsSetting = com.example.ui.screens.LocalMobileGridColumns.current
            val cols = if (isTvOptimized) tvVideoColsSetting else mobileColsSetting

            val onItemDelete = { videoRuntime: Video ->
                viewModel.deleteRecentItem(videoRuntime)
                recentVideos.filter { it.originId == videoRuntime.id || it.id == videoRuntime.id }.forEach { saved ->
                    viewModel.deleteRecentItem(
                        Video(
                            id = saved.id, title = saved.title, channel = saved.channel,
                            views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                            isPro = saved.isPro, category = saved.category, description = saved.description ?: "",
                            thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = saved.isBookmarked
                        )
                    )
                }
                android.widget.Toast.makeText(context, "Удалено из истории", android.widget.Toast.LENGTH_SHORT).show()
            }

            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .mouseDragScrollable(listState, isVertical = true),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 80.dp)
            ) {
                if (isTvOptimized) {
                    val chunked = filteredVideos.chunked(cols)
                    items(chunked, key = { row -> row.firstOrNull()?.id ?: row.hashCode() }) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { videoRuntime ->
                                com.example.ui.screens.home.components.SleekVideoGridItem(
                                    video = videoRuntime,
                                    onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                    onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                    onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                    onDeleteClick = { onItemDelete(videoRuntime) },
                                    isDark = isDarkTheme,
                                    isTvOptimized = isTvOptimized,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    if (isHistoryLargeCardsMode) {
                        items(filteredVideos, key = { it.id }) { videoRuntime ->
                            com.example.ui.screens.home.components.HeroVideoCard(
                                video = videoRuntime,
                                onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                onDeleteClick = { onItemDelete(videoRuntime) },
                                isDark = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        items(filteredVideos, key = { it.id }) { videoRuntime ->
                            com.example.ui.screens.home.components.SecondaryVideoItemRow(
                                video = videoRuntime,
                                onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                onDeleteClick = { onItemDelete(videoRuntime) },
                                isDark = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
