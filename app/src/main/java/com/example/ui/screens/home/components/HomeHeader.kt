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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
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
                    .sleekTvFocus(shape = RoundedCornerShape(8.dp))
                    .testTag("search_input"),
                singleLine = true,
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
                    modifier = Modifier.size(24.dp).sleekTvFocus(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = GreyText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(24.dp).sleekTvFocus(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Голосовой поиск",
                        tint = GreyText,
                        modifier = Modifier.size(18.dp)
                    )
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
                    .sleekTvFocus(shape = RoundedCornerShape(12.dp), scaleAmount = 1.18f, onEnter = { onCategorySelected(cat) })
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
    tabs: List<com.example.data.rutube.SmartRutubeParser.TabInfo>,
    selectedTab: com.example.data.rutube.SmartRutubeParser.TabInfo?,
    onTabSelected: (com.example.data.rutube.SmartRutubeParser.TabInfo) -> Unit,
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
                    .sleekTvFocus(shape = RoundedCornerShape(100.dp), scaleAmount = 1.18f, onEnter = { onTabSelected(tab) })
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
