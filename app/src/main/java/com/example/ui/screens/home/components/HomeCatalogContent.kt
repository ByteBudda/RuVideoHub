package com.example.ui.screens.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.Video
import com.example.viewmodel.VideoViewModel
import com.example.ui.screens.home.utils.*

fun LazyListScope.homeCatalogContent(
    groupedCatalogItems: Map<String, List<Video>>,
    catalogExpandedState: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean,
    isLargeCardsMode: Boolean,
    viewModel: VideoViewModel,
    tvVideoColsSetting: Int,
    mobileColsSetting: Int
) {
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

            val groupFolderCols = if (isTvOptimized) 4 else 2
            val groupSeriesCols = if (isTvOptimized) {
                if (groupFolders.isNotEmpty()) 4 else 5
            } else {
                2
            }
            val groupPlaylistCols = if (isTvOptimized) {
                if (groupFolders.isNotEmpty()) 4 else 4
            } else {
                2
            }

            val chunkedGroupFolders = groupFolders.chunked(groupFolderCols)
            val chunkedGroupSeries = groupSeries.chunked(groupSeriesCols)
            val chunkedGroupPlaylists = groupPlaylists.chunked(groupPlaylistCols)

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
                        repeat(groupFolderCols - rowItems.size) {
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
                            if (groupFolders.isNotEmpty()) {
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
                        repeat(groupSeriesCols - rowItems.size) {
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
                            if (groupFolders.isNotEmpty()) {
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
                        repeat(groupPlaylistCols - rowItems.size) {
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
                if (isTvOptimized) {
                    val cols = tvVideoColsSetting
                    val chunkedGroupVideos = groupVideos.chunked(cols)
                    items(chunkedGroupVideos, key = { "group_video_row_${groupName}_${cols}_${it.hashCode()}" }) { rowItems ->
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
                        items(groupVideos, key = { "group_video_large_${groupName}_${it.id}" }) { video ->
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
                        items(groupVideos, key = { "group_video_list_${groupName}_${it.id}" }) { video ->
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
}
