package com.example.ui.screens.home.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
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
    var isFocused by remember { mutableStateOf(false) }
    var parentWidth by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current

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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .onSizeChanged { parentWidth = it.width }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .liquidGlass(RoundedCornerShape(22.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
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
                    modifier = Modifier
                        .weight(1f)
                        .sleekTvFocus(shape = RoundedCornerShape(8.dp), enabled = isTvOptimized)
                        .onFocusChanged { isFocused = it.isFocused }
                        .testTag("search_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                onSearchConfirmed(searchQuery)
                            }
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Поиск видео...",
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
                        modifier = Modifier.size(36.dp).sleekTvFocus(CircleShape, onEnter = { onSearchQueryChanged("") }, enabled = isTvOptimized)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить",
                            tint = GreyText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onMicClick,
                        modifier = Modifier.size(36.dp).sleekTvFocus(CircleShape, onEnter = onMicClick, enabled = isTvOptimized)
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

            // Dropdown Overlay for Search History (Browser-like behavior!)
            if (isFocused && filteredHistory.isNotEmpty()) {
                val density = LocalDensity.current
                val widthDp = remember(parentWidth) { with(density) { parentWidth.toDp() } }
                
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, with(density) { 46.dp.roundToPx() }),
                    onDismissRequest = { isFocused = false },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .width(widthDp)
                            .padding(horizontal = 4.dp),
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
                                TextButton(
                                    onClick = onClearAll,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag("clear_history_button")
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
                                            .clickable {
                                                onSearchQueryChanged(item.query)
                                                onSearchConfirmed(item.query)
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
                                        
                                        IconButton(
                                            onClick = { onDeleteQuery(item.query) },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("delete_history_item_${item.query}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Удалить ${item.query} из истории",
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
