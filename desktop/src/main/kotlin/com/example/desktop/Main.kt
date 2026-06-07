package com.example.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// Desktop representing model
data class DesktopVideo(
    val id: String,
    val title: String,
    val channel: String,
    val views: String,
    val timeAgo: String,
    val duration: String,
    val category: String,
    val description: String,
    val thumbnailUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF253E), // Pure Elegant Deep Red
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF1F1F1F),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        var selectedCategory by remember { mutableStateOf("Фильмы") }
        val categories = listOf("Главная", "Фильмы", "Сериалы", "Дорамы", "Аниме", "Детям")
        var searchQuery by remember { mutableStateOf("") }
        
        // Comprehensive mock items mirroring the smart Rutube channels and items
        val sampleVideos = remember {
            listOf(
                DesktopVideo(
                    id = "v1",
                    title = "Невероятное приключение в горах - Эпизод 1",
                    channel = "Travel World",
                    views = "14K просмотров",
                    timeAgo = "2 дня назад",
                    duration = "10:24",
                    category = "Фильмы",
                    description = "Смотрите наше большое путешествие к вершинам Тянь-Шаня!"
                ),
                DesktopVideo(
                    id = "v2",
                    title = "Обзор шедевров мирового кино и новинок сериалов 2026 года",
                    channel = "КиноМания Pro",
                    views = "8.2K просмотров",
                    timeAgo = "5 часов назад",
                    duration = "15:40",
                    category = "Фильмы",
                    description = "Какое кино посмотреть на грядущих долгожданных выходных? Наш честный разбор."
                ),
                DesktopVideo(
                    id = "v3",
                    title = "Дорама 'Свет твоих ласковых глаз' - Официальный тизер",
                    channel = "AsianDrama Hub",
                    views = "32K просмотров",
                    timeAgo = "1 день назад",
                    duration = "02:15",
                    category = "Дорамы",
                    description = "Новая мелодрама, покорившая сердца миллионов зрителей!"
                ),
                DesktopVideo(
                    id = "v4",
                    title = "Топ 10 фантастических сериалов текущего десятилетия",
                    channel = "SciFi Club",
                    views = "45K просмотров",
                    timeAgo = "3 недели назад",
                    duration = "22:10",
                    category = "Сериалы",
                    description = "Потрясающие космические саги, которые вы обязаны посмотреть прямо сейчас."
                ),
                DesktopVideo(
                    id = "v5",
                    title = "Прекрасная дорама про расследования императора",
                    channel = "Китайский Кинематограф",
                    views = "12K просмотров",
                    timeAgo = "3 дня назад",
                    duration = "45:00",
                    category = "Дорамы",
                    description = "Увлекательный детектив в исторических декорациях Древнего Китая."
                ),
                DesktopVideo(
                    id = "v6",
                    title = "Главный обзор новинок Аниме летнего сезона",
                    channel = "Otaku Paradise",
                    views = "9.1K просмотров",
                    timeAgo = "Вчера",
                    duration = "18:22",
                    category = "Аниме",
                    description = "Все главные тайтлы сезона в одном большом разборе."
                )
            )
        }

        val filteredVideos = sampleVideos.filter {
            val matchesCategory = if (selectedCategory == "Главная") true else it.category == selectedCategory
            val matchesSearch = searchQuery.isEmpty() || 
                    it.title.contains(searchQuery, ignoreCase = true) || 
                    it.channel.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp).padding(end = 8.dp)
                            )
                            Text("Sleek Video Hub (Desktop)", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF141414)
                    ),
                    actions = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск фильмов, сериалов, дорам...", fontSize = 13.sp, color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.width(340.dp).padding(vertical = 4.dp, horizontal = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF2C2C2C),
                                focusedContainerColor = Color(0xFF1E1E1E),
                                unfocusedContainerColor = Color(0xFF161616)
                            )
                        )
                    }
                )
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Sidebar Navigation (Desktop Responsive Rail)
                NavigationRail(
                    containerColor = Color(0xFF141414),
                    modifier = Modifier.fillMaxHeight().width(120.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    categories.forEach { category ->
                        NavigationRailItem(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            icon = {
                                Icon(
                                    imageVector = when (category) {
                                        "Главная" -> Icons.Default.Home
                                        "Фильмы" -> Icons.Default.Movie
                                        "Сериалы" -> Icons.Default.Tv
                                        "Дорамы" -> Icons.Default.Favorite
                                        "Аниме" -> Icons.Default.AutoAwesome
                                        else -> Icons.Default.ChildCare
                                    },
                                    contentDescription = category
                                )
                            },
                            label = { Text(category, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // Main Content Screen (Sleek List matching the app's style & padding)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    if (filteredVideos.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = "Empty",
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Здесь пока ничего нет", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Попробуйте выбрать другой раздел или изменить запрос", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp).align(Alignment.TopCenter)
                        ) {
                            item {
                                Text(
                                    text = if (selectedCategory == "Главная") "Все рекомендации" else selectedCategory,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(filteredVideos, key = { it.id }) { video ->
                                DesktopVideoItemRow(video = video)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopVideoItemRow(video: DesktopVideo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Play video command on Desktop */ },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262626))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Video Thumbnail Frame
            Box(
                modifier = Modifier
                    .size(width = 200.dp, height = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(44.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Detailed Video Information
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = video.channel,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = video.views,
                        color = Color(0xFFA0A0A0),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = video.timeAgo,
                        color = Color(0xFFA0A0A0),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = video.description,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun main() = application {
    val windowState = rememberWindowState(width = 1150.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Sleek Video Hub - Premium Desktop"
    ) {
        DesktopApp()
    }
}
