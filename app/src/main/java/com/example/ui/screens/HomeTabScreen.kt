package com.example.ui.screens

import com.example.ui.screens.player.*

import com.example.viewmodel.*
import com.example.ui.screens.library.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.data.SavedVideo
import com.example.data.Video
import com.example.data.SearchHistory
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.liquidGlass
import com.example.viewmodel.VideoViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import kotlinx.coroutines.delay

// Home Component & Utility Imports
import com.example.ui.screens.home.components.*
import com.example.ui.screens.home.utils.*

@Composable
fun HomeTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchSource by viewModel.searchSource.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val isChannelView by viewModel.isChannelView.collectAsStateWithLifecycle()
    val channelActiveTab by viewModel.channelActiveTab.collectAsStateWithLifecycle()
    val channelVideos by viewModel.channelVideos.collectAsStateWithLifecycle()
    val channelPlaylists by viewModel.channelPlaylists.collectAsStateWithLifecycle()
    val isLoadingPlaylists by viewModel.isLoadingPlaylists.collectAsStateWithLifecycle()

    val currentVideos = if (isChannelView) {
        if (channelActiveTab == "Видео") channelVideos else channelPlaylists
    } else {
        filteredVideos
    }
    
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()
    val continueWatchingVideos by viewModel.continueWatchingVideos.collectAsStateWithLifecycle()

    val initialFocusRequester = remember { FocusRequester() }
    var isVoiceSearchActive by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        val feedTabs by viewModel.feedTabs.collectAsStateWithLifecycle()
        val selectedFeedTab by viewModel.selectedFeedTab.collectAsStateWithLifecycle()
        val selectedSubfolderName by viewModel.selectedSubfolderName.collectAsStateWithLifecycle()
        val currentSubfolderVideo by viewModel.currentSubfolderVideo.collectAsStateWithLifecycle()
        val currentChannelVideo by viewModel.currentChannelVideo.collectAsStateWithLifecycle()

        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val isMoreLoading by viewModel.isMoreLoading.collectAsStateWithLifecycle()

        val isCurrentTabLoading = if (isChannelView) {
            if (channelActiveTab == "Видео") isLoading else isLoadingPlaylists
        } else {
            isLoading
        }

        LaunchedEffect(isTvOptimized, isCurrentTabLoading, selectedFeedTab, selectedSubfolderName, isChannelView) {
            if (isTvOptimized && !isCurrentTabLoading) {
                delay(250)
                try {
                    initialFocusRequester.requestFocus()
                } catch (e: Exception) {}
            }
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val isLargeCardsMode by viewModel.isLargeCardsMode.collectAsStateWithLifecycle()
        
        val shouldLoadMore by remember {
            derivedStateOf {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    ?: return@derivedStateOf false
                lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
            }
        }

        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) {
                viewModel.loadNextPage()
            }
        }

        val catalogExpandedState = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

        val groupedCatalogItems = remember(currentVideos) { com.example.utils.VideoLayoutHelper.groupCatalogItems(currentVideos) }

        // Smart Classification Helpers
        val isSeriesItem = remember {
            { video: Video ->
                video.duration == com.example.utils.VideoType.SERIES
            }
        }

        val isChannelItem = remember {
            { video: Video ->
                video.duration == com.example.utils.VideoType.CHANNEL || video.id.startsWith("channel_")
            }
        }

        val isPlaylistItem = remember {
            { video: Video ->
                video.duration == com.example.utils.VideoType.PLAYLIST
            }
        }

        val isFolderItem = remember {
            { video: Video ->
                video.duration == com.example.utils.VideoType.FOLDER || video.duration == com.example.utils.VideoType.CATALOG
            }
        }

        val isVideoItem = remember {
            { video: Video ->
                video.duration != com.example.utils.VideoType.FOLDER &&
                video.duration != com.example.utils.VideoType.CHANNEL &&
                !video.id.startsWith("channel_") &&
                video.duration != com.example.utils.VideoType.PLAYLIST &&
                video.duration != com.example.utils.VideoType.SERIES &&
                video.duration != com.example.utils.VideoType.CATALOG
            }
        }

        // All categorical lists for currentVideos
        val folderItems = remember(currentVideos) { currentVideos.filter(isFolderItem) }
        val channelItems = remember(currentVideos) { currentVideos.filter(isChannelItem) }
        val playlistItems = remember(currentVideos) { currentVideos.filter(isPlaylistItem) }
        val seriesItems = remember(currentVideos) { currentVideos.filter(isSeriesItem) }
        val otherVideos = remember(currentVideos) { currentVideos.filter(isVideoItem) }

        // Column counts based on isTvOptimized settings
        val tvColsSetting = LocalTvGridColumns.current
        val tvVideoColsSetting = LocalTvVideoGridColumns.current
        val mobileColsSetting = LocalMobileGridColumns.current

        val folderCols = if (isTvOptimized) tvColsSetting else mobileColsSetting
        val seriesCols = if (isTvOptimized) {
            if (folderItems.isNotEmpty()) tvColsSetting else (tvColsSetting + 1)
        } else {
            mobileColsSetting
        }
        val playlistCols = if (isTvOptimized) {
            if (folderItems.isNotEmpty()) tvColsSetting else tvColsSetting
        } else {
            mobileColsSetting
        }

        // Chunked folders/series/playlists
        val chunkedFolders = remember(folderItems, folderCols) { folderItems.chunked(folderCols) }
        val chunkedSeries = remember(seriesItems, seriesCols) { seriesItems.chunked(seriesCols) }
        val chunkedPlaylists = remember(playlistItems, playlistCols) { playlistItems.chunked(playlistCols) }

        val context = LocalContext.current

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .mouseDragScrollable(listState, isVertical = true),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. App search header
            item {
                SleekHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                    onMicClick = { isVoiceSearchActive = true },
                    isDark = isDarkTheme,
                    isTvOptimized = isTvOptimized,
                    searchHistory = searchHistory,
                    onDeleteQuery = { viewModel.deleteSearchQuery(it) },
                    onClearAll = { viewModel.clearSearchHistory() },
                    onSearchConfirmed = { viewModel.saveSearchQuery(it) }
                )
            }

            // 2. Feed tabs
            val shouldFocusSubfolderBack = selectedSubfolderName != null
            val shouldFocusFeedTab = feedTabs.isNotEmpty() && !shouldFocusSubfolderBack && !isChannelView
            val shouldFocusContinueWatching = !shouldFocusFeedTab && !shouldFocusSubfolderBack && searchQuery.isBlank() && !isChannelView && selectedSubfolderName == null && continueWatchingVideos.isNotEmpty()
            val shouldFocusHeroCard = !shouldFocusFeedTab && !shouldFocusSubfolderBack && !shouldFocusContinueWatching && currentVideos.isNotEmpty()

            if (searchQuery.isNotEmpty()) {
                item {
                    val sources = listOf("Rutube", "VK Video", "Дзен")
                    com.example.ui.screens.home.components.CategoryRow(
                        categories = sources,
                        selectedCategory = searchSource,
                        onCategorySelected = { viewModel.setSearchSource(it) }
                    )
                }
            } else if (feedTabs.isNotEmpty()) {
                item {
                    FeedTabRow(
                        tabs = feedTabs,
                        selectedTab = selectedFeedTab,
                        onTabSelected = { viewModel.selectFeedTab(it) },
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized,
                        focusRequester = if (shouldFocusFeedTab) initialFocusRequester else null
                    )
                }
            }

            // 2.5. Continue Watching Slider
            if (searchQuery.isBlank() && !isChannelView && selectedSubfolderName == null && continueWatchingVideos.isNotEmpty()) {
                item {
                    ContinueWatchingSection(
                        videos = continueWatchingVideos,
                        onVideoClick = { savedVideo ->
                            val videoRuntime = Video(
                                id = savedVideo.id, title = savedVideo.title, channel = savedVideo.channel,
                                views = savedVideo.views, timeAgo = savedVideo.timeAgo, duration = savedVideo.duration,
                                isPro = savedVideo.isPro, category = savedVideo.category, description = "Продолжить просмотр",
                                thumbnailUrl = savedVideo.thumbnailUrl, isDownloaded = savedVideo.isDownloaded, isBookmarked = savedVideo.isBookmarked
                            )
                            viewModel.selectVideo(videoRuntime)
                        },
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized,
                        focusRequester = if (shouldFocusContinueWatching) initialFocusRequester else null
                    )
                }
            }

            // 3. Subfolder or Channel back button / path
            val showTopBar = selectedSubfolderName != null || isChannelView
            if (showTopBar) {
                item {
                    val activeVideo = if (isChannelView) currentChannelVideo else currentSubfolderVideo
                    val barTitle = if (isChannelView) {
                        "${selectedFeedTab?.name ?: "Назад"} › ${activeVideo?.title ?: "Канал"}"
                    } else {
                        "${selectedFeedTab?.name ?: "Назад"} › $selectedSubfolderName"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (shouldFocusSubfolderBack) Modifier.focusRequester(initialFocusRequester) else Modifier
                                )
                                .sleekTvFocus(shape = RoundedCornerShape(12.dp), onEnter = { viewModel.navigateBack() })
                                .clickable { viewModel.navigateBack() }
                                .liquidGlass(RoundedCornerShape(12.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Назад",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = barTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        activeVideo?.let { video ->
                            IconButton(
                                onClick = { viewModel.toggleBookmark(video) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .sleekTvFocus(CircleShape)
                                    .liquidGlass(CircleShape, borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                            ) {
                                Icon(
                                    imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "В избранное",
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            if (video.duration == "ПЛЕЙЛИСТ" || video.duration == com.example.utils.VideoType.SERIES || isChannelView) {
                                IconButton(
                                    onClick = {
                                        val shareUrl = if (isChannelView) {
                                            val rawChannelId = video.id.substringAfter("channel_").substringBefore("__")
                                            "https://rutube.ru/channel/$rawChannelId/"
                                        } else if (video.duration == com.example.utils.VideoType.SERIES) {
                                            val rawSeriesId = video.id.substringAfter("tv_").substringBefore("__")
                                            "https://rutube.ru/metainfo/tv/$rawSeriesId/"
                                        } else {
                                            val id = video.id.substringAfter("playlist_").substringBefore("__")
                                            "https://rutube.ru/plst/$id/"
                                        }
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Смотрите '${video.title}' в RuVideoHub:\n$shareUrl")
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Поделиться")
                                        context.startActivity(shareIntent)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .sleekTvFocus(CircleShape)
                                        .liquidGlass(CircleShape, borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Поделиться",
                                        tint = Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 3.4. Series / Playlist Header
            if (!isChannelView && (currentSubfolderVideo?.duration == com.example.utils.VideoType.SERIES || currentSubfolderVideo?.duration == com.example.utils.VideoType.PLAYLIST || currentSubfolderVideo?.duration == "ПЛЕЙЛИСТ")) {
                item {
                    com.example.ui.screens.home.components.SeriesHeader(
                        series = currentSubfolderVideo,
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized,
                        focusRequester = null
                    )
                }
            }

            // 3.5. Channel header
            if (isChannelView) {
                item {
                    val activeChannel = currentChannelVideo
                    ChannelHeader(
                        channel = activeChannel,
                        onFavoriteToggle = { channelVideo -> 
                            viewModel.toggleBookmark(channelVideo)
                        },
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized,
                        focusRequester = if (isChannelView) initialFocusRequester else null
                    )
                }
            }

            // 4. Channel view tabs
            if (isChannelView) {
                item {
                    androidx.compose.material3.TabRow(
                        selectedTabIndex = if (channelActiveTab == "Плейлисты") 1 else 0,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        androidx.compose.material3.Tab(
                            selected = channelActiveTab == "Видео",
                            onClick = { viewModel.setChannelActiveTab("Видео") },
                            text = { Text("Видео") }
                        )
                        androidx.compose.material3.Tab(
                            selected = channelActiveTab == "Плейлисты",
                            onClick = { viewModel.setChannelActiveTab("Плейлисты") },
                            text = { Text("Плейлисты") }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 5. Loading / Content states
            if (isCurrentTabLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            } else if (currentVideos.isEmpty()) {
                item {
                    EmptySearchState(query = searchQuery)
                }
            } else if (searchQuery.isNotEmpty()) {
                homeSearchContent(
                    currentVideos = currentVideos,
                    searchQuery = searchQuery,
                    isDarkTheme = isDarkTheme,
                    isTvOptimized = isTvOptimized,
                    isLargeCardsMode = isLargeCardsMode,
                    tvColsSetting = tvColsSetting,
                    tvVideoColsSetting = tvVideoColsSetting,
                    mobileColsSetting = mobileColsSetting,
                    shouldFocusHeroCard = shouldFocusHeroCard,
                    initialFocusRequester = initialFocusRequester,
                    viewModel = viewModel
                )
            } else {
                val firstItem = currentVideos.firstOrNull()
                if (firstItem != null) {
                    val isCatalog = firstItem.duration == com.example.utils.VideoType.CATALOG
                    if (isCatalog) {
                        homeCatalogContent(
                            groupedCatalogItems = groupedCatalogItems,
                            catalogExpandedState = catalogExpandedState,
                            isDarkTheme = isDarkTheme,
                            isTvOptimized = isTvOptimized,
                            isLargeCardsMode = isLargeCardsMode,
                            viewModel = viewModel,
                            tvVideoColsSetting = tvVideoColsSetting,
                            mobileColsSetting = mobileColsSetting
                        )
                    } else {
                        homeFeedContent(
                            folderItems = folderItems,
                            channelItems = channelItems,
                            seriesItems = seriesItems,
                            playlistItems = playlistItems,
                            otherVideos = otherVideos,
                            chunkedFolders = chunkedFolders,
                            chunkedSeries = chunkedSeries,
                            chunkedPlaylists = chunkedPlaylists,
                            folderCols = folderCols,
                            seriesCols = seriesCols,
                            playlistCols = playlistCols,
                            isDarkTheme = isDarkTheme,
                            isTvOptimized = isTvOptimized,
                            isLargeCardsMode = isLargeCardsMode,
                            viewModel = viewModel,
                            tvVideoColsSetting = tvVideoColsSetting,
                            mobileColsSetting = mobileColsSetting
                        )
                    }
                }

                if (isMoreLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        val voiceLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    viewModel.setSearchQuery(matches[0])
                    viewModel.saveSearchQuery(matches[0])
                }
            }
            isVoiceSearchActive = false
        }

        LaunchedEffect(isVoiceSearchActive) {
            if (isVoiceSearchActive) {
                try {
                    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Слушаю...")
                    }
                    voiceLauncher.launch(intent)
                } catch (e: Exception) {
                    // Simulation mode if no Speech Recognizer exists
                    kotlinx.coroutines.delay(1000)
                    viewModel.setSearchQuery("смешные коты")
                    viewModel.saveSearchQuery("смешные коты")
                    isVoiceSearchActive = false
                }
            }
        }
    }
}
}

@Composable
fun SearchHistorySection(
    history: List<SearchHistory>,
    onQueryClick: (String) -> Unit,
    onDeleteQuery: (String) -> Unit,
    onClearAll: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "История поиска",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "История поиска",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("clear_history_button")
            ) {
                Text(
                    text = "Очистить всё",
                    color = Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(history, key = { it.query }) { item ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.05f)
                        )
                        .clickable { onQueryClick(item.query) }
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                        .testTag("history_item_${item.query}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.query,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { onDeleteQuery(item.query) },
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("delete_history_item_${item.query}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить ${item.query} из истории",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

