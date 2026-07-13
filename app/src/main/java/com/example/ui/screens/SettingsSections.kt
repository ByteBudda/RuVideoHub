package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

@Composable
fun InterfaceSettingsSection(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
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
                        val newTheme = CustomTheme.fromJson(jsonText)
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

    var themeToExport by remember { mutableStateOf<CustomTheme?>(null) }

    val exportThemeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val theme = themeToExport
                    if (theme != null) {
                        val json = theme.toJsonString()
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                        }
                        themeStatusMessage = "Тема '${theme.name}' успешно экспортирована!"
                    }
                } catch (e: Exception) {
                    themeStatusMessage = "Ошибка экспорта: ${e.localizedMessage}"
                }
            }
        }
    }

    SettingsGroup(title = "Интерфейс", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        // App Theme Selector
        var themeDropdownExpanded by remember { mutableStateOf(false) }
        var themeToEdit by remember { mutableStateOf<CustomTheme?>(null) }
        val themeLabel = when (appTheme) {
            "dark" -> "Тёмная"
            "light" -> "Светлая"
            "slate" -> "Тёмная нейтральная"
            "sepia" -> "Светлая нейтральная"
            "cyberpunk" -> "Киберпанк (Неон)"
            "amoled" -> "Pure Black (AMOLED)"
            else -> customThemes.find { it.id == appTheme }?.name ?: "Тёмная"
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .sleekTvFocus(shape = RoundedCornerShape(12.dp))
                    .clickable { themeDropdownExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Цветовая тема",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Выберите оформление интерфейса",
                            fontSize = 11.sp,
                            color = GreyText,
                            modifier = Modifier.padding(top = 2.dp),
                            lineHeight = 15.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = themeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = themeDropdownExpanded,
                onDismissRequest = { themeDropdownExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .background(MaterialTheme.colorScheme.surface)
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
                        text = { Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                        onClick = {
                            viewModel.setAppTheme(id)
                            themeDropdownExpanded = false
                        },
                        trailingIcon = {
                            val matchingCustom = customThemes.find { it.id == id }
                            if (matchingCustom != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            themeToEdit = matchingCustom
                                            themeDropdownExpanded = false
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Редактировать тему",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeCustomTheme(id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить тему",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (themeToEdit != null) {
            ThemeCreatorDialog(
                initialTheme = themeToEdit,
                onDismiss = { themeToEdit = null },
                onSave = { updatedTheme ->
                    viewModel.addCustomTheme(updatedTheme)
                    if (appTheme == updatedTheme.id) {
                        viewModel.setAppTheme(updatedTheme.id)
                    }
                    themeStatusMessage = "Тема '${updatedTheme.name}' успешно обновлена!"
                    themeToEdit = null
                }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

        // Import Custom Theme
        SettingsRow(
            icon = Icons.Default.Style,
            title = "Импорт темы (.rvht)",
            description = if (themeStatusMessage.isNotBlank()) themeStatusMessage else "Загрузить пользовательскую тему из файла",
            onClick = { openThemeLauncher.launch(arrayOf("*/*")) },
            control = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

        // Export Custom Theme
        var showThemeExporterDialog by remember { mutableStateOf(false) }

        SettingsRow(
            icon = Icons.Default.Share,
            title = "Экспорт темы (.rvht)",
            description = "Сохранить созданную вами тему в файл для обмена",
            onClick = { showThemeExporterDialog = true },
            control = {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

        // Custom Theme Creator Row
        var showThemeCreatorDialog by remember { mutableStateOf(false) }

        SettingsRow(
            icon = Icons.Default.ColorLens,
            title = "Конструктор тем",
            description = "Создайте свою собственную тему с градиентами, свечением и прозрачностью",
            onClick = { showThemeCreatorDialog = true },
            control = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        if (showThemeCreatorDialog) {
            ThemeCreatorDialog(
                onDismiss = { showThemeCreatorDialog = false },
                onSave = { newTheme ->
                    viewModel.addCustomTheme(newTheme)
                    viewModel.setAppTheme(newTheme.id)
                    themeStatusMessage = "Создана тема '${newTheme.name}'!"
                    showThemeCreatorDialog = false
                }
            )
        }

        if (showThemeExporterDialog) {
            ThemeExporterDialog(
                customThemes = customThemes,
                onDismiss = { showThemeExporterDialog = false },
                onSelectTheme = { theme ->
                    themeToExport = theme
                    val safeName = theme.name.replace(Regex("[^a-zA-Z0-9а-яА-Я_]"), "_")
                    exportThemeLauncher.launch("$safeName.rvht")
                    showThemeExporterDialog = false
                }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

        // TV Mode Toggle Row
        SettingsRow(
            icon = Icons.Default.Tv,
            title = "ТВ-оптимизация",
            description = "Адаптировать интерфейс для управления пультом",
            onClick = { viewModel.toggleTvOptimized() },
            control = {
                Switch(
                    checked = isTvOptimized,
                    onCheckedChange = { viewModel.toggleTvOptimized() },
                    modifier = Modifier.testTag("setting_tv_switch")
                )
            }
        )
    }
}

@Composable
fun DisplaySettingsSection(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    val isLargeCardsMode by viewModel.isLargeCardsMode.collectAsStateWithLifecycle()
    val isHistoryLargeCardsMode by viewModel.isHistoryLargeCardsMode.collectAsStateWithLifecycle()
    val isDownloadsLargeCardsMode by viewModel.isDownloadsLargeCardsMode.collectAsStateWithLifecycle()
    val mobileGridColumns by viewModel.mobileGridColumns.collectAsStateWithLifecycle()
    val tvGridColumns by viewModel.tvGridColumns.collectAsStateWithLifecycle()
    val tvVideoGridColumns by viewModel.tvVideoGridColumns.collectAsStateWithLifecycle()
    val focusStyle by viewModel.focusStyle.collectAsStateWithLifecycle()

    SettingsGroup(title = "Отображение", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        if (!isTvOptimized) {
            // Large Cards Switch (only for Mobile)
            SettingsRow(
                icon = Icons.Default.ViewList,
                title = "Крупные карточки в каталоге",
                description = "Использовать большой размер для видео в списках",
                onClick = { viewModel.toggleLargeCardsMode() },
                control = {
                    Switch(
                        checked = isLargeCardsMode,
                        onCheckedChange = { viewModel.toggleLargeCardsMode() },
                        modifier = Modifier.testTag("setting_large_cards_switch")
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            SettingsRow(
                icon = Icons.Default.History,
                title = "Крупные карточки в истории",
                description = "Использовать большой размер для недавних видео",
                onClick = { viewModel.toggleHistoryLargeCardsMode() },
                control = {
                    Switch(
                        checked = isHistoryLargeCardsMode,
                        onCheckedChange = { viewModel.toggleHistoryLargeCardsMode() },
                        modifier = Modifier.testTag("setting_history_large_cards_switch")
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            SettingsRow(
                icon = Icons.Default.Download,
                title = "Крупные карточки в загрузках",
                description = "Использовать большой размер для загруженных видео",
                onClick = { viewModel.toggleDownloadsLargeCardsMode() },
                control = {
                    Switch(
                        checked = isDownloadsLargeCardsMode,
                        onCheckedChange = { viewModel.toggleDownloadsLargeCardsMode() },
                        modifier = Modifier.testTag("setting_downloads_large_cards_switch")
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            // Mobile Grid Columns
            var mobileGridDropdownExpanded by remember { mutableStateOf(false) }
            val mobileColsOptions = listOf(1, 2, 3)

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.GridView,
                    title = "Колонок в сетке (Мобильный)",
                    description = "Количество столбцов на мобильных устройствах",
                    onClick = { mobileGridDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "$mobileGridColumns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = mobileGridDropdownExpanded,
                    onDismissRequest = { mobileGridDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    mobileColsOptions.forEach { cols ->
                        DropdownMenuItem(
                            text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setMobileGridColumns(cols)
                                mobileGridDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        } else {
            // TV Grid Columns
            var tvGridDropdownExpanded by remember { mutableStateOf(false) }
            val tvColsOptions = listOf(3, 4, 5, 6)

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.GridOn,
                    title = "Колонок в сетке (ТВ)",
                    description = "Количество столбцов при ТВ-оптимизации",
                    onClick = { tvGridDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "$tvGridColumns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = tvGridDropdownExpanded,
                    onDismissRequest = { tvGridDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    tvColsOptions.forEach { cols ->
                        DropdownMenuItem(
                            text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setTvGridColumns(cols)
                                tvGridDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            // TV Video Grid Columns
            var tvVideoGridDropdownExpanded by remember { mutableStateOf(false) }
            val tvVideoColsOptions = listOf(3, 4, 5, 6, 7, 8)

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.OndemandVideo,
                    title = "Колонок в видео сетке (ТВ)",
                    description = "Количество столбцов для списка видео на ТВ",
                    onClick = { tvVideoGridDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "$tvVideoGridColumns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = tvVideoGridDropdownExpanded,
                    onDismissRequest = { tvVideoGridDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    tvVideoColsOptions.forEach { cols ->
                        DropdownMenuItem(
                            text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setTvVideoGridColumns(cols)
                                tvVideoGridDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            // TV Focus Highlight Style
            var focusStyleDropdownExpanded by remember { mutableStateOf(false) }
            val focusStyleLabel = when (focusStyle) {
                "scale" -> "Масштабирование"
                "scale_glow" -> "Масштаб + Свечение"
                "classic" -> "Простая рамка"
                "tint" -> "Подсветка фона"
                else -> "Свечение границ"
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.CenterFocusStrong,
                    title = "Стиль фокуса ТВ",
                    description = "Эффект подсветки при наведении пультом",
                    onClick = { focusStyleDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = focusStyleLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = focusStyleDropdownExpanded,
                    onDismissRequest = { focusStyleDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    listOf(
                        "glow" to "Свечение границ",
                        "scale" to "Масштабирование",
                        "scale_glow" to "Масштаб + Свечение",
                        "classic" to "Простая рамка",
                        "tint" to "Подсветка фона"
                    ).forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
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
}

@Composable
fun QualitySettingsSection(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    val playerQuality by viewModel.playerQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()

    SettingsGroup(title = "Качество воспроизведения и загрузки", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        // Player Quality Default
        var dropdownExpanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Default.VideoLibrary,
                title = "Качество плеера",
                description = "Качество по умолчанию при запуске видео",
                onClick = { dropdownExpanded = true },
                control = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = playerQuality, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                }
            )

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                listOf("Авто", "1080p", "720p", "480p", "360p").forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                        onClick = {
                            viewModel.setPlayerQuality(opt)
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

        // Download Quality Default
        var dlDropdownExpanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Default.Download,
                title = "Качество загрузки",
                description = "Желаемое качество при скачивании",
                onClick = { dlDropdownExpanded = true },
                control = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = downloadQuality, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                }
            )

            DropdownMenu(
                expanded = dlDropdownExpanded,
                onDismissRequest = { dlDropdownExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                listOf("1080p", "720p", "480p", "360p").forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                        onClick = {
                            viewModel.setDownloadQuality(opt)
                            dlDropdownExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StartPageSettingsSection(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    val startPageType by viewModel.startPageType.collectAsStateWithLifecycle()
    val startPageCategory by viewModel.startPageCategory.collectAsStateWithLifecycle()
    val startPageCustomUrl by viewModel.startPageCustomUrl.collectAsStateWithLifecycle()
    val bookmarkedVideos by viewModel.bookmarkedSavedVideos.collectAsStateWithLifecycle()
    val startPageFavoriteTitle by viewModel.startPageFavoriteTitle.collectAsStateWithLifecycle()

    SettingsGroup(title = "Стартовый экран", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        // Start Page Type
        var typeDropdownExpanded by remember { mutableStateOf(false) }
        val typeLabel = when (startPageType) {
            "category" -> "Выбранная категория"
            "custom_url" -> "Ссылка Rutube"
            "favorite" -> "Элемент из избранного"
            else -> "По умолчанию (Фильмы)"
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .sleekTvFocus(shape = RoundedCornerShape(12.dp))
                    .clickable { typeDropdownExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Тип стартовой страницы",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Что открывать при запуске приложения",
                            fontSize = 11.sp,
                            color = GreyText,
                            modifier = Modifier.padding(top = 2.dp),
                            lineHeight = 15.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = typeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = typeDropdownExpanded,
                onDismissRequest = { typeDropdownExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("По умолчанию (Фильмы)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                    onClick = {
                        viewModel.setStartPageType("default")
                        typeDropdownExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Выбрать категорию", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                    onClick = {
                        viewModel.setStartPageType("category")
                        typeDropdownExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Своя ссылка Rutube", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                    onClick = {
                        viewModel.setStartPageType("custom_url")
                        typeDropdownExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Элемент из избранного", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                    onClick = {
                        viewModel.setStartPageType("favorite")
                        typeDropdownExpanded = false
                    }
                )
            }
        }

        // Custom Favorite Selector
        if (startPageType == "favorite") {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            var favDropdownExpanded by remember { mutableStateOf(false) }
            val nonVideoBookmarks = remember(bookmarkedVideos) {
                bookmarkedVideos.filter { saved ->
                    saved.duration == "ПАПКА" ||
                    saved.duration == "КАТАЛОГ" ||
                    saved.duration == "СЕРИАЛ" ||
                    saved.duration == "КАНАЛ" ||
                    saved.duration == "ПЛЕЙЛИСТ"
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sleekTvFocus(shape = RoundedCornerShape(12.dp))
                        .clickable { favDropdownExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Элемент из избранного",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Выберите сохраненный плейлист, канал или сериал",
                                fontSize = 11.sp,
                                color = GreyText,
                                modifier = Modifier.padding(top = 2.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        val displayText = if (startPageFavoriteTitle.isNotBlank()) startPageFavoriteTitle else "Не выбрано"
                        Text(
                            text = displayText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = favDropdownExpanded,
                    onDismissRequest = { favDropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(MaterialTheme.colorScheme.surface)
                        .heightIn(max = 280.dp)
                ) {
                    if (nonVideoBookmarks.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Избранное пусто (добавьте туда плейлист/канал)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
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
                                    Text("${fav.title} ($typeLabel)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) 
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

        // Custom Category Selector
        if (startPageType == "category") {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            var catDropdownExpanded by remember { mutableStateOf(false) }
            val categoriesList = listOf(
                "Фильмы", "Сериалы", "Телепередачи", "Мультфильмы", "Музыка", "Спорт",
                "Юмор", "Видеоигры", "Технологии", "Блоги", "Новости", "Лайфхаки",
                "Детям", "Авто-мото", "Обучение", "Путешествия", "Кулинария", "Аниме"
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sleekTvFocus(shape = RoundedCornerShape(12.dp))
                        .clickable { catDropdownExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Категория для старта",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Каталог, загружаемый на первом экране",
                                fontSize = 11.sp,
                                color = GreyText,
                                modifier = Modifier.padding(top = 2.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = startPageCategory,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = catDropdownExpanded,
                    onDismissRequest = { catDropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(MaterialTheme.colorScheme.surface)
                        .heightIn(max = 280.dp)
                ) {
                    categoriesList.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setStartPageCategory(cat)
                                catDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Custom URL Input
        if (startPageType == "custom_url") {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                var textInput by remember(startPageCustomUrl) { mutableStateOf(startPageCustomUrl) }

                Text(
                    text = "Ссылка на Rutube (канал, плейлист, серия и др.)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("start_page_url_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.setStartPageCustomUrl(textInput)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .sleekTvFocus(RoundedCornerShape(10.dp))
                    ) {
                        Text("ОК", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Приложение автоматически найдет JSON endpoint и загрузит медиапоток при старте.",
                    fontSize = 10.sp,
                    color = GreyText
                )
            }
        }
    }
}

@Composable
fun BackupSettingsSection(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    var showBackupDialog by remember { mutableStateOf(false) }

    SettingsGroup(title = "Резервное копирование", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        SettingsRow(
            icon = Icons.Default.Backup,
            title = "Экспорт и импорт (Все данные)",
            description = "Сохранение закладок, истории и настроек в JSON",
            onClick = { showBackupDialog = true },
            control = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
}

@Composable
fun InfoSettingsSection(
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    var showAgreementDialog by remember { mutableStateOf(false) }

    SettingsGroup(title = "Информация", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        SettingsRow(
            icon = Icons.Default.Description,
            title = "Пользовательское соглашение",
            description = "Просмотреть условия использования приложения",
            onClick = { showAgreementDialog = true },
            control = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        if (showAgreementDialog) {
            FullAgreementDialog(onDismiss = { showAgreementDialog = false })
        }
    }
}



