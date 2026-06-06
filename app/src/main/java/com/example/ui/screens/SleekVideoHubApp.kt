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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.theme.OnPrimary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.OnPrimaryContainer
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.ProBadgeBg
import com.example.ui.theme.ProBadgeText
import com.example.viewmodel.VideoViewModel
import com.example.viewmodel.IptvChannel
import com.example.viewmodel.FederalChannel
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale


@Composable
fun SleekVideoHubApp(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentSelectedVideo by viewModel.currentSelectedVideo.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    if (currentSelectedVideo == null && (currentTab != "home" || searchQuery.isNotEmpty())) {
        androidx.activity.compose.BackHandler {
            if (currentTab != "home") {
                viewModel.selectTab("home")
            } else if (searchQuery.isNotEmpty()) {
                viewModel.setSearchQuery("")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            bottomBar = {
                SleekBottomNavigation(
                    selectedTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main switcher content based on selected tab
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "tab_fade",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        "home" -> HomeTabScreen(viewModel = viewModel)
                        "explore" -> ExploreTabScreen(viewModel = viewModel)
                        "tv" -> TvTabScreen(viewModel = viewModel)
                        "recents" -> RecentsTabScreen(viewModel = viewModel)
                        "downloads" -> DownloadsTabScreen(viewModel = viewModel)
                        "library" -> LibraryTabScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // Expanded video player detail overlay - slides from bottom
        AnimatedVisibility(
            visible = currentSelectedVideo != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220)
            )
        ) {
            currentSelectedVideo?.let { video ->
                SleekPlayerDetailOverlay(
                    video = video,
                    viewModel = viewModel,
                    onDismiss = { viewModel.selectVideo(null) }
                )
            }
        }
    }
}

@Composable
fun SleekBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SecondaryBackground,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .border(width = 1.dp, color = SurfaceVariant, shape = androidx.compose.ui.graphics.RectangleShape),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomTabItem(
                label = "Главная",
                icon = Icons.Default.Home,
                isActive = selectedTab == "home",
                onClick = { onTabSelected("home") },
                testTag = "tab_home"
            )
            BottomTabItem(
                label = "Обзор",
                icon = Icons.Default.Explore,
                isActive = selectedTab == "explore",
                onClick = { onTabSelected("explore") },
                testTag = "tab_explore"
            )
            BottomTabItem(
                label = "ТВ",
                icon = Icons.Default.Tv,
                isActive = selectedTab == "tv",
                onClick = { onTabSelected("tv") },
                testTag = "tab_tv"
            )
            BottomTabItem(
                label = "Недавние",
                icon = Icons.Default.History,
                isActive = selectedTab == "recents",
                onClick = { onTabSelected("recents") },
                testTag = "tab_recents"
            )
            BottomTabItem(
                label = "Загрузки",
                icon = Icons.Default.Download,
                isActive = selectedTab == "downloads",
                onClick = { onTabSelected("downloads") },
                testTag = "tab_downloads"
            )
            BottomTabItem(
                label = "Избранное",
                icon = Icons.Default.Favorite,
                isActive = selectedTab == "library",
                onClick = { onTabSelected("library") },
                testTag = "tab_library"
            )
        }
    }
}

@Composable
fun RowScope.BottomTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (isActive) PrimaryContainer else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else GreyText,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.onBackground else GreyText
        )
    }
}

@Composable
fun HomeTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val apiSource by viewModel.apiSource.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // App search header
        SleekHeader(
            searchQuery = searchQuery,
            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
            apiSource = apiSource
        )

        val feedTabs by viewModel.feedTabs.collectAsStateWithLifecycle()
        val selectedFeedTab by viewModel.selectedFeedTab.collectAsStateWithLifecycle()
        val selectedSubfolderName by viewModel.selectedSubfolderName.collectAsStateWithLifecycle()

        if (feedTabs.isNotEmpty()) {
            FeedTabRow(
                tabs = feedTabs,
                selectedTab = selectedFeedTab,
                onTabSelected = { viewModel.selectFeedTab(it) }
            )
        }

        if (selectedSubfolderName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp))
                    .clickable { viewModel.resetSubfolder() }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val isMoreLoading by viewModel.isMoreLoading.collectAsStateWithLifecycle()

        // Video lists
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        } else if (filteredVideos.isEmpty()) {
            EmptySearchState(query = searchQuery)
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            
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

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Section recommended (hero card)
                val heroVideo = filteredVideos.first()
                item {
                    HeroVideoCard(
                        video = heroVideo,
                        onVideoClick = { viewModel.selectVideo(heroVideo) },
                        onDownloadToggle = { viewModel.toggleDownload(heroVideo) },
                        onChannelClick = if (!heroVideo.authorId.isNullOrBlank()) {
                            {
                                val channelDummy = Video(
                                    id = "channel_${heroVideo.authorId}__${heroVideo.authorActionUrl ?: ""}",
                                    title = heroVideo.channel,
                                    channel = heroVideo.channel,
                                    views = "",
                                    timeAgo = "",
                                    duration = "КАНАЛ",
                                    category = heroVideo.category,
                                    description = ""
                                )
                                viewModel.selectVideo(channelDummy)
                            }
                        } else null
                    )
                }

                // Section listed items
                if (filteredVideos.size > 1) {
                    items(filteredVideos.subList(1, filteredVideos.size), key = { it.id }) { video ->
                        SecondaryVideoItemRow(
                            video = video,
                            onVideoClick = { viewModel.selectVideo(video) },
                            onDownloadToggle = { viewModel.toggleDownload(video) },
                            onBookmarkToggle = { viewModel.toggleBookmark(video) },
                            onChannelClick = if (!video.authorId.isNullOrBlank()) {
                                {
                                    val channelDummy = Video(
                                        id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                        title = video.channel,
                                        channel = video.channel,
                                        views = "",
                                        timeAgo = "",
                                        duration = "КАНАЛ",
                                        category = video.category,
                                        description = ""
                                    )
                                    viewModel.selectVideo(channelDummy)
                                }
                            } else null
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
    }
}

@Composable
fun SleekHeader(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    apiSource: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SecondaryBackground)
                .border(
                    width = 1.dp,
                    color = SurfaceVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Поиск",
                tint = GreyText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

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
                    .testTag("search_input"),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Поиск видео на Rutube",
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
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = GreyText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val badgeColor = when (apiSource) {
                "Rutube LIVE" -> Color(0xFF4CAF50)
                "Встроенные хиты" -> Color(0xFF9C27B0)
                else -> Color(0xFFFF9800)
            }

            val statusLabel = when (apiSource) {
                "Rutube LIVE" -> "Подключено к Rutube LIVE"
                "Встроенные хиты" -> "Встроенная медиатека (Офлайн)"
                else -> "Локальный архив (Офлайн)"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusLabel,
                    fontSize = 11.sp,
                    color = GreyText,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "v2.1.0-LIVE",
                fontSize = 10.sp,
                color = GreyText.copy(alpha = 0.5f),
                fontWeight = FontWeight.Light
            )
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
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(100.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = tab.name ?: "Раздел",
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else GreyText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
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
fun HeroVideoCard(
    video: Video,
    onVideoClick: () -> Unit,
    onDownloadToggle: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SurfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = video.channel,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(onClick = onChannelClick)
                            )
                            Text(
                                text = " • ${video.views} • ${video.timeAgo}",
                                color = GreyText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    } else {
                        Text(
                            text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                            color = GreyText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Action buttons right
                IconButton(
                    onClick = onDownloadToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer)
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

@Composable
fun SecondaryVideoItemRow(
    video: Video,
    onVideoClick: () -> Unit,
    onDownloadToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onVideoClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    Text(
                        text = video.channel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onChannelClick)
                    )
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
                    modifier = Modifier.size(24.dp).testTag("quick_download_${video.id}")
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
                    modifier = Modifier.size(24.dp).testTag("quick_bookmark_${video.id}")
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
    val isFolder = duration == "ПАПКА"
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
            Canvas(modifier = Modifier.fillMaxSize()) {
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
fun ExploreTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val realCategories by viewModel.realCategories.collectAsStateWithLifecycle()
    val isCategoriesLoading by viewModel.isCategoriesLoading.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Проводник Rutube",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Исследуйте полный каталог категорий, трансляций и шоу в реальном времени с Rutube",
            fontSize = 12.sp,
            color = GreyText,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            lineHeight = 16.sp
        )

        if (isCategoriesLoading && realCategories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in realCategories.indices step 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        val firstItem = realCategories[i]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(115.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, SurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.selectCategory(firstItem.title, firstItem.target)
                                    viewModel.setSearchQuery("")
                                    viewModel.selectTab("home")
                                }
                        ) {
                            AsyncImage(
                                model = firstItem.picture,
                                contentDescription = firstItem.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                            startY = 50f
                                        )
                                    )
                            )
                            Text(
                                text = firstItem.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }

                        if (i + 1 < realCategories.size) {
                            val secondItem = realCategories[i + 1]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, SurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.selectCategory(secondItem.title, secondItem.target)
                                        viewModel.setSearchQuery("")
                                        viewModel.selectTab("home")
                                    }
                            ) {
                                AsyncImage(
                                    model = secondItem.picture,
                                    contentDescription = secondItem.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                                startY = 50f
                                            )
                                        )
                                )
                                Text(
                                    text = secondItem.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val recentVideos by viewModel.recentSavedVideos.collectAsStateWithLifecycle()

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
                    }
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
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
                        colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
                        border = BorderStroke(1.dp, SurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectVideo(videoRuntime) }
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
                                },
                                modifier = Modifier.size(32.dp).testTag("delete_recent_${saved.id}")
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

@Composable
fun DownloadsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val downloadedVideos by viewModel.downloadedSavedVideos.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val activeList = androidx.compose.runtime.remember(activeDownloads) { activeDownloads.values.toList() }

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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
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
                            colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
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
                                    CircularProgressIndicator(
                                        progress = { active.progress },
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
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
                        colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
                        border = BorderStroke(1.dp, SurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectVideo(videoRuntime) }
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
                                modifier = Modifier.size(32.dp).testTag("delete_download_${saved.id}")
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

@Composable
fun LibraryTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val bookmarkedVideos by viewModel.bookmarkedSavedVideos.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        // Simple Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SecondaryBackground)
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

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Мои Закладки",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (bookmarkedVideos.isEmpty()) {
            EmptyStateContainer(
                title = "Список избранного пуст",
                hint = "Вы можете добавить медиа в этот список, нажав на кнопку закладок на карточке видео."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bookmarkedVideos, key = { it.id }) { saved ->
                     val videoRuntime = Video(
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

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, SurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectVideo(videoRuntime) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            VideoThumbnail(
                                id = saved.id,
                                duration = saved.duration,
                                thumbnailUrl = saved.thumbnailUrl,
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(48.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = saved.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = saved.channel,
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleBookmark(videoRuntime) },
                                modifier = Modifier.size(32.dp).testTag("delete_bookmark_${saved.id}")
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

@Composable
fun VoiceListeningOverlay(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic mic sound pulse animations
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val scale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val opacity by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "opacity"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.7f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, PrimaryContainer, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "Голосовой Поиск",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Слушаю Rutube... Произнесите ключевой запрос",
                fontSize = 11.sp,
                color = GreyText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Dynamic core pulsing ball
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                Box(
                    modifier = Modifier
                        .size(60.dp * scale)
                        .clip(CircleShape)
                        .background(Color(0xFF6750A4).copy(alpha = opacity))
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Микрофон активен",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant, contentColor = GreyText)
            ) {
                Text(text = "Отмена", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

data class EpisodeInfo(
    val baseTitle: String,
    val season: Int,
    val episode: Int,
    val rawNum: Int
)

fun parseEpisode(title: String): EpisodeInfo {
    val lower = title.lowercase()
    
    var season = 1
    var episode = 1
    var rawNum = -1
    var matched = false
    
    // 1. Unified S01E08, S1 Ep 8, 1x08 patterns
    val sExeRegex = Regex("""s\s*(\d+)\s*e\s*(\d+)""", RegexOption.IGNORE_CASE)
    val sEpRegex = Regex("""s\s*(\d+)\s*ep\s*(\d+)""", RegexOption.IGNORE_CASE)
    val xRegex = Regex("""(\d+)\s*x\s*(\d+)""", RegexOption.IGNORE_CASE)
    
    val sexMatch = sExeRegex.find(lower)
    val sEpMatch = sEpRegex.find(lower)
    val xMatch = xRegex.find(lower)
    
    if (sexMatch != null) {
        season = sexMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        episode = sexMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
        rawNum = episode
        matched = true
    } else if (sEpMatch != null) {
        season = sEpMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        episode = sEpMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
        rawNum = episode
        matched = true
    } else if (xMatch != null) {
        season = xMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        episode = xMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
        rawNum = episode
        matched = true
    }
    
    if (!matched) {
        // 2. Russian Combined patterns:
        // A. "1 сезон 8 серия" or "1-й сезон 8-я серия"
        val ruComb1 = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон\w*\s*[,.\s-]*\s*(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")
        // B. "сезон 1 серия 8" or "сезон 1, выпуск 8"
        val ruComb2 = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\s*[,.\s-]*\s*(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)""")
        // C. "1 сезон, серия 8" or "1 сезон, эпизод 8"
        val ruComb3 = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон\w*\s*[,.\s-]*\s*(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)""")
        // D. "сезон 1, 8 серия"
        val ruComb4 = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\s*[,.\s-]*\s*(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")

        val c1 = ruComb1.find(lower)
        val c2 = ruComb2.find(lower)
        val c3 = ruComb3.find(lower)
        val c4 = ruComb4.find(lower)
        
        if (c1 != null) {
            season = c1.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c1.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (c2 != null) {
            season = c2.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c2.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (c3 != null) {
            season = c3.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c3.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (c4 != null) {
            season = c4.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = c4.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        }
    }
    
    if (!matched) {
        // Fallback to standalone matches, but carefully.
        val seasonSuffixRegex = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон""")
        val seasonPrefixRegex = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\b""")
        
        val sSfxMatch = seasonSuffixRegex.find(lower)
        val sPfxMatch = seasonPrefixRegex.find(lower)
        
        if (sSfxMatch != null) {
            season = sSfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        } else if (sPfxMatch != null) {
            season = sPfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        }
        
        val epSuffixRegex = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")
        val epPrefixRegex = Regex("""(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)\b""")
        
        val epSfxMatch = epSuffixRegex.find(lower)
        val epPfxMatch = epPrefixRegex.find(lower)
        
        if (epSfxMatch != null) {
            episode = epSfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            rawNum = episode
        } else if (epPfxMatch != null) {
            episode = epPfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            rawNum = episode
        }
        
        if (rawNum == -1) {
            val digitRegex = Regex("""\d+""")
            val matches = digitRegex.findAll(lower).toList()
            if (matches.isNotEmpty()) {
                val lastNum = matches.last().groupValues.getOrNull(0)?.toIntOrNull() ?: 1
                if (matches.size == 1 && (sSfxMatch != null || sPfxMatch != null)) {
                    episode = 1
                } else {
                    episode = lastNum
                    rawNum = lastNum
                }
            }
        }
    }
    
    var baseTitle = title
        .replace(Regex("""(?i)\bs\d+e\d+\b"""), "")
        .replace(Regex("""(?i)\bs\d+ep\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+x\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*сезон\w*\b"""), "")
        .replace(Regex("""(?i)\bсезон\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*сери\w*\b"""), "")
        .replace(Regex("""(?i)\bсери\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*эпизод\w*\b"""), "")
        .replace(Regex("""(?i)\bэпизод\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*выпуск\w*\b"""), "")
        .replace(Regex("""(?i)\bвыпуск\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*част\w*\b"""), "")
        .replace(Regex("""(?i)\bчаст\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        
    if (baseTitle.length < 3) {
        baseTitle = title.take(15)
    }
    
    return EpisodeInfo(baseTitle, season, episode, if (rawNum != -1) rawNum else 1)
}

fun getSortedEpisodes(currentVideo: Video, allVideos: List<Video>): List<Video> {
    val currentInfo = parseEpisode(currentVideo.title)
    
    // Find all videos with similar base titles, or same channel & same base title word, or same channel
    val matching = allVideos.filter { item ->
        val itemInfo = parseEpisode(item.title)
        val shareBaseTitle = itemInfo.baseTitle.lowercase().split(" ").filter { it.length > 3 }
            .any { word -> currentInfo.baseTitle.lowercase().contains(word) }
        
        shareBaseTitle || item.channel == currentVideo.channel
    }
    
    val sorted = matching.distinctBy { it.id }.sortedWith(compareBy<Video> { 
        val info = parseEpisode(it.title)
        info.season
    }.thenBy { 
        val info = parseEpisode(it.title)
        info.episode
    })
    
    if (sorted.size > 1) {
        return sorted
    }
    
    // If no distinct multiple episodes, treat same category as episodes playlist
    val categoryVideos = allVideos.filter { it.category == currentVideo.category }
    if (categoryVideos.size > 1) {
        return categoryVideos
    }
    
    return allVideos.take(10)
}

@Composable
fun SleekPlayerDetailOverlay(
    video: Video,
    viewModel: VideoViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.playProgress.collectAsStateWithLifecycle()
    val allVideos by viewModel.allVideos.collectAsStateWithLifecycle()

    val currentEpList = remember(video, allVideos) {
        getSortedEpisodes(video, allVideos)
    }
    val currentIndex = currentEpList.indexOfFirst { it.id == video.id }

    val formattedElapsed = viewModel.getFormattedElapsedTime(video.duration, progress)

    var isFullscreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var selectedAspectRatio by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(VlcAspectRatio.FIT) }
    var showDownloadOptionsDialog by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val readGranted = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        if (readGranted || writeGranted || android.os.Build.VERSION.SDK_INT >= 29) {
            viewModel.saveToDevice(video, context) { success, message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Разрешение на работу с файлами отклонено", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Immersive mode orientation and system bar control
    val activity = context as? android.app.Activity
    LaunchedEffect(isFullscreen) {
        val window = activity?.window
        if (isFullscreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    it.insetsController?.hide(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
                }
            }
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            window?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    it.insetsController?.show(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val window = activity?.window
            window?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    it.insetsController?.show(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    androidx.activity.compose.BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onDismiss()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Player header bar - collapses during fullscreen to avoid layout structure alterations
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullscreen) 0.dp else 56.dp)
        ) {
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_player")) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть плеер",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "${video.category} • Воспроизведение",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        letterSpacing = 0.5.sp
                    )

                    Box(modifier = Modifier.size(24.dp)) // empty spacer for symmetry
                }
            }
        }

        // Active Player Canvas Render Box (16:9 Standard Aspect ratio or Fill Screen when Landscape Fullscreen)
        Box(
            modifier = if (isFullscreen) {
                Modifier.weight(1f).fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            }.background(Color.Black)
        ) {
            if (isPlaying) {
                RutubeVideoPlayer(
                    videoId = video.id,
                    viewModel = viewModel,
                    videoTitle = video.title,
                    aspectMode = selectedAspectRatio,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onChangeAspectRatio = { selectedAspectRatio = it },
                    onShare = { shareVideo(context, video) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                VideoThumbnail(
                    id = video.id,
                    duration = video.duration,
                    thumbnailUrl = video.thumbnailUrl,
                    hasPlayOverlay = true,
                    isPlaying = false,
                    onPlayClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!isFullscreen) {
            Spacer(modifier = Modifier.height(12.dp))

            // Detail descriptions and channels metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Title descriptor
                Text(
                    text = video.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Stats info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                ) {
                    if (!video.authorId.isNullOrBlank()) {
                        Text(
                            text = video.channel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                onDismiss()
                                val channelDummy = Video(
                                    id = "channel_${video.authorId}__${video.authorActionUrl ?: ""}",
                                    title = video.channel,
                                    channel = video.channel,
                                    views = "",
                                    timeAgo = "",
                                    duration = "КАНАЛ",
                                    category = video.category,
                                    description = ""
                                )
                                viewModel.selectVideo(channelDummy)
                            }
                        )
                        Text(
                            text = " • ${video.views} • ${video.timeAgo}",
                            fontSize = 11.sp,
                            color = GreyText
                        )
                    } else {
                        Text(
                            text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                            fontSize = 11.sp,
                            color = GreyText
                        )
                    }
                }

                // Direct active action layout pills
                val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
                val activeDownload = activeDownloads[video.id]

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Download action state button
                    Button(
                        onClick = {
                            if (video.isDownloaded) {
                                showDownloadOptionsDialog = true
                            } else {
                                if (activeDownload == null) {
                                    viewModel.toggleDownload(video)
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                video.isDownloaded -> Color(0xFF10B981)
                                activeDownload != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else -> PrimaryContainer
                            },
                            contentColor = when {
                                video.isDownloaded -> Color.White
                                activeDownload != null -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("player_action_download")
                    ) {
                        if (activeDownload != null) {
                            CircularProgressIndicator(
                                progress = { activeDownload.progress },
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (video.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                video.isDownloaded -> "Скачано"
                                activeDownload != null -> "${(activeDownload.progress * 100).toInt()}%"
                                else -> "Скачать"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Bookmark action state button
                    Button(
                        onClick = { viewModel.toggleBookmark(video) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (video.isBookmarked) Primary else SurfaceVariant,
                            contentColor = if (video.isBookmarked) Color.White else GreyText
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("player_action_bookmark")
                    ) {
                        Icon(
                            imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (video.isBookmarked) "Сохранено" else "В закладки",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Share action state button
                    Button(
                        onClick = { shareVideo(context, video) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("player_action_share")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Поделиться",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Beautiful visual active download card info below buttons
                if (activeDownload != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("active_download_progress_card"),
                        colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Скачивание во внутреннюю память...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = activeDownload.status,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { activeDownload.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Скорость: ${activeDownload.speed}",
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                                Text(
                                    text = "Осталось: ${activeDownload.eta}",
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                            }
                        }
                    }
                }

                // Download options Alert Dialog
                if (showDownloadOptionsDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDownloadOptionsDialog = false },
                        title = {
                            Text(
                                "Управление скачанным файлом",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                "Файл сохранен во внутреннем медиа-кэше приложения. Вы можете экспортировать его в папку 'Загрузки' вашего устройства или очистить кэш.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = GreyText
                            )
                        },
                        confirmButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        showDownloadOptionsDialog = false
                                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                                            viewModel.saveToDevice(video, context) { success, message ->
                                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                )
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Сохранить на устройство", fontSize = 11.sp)
                                }

                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showDownloadOptionsDialog = false
                                        viewModel.deleteDownload(video)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Удалить из кэша", fontSize = 11.sp)
                                }

                                androidx.compose.material3.TextButton(
                                    onClick = { showDownloadOptionsDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Отмена", fontSize = 11.sp)
                                }
                            }
                        },
                        dismissButton = {}
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Expandable Description Box card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Описание медиафайла",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.description,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = GreyText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

            // Related videos segment
            Text(
                text = if (currentEpList.size > 1) "Смотреть далее" else "Рекомендуем далее",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            currentEpList.forEach { ep ->
                val isActive = ep.id == video.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.selectVideo(ep) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else SurfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            VideoThumbnail(
                                id = ep.id,
                                duration = ep.duration,
                                thumbnailUrl = ep.thumbnailUrl,
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(48.dp)
                            )
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .height(48.dp)
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Играет сейчас",
                                        tint = Primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val epInfo = parseEpisode(ep.title)
                            Text(
                                text = ep.title,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Сезон ${epInfo.season} • Серия ${epInfo.episode}",
                                    fontSize = 9.sp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else GreyText
                                )
                                if (isActive) {
                                    Text(
                                        text = "Воспроизведение",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary,
                                        modifier = Modifier
                                            .background(Primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

@Composable
fun SimulatedPlaybackBars(modifier: Modifier = Modifier) {
    // Generate simple pulsing lines at the bottom of the black player background
    val transition = rememberInfiniteTransition(label = "audio_visualizer")
    val p1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "p2"
    )
    val p3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p3"
    )

    Row(
        modifier = modifier
            .background(Color(0xFF0F0F1A))
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        val barCount = 12
        for (i in 0 until barCount) {
            val scale = when (i % 3) {
                0 -> p1
                1 -> p2
                else -> p3
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .fillMaxHeight(scale * 0.4f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Primary.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
fun RutubeVideoPlayer(
    videoId: String,
    viewModel: VideoViewModel,
    videoTitle: String = "",
    aspectMode: VlcAspectRatio = VlcAspectRatio.FIT,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onChangeAspectRatio: (VlcAspectRatio) -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
    val offlineFile = java.io.File(downloadFolder, "$videoId.mp4")

    var hlsUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var loadError by remember(videoId) { mutableStateOf<String?>(null) }
    var useEmbedPlayer by remember(videoId) { mutableStateOf(false) }

    // Position & duration states for custom controls
    var isPlayingState by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }

    // HUD message for aspect ratio cycle
    var hudMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId) {
        if (offlineFile.exists()) {
            hlsUrl = offlineFile.absolutePath
            isLoading = false
        } else {
            isLoading = true
            loadError = null
            useEmbedPlayer = false
            val resolvedUrl = viewModel.fetchHlsStreamUrl(videoId)
            if (resolvedUrl != null) {
                hlsUrl = resolvedUrl
                isLoading = false
            } else {
                useEmbedPlayer = true
                isLoading = false
            }
        }
    }

    val exoPlayer = remember(videoId, hlsUrl) {
        if (hlsUrl == null) null else {
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                playWhenReady = isPlayingState
                val uri = if (offlineFile.exists()) {
                    android.net.Uri.fromFile(offlineFile)
                } else {
                    android.net.Uri.parse(hlsUrl)
                }

                if (!offlineFile.exists() && hlsUrl!!.contains(".m3u8")) {
                    val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .setDefaultRequestProperties(mapOf(
                            "Accept" to "*/*",
                            "Referer" to "https://rutube.ru/"
                        ))
                    val mediaSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(androidx.media3.common.MediaItem.fromUri(uri))
                    setMediaSource(mediaSource)
                } else {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                }

                prepare()

                val savedPos = viewModel.getVideoPosition(videoId)
                if (savedPos > 0L) {
                    seekTo(savedPos)
                    currentPos = savedPos
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    LaunchedEffect(isPlayingState, exoPlayer) {
        exoPlayer?.playWhenReady = isPlayingState
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                useEmbedPlayer = true
            }
        })
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(4000)
            if (System.currentTimeMillis() - lastInteractionTime >= 4000) {
                controlsVisible = false
            }
        }
    }

    // Clear HUD message after 1.5 seconds
    LaunchedEffect(hudMessage) {
        if (hudMessage != null) {
            kotlinx.coroutines.delay(1500)
            hudMessage = null
        }
    }

    // Progress update loop
    LaunchedEffect(exoPlayer, isPlayingState) {
        while (isPlayingState && exoPlayer != null) {
            if (exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition
                if (pos > 0L) {
                    currentPos = pos
                    val dur = exoPlayer.duration
                    if (dur > 0L) {
                        totalDuration = dur
                    }
                    viewModel.saveVideoPosition(videoId, currentPos)
                }
            }
            kotlinx.coroutines.delay(250)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                lastInteractionTime = System.currentTimeMillis()
                controlsVisible = !controlsVisible
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Получение видеопотока...",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (useEmbedPlayer) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mediaPlaybackRequiresUserGesture = false
                                databaseEnabled = true
                            }
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            val embedUrl = "https://rutube.ru/play/embed/$videoId/?autoplay=1"
                            loadUrl(embedUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay Controls inside embed player web layout
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Во весь экран",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { 
                            useEmbedPlayer = false 
                            hlsUrl = null 
                            loadError = "Переключен назад на стандартный плеер." 
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Стандартный плеер",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else if (loadError != null) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Ошибка",
                    tint = Color.Red,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = loadError ?: "Ошибка воспроизведения",
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isLoading = true
                            loadError = null
                            hlsUrl = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Повторить", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            useEmbedPlayer = true
                            loadError = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Embed-плеер", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        } else if (hlsUrl != null) {
            // Video View Container
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        player = exoPlayer
                        resizeMode = when (aspectMode) {
                            VlcAspectRatio.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            VlcAspectRatio.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = when (aspectMode) {
                        VlcAspectRatio.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VlcAspectRatio.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VlcAspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Transparent overlay for Controls
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            lastInteractionTime = System.currentTimeMillis()
                            controlsVisible = false
                        }
                ) {
                    // Top Bar Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isFullscreen) {
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (currentPos > 0) {
                                            viewModel.saveVideoPosition(videoId, currentPos)
                                        }
                                        onToggleFullscreen()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Выйти из полного экрана",
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = if (offlineFile.exists()) "$videoTitle • Offline" else videoTitle.ifBlank { "Rutube Онлайн-превью" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        // Right actions (Aspect ratio & Share)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Cycle Aspect Ratio option like VLC
                            Button(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val nextMode = aspectMode.next()
                                    hudMessage = "Соотношение: ${nextMode.displayName}"
                                    onChangeAspectRatio(nextMode)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(aspectMode.displayName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Share video link
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    onShare()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Поделиться",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Center playback buttons (Rewind, Play/Pause, Forward)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}, // consume clicks to avoid hiding controls
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                                    player.seekTo(newPos)
                                    currentPos = newPos
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Назад 10с",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) {
                                        player.pause()
                                        isPlayingState = false
                                    } else {
                                        player.play()
                                        isPlayingState = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Primary.copy(alpha = 0.9f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Пауза/Пуск",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                    player.seekTo(newPos)
                                    currentPos = newPos
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Вперед 10с",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Bottom Bar Controls with Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(bottom = if (isFullscreen) 16.dp else 6.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {} // Consume touch events
                    ) {
                        // Progress Slider Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatMillis(currentPos),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            androidx.compose.material3.Slider(
                                value = currentPos.toFloat().coerceIn(0f, totalDuration.toFloat().coerceAtLeast(1f)),
                                onValueChange = { newValue ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    currentPos = newValue.toLong()
                                    exoPlayer?.seekTo(newValue.toLong())
                                },
                                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = Primary,
                                    activeTrackColor = Primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            )

                            Text(
                                text = formatMillis(totalDuration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Fullscreen Toggle action
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (currentPos > 0) {
                                        viewModel.saveVideoPosition(videoId, currentPos)
                                    }
                                    onToggleFullscreen()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.Close else Icons.Default.AspectRatio,
                                    contentDescription = "Во весь экран",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // HUD notification for Aspect Ratio cycles
            hudMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = msg, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// VLC-style aspect ratios enum
enum class VlcAspectRatio(val displayName: String) {
    FIT("Вписать"),
    FILL("Заполнить"),
    STRETCH("Растянуть"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3");

    fun next(): VlcAspectRatio {
        val entries = values()
        return entries[(ordinal + 1) % entries.size]
    }
}

// Share Video Intent launcher
fun shareVideo(context: android.content.Context, video: Video) {
    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, "Смотрю видео в Rutube Hub: \"${video.title}\"\n\nПосмотреть на Rutube: https://rutube.ru/video/${video.id}/")
        type = "text/plain"
    }
    val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться видео")
    context.startActivity(shareIntent)
}

// Format duration helper
fun formatMillis(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

// --- Dynamic IPTV and Federal TV Tab Screens ---
@Composable
fun TvTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tvMode by viewModel.tvMode.collectAsStateWithLifecycle()
    val iptvChannels by viewModel.iptvChannels.collectAsStateWithLifecycle()
    val isIptvLoading by viewModel.isIptvLoading.collectAsStateWithLifecycle()
    val iptvLoadError by viewModel.iptvLoadError.collectAsStateWithLifecycle()
    val selectedIptvChannel by viewModel.selectedIptvChannel.collectAsStateWithLifecycle()
    val selectedFederalChannel by viewModel.selectedFederalChannel.collectAsStateWithLifecycle()
    val iptvGroups by viewModel.iptvGroups.collectAsStateWithLifecycle()
    val selectedIptvGroup by viewModel.selectedIptvGroup.collectAsStateWithLifecycle()
    val tvSearchQuery by viewModel.tvSearchQuery.collectAsStateWithLifecycle()

    // Filter IPTV channels
    val filteredIptvChannels = remember(iptvChannels, selectedIptvGroup, tvSearchQuery) {
        iptvChannels.filter { channel ->
            val matchesGroup = (selectedIptvGroup == "Все" || channel.group == selectedIptvGroup)
            val matchesSearch = (tvSearchQuery.isEmpty() || channel.name.contains(tvSearchQuery, ignoreCase = true))
            matchesGroup && matchesSearch
        }
    }

    // Filter Federal channels
    val filteredFederalChannels = remember(viewModel.federalChannelsList, tvSearchQuery) {
        viewModel.federalChannelsList.filter { channel ->
            tvSearchQuery.isEmpty() || channel.name.contains(tvSearchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- TV PLAYER CANVAS RENDER BOX (Top Sticky Panel) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .aspectRatio(16f / 9f)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedIptvChannel != null) {
                // Play custom IPTV channel
                IptvLiveStreamPlayer(
                    streamUrl = selectedIptvChannel!!.url,
                    channelName = selectedIptvChannel!!.name,
                    keyId = selectedIptvChannel!!.id
                )
            } else if (selectedFederalChannel != null) {
                // Play Rutube Federal channel embed via WebView
                FederalWebPlayer(
                    embedUrl = selectedFederalChannel!!.embedUrl,
                    channelName = selectedFederalChannel!!.name
                )
            } else {
                // TV Player Welcome Empty Canvas
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = "ТВ Эфир",
                        tint = Primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Выберите канал для запуска трансляции",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Доступно более сотни динамических IPTV каналов и федеральных Rutube стримов",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // --- SECTION SELECTOR (Material Segmented Controls) ---
        Surface(
            color = SecondaryBackground,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                listOf(
                    Pair("iptv", "IPTV Плейлист"),
                    Pair("federal", "Федеральные РФ")
                ).forEach { modeItem ->
                    val isSelected = tvMode == modeItem.first
                    Box(
                        modifier = Modifier
                            .weight(1.0f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                viewModel.setTvMode(modeItem.first)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeItem.second,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // --- SEARCH INPUT BAR ---
        OutlinedTextField(
            value = tvSearchQuery,
            onValueChange = { viewModel.setTvSearchQuery(it) },
            placeholder = { Text("Поиск каналов...", fontSize = 13.sp, color = GreyText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск", tint = GreyText) },
            trailingIcon = {
                if (tvSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setTvSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить", tint = GreyText)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SecondaryBackground,
                unfocusedContainerColor = SecondaryBackground,
                focusedBorderColor = Primary.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent
            )
        )

        // --- CONTEXT FILTERING CHIPS (Only for custom IPTV Playlist) ---
        if (tvMode == "iptv" && iptvGroups.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(iptvGroups) { group ->
                    val isSelected = selectedIptvGroup == group
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectIptvGroup(group) },
                        label = { Text(group, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryContainer,
                            selectedLabelColor = OnPrimaryContainer
                        )
                    )
                }
            }
        }

        // --- CHANNEL CONTENT GRID/LIST ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (tvMode == "iptv" && isIptvLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Загрузка IPTV плейлиста...", color = GreyText, fontSize = 13.sp)
                }
            } else if (tvMode == "iptv" && iptvLoadError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = "Error", modifier = Modifier.size(48.dp), tint = Color.Red.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(iptvLoadError!!, color = Color.Red, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.fetchIptvPlaylist() },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("Повторить попытку")
                    }
                }
            } else {
                val currentList = if (tvMode == "iptv") filteredIptvChannels else filteredFederalChannels

                if (currentList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.TvOff, contentDescription = "Каналов нет", modifier = Modifier.size(48.dp), tint = GreyText.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Каналы не найдены", color = GreyText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(currentList) { item ->
                            when (item) {
                                is IptvChannel -> {
                                    val isSelected = selectedIptvChannel?.id == item.id
                                    TvChannelRow(
                                        name = item.name,
                                        logoUrl = item.logoUrl,
                                        badge = item.group,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectIptvChannel(item) }
                                    )
                                }
                                is FederalChannel -> {
                                    val isSelected = selectedFederalChannel?.name == item.name
                                    TvChannelRow(
                                        name = item.name,
                                        logoUrl = null,
                                        badge = "Федеральный РФ",
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectFederalChannel(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvChannelRow(
    name: String,
    logoUrl: String?,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tv_channel_${name.hashCode()}")
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Primary else SurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) PrimaryContainer.copy(alpha = 0.5f) else SecondaryBackground
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Logo / Indicator Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (!logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Visual beautiful initials placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Primary, PrimaryContainer))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(2).uppercase(),
                            color = OnPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1.0f)
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badge.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaryContainer.copy(alpha = 0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Icon(
                imageVector = if (isSelected) Icons.Default.PlayCircleFilled else Icons.Default.PlayArrow,
                contentDescription = "Воспроизвести",
                tint = if (isSelected) Primary else GreyText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun IptvLiveStreamPlayer(
    streamUrl: String,
    channelName: String,
    keyId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember(keyId) { mutableStateOf(true) }
    var isMuted by remember(keyId) { mutableStateOf(false) }
    var isVideoLoading by remember(keyId) { mutableStateOf(true) }
    var playErrorMsg by remember(keyId) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(keyId, streamUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            val uri = android.net.Uri.parse(streamUrl)
            
            // Handle HLS / dynamic streams safely
            val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .setDefaultRequestProperties(mapOf("Accept" to "*/*"))
            
            val mediaSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(androidx.media3.common.MediaItem.fromUri(uri))
            
            setMediaSource(mediaSource)
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isVideoLoading = state == androidx.media3.common.Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playErrorMsg = "Ошибка воспроизведения: ${error.localizedMessage ?: "Сбой потока"}"
                isVideoLoading = false
            }
        })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Render Canvas
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Stream Info Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Red.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ЭФИР",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Action Overlay Controllers (Mute & Pause)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mute Switch toggle
            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Включить звук" else "Выключить звук",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Play / Pause toggle
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Старт",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Loading Overlay Spinner
        if (isVideoLoading) {
            CircularProgressIndicator(
                color = OnPrimary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )
        }

        // Stream Fail Overlay Option helper
        if (playErrorMsg != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Ошибка",
                    tint = Color.Red,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Не удалось открыть поток live ТВ",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playErrorMsg!!,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FederalWebPlayer(
    embedUrl: String,
    channelName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                    }
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                webView.loadUrl(embedUrl)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Title watermarks for federal channels
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RUTUBE WEB",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

