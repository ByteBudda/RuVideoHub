package com.example.ui.screens.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.screens.mouseDragScrollable
import com.example.data.Video
import com.example.viewmodel.VideoViewModel
import com.example.ui.screens.home.utils.*

fun LazyListScope.homeFeedContent(
    folderItems: List<Video>,
    channelItems: List<Video>,
    seriesItems: List<Video>,
    playlistItems: List<Video>,
    otherVideos: List<Video>,
    chunkedFolders: List<List<Video>>,
    chunkedSeries: List<List<Video>>,
    chunkedPlaylists: List<List<Video>>,
    folderCols: Int,
    seriesCols: Int,
    playlistCols: Int,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean,
    isLargeCardsMode: Boolean,
    viewModel: VideoViewModel,
    tvVideoColsSetting: Int,
    mobileColsSetting: Int
) {
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
                repeat(folderCols - rowItems.size) {
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
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .mouseDragScrollable(listState),
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
                repeat(seriesCols - rowItems.size) {
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
                repeat(playlistCols - rowItems.size) {
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
        if (isTvOptimized) {
            val cols = tvVideoColsSetting
            val chunkedOtherVideos = otherVideos.chunked(cols)
            items(chunkedOtherVideos, key = { "other_video_row_${cols}_${it.hashCode()}" }) { rowItems ->
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
                items(otherVideos, key = { "other_large_${it.id}" }) { video ->
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
                items(otherVideos, key = { "other_list_${it.id}" }) { video ->
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
