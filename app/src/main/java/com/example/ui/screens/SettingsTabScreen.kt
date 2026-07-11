package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.ui.theme.liquidGlass
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()
    val isLargeCardsMode by viewModel.isLargeCardsMode.collectAsStateWithLifecycle()
    val playerQuality by viewModel.playerQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val startPageType by viewModel.startPageType.collectAsStateWithLifecycle()
    val startPageCategory by viewModel.startPageCategory.collectAsStateWithLifecycle()
    val startPageCustomUrl by viewModel.startPageCustomUrl.collectAsStateWithLifecycle()
    val bookmarkedVideos by viewModel.bookmarkedSavedVideos.collectAsStateWithLifecycle()
    val customThemes by viewModel.customThemes.collectAsStateWithLifecycle()
    val tvGridColumns by viewModel.tvGridColumns.collectAsStateWithLifecycle()
    val tvVideoGridColumns by viewModel.tvVideoGridColumns.collectAsStateWithLifecycle()
    val mobileGridColumns by viewModel.mobileGridColumns.collectAsStateWithLifecycle()
    val focusStyle by viewModel.focusStyle.collectAsStateWithLifecycle()
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
                        themeStatusMessage = "Тема '${newTheme.name}' импортирована!"
                    }
                } catch (e: Exception) {
                    themeStatusMessage = "Ошибка: ${e.localizedMessage}"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Text(
                text = "Настройки",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Конфигурация приложения, тем и качества",
                fontSize = 12.sp,
                color = GreyText,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // ============ ИНТЕРФЕЙС ============
        SettingsSectionHeader("Интерфейс")

        SettingsCard(isDarkTheme, isTvOptimized) {
            // Тема
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "Цветовая тема",
                subtitle = "Выберите оформление"
            ) {
                ThemeDropdown(
                    appTheme = appTheme,
                    customThemes = customThemes,
                    onThemeSelected = { viewModel.setAppTheme(it) },
                    onThemeDeleted = { viewModel.removeCustomTheme(it) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Импорт темы
            SettingsRow(
                icon = Icons.Default.Style,
                title = "Импорт темы",
                subtitle = if (themeStatusMessage.isNotBlank()) themeStatusMessage else "Загрузить .rvht файл",
                subtitleColor = if (themeStatusMessage.isNotBlank()) MaterialTheme.colorScheme.primary else GreyText,
                onClick = { openThemeLauncher.launch(arrayOf("*/*")) }
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // ТВ-оптимизация
            SettingsRow(
                icon = Icons.Default.Tv,
                title = "ТВ-оптимизация",
                subtitle = "Адаптация для управления пультом"
            ) {
                Switch(
                    checked = isTvOptimized,
                    onCheckedChange = { viewModel.toggleTvOptimized() },
                    modifier = Modifier.testTag("setting_tv_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ ОТОБРАЖЕНИЕ ============
        SettingsSectionHeader("Отображение")

        SettingsCard(isDarkTheme, isTvOptimized) {
            if (!isTvOptimized) {
                SettingsRow(
                    icon = Icons.Default.ViewList,
                    title = "Крупные карточки",
                    subtitle = "Большой размер видео в каталоге"
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
                SettingsRow(
                    icon = Icons.Default.GridOn,
                    title = "Колонок в сетке (ТВ)",
                    subtitle = "Количество столбцов"
                ) {
                    GridSelector(
                        value = tvGridColumns,
                        options = listOf(3, 4, 5, 6),
                        onValueSelected = { viewModel.setTvGridColumns(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                SettingsRow(
                    icon = Icons.Default.OndemandVideo,
                    title = "Колонок в видео сетке (ТВ)",
                    subtitle = "Количество столбцов"
                ) {
                    GridSelector(
                        value = tvVideoGridColumns,
                        options = listOf(3, 4, 5, 6, 7, 8),
                        onValueSelected = { viewModel.setTvVideoGridColumns(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            if (!isTvOptimized) {
                SettingsRow(
                    icon = Icons.Default.GridView,
                    title = "Колонок в сетке (Мобильный)",
                    subtitle = "Количество столбцов"
                ) {
                    GridSelector(
                        value = mobileGridColumns,
                        options = listOf(1, 2, 3),
                        onValueSelected = { viewModel.setMobileGridColumns(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            if (isTvOptimized) {
                SettingsRow(
                    icon = Icons.Default.CenterFocusStrong,
                    title = "Стиль фокуса ТВ",
                    subtitle = "Эффект при наведении"
                ) {
                    FocusStyleSelector(
                        value = focusStyle,
                        onValueSelected = { viewModel.setFocusStyle(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ КАЧЕСТВО ============
        SettingsSectionHeader("Качество воспроизведения и загрузки")

        SettingsCard(isDarkTheme, isTvOptimized) {
            SettingsRow(
                icon = Icons.Default.VideoLibrary,
                title = "Качество плеера",
                subtitle = "По умолчанию при запуске"
            ) {
                QualitySelector(
                    value = playerQuality,
                    options = listOf("Авто", "2160p", "1440p", "1080p", "720p", "480p", "360p"),
                    onValueSelected = { viewModel.setPlayerQuality(it) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            SettingsRow(
                icon = Icons.Default.Download,
                title = "Качество загрузки",
                subtitle = "Желаемое качество"
            ) {
                QualitySelector(
                    value = downloadQuality,
                    options = listOf("2160p", "1440p", "1080p", "720p", "480p", "360p"),
                    onValueSelected = { viewModel.setDownloadQuality(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ СТАРТОВЫЙ ЭКРАН ============
        SettingsSectionHeader("Стартовый экран")

        SettingsCard(isDarkTheme, isTvOptimized) {
            SettingsRow(
                icon = Icons.Default.Home,
                title = "Тип стартовой страницы",
                subtitle = "Что открывать при запуске"
            ) {
                StartPageTypeSelector(
                    value = startPageType,
                    onValueSelected = { viewModel.setStartPageType(it) }
                )
            }

            when (startPageType) {
                "favorite" -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    SettingsRow(
                        icon = Icons.Default.Favorite,
                        title = "Элемент из избранного",
                        subtitle = "Плейлист, канал или подкатегория"
                    ) {
                        FavoriteSelector(
                            bookmarks = bookmarkedVideos,
                            selectedTitle = viewModel.startPageFavoriteTitle.collectAsStateWithLifecycle().value,
                            onSelected = { id, title -> viewModel.setStartPageFavorite(id, title) }
                        )
                    }
                }
                "category" -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    SettingsRow(
                        icon = Icons.Default.Category,
                        title = "Категория для старта",
                        subtitle = "Каталог на первом экране"
                    ) {
                        CategorySelector(
                            value = startPageCategory,
                            onValueSelected = { viewModel.setStartPageCategory(it) }
                        )
                    }
                }
                "custom_url" -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    CustomUrlInput(
                        value = startPageCustomUrl,
                        onValueChanged = { viewModel.setStartPageCustomUrl(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ ОБНОВЛЕНИЕ ============
        SettingsSectionHeader("Обновление")
        UpdateSection(isDarkTheme, isTvOptimized)

        Spacer(modifier = Modifier.height(16.dp))

        // ============ РЕЗЕРВНОЕ КОПИРОВАНИЕ ============
        SettingsSectionHeader("Резервное копирование")

        SettingsCard(isDarkTheme, isTvOptimized) {
            var showBackupDialog by remember { mutableStateOf(false) }

            SettingsRow(
                icon = Icons.Default.Backup,
                title = "Экспорт и импорт",
                subtitle = "Сохранение закладок, истории и настроек",
                onClick = { showBackupDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

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

        // ============ ИНФОРМАЦИЯ ============
        SettingsSectionHeader("Информация")

        SettingsCard(isDarkTheme, isTvOptimized) {
            var showAgreementDialog by remember { mutableStateOf(false) }

            SettingsRow(
                icon = Icons.Default.Description,
                title = "Пользовательское соглашение",
                subtitle = "Условия использования",
                onClick = { showAgreementDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (showAgreementDialog) {
                FullAgreementDialog(onDismiss = { showAgreementDialog = false })
            }
        }
    }
}

// ==================== КОМПОНЕНТЫ ====================

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    isDarkTheme: Boolean,
    isTvOptimized: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = GreyText,
    onClick: (() -> Unit)? = null,
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
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
    }
}

@Composable
private fun ThemeDropdown(
    appTheme: String,
    customThemes: List<com.example.ui.theme.CustomTheme>,
    onThemeSelected: (String) -> Unit,
    onThemeDeleted: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val themeLabel = when (appTheme) {
        "dark" -> "Тёмная"
        "light" -> "Светлая"
        "slate" -> "Тёмная нейтральная"
        "sepia" -> "Светлая нейтральная"
        "cyberpunk" -> "Киберпанк"
        "amoled" -> "Pure Black"
        else -> customThemes.find { it.id == appTheme }?.name ?: "Тёмная"
    }

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = themeLabel,
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
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(max = 300.dp)
        ) {
            val baseThemes = listOf(
                "dark" to "Тёмная",
                "light" to "Светлая",
                "slate" to "Тёмная нейтральная",
                "sepia" to "Светлая нейтральная",
                "cyberpunk" to "Киберпанк",
                "amoled" to "Pure Black"
            )
            val allThemes = baseThemes + customThemes.map { it.id to it.name }
            
            allThemes.forEach { (id, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onThemeSelected(id)
                        expanded = false
                    },
                    trailingIcon = {
                        if (customThemes.any { it.id == id }) {
                            IconButton(
                                onClick = { onThemeDeleted(id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить",
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
}

@Composable
private fun GridSelector(
    value: Int,
    options: List<Int>,
    onValueSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = "$value",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
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
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "$option",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onValueSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun QualitySelector(
    value: String,
    options: List<String>,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = value,
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
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onValueSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FocusStyleSelector(
    value: String,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val label = when (value) {
        "scale" -> "Масштабирование"
        "scale_glow" -> "Масштаб + Свечение"
        "classic" -> "Простая рамка"
        "tint" -> "Подсветка фона"
        else -> "Свечение границ"
    }

    Box {
        Button(
            onClick = { expanded = true },
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
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            listOf(
                "glow" to "Свечение границ",
                "scale" to "Масштабирование",
                "scale_glow" to "Масштаб + Свечение",
                "classic" to "Простая рамка",
                "tint" to "Подсветка фона"
            ).forEach { (id, labelText) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = labelText,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onValueSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StartPageTypeSelector(
    value: String,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val label = when (value) {
        "category" -> "Категория"
        "custom_url" -> "Ссылка Rutube"
        "favorite" -> "Из избранного"
        else -> "По умолчанию"
    }

    Box {
        Button(
            onClick = { expanded = true },
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
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            listOf(
                "default" to "По умолчанию",
                "category" to "Категория",
                "custom_url" to "Ссылка Rutube",
                "favorite" to "Из избранного"
            ).forEach { (id, labelText) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = labelText,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onValueSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoriteSelector(
    bookmarks: List<com.example.data.model.SavedVideo>,
    selectedTitle: String,
    onSelected: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val nonVideoBookmarks = remember(bookmarks) {
        bookmarks.filter { saved ->
            saved.duration in listOf("ПАПКА", "КАТАЛОГ", "СЕРИАЛ", "КАНАЛ", "ПЛЕЙЛИСТ")
        }
    }
    
    val displayText = if (selectedTitle.isNotBlank()) selectedTitle else "Не выбрано"

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = displayText,
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
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(max = 280.dp)
        ) {
            if (nonVideoBookmarks.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Избранное пусто",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    },
                    onClick = { expanded = false },
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
                            Text(
                                text = "${fav.title} ($typeLabel)",
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                        },
                        onClick = {
                            onSelected(fav.id, fav.title)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelector(
    value: String,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val categories = listOf(
        "Фильмы", "Сериалы", "Телепередачи", "Мультфильмы", "Музыка", "Спорт",
        "Юмор", "Видеоигры", "Технологии", "Блоги", "Новости", "Лайфхаки",
        "Детям", "Авто-мото", "Обучение", "Путешествия", "Кулинария", "Аниме"
    )

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = value,
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
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(max = 280.dp)
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = category,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onValueSelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CustomUrlInput(
    value: String,
    onValueChanged: (String) -> Unit
) {
    var textInput by remember(value) { mutableStateOf(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        text = "https://rutube.ru/...",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                    .height(44.dp)
                    .testTag("start_page_url_input"),
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = { onValueChanged(textInput) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("ОК", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = "Приложение автоматически найдет JSON и загрузит медиапоток",
            fontSize = 9.sp,
            color = GreyText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                    statusMessage = "Резервная копия сохранена!"
                } catch (e: Exception) {
                    statusMessage = "Ошибка: ${e.localizedMessage}"
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
                        statusMessage = "Файл пуст"
                    } else {
                        viewModel.importBackupFromJson(jsonText)
                            .onSuccess { msg -> statusMessage = "Успешно: $msg" }
                            .onFailure { err -> statusMessage = "Ошибка: ${err.localizedMessage}" }
                    }
                } catch (e: Exception) {
                    statusMessage = "Ошибка: ${e.localizedMessage}"
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Сохраняет настройки, историю и закладки в JSON файл",
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Экспорт",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "• Закладки: ${bookmarkedVideos.size}\n• История: ${recentVideos.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = { createDocumentLauncher.launch("sleek_video_hub_backup.json") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Экспортировать .json", fontSize = 12.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Импорт",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = { openDocumentLauncher.launch(arrayOf("application/json", "*/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Импортировать .json", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}