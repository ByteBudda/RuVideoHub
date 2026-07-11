package com.example.ui.screens

import androidx.compose.foundation.*
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 80.dp)
            ) {
                items(recentVideos, key = { it.id }) { saved ->
                    val videoRuntime = Video(
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

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sleekTvFocus(
                                shape = RoundedCornerShape(16.dp), 
                                onEnter = { viewModel.selectVideo(videoRuntime) },
                                onLongEnter = { 
                                    viewModel.deleteRecentItem(videoRuntime)
                                    android.widget.Toast.makeText(context, "Удалено из истории", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                            .then(
                                if (isTvOptimized) Modifier
                                else Modifier.combinedClickable(
                                    onClick = { viewModel.selectVideo(videoRuntime) },
                                    onLongClick = { 
                                        viewModel.deleteRecentItem(videoRuntime)
                                        android.widget.Toast.makeText(context, "Удалено из истории", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VideoThumbnail(
                                id = saved.id,
                                duration = saved.duration,
                                thumbnailUrl = saved.thumbnailUrl,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(60.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = saved.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = saved.channel,
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                            }

                            IconButton(
                                onClick = { 
                                    viewModel.deleteRecentItem(videoRuntime)
                                    android.widget.Toast.makeText(context, "Удалено из истории", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                                    .sleekTvFocus(CircleShape)
                                    .testTag("delete_recent_${saved.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить из истории",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val downloadedVideos by viewModel.downloadedSavedVideos.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
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
            LazyColumn(
                modifier = Modifier.weight(1f),
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

                items(downloadedVideos, key = { it.id }) { saved ->
                    val videoRuntime = Video(
                        id = saved.id,
                        title = saved.title,
                        channel = saved.channel,
                        views = saved.views,
                        timeAgo = saved.timeAgo,
                        duration = saved.duration,
                        isPro = saved.isPro,
                        category = saved.category,
                        description = "Скачанное видео.",
                        thumbnailUrl = saved.thumbnailUrl,
                        isDownloaded = true,
                        isBookmarked = saved.isBookmarked
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sleekTvFocus(
                                shape = RoundedCornerShape(16.dp), 
                                onEnter = { viewModel.selectVideo(videoRuntime) },
                                onLongEnter = { viewModel.toggleDownload(videoRuntime) }
                            )
                            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                            .then(
                                if (isTvOptimized) Modifier
                                else Modifier.combinedClickable(
                                    onClick = { viewModel.selectVideo(videoRuntime) },
                                    onLongClick = { viewModel.toggleDownload(videoRuntime) }
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VideoThumbnail(
                                id = saved.id,
                                duration = saved.duration,
                                thumbnailUrl = saved.thumbnailUrl,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(60.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = saved.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = saved.channel,
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                            }

                            IconButton(
                                onClick = { 
                                    viewModel.toggleDownload(videoRuntime)
                                },
                                modifier = Modifier.size(32.dp)
                                    .sleekTvFocus(CircleShape)
                                    .testTag("delete_download_${saved.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить загрузку",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class BookmarkSort(val label: String) {
    DATE_ADDED("Сначала новые"),
    TITLE("По названию"),
    DURATION("По длительности")
}

private fun parseDurationToSeconds(duration: String): Int {
    val parts = duration.split(":")
    return try {
        if (parts.size == 2) {
            val minutes = parts[0].toIntOrNull() ?: 0
            val seconds = parts[1].toIntOrNull() ?: 0
            minutes * 60 + seconds
        } else if (parts.size == 3) {
            val hours = parts[0].toIntOrNull() ?: 0
            val minutes = parts[1].toIntOrNull() ?: 0
            val seconds = parts[2].toIntOrNull() ?: 0
            hours * 3600 + minutes * 60 + seconds
        } else {
            0
        }
    } catch (e: Exception) {
        0
    }
}

@Composable
fun LibraryTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val bookmarkedVideos by viewModel.bookmarkedSavedVideos.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(BookmarkSort.DATE_ADDED) }
    var showSortMenu by remember { mutableStateOf(false) }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Избранное",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Просмотр сохраненного контента и избранных видео",
                    fontSize = 12.sp,
                    color = GreyText,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }


        }

        // Simple Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${bookmarkedVideos.size}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(text = "Закладок", fontSize = 10.sp, color = GreyText)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(SurfaceVariant)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val totalDurationText = remember(bookmarkedVideos) {
                    var totalSeconds = 0
                    for (video in bookmarkedVideos) {
                        val durationStr = video.duration.trim()
                        if (durationStr.isEmpty()) continue
                        try {
                            val parts = durationStr.split(":")
                            if (parts.size == 3) {
                                val hours = parts[0].toIntOrNull() ?: 0
                                val minutes = parts[1].toIntOrNull() ?: 0
                                val seconds = parts[2].toIntOrNull() ?: 0
                                totalSeconds += hours * 3600 + minutes * 60 + seconds
                            } else if (parts.size == 2) {
                                val minutes = parts[0].toIntOrNull() ?: 0
                                val seconds = parts[1].toIntOrNull() ?: 0
                                totalSeconds += minutes * 60 + seconds
                            } else {
                                val clean = durationStr.replace(Regex("[^0-9]"), "")
                                val num = clean.toIntOrNull() ?: 0
                                if (durationStr.contains("ч", ignoreCase = true) || durationStr.contains("h", ignoreCase = true)) {
                                    totalSeconds += num * 3600
                                } else {
                                    totalSeconds += num * 60
                                }
                            }
                        } catch (e: Exception) {
                            totalSeconds += 30 * 60
                        }
                    }
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    if (hours > 0) {
                        if (minutes > 0) {
                            "$hours ч $minutes мин"
                        } else {
                            "$hours ч"
                        }
                    } else {
                        "$minutes мин"
                    }
                }
                Text(
                    text = totalDurationText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(text = "Время просмотра", fontSize = 10.sp, color = GreyText)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search & Filter Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск в избранном...", fontSize = 12.sp, color = GreyText) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GreyText,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить",
                                tint = GreyText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("bookmark_search_input")
            )

            Box {
                Button(
                    onClick = { showSortMenu = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .sleekTvFocus(RoundedCornerShape(12.dp))
                        .testTag("bookmark_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Сортировка",
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sortOrder.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    BookmarkSort.values().forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label, fontSize = 13.sp) },
                            onClick = {
                                sortOrder = order
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortOrder == order) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (bookmarkedVideos.isEmpty()) {
            EmptyStateContainer(
                title = "Список избранного пуст",
                hint = "Вы можете добавить медиа в этот список, нажав на кнопку закладок на карточке видео."
            )
        } else {
            val filteredAndSorted = remember(bookmarkedVideos, searchQuery, sortOrder) {
                val filtered = if (searchQuery.isBlank()) {
                    bookmarkedVideos
                } else {
                    bookmarkedVideos.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.channel.contains(searchQuery, ignoreCase = true)
                    }
                }
                
                when (sortOrder) {
                    BookmarkSort.DATE_ADDED -> filtered.sortedByDescending { it.savedAt }
                    BookmarkSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
                    BookmarkSort.DURATION -> filtered.sortedByDescending { parseDurationToSeconds(it.duration) }
                }
            }

            val videos = remember(filteredAndSorted) {
                filteredAndSorted.filter { 
                    it.duration != "КАНАЛ" && 
                    it.duration != "ПАПКА" && 
                    it.duration != "КАТАЛОГ" && 
                    it.duration != "СЕРИАЛ" && 
                    it.duration != "ПЛЕЙЛИСТ" 
                }
            }
            val channels = remember(filteredAndSorted) {
                filteredAndSorted.filter { it.duration == "КАНАЛ" }
            }
            val tvSeries = remember(filteredAndSorted) {
                filteredAndSorted.filter { it.duration == "СЕРИАЛ" }
            }
            val subcategories = remember(filteredAndSorted) {
                filteredAndSorted.filter { 
                    it.duration == "ПАПКА" || 
                    it.duration == "КАТАЛОГ"
                }
            }
            val playlists = remember(filteredAndSorted) {
                filteredAndSorted.filter { it.duration == "ПЛЕЙЛИСТ" }
            }

            var videosExpanded by remember { mutableStateOf(true) }
            var channelsExpanded by remember { mutableStateOf(true) }
            var tvSeriesExpanded by remember { mutableStateOf(true) }
            var subcategoriesExpanded by remember { mutableStateOf(true) }
            var playlistsExpanded by remember { mutableStateOf(true) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. VIDEOS
                if (videos.isNotEmpty()) {
                    item {
                        BookmarkSectionHeader(
                            title = "Видео",
                            count = videos.size,
                            icon = Icons.Default.VideoLibrary,
                            isExpanded = videosExpanded,
                            onClick = { videosExpanded = !videosExpanded }
                        )
                    }
                    if (videosExpanded) {
                        items(videos, key = { "vid_" + it.id }) { saved ->
                            BookmarkItemRow(
                                saved = saved,
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                onDelete = {
                                    val videoRuntime = Video(
                                        id = saved.id, title = saved.title, channel = saved.channel,
                                        views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                                        isPro = saved.isPro, category = saved.category, description = "Сохраненный элемент.",
                                        thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = true
                                    )
                                    viewModel.toggleBookmark(videoRuntime)
                                }
                            )
                        }
                    }
                }

                // 2. CHANNELS
                if (channels.isNotEmpty()) {
                    item {
                        BookmarkSectionHeader(
                            title = "Каналы",
                            count = channels.size,
                            icon = Icons.Default.Tv,
                            isExpanded = channelsExpanded,
                            onClick = { channelsExpanded = !channelsExpanded }
                        )
                    }
                    if (channelsExpanded) {
                        items(channels, key = { "chan_" + it.id }) { saved ->
                            BookmarkItemRow(
                                saved = saved,
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                onDelete = {
                                    val videoRuntime = Video(
                                        id = saved.id, title = saved.title, channel = saved.channel,
                                        views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                                        isPro = saved.isPro, category = saved.category, description = "Сохраненный элемент.",
                                        thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = true
                                    )
                                    viewModel.toggleBookmark(videoRuntime)
                                }
                            )
                        }
                    }
                }

                // 3. TV SERIES
                if (tvSeries.isNotEmpty()) {
                    item {
                        BookmarkSectionHeader(
                            title = "Сериалы",
                            count = tvSeries.size,
                            icon = Icons.Default.Tv,
                            isExpanded = tvSeriesExpanded,
                            onClick = { tvSeriesExpanded = !tvSeriesExpanded }
                        )
                    }
                    if (tvSeriesExpanded) {
                        items(tvSeries, key = { "series_" + it.id }) { saved ->
                            BookmarkItemRow(
                                saved = saved,
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                onDelete = {
                                    val videoRuntime = Video(
                                        id = saved.id, title = saved.title, channel = saved.channel,
                                        views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                                        isPro = saved.isPro, category = saved.category, description = "Сохраненный элемент.",
                                        thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = true
                                    )
                                    viewModel.toggleBookmark(videoRuntime)
                                }
                            )
                        }
                    }
                }

                // 4. SUBCATEGORIES
                if (subcategories.isNotEmpty()) {
                    item {
                        BookmarkSectionHeader(
                            title = "Подкатегории",
                            count = subcategories.size,
                            icon = Icons.Default.Folder,
                            isExpanded = subcategoriesExpanded,
                            onClick = { subcategoriesExpanded = !subcategoriesExpanded }
                        )
                    }
                    if (subcategoriesExpanded) {
                        items(subcategories, key = { "sub_" + it.id }) { saved ->
                            BookmarkItemRow(
                                saved = saved,
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                onDelete = {
                                    val videoRuntime = Video(
                                        id = saved.id, title = saved.title, channel = saved.channel,
                                        views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                                        isPro = saved.isPro, category = saved.category, description = "Сохраненный элемент.",
                                        thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = true
                                    )
                                    viewModel.toggleBookmark(videoRuntime)
                                }
                            )
                        }
                    }
                }

                // 5. PLAYLISTS
                if (playlists.isNotEmpty()) {
                    item {
                        BookmarkSectionHeader(
                            title = "Плейлисты",
                            count = playlists.size,
                            icon = Icons.Default.PlaylistPlay,
                            isExpanded = playlistsExpanded,
                            onClick = { playlistsExpanded = !playlistsExpanded }
                        )
                    }
                    if (playlistsExpanded) {
                        items(playlists, key = { "play_" + it.id }) { saved ->
                            BookmarkItemRow(
                                saved = saved,
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                isTvOptimized = isTvOptimized,
                                onDelete = {
                                    val videoRuntime = Video(
                                        id = saved.id, title = saved.title, channel = saved.channel,
                                        views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                                        isPro = saved.isPro, category = saved.category, description = "Сохраненный элемент.",
                                        thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = true
                                    )
                                    viewModel.toggleBookmark(videoRuntime)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkSectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = GreyText,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "Развернуть/Свернуть",
            tint = GreyText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookmarkItemRow(
    saved: SavedVideo,
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean,
    onDelete: () -> Unit
) {
    val isChannel = saved.duration == "КАНАЛ"
    val isTvSeries = saved.duration == "СЕРИАЛ"
    val isSubcategory = saved.duration == "ПАПКА" || saved.duration == "КАТАЛОГ"
    val isPlaylist = saved.duration == "ПЛЕЙЛИСТ"

    val videoRuntime = remember(saved) {
        Video(
            id = saved.id,
            title = saved.title,
            channel = saved.channel,
            views = saved.views,
            timeAgo = saved.timeAgo,
            duration = saved.duration,
            isPro = saved.isPro,
            category = saved.category,
            description = "Сохраненный элемент.",
            thumbnailUrl = saved.thumbnailUrl,
            isDownloaded = saved.isDownloaded,
            isBookmarked = true
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .sleekTvFocus(
                shape = RoundedCornerShape(16.dp), 
                onEnter = { viewModel.selectVideo(videoRuntime) },
                onLongEnter = onDelete
            )
            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
            .then(
                if (isTvOptimized) Modifier
                else Modifier.combinedClickable(
                    onClick = { viewModel.selectVideo(videoRuntime) },
                    onLongClick = onDelete
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isChannel) {
                    if (!saved.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = saved.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                        )
                    } else {
                        val firstLetter = saved.title.firstOrNull()?.toString() ?: "К"
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.2f))
                                .border(1.dp, Primary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = firstLetter,
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else if (isPlaylist) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (isTvSeries) {
                    if (!saved.thumbnailUrl.isNullOrBlank()) {
                        VideoThumbnail(
                            id = saved.id,
                            duration = saved.duration,
                            thumbnailUrl = saved.thumbnailUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else if (isSubcategory) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    VideoThumbnail(
                        id = saved.id,
                        duration = saved.duration,
                        thumbnailUrl = saved.thumbnailUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = saved.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val typeLabel = when {
                        isChannel -> "Канал"
                        isPlaylist -> "Плейлист"
                        isTvSeries -> "Сериал"
                        isSubcategory -> {
                            when (saved.duration) {
                                "КАТАЛОГ" -> "Каталог"
                                else -> "Подкатегория"
                            }
                        }
                        else -> null
                    }

                    if (typeLabel != null) {
                        Text(
                            text = typeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (!isChannel && saved.channel.isNotBlank()) {
                        Text(
                            text = saved.channel,
                            fontSize = 10.sp,
                            color = GreyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .sleekTvFocus(CircleShape)
                    .testTag("delete_bookmark_${saved.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Удалить из закладок",
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStateContainer(
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = GreyText.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hint,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = GreyText,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = GreyText.copy(alpha = 0.5f),
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ничего не найдено",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "По запросу \"$query\" совпадений не обнаружено. Пожалуйста, измените запрос или смените вкладку фильтра.",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = GreyText,
            lineHeight = 16.sp
        )
    }
}
