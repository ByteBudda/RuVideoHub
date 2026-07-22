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
import androidx.compose.material.icons.automirrored.filled.*
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
    val isLargeCardsMode by viewModel.isLargeCardsMode.collectAsStateWithLifecycle()

    val mobileColsSetting = com.example.ui.screens.LocalMobileGridColumns.current
    val tvVideoColsSetting = com.example.ui.screens.LocalTvVideoGridColumns.current
    val cols = if (isTvOptimized) tvVideoColsSetting else mobileColsSetting

    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(BookmarkSort.DATE_ADDED) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }


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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
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
                    text = "Всего закладок: ${bookmarkedVideos.size}  •  Длительность: $totalDurationText",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
                        imageVector = Icons.AutoMirrored.Filled.Sort,
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
                    it.duration != "ПЛЕЙЛИСТ" &&
                    it.duration != com.example.utils.VideoType.CHANNEL &&
                    it.duration != com.example.utils.VideoType.FOLDER &&
                    it.duration != com.example.utils.VideoType.SERIES &&
                    it.duration != com.example.utils.VideoType.PLAYLIST
                }
            }
            val playlists = remember(filteredAndSorted) {
                filteredAndSorted.filter { 
                    it.duration == "ПЛЕЙЛИСТ" || it.duration == com.example.utils.VideoType.PLAYLIST 
                }
            }
            val tvSeries = remember(filteredAndSorted) {
                filteredAndSorted.filter { 
                    it.duration == "СЕРИАЛ" || it.duration == com.example.utils.VideoType.SERIES 
                }
            }
            val channels = remember(filteredAndSorted) {
                filteredAndSorted.filter { 
                    it.duration == "КАНАЛ" || it.duration == com.example.utils.VideoType.CHANNEL 
                }
            }
            val subcategories = remember(filteredAndSorted) {
                filteredAndSorted.filter { 
                    it.duration == "ПАПКА" || it.duration == "КАТАЛОГ" || it.duration == com.example.utils.VideoType.FOLDER 
                }
            }

            val tabsList = listOf(
                "Видео" to videos,
                "Плейлисты" to playlists,
                "Сериалы" to tvSeries,
                "Каналы" to channels,
                "Подкатегории" to subcategories
            )

            // Horizontal Tab Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabsList.forEachIndexed { index, (title, items) ->
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
                            text = "$title (${items.size})",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val currentTabItems = tabsList.getOrNull(selectedTab)?.second ?: emptyList()

            if (currentTabItems.isEmpty()) {
                val (emptyTitle, emptyHint) = when (selectedTab) {
                    0 -> "Нет избранных видео" to "Вы можете добавить видео в закладки, нажав на иконку закладок."
                    1 -> "Нет сохраненных плейлистов" to "Добавляйте плейлисты в избранное для быстрого доступа."
                    2 -> "Нет сохраненных сериалов" to "Здесь будут отображаться избранные сериалы и телешоу."
                    3 -> "Нет отслеживаемых каналов" to "Добавляйте интересные каналы в избранное."
                    else -> "Нет сохраненных подкатегорий" to "Здесь будут сохраненные разделы и папки каталога."
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = emptyTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = emptyHint,
                            fontSize = 13.sp,
                            color = GreyText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .mouseDragScrollable(listState, isVertical = true),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (cols > 1) {
                        val chunked = currentTabItems.chunked(cols)
                        items(chunked, key = { row -> row.firstOrNull()?.id ?: row.hashCode() }) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { saved ->
                                    val videoRuntime = Video(
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
                                        isBookmarked = true,
                                        pageUrl = saved.pageUrl,
                                        originType = saved.originType,
                                        originId = saved.originId,
                                        originTitle = saved.originTitle,
                                        authorId = saved.authorId,
                                        authorAvatarUrl = saved.authorAvatarUrl
                                    )
                                    com.example.ui.screens.home.components.SleekVideoGridItem(
                                        video = videoRuntime,
                                        onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                        onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                        onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
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
                        if (isLargeCardsMode) {
                            items(currentTabItems, key = { it.id }) { saved ->
                                val videoRuntime = Video(
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
                                    isBookmarked = true,
                                    pageUrl = saved.pageUrl,
                                    originType = saved.originType,
                                    originId = saved.originId,
                                    originTitle = saved.originTitle,
                                    authorId = saved.authorId,
                                    authorAvatarUrl = saved.authorAvatarUrl
                                )
                                com.example.ui.screens.home.components.HeroVideoCard(
                                    video = videoRuntime,
                                    onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                    onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                    onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                    isDark = isDarkTheme,
                                    isTvOptimized = isTvOptimized,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            items(currentTabItems, key = { it.id }) { saved ->
                                val videoRuntime = Video(
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
                                    isBookmarked = true,
                                    pageUrl = saved.pageUrl,
                                    originType = saved.originType,
                                    originId = saved.originId,
                                    originTitle = saved.originTitle,
                                    authorId = saved.authorId,
                                    authorAvatarUrl = saved.authorAvatarUrl
                                )
                                com.example.ui.screens.home.components.SecondaryVideoItemRow(
                                    video = videoRuntime,
                                    onVideoClick = { viewModel.selectVideo(videoRuntime) },
                                    onDownloadToggle = { viewModel.toggleDownload(videoRuntime) },
                                    onBookmarkToggle = { viewModel.toggleBookmark(videoRuntime) },
                                    isDark = isDarkTheme,
                                    isTvOptimized = isTvOptimized,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
