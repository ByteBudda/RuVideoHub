package com.example.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.example.data.SearchHistory
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.liquidGlass
import com.example.ui.screens.sleekTvFocus

@Composable
fun SleekHeader(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onMicClick: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    searchHistory: List<SearchHistory> = emptyList(),
    onDeleteQuery: (String) -> Unit = {},
    onClearAll: () -> Unit = {},
    onSearchConfirmed: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Отслеживаем фокус ВСЕГО виджета (поле ввода + история), чтобы история не пропадала при скролле вниз
    var widgetHasFocus by remember { mutableStateOf(false) }
    // Отслеживаем фокус именно самого текстового поля для визуальной подсветки
    var isSearchFocused by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }

    val filteredHistory = remember(searchQuery, searchHistory) {
        if (searchQuery.isBlank()) {
            searchHistory
        } else {
            searchHistory.filter { it.query.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .onFocusChanged { widgetHasFocus = it.hasFocus },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
        ) {
            // Строка поиска
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .liquidGlass(RoundedCornerShape(22.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
                    // Добавляем красивую рамку, когда текстовое поле в фокусе (т.к. мы убрали sleekTvFocus)
                    .border(
                        width = if (isSearchFocused && isTvOptimized) 2.dp else 0.dp,
                        color = if (isSearchFocused && isTvOptimized) Primary else Color.Transparent,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Поиск",
                    tint = GreyText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Primary), // ФИКС: Делаем курсор видимым!
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(inputFocusRequester)
                        // ФИКС: Убрали sleekTvFocus, чтобы пульт давал фокус НАПРЯМУЮ полю ввода
                        .onFocusChanged { 
                            isSearchFocused = it.isFocused 
                        }
                        .onKeyEvent { event ->
                            // Если нажали ОК на пульте - открываем клавиатуру
                            if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                                keyboardController?.show()
                                true
                            } else {
                                false
                            }
                        }
                        .testTag("search_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                onSearchConfirmed(searchQuery)
                            }
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(text = "Поиск видео...", color = GreyText, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    // ФИКС: Используем Box вместо IconButton для избежания "матрешки" фокусов на ТВ
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .sleekTvFocus(
                                shape = CircleShape, 
                                enabled = isTvOptimized, 
                                onEnter = { 
                                    onSearchQueryChanged("")
                                    inputFocusRequester.requestFocus() 
                                }
                            )
                            .clip(CircleShape)
                            .clickable {
                                onSearchQueryChanged("")
                                inputFocusRequester.requestFocus() 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить",
                            tint = GreyText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .sleekTvFocus(shape = CircleShape, enabled = isTvOptimized, onEnter = onMicClick)
                            .clip(CircleShape)
                            .clickable { onMicClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Голосовой поиск",
                            tint = GreyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // История поиска (Выдвигается плавно, находится в одном окне со строкой)
            AnimatedVisibility(
                visible = widgetHasFocus && filteredHistory.isNotEmpty(),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp), 
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant
                                         else MaterialTheme.colorScheme.background
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "История поиска",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "История поиска",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // ФИКС: Используем Box вместо TextButton
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .sleekTvFocus(
                                        shape = RoundedCornerShape(8.dp), 
                                        enabled = isTvOptimized, 
                                        onEnter = { 
                                            onClearAll()
                                            inputFocusRequester.requestFocus() 
                                        }
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onClearAll()
                                        inputFocusRequester.requestFocus() 
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Очистить всё",
                                    color = Primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            filteredHistory.take(6).forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sleekTvFocus(
                                            shape = RoundedCornerShape(8.dp),
                                            enabled = isTvOptimized,
                                            onEnter = {
                                                onSearchQueryChanged(item.query)
                                                onSearchConfirmed(item.query)
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                            }
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSearchQueryChanged(item.query)
                                            onSearchConfirmed(item.query)
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .testTag("history_item_${item.query}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.query,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    // ФИКС крестика удаления внутри истории
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .sleekTvFocus(
                                                shape = CircleShape,
                                                enabled = isTvOptimized,
                                                onEnter = { 
                                                    onDeleteQuery(item.query)
                                                    inputFocusRequester.requestFocus() 
                                                }
                                            )
                                            .clip(CircleShape)
                                            .clickable {
                                                onDeleteQuery(item.query)
                                                inputFocusRequester.requestFocus() 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
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
}

// CategoryRow и FeedTabRow остаются без изменений (как в оригинале)
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
                    .sleekTvFocus(shape = RoundedCornerShape(12.dp), scaleAmount = 1.05f, onEnter = { onCategorySelected(cat) })
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
    tabs: List<com.example.data.rutube.parser.TabInfo>,
    selectedTab: com.example.data.rutube.parser.TabInfo?,
    onTabSelected: (com.example.data.rutube.parser.TabInfo) -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
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
                    .sleekTvFocus(shape = RoundedCornerShape(100.dp), scaleAmount = 1.05f, onEnter = { onTabSelected(tab) })
                    .then(
                        if (isSelected && focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(100.dp)
                                )
                        } else {
                            Modifier.liquidGlass(RoundedCornerShape(100.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
                        }
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = tab.name ?: "Раздел",
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
