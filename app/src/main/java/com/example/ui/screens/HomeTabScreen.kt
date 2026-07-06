package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.ProBadgeBg
import com.example.ui.theme.ProBadgeText
import com.example.ui.theme.liquidGlass
import com.example.viewmodel.VideoViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
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

    Column(modifier = modifier.fillMaxSize()) {
        val feedTabs by viewModel.feedTabs.collectAsStateWithLifecycle()
        val selectedFeedTab by viewModel.selectedFeedTab.collectAsStateWithLifecycle()
        val selectedSubfolderName by viewModel.selectedSubfolderName.collectAsStateWithLifecycle()
        val currentSubfolderVideo by viewModel.currentSubfolderVideo.collectAsStateWithLifecycle()

        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val isMoreLoading by viewModel.isMoreLoading.collectAsStateWithLifecycle()

        val isCurrentTabLoading = if (isChannelView) {
            if (channelActiveTab == "Видео") isLoading else isLoadingPlaylists
        } else {
            isLoading
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
        val folderItemsToRender = remember(currentVideos) { com.example.utils.VideoLayoutHelper.groupFolderItems(currentVideos) }

        // Smart Classification Helpers
        val isSeriesItem = remember {
            { video: com.example.data.Video ->
                video.duration == com.example.utils.VideoType.SERIES
            }
        }

        val isChannelItem = remember {
            { video: com.example.data.Video ->
                video.duration == com.example.utils.VideoType.CHANNEL || video.id.startsWith("channel_")
            }
        }

        val isPlaylistItem = remember {
            { video: com.example.data.Video ->
                video.duration == com.example.utils.VideoType.PLAYLIST
            }
        }

        val isFolderItem = remember {
            { video: com.example.data.Video ->
                video.duration == com.example.utils.VideoType.FOLDER || video.duration == com.example.utils.VideoType.CATALOG
            }
        }

        val isVideoItem = remember {
            { video: com.example.data.Video ->
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

        // Chunked folders/series/playlists
        val chunkedFolders = remember(folderItems) { folderItems.chunked(2) }
        val chunkedSeries = remember(seriesItems) { seriesItems.chunked(2) }
        val chunkedPlaylists = remember(playlistItems) { playlistItems.chunked(2) }

        // All categorical lists for searchVideos
        val searchVideos = remember(currentVideos) {
            currentVideos.filter { !it.id.startsWith("channel_") && it.duration != com.example.utils.VideoType.CHANNEL }
        }
        val folderItemsSearch = remember(searchVideos) { searchVideos.filter(isFolderItem) }
        val channelItemsSearch = remember(searchVideos) { searchVideos.filter(isChannelItem) }
        val playlistItemsSearch = remember(searchVideos) { searchVideos.filter(isPlaylistItem) }
        val seriesItemsSearch = remember(searchVideos) { searchVideos.filter(isSeriesItem) }
        val otherVideosSearch = remember(searchVideos) { searchVideos.filter(isVideoItem) }

        val chunkedFoldersSearch = remember(folderItemsSearch) { folderItemsSearch.chunked(2) }
        val chunkedSeriesSearch = remember(seriesItemsSearch) { seriesItemsSearch.chunked(2) }
        val chunkedPlaylistsSearch = remember(playlistItemsSearch) { playlistItemsSearch.chunked(2) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. App search header
            item {
                SleekHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                    isDark = isDarkTheme,
                    isTvOptimized = isTvOptimized
                )
            }

            // 2. Feed tabs
            if (feedTabs.isNotEmpty()) {
                item {
                    FeedTabRow(
                        tabs = feedTabs,
                        selectedTab = selectedFeedTab,
                        onTabSelected = { viewModel.selectFeedTab(it) },
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized
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
                        isTvOptimized = isTvOptimized
                    )
                }
            }

            // 3. Subfolder back button / path
            if (selectedSubfolderName != null) {
                item {
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
                                text = "${selectedFeedTab?.name ?: "Назад"} › $selectedSubfolderName",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        currentSubfolderVideo?.let { activeVideo ->
                            IconButton(
                                onClick = { viewModel.toggleBookmark(activeVideo) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .sleekTvFocus(CircleShape)
                                    .liquidGlass(CircleShape, borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                            ) {
                                Icon(
                                    imageVector = if (activeVideo.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "В избранное",
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // 3.5. Channel header
            if (isChannelView) {
                item {
                    val activeChannel by viewModel.currentChannelVideo.collectAsStateWithLifecycle()
                    ChannelHeader(
                        channel = activeChannel,
                        onFavoriteToggle = { channelVideo -> 
                            viewModel.toggleBookmark(channelVideo)
                        },
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized
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

            // 5. Loading State
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
                // Highly optimized search results display!
                val searchVideos = currentVideos.filter { !it.id.startsWith("channel_") && it.duration != com.example.utils.VideoType.CHANNEL }
                val explicitChannels = currentVideos.filter { it.id.startsWith("channel_") || it.duration == com.example.utils.VideoType.CHANNEL }
                val extractedChannels = searchVideos
                    .filter { !it.authorId.isNullOrBlank() && !it.channel.isNullOrBlank() }
                    .map { video ->
                        val chanId = video.authorId!!
                        Video(
                            id = "channel_${chanId}__${video.authorActionUrl ?: ""}",
                            title = video.channel,
                            channel = video.channel,
                            views = "",
                            timeAgo = "",
                            duration = com.example.utils.VideoType.CHANNEL,
                            category = video.category,
                            description = "",
                            thumbnailUrl = video.authorAvatarUrl ?: video.thumbnailUrl,
                            authorAvatarUrl = video.authorAvatarUrl,
                            authorId = chanId,
                            authorActionUrl = video.authorActionUrl
                        )
                    }
                val searchChannels = (explicitChannels + extractedChannels).distinctBy { it.authorId ?: it.id }

                // 2. Render Channels Row at the top if present
                if (searchChannels.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Каналы",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(searchChannels, key = { it.id }) { channel ->
                                    SleekSearchChannelItem(
                                        channel = channel,
                                        onClick = {
                                            viewModel.selectVideo(channel)
                                        },
                                        isDark = isDarkTheme,
                                        isTvOptimized = isTvOptimized
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                }

                // 3. Render Search Videos
                if (searchVideos.isNotEmpty()) {
                    // 1. Subfolders
                    if (folderItemsSearch.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Подкатегории", isDark = isDarkTheme)
                        }
                        items(chunkedFoldersSearch) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { folder ->
                                    SleekFolderGridItem(
                                        video = folder,
                                        onFolderClick = { viewModel.selectVideo(folder) },
                                        onBookmarkToggle = { viewModel.toggleBookmark(folder) },
                                        isDark = isDarkTheme,
                                        isTvOptimized = isTvOptimized,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 2. Channels (if any)
                    if (channelItemsSearch.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Каналы", isDark = isDarkTheme)
                        }
                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(channelItemsSearch) { channel ->
                                    CircularChannelItem(
                                        video = channel,
                                        onClick = { viewModel.selectVideo(channel) },
                                        isDark = isDarkTheme,
                                        isTvOptimized = isTvOptimized
                                    )
                                }
                            }
                        }
                    }

                    // 3. Series
                    if (seriesItemsSearch.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Сериалы", isDark = isDarkTheme)
                        }
                        items(chunkedSeriesSearch) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { seriesItem ->
                                    SleekSeriesCard(
                                        video = seriesItem,
                                        onClick = { viewModel.selectVideo(seriesItem) },
                                        onBookmarkToggle = { viewModel.toggleBookmark(seriesItem) },
                                        isDark = isDarkTheme,
                                        isTvOptimized = isTvOptimized,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 4. Playlists
                    if (playlistItemsSearch.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Плейлисты", isDark = isDarkTheme)
                        }
                        items(chunkedPlaylistsSearch) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { playlist ->
                                    SleekPlaylistCard(
                                        video = playlist,
                                        onClick = { viewModel.selectVideo(playlist) },
                                        onBookmarkToggle = { viewModel.toggleBookmark(playlist) },
                                        isDark = isDarkTheme,
                                        isTvOptimized = isTvOptimized,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 5. Render Other Videos (if any)
                    if (otherVideosSearch.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Выпуски", isDark = isDarkTheme)
                        }
                        
                        val firstVideo = otherVideosSearch.first()
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                HeroVideoCard(
                                    video = firstVideo,
                                    onVideoClick = { viewModel.selectVideo(firstVideo) },
                                    onDownloadToggle = { viewModel.toggleDownload(firstVideo) },
                                    onBookmarkToggle = { viewModel.toggleBookmark(firstVideo) },
                                    isDark = isDarkTheme,
                                    onChannelClick = if (!firstVideo.authorId.isNullOrBlank()) {
                                        {
                                            val channelDummy = Video(
                                                id = "channel_${firstVideo.authorId}__${firstVideo.authorActionUrl ?: ""}",
                                                title = firstVideo.channel,
                                                channel = firstVideo.channel,
                                                views = "",
                                                timeAgo = "",
                                                duration = com.example.utils.VideoType.CHANNEL,
                                                category = firstVideo.category,
                                                description = "",
                                                thumbnailUrl = firstVideo.authorAvatarUrl,
                                                authorAvatarUrl = firstVideo.authorAvatarUrl,
                                                authorId = firstVideo.authorId,
                                                authorActionUrl = firstVideo.authorActionUrl
                                            )
                                            viewModel.selectVideo(channelDummy)
                                        }
                                    } else null,
                                    isTvOptimized = isTvOptimized
                                )
                            }
                        }

                        if (otherVideosSearch.size > 1) {
                            items(otherVideosSearch.subList(1, otherVideosSearch.size), key = { it.id }) { video ->
                                if (isLargeCardsMode) {
                                    HeroVideoCard(
                                        video = video,
                                        onVideoClick = { viewModel.selectVideo(video) },
                                        onDownloadToggle = { viewModel.toggleDownload(video) },
                                        onBookmarkToggle = { viewModel.toggleBookmark(video) },
                                        isDark = isDarkTheme,
                                        onChannelClick = if (!video.authorId.isNullOrBlank()) {
                                            {
                                                val channelDummy = Video(
                                                    id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                                    title = video.channel,
                                                    channel = video.channel,
                                                    views = "",
                                                    timeAgo = "",
                                                    duration = com.example.utils.VideoType.CHANNEL,
                                                    category = video.category,
                                                    description = "",
                                                    thumbnailUrl = video.authorAvatarUrl,
                                                    authorAvatarUrl = video.authorAvatarUrl,
                                                    authorId = video.authorId,
                                                    authorActionUrl = video.authorActionUrl
                                                )
                                                viewModel.selectVideo(channelDummy)
                                            }
                                        } else null,
                                        isTvOptimized = isTvOptimized
                                    )
                                } else {
                                    SecondaryVideoItemRow(
                                        video = video,
                                        onVideoClick = { viewModel.selectVideo(video) },
                                        onDownloadToggle = { viewModel.toggleDownload(video) },
                                        onBookmarkToggle = { viewModel.toggleBookmark(video) },
                                        isDark = isDarkTheme,
                                        onChannelClick = if (!video.authorId.isNullOrBlank()) {
                                            {
                                                val channelDummy = Video(
                                                    id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                                    title = video.channel,
                                                    channel = video.channel,
                                                    views = "",
                                                    timeAgo = "",
                                                    duration = com.example.utils.VideoType.CHANNEL,
                                                    category = video.category,
                                                    description = "",
                                                    thumbnailUrl = video.authorAvatarUrl,
                                                    authorAvatarUrl = video.authorAvatarUrl,
                                                    authorId = video.authorId,
                                                    authorActionUrl = video.authorActionUrl
                                                )
                                                viewModel.selectVideo(channelDummy)
                                            }
                                        } else null,
                                        isTvOptimized = isTvOptimized
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Section recommended (hero card for videos, uniform list for folders)
                val firstItem = currentVideos.first()
                val isFolderList = firstItem.duration == com.example.utils.VideoType.FOLDER || 
                                   firstItem.duration == com.example.utils.VideoType.CATALOG ||
                                   firstItem.duration == com.example.utils.VideoType.SERIES ||
                                   firstItem.duration == com.example.utils.VideoType.CHANNEL ||
                                   firstItem.duration == com.example.utils.VideoType.PLAYLIST ||
                                   firstItem.duration == com.example.utils.VideoType.PROMO

                if (!isFolderList) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            HeroVideoCard(
                                video = firstItem,
                                onVideoClick = { viewModel.selectVideo(firstItem) },
                                onDownloadToggle = { viewModel.toggleDownload(firstItem) },
                                onBookmarkToggle = { viewModel.toggleBookmark(firstItem) },
                                isDark = isDarkTheme,
                                onChannelClick = if (!firstItem.authorId.isNullOrBlank()) {
                                    {
                                        val channelDummy = Video(
                                            id = "channel_${firstItem.authorId}__${firstItem.authorActionUrl ?: ""}",
                                            title = firstItem.channel,
                                            channel = firstItem.channel,
                                            views = "",
                                            timeAgo = "",
                                            duration = com.example.utils.VideoType.CHANNEL,
                                            category = firstItem.category,
                                            description = "",
                                            thumbnailUrl = firstItem.authorAvatarUrl,
                                            authorAvatarUrl = firstItem.authorAvatarUrl,
                                            authorId = firstItem.authorId,
                                            authorActionUrl = firstItem.authorActionUrl
                                        )
                                        viewModel.selectVideo(channelDummy)
                                    }
                                } else null,
                                isTvOptimized = isTvOptimized
                            )
                        }
                    }

                    // Section listed items
                    if (currentVideos.size > 1) {
                        items(currentVideos.subList(1, currentVideos.size), key = { it.id }) { video ->
                            if (isLargeCardsMode) {
                                HeroVideoCard(
                                    video = video,
                                    onVideoClick = { viewModel.selectVideo(video) },
                                    onDownloadToggle = { viewModel.toggleDownload(video) },
                                    onBookmarkToggle = { viewModel.toggleBookmark(video) },
                                    isDark = isDarkTheme,
                                    onChannelClick = if (!video.authorId.isNullOrBlank()) {
                                        {
                                            val channelDummy = Video(
                                                id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                                title = video.channel,
                                                channel = video.channel,
                                                views = "",
                                                timeAgo = "",
                                                duration = com.example.utils.VideoType.CHANNEL,
                                                category = video.category,
                                                description = "",
                                                thumbnailUrl = video.authorAvatarUrl,
                                                authorAvatarUrl = video.authorAvatarUrl,
                                                authorId = video.authorId,
                                                authorActionUrl = video.authorActionUrl
                                            )
                                            viewModel.selectVideo(channelDummy)
                                        }
                                    } else null,
                                    isTvOptimized = isTvOptimized
                                )
                            } else {
                                SecondaryVideoItemRow(
                                    video = video,
                                    onVideoClick = { viewModel.selectVideo(video) },
                                    onDownloadToggle = { viewModel.toggleDownload(video) },
                                    onBookmarkToggle = { viewModel.toggleBookmark(video) },
                                    isDark = isDarkTheme,
                                    onChannelClick = if (!video.authorId.isNullOrBlank()) {
                                        {
                                            val channelDummy = Video(
                                                id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                                title = video.channel,
                                                channel = video.channel,
                                                views = "",
                                                timeAgo = "",
                                                duration = com.example.utils.VideoType.CHANNEL,
                                                category = video.category,
                                                description = "",
                                                thumbnailUrl = video.authorAvatarUrl,
                                                authorAvatarUrl = video.authorAvatarUrl,
                                                authorId = video.authorId,
                                                authorActionUrl = video.authorActionUrl
                                            )
                                            viewModel.selectVideo(channelDummy)
                                        }
                                    } else null,
                                    isTvOptimized = isTvOptimized
                                )
                            }
                        }
                    }
                } else {
                    val isCatalog = firstItem.duration == com.example.utils.VideoType.CATALOG
                    if (isCatalog) {
                        groupedCatalogItems.forEach { (groupName, videoList) ->
                            val isExpanded = catalogExpandedState[groupName] ?: false
                            item(key = "header_$groupName") {
                                CatalogGroupHeader(
                                    groupName = groupName,
                                    isExpanded = isExpanded,
                                    onClick = { catalogExpandedState[groupName] = !isExpanded },
                                    isDark = isDarkTheme
                                )
                            }
                            if (isExpanded) {
                                val groupFolders = videoList.filter(isFolderItem)
                                val groupChannels = videoList.filter(isChannelItem)
                                val groupPlaylists = videoList.filter(isPlaylistItem)
                                val groupSeries = videoList.filter(isSeriesItem)
                                val groupVideos = videoList.filter(isVideoItem)

                                val chunkedGroupFolders = groupFolders.chunked(2)
                                val chunkedGroupSeries = groupSeries.chunked(2)
                                val chunkedGroupPlaylists = groupPlaylists.chunked(2)

                                // 1. Render Subfolders (if any)
                                if (groupFolders.isNotEmpty()) {
                                    item(key = "subheader_folders_$groupName") {
                                        SectionHeader(title = "Подкатегории", isDark = isDarkTheme)
                                    }
                                    items(chunkedGroupFolders, key = { "folders_${groupName}_${it.hashCode()}" }) { rowItems ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowItems.forEach { folder ->
                                                SleekFolderGridItem(
                                                    video = folder,
                                                    onFolderClick = { viewModel.selectVideo(folder) },
                                                    onBookmarkToggle = { viewModel.toggleBookmark(folder) },
                                                    isDark = isDarkTheme,
                                                    isTvOptimized = isTvOptimized,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (rowItems.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // 2. Render Channels (if any)
                                if (groupChannels.isNotEmpty()) {
                                    item(key = "subheader_channels_$groupName") {
                                        SectionHeader(title = "Каналы", isDark = isDarkTheme)
                                    }
                                    item(key = "row_channels_$groupName") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(groupChannels, key = { "channel_${groupName}_${it.id}" }) { channel ->
                                                CircularChannelItem(
                                                    video = channel,
                                                    onClick = { viewModel.selectVideo(channel) },
                                                    isDark = isDarkTheme,
                                                    isTvOptimized = isTvOptimized
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Render Series (if any)
                                if (groupSeries.isNotEmpty()) {
                                    item(key = "subheader_series_$groupName") {
                                        SectionHeader(title = "Сериалы", isDark = isDarkTheme)
                                    }
                                    items(chunkedGroupSeries, key = { "series_${groupName}_${it.hashCode()}" }) { rowItems ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowItems.forEach { seriesItem ->
                                                SleekFolderGridItem(
                                                    video = seriesItem,
                                                    onFolderClick = { viewModel.selectVideo(seriesItem) },
                                                    onBookmarkToggle = { viewModel.toggleBookmark(seriesItem) },
                                                    isDark = isDarkTheme,
                                                    isTvOptimized = isTvOptimized,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (rowItems.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // 4. Render Playlists (if any)
                                if (groupPlaylists.isNotEmpty()) {
                                    item(key = "subheader_playlists_$groupName") {
                                        SectionHeader(title = "Плейлисты", isDark = isDarkTheme)
                                    }
                                    items(chunkedGroupPlaylists, key = { "playlists_${groupName}_${it.hashCode()}" }) { rowItems ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowItems.forEach { playlist ->
                                                SleekFolderGridItem(
                                                    video = playlist,
                                                    onFolderClick = { viewModel.selectVideo(playlist) },
                                                    onBookmarkToggle = { viewModel.toggleBookmark(playlist) },
                                                    isDark = isDarkTheme,
                                                    isTvOptimized = isTvOptimized,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (rowItems.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // 5. Render Videos (if any)
                                if (groupVideos.isNotEmpty()) {
                                    item(key = "subheader_videos_$groupName") {
                                        SectionHeader(title = "Выпуски", isDark = isDarkTheme)
                                    }
                                    items(groupVideos, key = { "video_${groupName}_${it.id}" }) { video ->
                                        SecondaryVideoItemRow(
                                            video = video,
                                            onVideoClick = { viewModel.selectVideo(video) },
                                            onDownloadToggle = { viewModel.toggleDownload(video) },
                                            onBookmarkToggle = { viewModel.toggleBookmark(video) },
                                            isDark = isDarkTheme,
                                            onChannelClick = if (!video.authorId.isNullOrBlank()) {
                                                {
                                                    val channelDummy = Video(
                                                        id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                                        title = video.channel,
                                                        channel = video.channel,
                                                        views = "",
                                                        timeAgo = "",
                                                        duration = com.example.utils.VideoType.CHANNEL,
                                                        category = video.category,
                                                        description = "",
                                                        thumbnailUrl = video.authorAvatarUrl,
                                                        authorAvatarUrl = video.authorAvatarUrl,
                                                        authorId = video.authorId,
                                                        authorActionUrl = video.authorActionUrl
                                                    )
                                                    viewModel.selectVideo(channelDummy)
                                                }
                                            } else null,
                                            isTvOptimized = isTvOptimized
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // 1. Render Subfolders (if any)
                        if (folderItems.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Подкатегории", isDark = isDarkTheme)
                            }
                            items(chunkedFolders) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { folder ->
                                        SleekFolderGridItem(
                                            video = folder,
                                            onFolderClick = { viewModel.selectVideo(folder) },
                                            onBookmarkToggle = { viewModel.toggleBookmark(folder) },
                                            isDark = isDarkTheme,
                                            isTvOptimized = isTvOptimized,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // 2. Render Channels (if any)
                        if (channelItems.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Каналы", isDark = isDarkTheme)
                            }
                            item {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(channelItems) { channel ->
                                        CircularChannelItem(
                                            video = channel,
                                            onClick = { viewModel.selectVideo(channel) },
                                            isDark = isDarkTheme,
                                            isTvOptimized = isTvOptimized
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Render Series (if any)
                        if (seriesItems.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Сериалы", isDark = isDarkTheme)
                            }
                            items(chunkedSeries) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { seriesItem ->
                                        if (folderItems.isNotEmpty()) {
                                            SleekFolderGridItem(
                                                video = seriesItem,
                                                onFolderClick = { viewModel.selectVideo(seriesItem) },
                                                onBookmarkToggle = { viewModel.toggleBookmark(seriesItem) },
                                                isDark = isDarkTheme,
                                                isTvOptimized = isTvOptimized,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            SleekSeriesCard(
                                                video = seriesItem,
                                                onClick = { viewModel.selectVideo(seriesItem) },
                                                onBookmarkToggle = { viewModel.toggleBookmark(seriesItem) },
                                                isDark = isDarkTheme,
                                                isTvOptimized = isTvOptimized,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // 4. Render Playlists (if any)
                        if (playlistItems.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Плейлисты", isDark = isDarkTheme)
                            }
                            items(chunkedPlaylists) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { playlist ->
                                        if (folderItems.isNotEmpty()) {
                                            SleekFolderGridItem(
                                                video = playlist,
                                                onFolderClick = { viewModel.selectVideo(playlist) },
                                                onBookmarkToggle = { viewModel.toggleBookmark(playlist) },
                                                isDark = isDarkTheme,
                                                isTvOptimized = isTvOptimized,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            SleekPlaylistCard(
                                                video = playlist,
                                                onClick = { viewModel.selectVideo(playlist) },
                                                onBookmarkToggle = { viewModel.toggleBookmark(playlist) },
                                                isDark = isDarkTheme,
                                                isTvOptimized = isTvOptimized,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // 5. Render Other Videos (if any)
                        if (otherVideos.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Выпуски", isDark = isDarkTheme)
                            }
                            items(otherVideos) { video ->
                                SecondaryVideoItemRow(
                                    video = video,
                                    onVideoClick = { viewModel.selectVideo(video) },
                                    onDownloadToggle = { viewModel.toggleDownload(video) },
                                    onBookmarkToggle = { viewModel.toggleBookmark(video) },
                                    isDark = isDarkTheme,
                                    onChannelClick = if (!video.authorId.isNullOrBlank()) {
                                        {
                                            val channelDummy = Video(
                                                id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                                title = video.channel,
                                                channel = video.channel,
                                                views = "",
                                                timeAgo = "",
                                                duration = com.example.utils.VideoType.CHANNEL,
                                                category = video.category,
                                                description = "",
                                                thumbnailUrl = video.authorAvatarUrl,
                                                authorAvatarUrl = video.authorAvatarUrl,
                                                authorId = video.authorId,
                                                authorActionUrl = video.authorActionUrl
                                            )
                                            viewModel.selectVideo(channelDummy)
                                        }
                                    } else null,
                                    isTvOptimized = isTvOptimized
                                )
                            }
                        }
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
    }
}

@Composable
fun SleekHeader(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .height(44.dp)
                .liquidGlass(RoundedCornerShape(22.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Поиск",
                tint = GreyText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .weight(1f)
                    .sleekTvFocus(shape = RoundedCornerShape(8.dp))
                    .testTag("search_input"),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Поиск видео...",
                            color = GreyText,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChanged("") },
                    modifier = Modifier.size(24.dp).sleekTvFocus(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = GreyText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { cat ->
            val isSelected = cat == selectedCategory

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Primary else SurfaceVariant)
                    .sleekTvFocus(shape = RoundedCornerShape(12.dp), onEnter = { onCategorySelected(cat) })
                    .clickable { onCategorySelected(cat) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("category_chip_$cat")
            ) {
                Text(
                    text = cat,
                    color = if (isSelected) Color.White else GreyText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun FeedTabRow(
    tabs: List<com.example.data.rutube.SmartRutubeParser.TabInfo>,
    selectedTab: com.example.data.rutube.SmartRutubeParser.TabInfo?,
    onTabSelected: (com.example.data.rutube.SmartRutubeParser.TabInfo) -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs) { tab ->
            val isSelected = tab == selectedTab
            
            Box(
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(100.dp)
                                )
                        } else {
                            Modifier.liquidGlass(RoundedCornerShape(100.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
                        }
                    )
                    .sleekTvFocus(shape = RoundedCornerShape(100.dp), onEnter = { onTabSelected(tab) })
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = tab.name ?: "Раздел",
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RecommendedSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "РЕКОМЕНДУЕМОЕ • БЕЗ РЕКЛАМЫ",
            color = Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(ProBadgeBg)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "PRO",
                tint = ProBadgeText,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = "PRO",
                color = ProBadgeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ChannelAvatarPlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    val initials = remember(title) {
        val words = title.trim().split("\\s+".toRegex())
        if (words.size >= 2) {
            (words[0].take(1) + words[1].take(1)).uppercase()
        } else if (words.isNotEmpty() && words[0].isNotBlank()) {
            words[0].take(2).uppercase()
        } else {
            "CH"
        }
    }
    val hashColor = remember(title) {
        val hues = listOf(
            Color(0xFFFF253E), // Coral Red
            Color(0xFF00C853), // Emerald Green
            Color(0xFF00B0FF), // Neon Blue
            Color(0xFFAA00FF), // Deep Violet
            Color(0xFFFFD600)  // Gold Amber
        )
        val index = kotlin.math.abs(title.hashCode()) % hues.size
        hues[index]
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        hashColor,
                        hashColor.copy(alpha = 0.5f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun CircularChannelItem(
    video: Video,
    onClick: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatarUrl = if (!video.thumbnailUrl.isNullOrBlank()) {
        video.thumbnailUrl
    } else if (!video.authorAvatarUrl.isNullOrBlank()) {
        video.authorAvatarUrl
    } else {
        ""
    }

    Column(
        modifier = modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .sleekTvFocus(shape = RoundedCornerShape(12.dp), onEnter = onClick)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF0F0F0))
                .border(2.dp, Brush.linearGradient(listOf(Primary, Color(0xFF9C27B0))), CircleShape)
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                ChannelAvatarPlaceholder(title = video.title)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = video.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun HeroVideoCard(
    video: Video,
    onVideoClick: () -> Unit,
    onDownloadToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    onChannelClick: (() -> Unit)? = null,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .sleekTvFocus(shape = RoundedCornerShape(28.dp), onEnter = onVideoClick)
            .liquidGlass(RoundedCornerShape(28.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .clickable(onClick = onVideoClick)
    ) {
        Column {
            // Thumbnail
            VideoThumbnail(
                id = video.id,
                duration = video.duration,
                thumbnailUrl = video.thumbnailUrl,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Avatar symbol
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

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
                    Spacer(modifier = Modifier.height(4.dp))
                    if (onChannelClick != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .sleekTvFocus(shape = RoundedCornerShape(8.dp), onEnter = onChannelClick)
                                    .clickable(onClick = onChannelClick)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Канал",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = video.channel,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = " • ${video.views} • ${video.timeAgo}",
                                color = GreyText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                            color = GreyText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action buttons right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDownloadToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer)
                            .sleekTvFocus(CircleShape)
                            .testTag("download_button_${video.id}")
                    ) {
                        Icon(
                            imageVector = if (video.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = "Скачать",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SleekPlaylistCard(
    video: Video,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .sleekTvFocus(shape = RoundedCornerShape(24.dp), onEnter = onClick)
            .liquidGlass(RoundedCornerShape(24.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail with Playlist Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Playlist Stack Visual Effect on the right
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.35f)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(topEnd = 24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Плейлист",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.views.ifBlank { "ПЛЕЙЛИСТ" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Info details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = video.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (video.channel.isNotBlank()) video.channel else "Плейлист",
                    color = GreyText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SleekSeriesCard(
    video: Video,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .sleekTvFocus(shape = RoundedCornerShape(24.dp), onEnter = onClick)
            .liquidGlass(RoundedCornerShape(24.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail with Series Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // TV / Series badge in top-left
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .align(Alignment.TopStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Сериал",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "СЕРИАЛ",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Info details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = video.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (video.channel.isNotBlank()) video.channel else "Сериал",
                    color = GreyText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SleekChannelRowItem(
    video: Video,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatarUrl = if (!video.thumbnailUrl.isNullOrBlank()) {
        video.thumbnailUrl
    } else if (!video.authorAvatarUrl.isNullOrBlank()) {
        video.authorAvatarUrl
    } else {
        ""
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sleekTvFocus(shape = RoundedCornerShape(16.dp), onEnter = onClick)
            .clickable(onClick = onClick)
            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    ChannelAvatarPlaceholder(title = video.title)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.views.ifBlank { "Авторский канал" },
                    fontSize = 11.sp,
                    color = GreyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onBookmarkToggle,
                modifier = Modifier
                    .size(40.dp)
                    .sleekTvFocus(CircleShape)
            ) {
                Icon(
                    imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "В избранное",
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AdaptiveCatalogItem(
    video: Video,
    onFolderClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (video.duration) {
        com.example.utils.VideoType.CHANNEL -> {
            SleekChannelRowItem(
                video = video,
                onClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
        com.example.utils.VideoType.PLAYLIST -> {
            SleekPlaylistCard(
                video = video,
                onClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
        com.example.utils.VideoType.SERIES -> {
            SleekSeriesCard(
                video = video,
                onClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
        else -> {
            SleekFolderGridItem(
                video = video,
                onFolderClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
    }
}

@Composable
fun SleekFolderGridItem(
    video: Video,
    onFolderClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hashColor = remember(video.title) {
        val hues = listOf(
            Color(0xFFFF253E), // Coral Red
            Color(0xFF00C853), // Emerald Green
            Color(0xFF00B0FF), // Neon Blue
            Color(0xFFAA00FF), // Deep Violet
            Color(0xFFFFD600)  // Gold Amber
        )
        val index = kotlin.math.abs(video.title.hashCode()) % hues.size
        hues[index]
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sleekTvFocus(shape = RoundedCornerShape(12.dp), onEnter = onFolderClick)
            .clickable(onClick = onFolderClick)
            .liquidGlass(RoundedCornerShape(12.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isChannelType = video.duration == com.example.utils.VideoType.CHANNEL

            if (isChannelType && !video.thumbnailUrl.isNullOrBlank()) {
                val imageShape = CircleShape
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(imageShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    hashColor,
                                    hashColor.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }

            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )
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
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .sleekTvFocus(shape = RoundedCornerShape(16.dp), onEnter = onVideoClick)
            .clickable(onClick = onVideoClick)
            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Left
        VideoThumbnail(
            id = video.id,
            duration = video.duration,
            thumbnailUrl = video.thumbnailUrl,
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
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onChannelClick != null) {
                    Row(
                        modifier = Modifier
                            .sleekTvFocus(shape = RoundedCornerShape(8.dp), onEnter = onChannelClick)
                            .clickable(onClick = onChannelClick)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Канал",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = video.channel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = video.channel,
                        fontSize = 10.sp,
                        color = GreyText
                    )
                }
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCAC4D0))
                )
                Text(
                    text = "HD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreyText
                )
            }

            // Quick mini controls
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = onDownloadToggle,
                    modifier = Modifier.size(24.dp)
                        .sleekTvFocus(CircleShape)
                        .testTag("quick_download_${video.id}")
                ) {
                    Icon(
                        imageVector = if (video.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                        contentDescription = "Скачать",
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier.size(24.dp)
                        .sleekTvFocus(CircleShape)
                        .testTag("quick_bookmark_${video.id}")
                ) {
                    Icon(
                        imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Закладка",
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoThumbnail(
    id: String,
    duration: String,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    hasPlayOverlay: Boolean = false,
    isPlaying: Boolean = false,
    onPlayClick: (() -> Unit)? = null
) {
    val isFolder = duration == com.example.utils.VideoType.FOLDER
    val gradientColors = when {
        isFolder -> listOf(Color(0xFFFFB300), Color(0xFFE65100))
        id == "api_review" -> listOf(Color(0xFF6750A4), Color(0xFF21005D))
        id == "top_10" -> listOf(Color(0xFF4B3978), Color(0xFF1B033A))
        id == "history_rutube" -> listOf(Color(0xFF8B5CF6), Color(0xFF3B0764))
        id == "android_2026" -> listOf(Color(0xFF0284C7), Color(0xFF0369A1))
        id == "sleek_compose" -> listOf(Color(0xFFEC4899), Color(0xFFBE185D))
        id == "recommender_secrets" -> listOf(Color(0xFF10B981), Color(0xFF047857))
        else -> listOf(Color(0xFF333333), Color(0xFF111111))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = gradientColors))
    ) {
        if (isFolder) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Папка раздела",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        } else if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Draw grid lines
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Tech grid aesthetics
                val lines = 5
                for (i in 1..lines) {
                    val r = i.toFloat() / (lines + 1)
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(width * r, 0f),
                        end = Offset(width * r, height),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, height * r),
                        end = Offset(width, height * r),
                        strokeWidth = 1.5f
                    )
                }
            }

            // Illustrative icons overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp)
            ) {
                val elementIcon = when (id) {
                    "api_review" -> Icons.Default.Code
                    "top_10" -> Icons.Default.ElectricBolt
                    "history_rutube" -> Icons.Default.Timeline
                    "android_2026" -> Icons.Default.Android
                    "sleek_compose" -> Icons.Default.AutoAwesome
                    "recommender_secrets" -> Icons.Default.Psychology
                    else -> Icons.Default.Movie
                }
                Icon(
                    imageVector = elementIcon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // Dark dim gradient layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                    )
                )
        )

        // Core visual player overlays
        if (hasPlayOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { onPlayClick?.invoke() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.40f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Кнопка воспроизведения",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Duration capsule
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = duration,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ChannelHeader(
    channel: Video?,
    onFavoriteToggle: (Video) -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean,
    modifier: Modifier = Modifier
) {
    if (channel == null) return

    val context = LocalContext.current
    
    val avatarUrl = if (!channel.authorAvatarUrl.isNullOrBlank()) {
        channel.authorAvatarUrl
    } else if (!channel.thumbnailUrl.isNullOrBlank()) {
        channel.thumbnailUrl
    } else {
        ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .liquidGlass(
                RoundedCornerShape(20.dp),
                borderWidth = 1.2.dp,
                isDark = isDark,
                isTvOptimized = isTvOptimized
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel Avatar
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Аватар канала",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    ChannelAvatarPlaceholder(title = channel.title)
                }
            }

            // Channel Title and details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channel.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (channel.views.isNotBlank()) {
                    Text(
                        text = channel.views,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (channel.timeAgo.isNotBlank() && channel.timeAgo != channel.views) {
                    Text(
                        text = channel.timeAgo,
                        fontSize = 11.sp,
                        color = GreyText
                    )
                }
            }
        }

        // Description if available
        if (channel.description.isNotBlank()) {
            Text(
                text = channel.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Bottom Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favorite Button
            Button(
                onClick = { onFavoriteToggle(channel) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (channel.isBookmarked) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (channel.isBookmarked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (channel.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "В избранное",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (channel.isBookmarked) "В избранном" else "В избранное",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Share Button
            OutlinedButton(
                onClick = {
                    val rawChannelId = channel.id.substringAfter("channel_").substringBefore("__")
                    val shareUrl = "https://rutube.ru/channel/$rawChannelId/"
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Смотрите авторский канал '${channel.title}' в RuVideoHub:\n$shareUrl")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Поделиться каналом")
                    context.startActivity(shareIntent)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Поделиться",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SleekSearchChannelItem(
    channel: Video,
    onClick: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatarUrl = if (!channel.authorAvatarUrl.isNullOrBlank()) {
        channel.authorAvatarUrl
    } else if (!channel.thumbnailUrl.isNullOrBlank()) {
        channel.thumbnailUrl
    } else {
        ""
    }

    Column(
        modifier = modifier
            .width(96.dp)
            .sleekTvFocus(shape = CircleShape, onEnter = onClick)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circle Avatar with Liquid Glass Border Effect
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(
                    width = 2.dp,
                    color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = channel.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                ChannelAvatarPlaceholder(title = channel.title)
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Title
        Text(
            text = channel.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
        
        // Subtitle (Subscriber or video count, etc.)
        val originalText = channel.views.ifBlank { "Канал" }
        val shortSubsText = originalText
            .replace("подписчиков", "подп.")
            .replace("подписчика", "подп.")
            .replace("подписчик", "подп.")
        Text(
            text = shortSubsText,
            color = GreyText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun CatalogGroupHeader(
    groupName: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = groupName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(
            imageVector = if (isExpanded) androidx.compose.material.icons.Icons.Default.ExpandLess else androidx.compose.material.icons.Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ContinueWatchingSection(
    videos: List<SavedVideo>,
    onVideoClick: (SavedVideo) -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Продолжить просмотр",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(videos, key = { it.id }) { saved ->
                val progressPercent = if (saved.lastDuration > 0) {
                    saved.lastProgress.toFloat() / saved.lastDuration.toFloat()
                } else {
                    0f
                }.coerceIn(0f, 1f)

                Card(
                    onClick = { onVideoClick(saved) },
                    modifier = Modifier
                        .width(160.dp)
                        .height(135.dp)
                        .sleekTvFocus(RoundedCornerShape(12.dp))
                        .testTag("continue_watching_card_${saved.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                        ) {
                            AsyncImage(
                                model = saved.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Play overlay on hover/center
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Воспроизвести",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .padding(4.dp)
                                )
                            }

                            // Sleek progress bar overlay at the very bottom of the thumbnail
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .align(Alignment.BottomStart)
                                    .background(Color.White.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressPercent)
                                        .background(Primary)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp)
                        ) {
                            Text(
                                text = saved.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = saved.channel,
                                fontSize = 9.sp,
                                color = GreyText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

