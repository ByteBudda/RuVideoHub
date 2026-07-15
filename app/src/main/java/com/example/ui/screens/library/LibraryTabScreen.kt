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

            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .mouseDragScrollable(listState, isVertical = true),
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
