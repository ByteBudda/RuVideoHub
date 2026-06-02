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
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.ProBadgeBg
import com.example.ui.theme.ProBadgeText
import com.example.viewmodel.VideoViewModel
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

        // Voice search active pulse simulation overlay
        val isMicActive by viewModel.isMicrophoneActive.collectAsStateWithLifecycle()
        if (isMicActive) {
            VoiceListeningOverlay(onCancel = { viewModel.toggleMicrophone(false) })
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
                label = "Загрузки",
                icon = Icons.Default.DownloadDone,
                isActive = selectedTab == "downloads",
                onClick = { onTabSelected("downloads") },
                testTag = "tab_downloads"
            )
            BottomTabItem(
                label = "Медиатека",
                icon = Icons.Default.VideoLibrary,
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
                .padding(horizontal = 20.dp, vertical = 6.dp),
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
    val isMicActive by viewModel.isMicrophoneActive.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val apiSource by viewModel.apiSource.collectAsStateWithLifecycle()

    val categories = listOf("Все", "Фильмы", "Сериалы", "Телепередачи", "Музыка", "Мультфильмы", "Спорт", "Юмор", "Видеоигры", "Технологии")

    Column(modifier = modifier.fillMaxSize()) {
        // App search header
        SleekHeader(
            searchQuery = searchQuery,
            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
            isMicActive = isMicActive,
            onMicToggle = { viewModel.toggleMicrophone(it) },
            apiSource = apiSource
        )

        // Filter chips list
        CategoryRow(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Section recommended (header + hero card)
                val heroVideo = filteredVideos.first()
                item {
                    RecommendedSectionHeader()
                    HeroVideoCard(
                        video = heroVideo,
                        onVideoClick = { viewModel.selectVideo(heroVideo) },
                        onDownloadToggle = { viewModel.toggleDownload(heroVideo) }
                    )
                }

                // Section listed items
                if (filteredVideos.size > 1) {
                    items(filteredVideos.subList(1, filteredVideos.size), key = { it.id }) { video ->
                        SecondaryVideoItemRow(
                            video = video,
                            onVideoClick = { viewModel.selectVideo(video) },
                            onDownloadToggle = { viewModel.toggleDownload(video) },
                            onBookmarkToggle = { viewModel.toggleBookmark(video) }
                        )
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
    isMicActive: Boolean,
    onMicToggle: (Boolean) -> Unit,
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
                            text = if (isMicActive) "Слушаю..." else "Поиск видео на Rutube",
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
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(
                onClick = { onMicToggle(!isMicActive) },
                modifier = Modifier
                    .size(28.dp)
                    .testTag("mic_button")
            ) {
                Icon(
                    imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = "Голосовой поиск",
                    tint = if (isMicActive) Primary else GreyText,
                    modifier = Modifier.size(20.dp)
                )
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
                "Gemini Hybrid AI" -> Color(0xFF9C27B0)
                else -> Color(0xFFFF9800)
            }

            val statusLabel = when (apiSource) {
                "Rutube LIVE" -> "Подключено к Rutube LIVE"
                "Gemini Hybrid AI" -> "Умный поиск Gemini AI (Мягкий fallback)"
                else -> "Локальная демоверсия (Офлайн)"
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
                    Text(
                        text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                        color = GreyText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
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
                Text(
                    text = video.channel,
                    fontSize = 10.sp,
                    color = GreyText
                )
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
    val gradientColors = when (id) {
        "api_review" -> listOf(Color(0xFF6750A4), Color(0xFF21005D))
        "top_10" -> listOf(Color(0xFF4B3978), Color(0xFF1B033A))
        "history_rutube" -> listOf(Color(0xFF8B5CF6), Color(0xFF3B0764))
        "android_2026" -> listOf(Color(0xFF0284C7), Color(0xFF0369A1))
        "sleek_compose" -> listOf(Color(0xFFEC4899), Color(0xFFBE185D))
        "recommender_secrets" -> listOf(Color(0xFF10B981), Color(0xFF047857))
        else -> listOf(Color(0xFF333333), Color(0xFF111111))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = gradientColors))
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
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
                                    viewModel.selectCategory(firstItem.title)
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
                                        viewModel.selectCategory(secondItem.title)
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
fun DownloadsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val downloadedVideos by viewModel.downloadedSavedVideos.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()

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
            Text(
                text = "Офлайн-Загрузки",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (downloadedVideos.isNotEmpty()) {
                Surface(
                    color = PrimaryContainer,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "Файлов: ${downloadedVideos.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Active downloads section
        if (activeDownloads.isNotEmpty()) {
            Text(
                text = "Активные загрузки (yt-dlp CLI)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            activeDownloads.values.forEach { dl ->
                var isTerminalExpanded by remember { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryBackground),
                    border = BorderStroke(1.dp, SurfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VideoThumbnail(
                                id = dl.id,
                                duration = "",
                                thumbnailUrl = dl.thumbnailUrl,
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(42.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dl.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val badgeColor = when (dl.status) {
                                        "Extracting" -> Color(0xFFF59E0B)
                                        "Downloading" -> Color(0xFF3B82F6)
                                        "Merging" -> Color(0xFF8B5CF6)
                                        "Completed" -> Color(0xFF10B981)
                                        "Failed" -> Color(0xFFEF4444)
                                        else -> Color.Gray
                                    }
                                    val statusText = when (dl.status) {
                                        "Queued" -> "В очереди"
                                        "Extracting" -> "Анализ линков"
                                        "Downloading" -> "Скачивание"
                                        "Merging" -> "Сборка FFmpeg"
                                        "Completed" -> "Успешно"
                                        "Failed" -> "Ошибка"
                                        else -> dl.status
                                    }

                                    Surface(
                                        color = badgeColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (dl.status == "Downloading") {
                                        Text(
                                            text = "${dl.speed} • ETA ${dl.eta}",
                                            fontSize = 9.sp,
                                            color = GreyText
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { isTerminalExpanded = !isTerminalExpanded },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Терминал",
                                    tint = if (isTerminalExpanded) MaterialTheme.colorScheme.primary else GreyText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (dl.status != "Completed" && dl.status != "Failed") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { dl.progress },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = SurfaceVariant
                                )
                                Text(
                                    text = "${(dl.progress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        if (isTerminalExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val scrollState = rememberScrollState()
                                LaunchedEffect(dl.logs.size) {
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 145.dp)
                                        .verticalScroll(scrollState)
                                        .padding(8.dp)
                                ) {
                                    dl.logs.forEach { line ->
                                        Text(
                                            text = line,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = if (line.contains("[error]")) Color(0xFFF87171) else Color(0xFF34D399),
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (downloadedVideos.isEmpty() && activeDownloads.isEmpty()) {
            EmptyStateContainer(
                title = "Нет загруженных видео",
                hint = "Вы можете скачать любой медиаконтент, нажав на кнопку загрузки в плеере. Все загрузки выполняются через ядро yt-dlp с высокой скоростью!"
            )
        } else if (downloadedVideos.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
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
                        description = "Файл сохранен локально во внутреннем кэше устройства.",
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = saved.channel,
                                    fontSize = 10.sp,
                                    color = GreyText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Доступно офлайн",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleDownload(videoRuntime) },
                                modifier = Modifier.size(32.dp).testTag("delete_download_${saved.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
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
            text = "Медиатека",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Просмотр сохраненного контента и закладок",
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
                val sumDuration = 30 * bookmarkedVideos.size
                Text(
                    text = "$sumDuration мин",
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
                title = "Медиатека пуста",
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

@Composable
fun SleekPlayerDetailOverlay(
    video: Video,
    viewModel: VideoViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.playProgress.collectAsStateWithLifecycle()
    val allVideos by viewModel.allVideos.collectAsStateWithLifecycle()

    val formattedElapsed = viewModel.getFormattedElapsedTime(video.duration, progress)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Player header bar
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

        // Active Player Canvas Render Box (16:9 Aspect ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (isPlaying) {
                RutubeVideoPlayer(
                    videoId = video.id,
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
            Text(
                text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                fontSize = 11.sp,
                color = GreyText,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Direct active action layout pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Download action state button
                Button(
                    onClick = { viewModel.toggleDownload(video) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (video.isDownloaded) Color(0xFF10B981) else PrimaryContainer,
                        contentColor = if (video.isDownloaded) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("player_action_download")
                ) {
                    Icon(
                        imageVector = if (video.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (video.isDownloaded) "Скачано" else "Скачать",
                        fontSize = 12.sp,
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                text = "Рекомендуем далее",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Select 2 related videos matching different categories
            val relatedList = allVideos.filter { it.id != video.id }.take(3)
            relatedList.forEach { related ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.selectVideo(related) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, SurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VideoThumbnail(
                            id = related.id,
                            duration = related.duration,
                            thumbnailUrl = related.thumbnailUrl,
                            modifier = Modifier
                                .width(72.dp)
                                .height(44.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = related.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = related.channel,
                                fontSize = 9.sp,
                                color = GreyText
                            )
                        }
                    }
                }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RutubeVideoPlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
    val offlineFile = java.io.File(downloadFolder, "$videoId.mp4")

    if (offlineFile.exists()) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val mediaController = android.widget.MediaController(ctx)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                    setVideoPath(offlineFile.absolutePath)
                    start()
                }
            },
            update = { videoView ->
                if (!videoView.isPlaying) {
                    videoView.start()
                }
            },
            modifier = modifier
        )
    } else {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    loadUrl("https://rutube.ru/play/embed/$videoId")
                }
            },
            update = { webView ->
                val expectedUrl = "https://rutube.ru/play/embed/$videoId"
                if (webView.url != expectedUrl) {
                    webView.loadUrl(expectedUrl)
                }
            },
            modifier = modifier
        )
    }
}

