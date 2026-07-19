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
