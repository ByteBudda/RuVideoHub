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
                        themeStatusMessage = "Тема '${newTheme.name}' импортирована!"
                    }
                } catch (e: Exception) {
                    themeStatusMessage = "Ошибка: ${e.localizedMessage}"
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
        Text(
            text = "Интерфейс",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Цветовая тема
                var themeDropdownExpanded by remember { mutableStateOf(false) }
                val themeLabel = when (appTheme) {
                    "dark" -> "Тёмная"
                    "light" -> "Светлая"
                    "slate" -> "Тёмная нейтральная"
                    "sepia" -> "Светлая нейтральная"
                    "cyberpunk" -> "Киберпанк"
                    "amoled" -> "Pure Black"
                    else -> customThemes.find { it.id == appTheme }?.name ?: "Тёмная"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Цветовая тема",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Выберите оформление",
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }

                    Box {
                        Button(
                            onClick = { themeDropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = themeLabel,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = themeDropdownExpanded,
                            onDismissRequest = { themeDropdownExpanded = false },
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
                                    text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        viewModel.setAppTheme(id)
                                        themeDropdownExpanded = false
                                    },
                                    trailingIcon = {
                                        if (customThemes.any { it.id == id }) {
                                            IconButton(
                                                onClick = { viewModel.removeCustomTheme(id) },
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

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Импорт темы
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openThemeLauncher.launch(arrayOf("*/*")) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Импорт темы",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (themeStatusMessage.isNotBlank()) themeStatusMessage else "Загрузить из .rvht файла",
                                fontSize = 12.sp,
                                color = if (themeStatusMessage.isNotBlank()) MaterialTheme.colorScheme.primary else GreyText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GreyText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // ТВ-оптимизация
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ТВ-оптимизация",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Адаптация для пульта",
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }
                    Switch(
                        checked = isTvOptimized,
                        onCheckedChange = { viewModel.toggleTvOptimized() },
                        modifier = Modifier.testTag("setting_tv_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ ОТОБРАЖЕНИЕ ============
        Text(
            text = "Отображение",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (!isTvOptimized) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                    imageVector = Icons.Default.ViewList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Крупные карточки",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Большой размер видео",
                                    fontSize = 12.sp,
                                    color = GreyText
                                )
                            }
                        }
                        Switch(
                            checked = isLargeCardsMode,
                            onCheckedChange = { viewModel.toggleLargeCardsMode() },
                            modifier = Modifier.testTag("setting_large_cards_switch")
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                if (isTvOptimized) {
                    // Колонки ТВ
                    var tvGridExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Колонок в сетке (ТВ)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Количество столбцов",
                                    fontSize = 12.sp,
                                    color = GreyText
                                )
                            }
                        }
                        Box {
                            Button(
                                onClick = { tvGridExpanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("$tvGridColumns", fontSize = 12.sp)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = tvGridExpanded,
                                onDismissRequest = { tvGridExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                listOf(3, 4, 5, 6).forEach { cols ->
                                    DropdownMenuItem(
                                        text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            viewModel.setTvGridColumns(cols)
                                            tvGridExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                if (!isTvOptimized) {
                    // Мобильные колонки
                    var mobileGridExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Колонок в сетке",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "На мобильных устройствах",
                                    fontSize = 12.sp,
                                    color = GreyText
                                )
                            }
                        }
                        Box {
                            Button(
                                onClick = { mobileGridExpanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("$mobileGridColumns", fontSize = 12.sp)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = mobileGridExpanded,
                                onDismissRequest = { mobileGridExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                listOf(1, 2, 3).forEach { cols ->
                                    DropdownMenuItem(
                                        text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            viewModel.setMobileGridColumns(cols)
                                            mobileGridExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                if (isTvOptimized) {
                    // Стиль фокуса
                    var focusExpanded by remember { mutableStateOf(false) }
                    val focusLabel = when (focusStyle) {
                        "scale" -> "Масштаб"
                        "scale_glow" -> "Масштаб+Свечение"
                        "classic" -> "Рамка"
                        "tint" -> "Подсветка"
                        else -> "Свечение"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Стиль фокуса ТВ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Эффект при наведении",
                                    fontSize = 12.sp,
                                    color = GreyText
                                )
                            }
                        }
                        Box {
                            Button(
                                onClick = { focusExpanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(focusLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = focusExpanded,
                                onDismissRequest = { focusExpanded = false },
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
                                        text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            viewModel.setFocusStyle(id)
                                            focusExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ КАЧЕСТВО ============
        Text(
            text = "Качество",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Качество плеера
                var qualityExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Качество плеера",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "По умолчанию",
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }
                    Box {
                        Button(
                            onClick = { qualityExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(playerQuality, fontSize = 12.sp)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = qualityExpanded,
                            onDismissRequest = { qualityExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            listOf("Авто", "2160p", "1440p", "1080p", "720p", "480p", "360p").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        viewModel.setPlayerQuality(opt)
                                        qualityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Качество загрузки
                var downloadExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Качество загрузки",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "При скачивании",
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }
                    Box {
                        Button(
                            onClick = { downloadExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(downloadQuality, fontSize = 12.sp)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = downloadExpanded,
                            onDismissRequest = { downloadExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            listOf("2160p", "1440p", "1080p", "720p", "480p", "360p").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        viewModel.setDownloadQuality(opt)
                                        downloadExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ СТАРТОВЫЙ ЭКРАН ============
        Text(
            text = "Стартовый экран",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Тип стартовой страницы
                var typeExpanded by remember { mutableStateOf(false) }
                val typeLabel = when (startPageType) {
                    "category" -> "Категория"
                    "custom_url" -> "Ссылка Rutube"
                    "favorite" -> "Из избранного"
                    else -> "По умолчанию"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Тип стартовой страницы",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Что открывать при запуске",
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }
                    Box {
                        Button(
                            onClick = { typeExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(typeLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
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
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Избранное
                if (startPageType == "favorite") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    var favExpanded by remember { mutableStateOf(false) }
                    val nonVideoBookmarks = remember(bookmarkedVideos) {
                        bookmarkedVideos.filter { saved ->
                            saved.duration in listOf("ПАПКА", "КАТАЛОГ", "СЕРИАЛ", "КАНАЛ", "ПЛЕЙЛИСТ")
                        }
                    }
                    val startPageFavoriteTitle by viewModel.startPageFavoriteTitle.collectAsStateWithLifecycle()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Выберите элемент из избранного",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Button(
                            onClick = { favExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (startPageFavoriteTitle.isNotBlank()) startPageFavoriteTitle else "Не выбрано",
                                    fontSize = 13.sp,
                                    color = if (startPageFavoriteTitle.isNotBlank()) MaterialTheme.colorScheme.onBackground else GreyText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = GreyText
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = favExpanded,
                            onDismissRequest = { favExpanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .heightIn(max = 280.dp)
                        ) {
                            if (nonVideoBookmarks.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Избранное пусто", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { favExpanded = false },
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
                                                "${fav.title} ($typeLabel)",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            viewModel.setStartPageFavorite(fav.id, fav.title)
                                            favExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Категория
                if (startPageType == "category") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    var catExpanded by remember { mutableStateOf(false) }
                    val categoriesList = listOf(
                        "Фильмы", "Сериалы", "Телепередачи", "Мультфильмы", "Музыка", "Спорт",
                        "Юмор", "Видеоигры", "Технологии", "Блоги", "Новости", "Лайфхаки",
                        "Детям", "Авто-мото", "Обучение", "Путешествия", "Кулинария", "Аниме"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Выберите категорию",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Button(
                            onClick = { catExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = startPageCategory,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = GreyText
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .heightIn(max = 280.dp)
                        ) {
                            categoriesList.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        viewModel.setStartPageCategory(cat)
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Своя ссылка
                if (startPageType == "custom_url") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    var textInput by remember(startPageCustomUrl) { mutableStateOf(startPageCustomUrl) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ссылка на Rutube",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Канал, плейлист, серия",
                            fontSize = 11.sp,
                            color = GreyText
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
                            text = "Автоматически найдет JSON и загрузит медиапоток",
                            fontSize = 10.sp,
                            color = GreyText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ ОБНОВЛЕНИЕ ============
        Text(
            text = "Обновление",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        UpdateSection(isDarkTheme, isTvOptimized)

        Spacer(modifier = Modifier.height(16.dp))

        // ============ РЕЗЕРВНОЕ КОПИРОВАНИЕ ============
        Text(
            text = "Резервное копирование",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                var showBackupDialog by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBackupDialog = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Экспорт и импорт",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Закладки, история, настройки",
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GreyText,
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============ ИНФОРМАЦИЯ ============
        Text(
            text = "Информация",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                var showAgreementDialog by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAgreementDialog = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Пользовательское соглашение",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Условия использования",
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GreyText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (showAgreementDialog) {
                    FullAgreementDialog(onDismiss = { showAgreementDialog = false })
                }
            }
        }
    }
}

// ==================== ДИАЛОГИ ====================

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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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