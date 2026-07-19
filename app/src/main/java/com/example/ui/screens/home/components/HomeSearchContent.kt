package com.example.ui.screens.home.components

import com.example.viewmodel.*
import com.example.viewmodel.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.ui.screens.mouseDragScrollable
import androidx.compose.ui.unit.sp
import com.example.data.Video
import com.example.viewmodel.VideoViewModel
import com.example.ui.theme.Primary
import com.example.ui.screens.home.utils.*

fun LazyListScope.homeSearchContent(
    currentVideos: List<Video>,
    searchQuery: String,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean,
    isLargeCardsMode: Boolean,
    tvColsSetting: Int,
    tvVideoColsSetting: Int,
    mobileColsSetting: Int,
    shouldFocusHeroCard: Boolean,
    initialFocusRequester: FocusRequester,
    viewModel: VideoViewModel
) {
    // Smart Classification Helpers
    val isSeriesItem = { video: Video ->
        video.duration == com.example.utils.VideoType.SERIES
    }

    val isChannelItem = { video: Video ->
        video.duration == com.example.utils.VideoType.CHANNEL || video.id.startsWith("channel_")
    }

    val isPlaylistItem = { video: Video ->
        video.duration == com.example.utils.VideoType.PLAYLIST
    }

    val isFolderItem = { video: Video ->
        video.duration == com.example.utils.VideoType.FOLDER || video.duration == com.example.utils.VideoType.CATALOG
    }

    val isVideoItem = { video: Video ->
        video.duration != com.example.utils.VideoType.FOLDER &&
        video.duration != com.example.utils.VideoType.CHANNEL &&
        !video.id.startsWith("channel_") &&
        video.duration != com.example.utils.VideoType.PLAYLIST &&
        video.duration != com.example.utils.VideoType.SERIES &&
        video.duration != com.example.utils.VideoType.CATALOG
    }

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

    // Render Channels Row at the top if present
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                val listState1 = rememberLazyListState()
                LazyRow(
                    state = listState1,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .mouseDragScrollable(listState1)
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

    // Render Search Videos
    if (searchVideos.isNotEmpty()) {
        val folderItemsSearch = searchVideos.filter(isFolderItem)
        val channelItemsSearch = searchVideos.filter(isChannelItem)
        val playlistItemsSearch = searchVideos.filter(isPlaylistItem)
        val seriesItemsSearch = searchVideos.filter(isSeriesItem)
        val otherVideosSearch = searchVideos.filter(isVideoItem)

        val folderColsSearch = if (isTvOptimized) tvColsSetting else mobileColsSetting
        val seriesColsSearch = if (isTvOptimized) {
            if (folderItemsSearch.isNotEmpty()) tvColsSetting else (tvColsSetting + 1)
        } else {
            mobileColsSetting
        }
        val playlistColsSearch = if (isTvOptimized) {
            if (folderItemsSearch.isNotEmpty()) tvColsSetting else tvColsSetting
        } else {
            mobileColsSetting
        }

        val chunkedFoldersSearch = folderItemsSearch.chunked(folderColsSearch)
        val chunkedSeriesSearch = seriesItemsSearch.chunked(seriesColsSearch)
        val chunkedPlaylistsSearch = playlistItemsSearch.chunked(playlistColsSearch)

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
                    repeat(folderColsSearch - rowItems.size) {
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
                val listState2 = rememberLazyListState()
                LazyRow(
                    state = listState2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .mouseDragScrollable(listState2),
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
                        if (folderItemsSearch.isNotEmpty()) {
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
                    repeat(seriesColsSearch - rowItems.size) {
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
                        if (folderItemsSearch.isNotEmpty()) {
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
                    repeat(playlistColsSearch - rowItems.size) {
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
            if (isTvOptimized) {
                val cols = tvVideoColsSetting
                val chunkedOtherVideos = otherVideosSearch.chunked(cols)
                items(chunkedOtherVideos, key = { "search_video_row_${cols}_${it.hashCode()}" }) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { video ->
                            if (isLargeCardsMode && !isTvOptimized) {
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
                                    isTvOptimized = true,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                SleekVideoGridItem(
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
                                    isTvOptimized = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        repeat(cols - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                if (isLargeCardsMode && !isTvOptimized) {
                    items(otherVideosSearch, key = { "search_large_${it.id}" }) { video ->
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
                            isTvOptimized = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    items(otherVideosSearch, key = { "search_list_${it.id}" }) { video ->
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
                            isTvOptimized = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
