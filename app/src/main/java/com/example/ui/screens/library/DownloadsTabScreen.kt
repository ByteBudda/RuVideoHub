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

@Composable
fun DownloadsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadedVideos by viewModel.downloadedSavedVideos.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val savingProgress by viewModel.downloadManager.savingProgress.collectAsStateWithLifecycle()
    val activeList = remember(activeDownloads) { activeDownloads.values.toList() }
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = "Загрузки",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Скачанные видео для офлайн-просмотра",
                fontSize = 11.sp,
                color = GreyText
            )
        }

        if (downloadedVideos.isEmpty() && activeList.isEmpty()) {
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
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = GreyText.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Список загрузок пуст",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Вы можете скачивать видео во время воспроизведения в плеере.",
                        fontSize = 11.sp,
                        color = GreyText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val isDownloadsLargeCardsMode by viewModel.isDownloadsLargeCardsMode.collectAsStateWithLifecycle()
            val tvVideoColsSetting = com.example.ui.screens.LocalTvVideoGridColumns.current
            val mobileColsSetting = com.example.ui.screens.LocalMobileGridColumns.current
            val cols = if (isTvOptimized) tvVideoColsSetting else mobileColsSetting

            val downloadedVideosRuntime = downloadedVideos.map { saved ->
                Video(
                    id = saved.id,
                    title = saved.title,
                    channel = saved.channel,
                    views = saved.views,
                    timeAgo = saved.timeAgo,
                    duration = saved.duration,
                    isPro = saved.isPro,
                    category = saved.category,
                    description = saved.description ?: "", thumbnailUrl = saved.thumbnailUrl,
                    isDownloaded = true,
                    isBookmarked = saved.isBookmarked, authorId = saved.authorId, authorAvatarUrl = saved.authorAvatarUrl, originType = saved.originType, originId = saved.originId, originTitle = saved.originTitle, pageUrl = saved.pageUrl
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
                if (activeList.isNotEmpty()) {
                    item {
                        Text(
                            text = "Активные загрузки",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(activeList, key = { "active_" + it.id }) { active ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    VideoThumbnail(
                                        id = active.id,
                                        duration = active.eta,
                                        thumbnailUrl = active.thumbnailUrl,
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(60.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = active.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "${active.channel} • ${active.status}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Скорость: ${active.speed} • Осталось: ${active.eta}",
                                            fontSize = 9.sp,
                                            color = GreyText
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { active.progress },
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                        IconButton(
                                            onClick = { viewModel.cancelDownload(active.id) },
                                            modifier = Modifier.size(32.dp).sleekTvFocus(CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Отменить загрузку",
                                                tint = Color.Red.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { active.progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Сохраненные видео ( " + downloadedVideos.size + " )",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                if (isTvOptimized) {
                    val chunked = downloadedVideosRuntime.chunked(cols)
                    items(chunked, key = { "downloads_" + it.hashCode() }) { rowItems ->
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
                                    onSaveToDeviceClick = {
                                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                                            viewModel.saveToDevice(videoRuntime, context) { _, message ->
                                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Эта функция требует Android 10 и выше.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    savingProgress = savingProgress,
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
                    if (isDownloadsLargeCardsMode) {
                        items(downloadedVideosRuntime, key = { "large_" + it.id }) { videoRuntime ->
                            com.example.ui.screens.home.components.HeroVideoCard(
                                video = videoRuntime,
                                onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                onSaveToDeviceClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                                        viewModel.saveToDevice(videoRuntime, context) { _, message ->
                                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Эта функция требует Android 10 и выше.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                savingProgress = savingProgress,
                                isDark = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        items(downloadedVideosRuntime, key = { "list_" + it.id }) { videoRuntime ->
                            com.example.ui.screens.home.components.SecondaryVideoItemRow(
                                video = videoRuntime,
                                onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                onDeleteClick = null,
                                onSaveToDeviceClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                                        viewModel.saveToDevice(videoRuntime, context) { _, message ->
                                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Эта функция требует Android 10 и выше.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                savingProgress = savingProgress,
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
