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

            if (recentVideos.isNotEmpty()) {
                TextButton(
                    onClick = {
                        // Clear all history
                        recentVideos.forEach {
                            viewModel.deleteRecentItem(Video(
                                id = it.id, title = it.title, channel = it.channel,
                                views = it.views, timeAgo = it.timeAgo, duration = it.duration,
                                isPro = it.isPro, category = it.category, description = "", thumbnailUrl = it.thumbnailUrl,
                                isDownloaded = it.isDownloaded, isBookmarked = it.isBookmarked
                            ))
                        }
                    },
                    modifier = Modifier.sleekTvFocus(RoundedCornerShape(8.dp))
                ) {
                    Text("Очистить всё", color = Primary, fontSize = 12.sp)
                }
            }
        }

        if (recentVideos.isEmpty()) {
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
                    Text(
                        text = "История пока пуста",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Здесь будут отображаться видео, которые вы запускали на просмотр.",
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

            val recentVideosRuntime = recentVideos.map { saved ->
                Video(
                    id = saved.id,
                    title = saved.title,
                    channel = saved.channel,
                    views = saved.views,
                    timeAgo = saved.timeAgo,
                    duration = saved.duration,
                    isPro = saved.isPro,
                    category = saved.category,
                    description = "Просмотрено недавно.",
                    thumbnailUrl = saved.thumbnailUrl,
                    isDownloaded = saved.isDownloaded,
                    isBookmarked = saved.isBookmarked
                )
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
                    val chunked = recentVideosRuntime.chunked(cols)
                    items(chunked, key = { it.hashCode() }) { rowItems ->
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
                                    onDeleteClick = {
                                        viewModel.deleteRecentItem(videoRuntime)
                                        android.widget.Toast.makeText(context, "Удалено из истории", android.widget.Toast.LENGTH_SHORT).show()
                                    },
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
                        items(recentVideosRuntime, key = { it.id }) { videoRuntime ->
                            com.example.ui.screens.home.components.HeroVideoCard(
                                video = videoRuntime,
                                onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                isDark = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).combinedClickable(
                                    onClick = { viewModel.selectVideo(videoRuntime) },
                                    onLongClick = {
                                        viewModel.deleteRecentItem(videoRuntime)
                                        android.widget.Toast.makeText(context, "Удалено из истории", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            )
                        }
                    } else {
                        items(recentVideosRuntime, key = { it.id }) { videoRuntime ->
                            com.example.ui.screens.home.components.SecondaryVideoItemRow(
                                video = videoRuntime,
                                onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                onDeleteClick = {
                                    viewModel.deleteRecentItem(videoRuntime)
                                    android.widget.Toast.makeText(context, "Удалено из истории", android.widget.Toast.LENGTH_SHORT).show()
                                },
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
