package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.SurfaceVariant
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.liquidGlass
import com.example.viewmodel.VideoViewModel
import com.example.ui.theme.CustomTheme
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val appEffect by viewModel.appEffect.collectAsStateWithLifecycle()
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()
    val isLargeCardsMode by viewModel.isLargeCardsMode.collectAsStateWithLifecycle()
    val playerQuality by viewModel.playerQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val startPageType by viewModel.startPageType.collectAsStateWithLifecycle()
    val startPageCategory by viewModel.startPageCategory.collectAsStateWithLifecycle()
    val startPageCustomUrl by viewModel.startPageCustomUrl.collectAsStateWithLifecycle()
    val bookmarkedVideos by viewModel.bookmarkedSavedVideos.collectAsStateWithLifecycle()
    val customThemes by viewModel.customThemes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var themeStatusMessage by remember { mutableStateOf("") }
    
    val openThemeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val jsonText = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                    if (jsonText.isNotBlank()) {
                        val newTheme = com.example.ui.theme.CustomTheme.fromJson(jsonText)
                        viewModel.addCustomTheme(newTheme)
                        viewModel.setAppTheme(newTheme.id)
                        themeStatusMessage = "Тема '${newTheme.name}' успешно импортирована!"
                    }
                } catch (e: Exception) {
                    themeStatusMessage = "Ошибка импорта: ${e.localizedMessage}"
                }
            }
        }
    }

    val tvGridColumns by viewModel.tvGridColumns.collectAsStateWithLifecycle()
    val tvVideoGridColumns by viewModel.tvVideoGridColumns.collectAsStateWithLifecycle()
    val mobileGridColumns by viewModel.mobileGridColumns.collectAsStateWithLifecycle()
    val focusStyle by viewModel.focusStyle.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Screen Header
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Text(
                text = "Настройки",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Конфигурация приложения, тем и качества медиапотока",
                fontSize = 12.sp,
                color = GreyText,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Section: Interface Settings
        Text(
            text = "Интерфейс",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // App Theme row
            var themeDropdownExpanded by remember { mutableStateOf(false) }
            val themeLabel = when (appTheme) {
                "dark" -> "Тёмная"
                "light" -> "Светлая"
                "slate" -> "Тёмная нейтральная"
                "sepia" -> "Светлая нейтральная"
                "cyberpunk" -> "Киберпанк (Неон)"
                "amoled" -> "Pure Black (AMOLED)"
                else -> customThemes.find { it.id == appTheme }?.name ?: "Тёмная"
            }

            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Цветовая тема",
                subtitle = "Выберите оформление интерфейса"
            ) {
                DropdownButton(
                    label = themeLabel,
                    expanded = themeDropdownExpanded,
                    onExpand = { themeDropdownExpanded = true },
                    onDismiss = { themeDropdownExpanded = false }
                ) {
                    val baseThemes = listOf(
                        "dark" to "Тёмная",
                        "light" to "Светлая",
                        "slate" to "Тёмная нейтральная",
                        "sepia" to "Светлая нейтральная",
                        "cyberpunk" to "Киберпанк (Неон)",
                        "amoled" to "Pure Black (AMOLED)"
                    )
                    val allThemes = baseThemes + customThemes.map { it.id to it.name }
                    allThemes.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                viewModel.setAppTheme(id)
                                themeDropdownExpanded = false
                            },
                            trailingIcon = {
                                if (customThemes.any { it.id == id }) {
                                    IconButton(onClick = { viewModel.removeCustomTheme(id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить тему",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Import Custom Theme
            SettingsItem(
                icon = Icons.Default.Style,
                title = "Импорт темы (.rvht)",
                subtitle = if (themeStatusMessage.isNotBlank()) themeStatusMessage else "Загрузить пользовательскую тему из файла",
                subtitleColor = if (themeStatusMessage.isNotBlank()) MaterialTheme.colorScheme.primary else GreyText,
                onClick = { openThemeLauncher.launch(arrayOf("*/*")) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // TV Mode Toggle row
            SettingsItem(
                icon = Icons.Default.Tv,
                title = "ТВ-оптимизация",
                subtitle = "Адаптировать интерфейс для управления пультом"
            ) {
                Switch(
                    checked = isTvOptimized,
                    onCheckedChange = { viewModel.toggleTvOptimized() },
                    modifier = Modifier.testTag("setting_tv_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Отображение",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!isTvOptimized) {
                SettingsItem(
                    icon = Icons.Default.ViewList,
                    title = "Крупные карточки",
                    subtitle = "Использовать большой размер для видео в списках"
                ) {
                    Switch(
                        checked = isLargeCardsMode,
                        onCheckedChange = { viewModel.toggleLargeCardsMode() },
                        modifier = Modifier.testTag("setting_large_cards_switch")
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            if (isTvOptimized) {
                // TV Grid columns
                var tvGridDropdownExpanded by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.GridOn,
                    title = "Колонок в сетке (ТВ)",
                    subtitle = "Количество столбцов при ТВ-оптимизации"
                ) {
                    DropdownButton(
                        label = "$tvGridColumns",
                        expanded = tvGridDropdownExpanded,
                        onExpand = { tvGridDropdownExpanded = true },
                        onDismiss = { tvGridDropdownExpanded = false }
                    ) {
                        listOf(3, 4, 5, 6).forEach { cols ->
                            DropdownMenuItem(
                                text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setTvGridColumns(cols)
                                    tvGridDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // TV Video Grid columns
                var tvVideoGridDropdownExpanded by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.OndemandVideo,
                    title = "Колонок в видео сетке (ТВ)",
                    subtitle = "Количество столбцов для списка видео на ТВ"
                ) {
                    DropdownButton(
                        label = "$tvVideoGridColumns",
                        expanded = tvVideoGridDropdownExpanded,
                        onExpand = { tvVideoGridDropdownExpanded = true },
                        onDismiss = { tvVideoGridDropdownExpanded = false }
                    ) {
                        listOf(3, 4, 5, 6, 7, 8).forEach { cols ->
                            DropdownMenuItem(
                                text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setTvVideoGridColumns(cols)
                                    tvVideoGridDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            if (!isTvOptimized) {
                // Mobile Grid columns
                var mobileGridDropdownExpanded by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.GridView,
                    title = "Колонок в сетке (Мобильный)",
                    subtitle = "Количество столбцов на мобильных устройствах"
                ) {
                    DropdownButton(
                        label = "$mobileGridColumns",
                        expanded = mobileGridDropdownExpanded,
                        onExpand = { mobileGridDropdownExpanded = true },
                        onDismiss = { mobileGridDropdownExpanded = false }
                    ) {
                        listOf(1, 2, 3).forEach { cols ->
                            DropdownMenuItem(
                                text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setMobileGridColumns(cols)
                                    mobileGridDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            if (isTvOptimized) {
                // TV Focus Highlight Style
                var focusStyleDropdownExpanded by remember { mutableStateOf(false) }
                val focusStyleLabel = when (focusStyle) {
                    "scale" -> "Масштабирование"
                    "scale_glow" -> "Масштаб + Свечение"
                    "classic" -> "Простая рамка"
                    "tint" -> "Подсветка фона"
                    else -> "Свечение границ"
                }

                SettingsItem(
                    icon = Icons.Default.CenterFocusStrong,
                    title = "Стиль фокуса ТВ",
                    subtitle = "Эффект подсветки при наведении пультом"
                ) {
                    DropdownButton(
                        label = focusStyleLabel,
                        expanded = focusStyleDropdownExpanded,
                        onExpand = { focusStyleDropdownExpanded = true },
                        onDismiss = { focusStyleDropdownExpanded = false }
                    ) {
                        listOf(
                            "glow" to "Свечение границ",
                            "scale" to "Масштабирование",
                            "scale_glow" to "Масштаб + Свечение",
                            "classic" to "Простая рамка",
                            "tint" to "Подсветка фона"
                        ).forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setFocusStyle(id)
                                    focusStyleDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Quality Settings
        Text(
            text = "Качество воспроизведения и загрузки",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Player Default Quality
            var dropdownExpanded by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.VideoLibrary,
                title = "Качество плеера",
                subtitle = "Качество по умолчанию при запуске видео"
            ) {
                DropdownButton(
                    label = playerQuality,
                    expanded = dropdownExpanded,
                    onExpand = { dropdownExpanded = true },
                    onDismiss = { dropdownExpanded = false }
                ) {
                    listOf("Авто", "2160p", "1440p", "1080p", "720p", "480p", "360p").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                viewModel.setPlayerQuality(opt)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Downloader Default Quality
            var dlDropdownExpanded by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Качество загрузки",
                subtitle = "Желаемое качество при скачивании"
            ) {
                DropdownButton(
                    label = downloadQuality,
                    expanded = dlDropdownExpanded,
                    onExpand = { dlDropdownExpanded = true },
                    onDismiss = { dlDropdownExpanded = false }
                ) {
                    listOf("2160p", "1440p", "1080p", "720p", "480p", "360p").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                viewModel.setDownloadQuality(opt)
                                dlDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Start Page Settings
        Text(
            text = "Стартовый экран",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Choice 1: Start page type selection
            var typeDropdownExpanded by remember { mutableStateOf(false) }
            val typeLabel = when (startPageType) {
                "category" -> "Выбранная категория"
                "custom_url" -> "Ссылка Rutube"
                "favorite" -> "Элемент из избранного"
                else -> "По умолчанию (Фильмы)"
            }

            SettingsItem(
                icon = Icons.Default.Home,
                title = "Тип стартовой страницы",
                subtitle = "Что открывать при запуске приложения"
            ) {
                DropdownButton(
                    label = typeLabel,
                    expanded = typeDropdownExpanded,
                    onExpand = { typeDropdownExpanded = true },
                    onDismiss = { typeDropdownExpanded = false }
                ) {
                    listOf(
                        "default" to "По умолчанию (Фильмы)",
                        "category" to "Выбрать категорию",
                        "custom_url" to "Своя ссылка Rutube",
                        "favorite" to "Элемент из избранного"
                    ).forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                viewModel.setStartPageType(id)
                                typeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Conditionally show favorite selection
            if (startPageType == "favorite") {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                
                var favDropdownExpanded by remember { mutableStateOf(false) }
                val nonVideoBookmarks = remember(bookmarkedVideos) {
                    bookmarkedVideos.filter { saved ->
                        saved.duration in listOf("ПАПКА", "КАТАЛОГ", "СЕРИАЛ", "КАНАЛ", "ПЛЕЙЛИСТ")
                    }
                }
                val startPageFavoriteTitle by viewModel.startPageFavoriteTitle.collectAsStateWithLifecycle()

                SettingsItem(
                    icon = Icons.Default.Favorite,
                    title = "Элемент из избранного",
                    subtitle = "Выберите плейлист, подкатегорию или канал"
                ) {
                    DropdownButton(
                        label = if (startPageFavoriteTitle.isNotBlank()) startPageFavoriteTitle else "Не выбрано",
                        expanded = favDropdownExpanded,
                        onExpand = { favDropdownExpanded = true },
                        onDismiss = { favDropdownExpanded = false },
                        maxHeight = 280.dp
                    ) {
                        if (nonVideoBookmarks.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Избранное пусто (добавьте туда плейлист/канал)", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { favDropdownExpanded = false },
                                enabled = false
                            )
                        } else {
                            nonVideoBookmarks.forEach { fav ->
                                DropdownMenuItem(
                                    text = {
                                        val typeLabel = when (fav.duration) {
                                            "ПАПКА", "КАТАЛОГ" -> "Подкатегория"
                                            "СЕРИАЛ" -> "Сериал"
                                            "КАНАЛ" -> "Канал"
                                            "ПЛЕЙЛИСТ" -> "Плейлист"
                                            else -> "Элемент"
                                        }
                                        Text("${fav.title} ($typeLabel)", color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    onClick = {
                                        viewModel.setStartPageFavorite(fav.id, fav.title)
                                        favDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Conditionally show category selection
            if (startPageType == "category") {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                
                var catDropdownExpanded by remember { mutableStateOf(false) }
                val categoriesList = listOf(
                    "Фильмы", "Сериалы", "Телепередачи", "Мультфильмы", "Музыка", "Спорт",
                    "Юмор", "Видеоигры", "Технологии", "Блоги", "Новости", "Лайфхаки",
                    "Детям", "Авто-мото", "Обучение", "Путешествия", "Кулинария", "Аниме"
                )

                SettingsItem(
                    icon = Icons.Default.Category,
                    title = "Категория для старта",
                    subtitle = "Каталог, загружаемый на первом экране"
                ) {
                    DropdownButton(
                        label = startPageCategory,
                        expanded = catDropdownExpanded,
                        onExpand = { catDropdownExpanded = true },
                        onDismiss = { catDropdownExpanded = false },
                        maxHeight = 280.dp
                    ) {
                        categoriesList.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setStartPageCategory(cat)
                                    catDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Conditionally show custom URL input
            if (startPageType == "custom_url") {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var textInput by remember(startPageCustomUrl) { mutableStateOf(startPageCustomUrl) }

                    Text(
                        text = "Ссылка на Rutube (канал, плейлист, серия и др.)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("https://rutube.ru/...", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_page_url_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = { viewModel.setStartPageCustomUrl(textInput) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("ОК", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Приложение автоматически найдет endpoint JSON и загрузит медиапоток.",
                        fontSize = 9.sp,
                        color = GreyText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Updates
        Text(
            text = "Обновление",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        UpdateSection(isDarkTheme, isTvOptimized)

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Backup & Restore
        Text(
            text = "Резервное копирование",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            var showBackupDialog by remember { mutableStateOf(false) }

            SettingsItem(
                icon = Icons.Default.Backup,
                title = "Экспорт и импорт (Все данные)",
                subtitle = "Сохранение закладок, истории и настроек в JSON",
                onClick = { showBackupDialog = true },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            if (showBackupDialog) {
                FullBackupRestoreDialog(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    isTvOptimized = isTvOptimized,
                    onDismiss = { showBackupDialog = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Information
        Text(
            text = "Информация",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            var showAgreementDialog by remember { mutableStateOf(false) }

            SettingsItem(
                icon = Icons.Default.Description,
                title = "Пользовательское соглашение",
                subtitle = "Просмотреть условия использования приложения",
                onClick = { showAgreementDialog = true },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            if (showAgreementDialog) {
                FullAgreementDialog(onDismiss = { showAgreementDialog = false })
            }
        }
    }
}

// ==================== ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ ====================

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = GreyText,
    onClick: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
        
        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}

@Composable
private fun DropdownButton(
    label: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    maxHeight: androidx.compose.ui.unit.Dp = 400.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        Button(
            onClick = onExpand,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(max = maxHeight)
        ) {
            content()
        }
    }
}

@Composable
fun FullBackupRestoreDialog(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    
    val bookmarkedVideos by viewModel.bookmarkedSavedVideos.collectAsStateWithLifecycle()
    val recentVideos by viewModel.recentSavedVideos.collectAsStateWithLifecycle()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = viewModel.exportBackupToJson()
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    statusMessage = "Резервная копия успешно сохранена на диск!"
                } catch (e: Exception) {
                    statusMessage = "Ошибка сохранения: ${e.localizedMessage}"
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val jsonText = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                    if (jsonText.isBlank()) {
                        statusMessage = "Выбранный файл пуст или не удалось его прочесть."
                    } else {
                        viewModel.importBackupFromJson(jsonText)
                            .onSuccess { msg ->
                                statusMessage = "Успешное восстановление: $msg"
                            }
                            .onFailure { err ->
                                statusMessage = "Ошибка восстановления: ${err.localizedMessage}"
                            }
                    }
                } catch (e: Exception) {
                    statusMessage = "Ошибка чтения файла: ${e.localizedMessage}"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Резервное копирование",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Резервная копия сохраняет ваши настройки, историю просмотров и закладки в файл .json на диске. Вы можете импортировать этот файл на любом устройстве.",
                    fontSize = 12.sp,
                    color = GreyText
                )

                if (statusMessage.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // EXPORT SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Экспорт данных",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "• Настройки приложения\n• Закладки (${bookmarkedVideos.size} шт.)\n• История просмотров (${recentVideos.size} шт.)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            try {
                                createDocumentLauncher.launch("sleek_video_hub_backup.json")
                            } catch (e: Exception) {
                                statusMessage = "Не удалось запустить выбор сохранения: ${e.localizedMessage}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sleekTvFocus(RoundedCornerShape(8.dp))
                    ) {
                        Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Экспортировать в файл .json", fontSize = 12.sp)
                    }
                }

                // IMPORT SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Импорт данных",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Выберите ранее экспортированный файл резервной копии .json для восстановления всех данных.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            try {
                                openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                            } catch (e: Exception) {
                                statusMessage = "Не удалось открыть выбор файла: ${e.localizedMessage}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sleekTvFocus(RoundedCornerShape(8.dp))
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Импортировать из файла .json", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.sleekTvFocus()
            ) {
                Text("Закрыть", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}