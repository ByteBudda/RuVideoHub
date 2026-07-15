package com.example.ui.screens.settings

import com.example.viewmodel.*
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
import com.example.ui.screens.*

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
            "slate" -> "Скетч Dark"
            "sepia" -> "Скетч"
            "cyberpunk" -> "Aero Vista Dark"
            "amoled" -> "Aero Vista Classic"
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
                    "slate" to "Скетч Dark",
                    "sepia" to "Скетч",
                    "cyberpunk" to "Aero Vista Dark",
                    "amoled" to "Aero Vista Classic"
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
