package com.example.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Video
import com.example.ui.theme.liquidGlass
import com.example.ui.screens.sleekTvFocus

@Composable
fun AdaptiveCatalogItem(
    video: Video,
    onFolderClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (video.duration) {
        com.example.utils.VideoType.CHANNEL -> {
            SleekChannelRowItem(
                video = video,
                onClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
        com.example.utils.VideoType.PLAYLIST -> {
            SleekPlaylistCard(
                video = video,
                onClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
        com.example.utils.VideoType.SERIES -> {
            SleekSeriesCard(
                video = video,
                onClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
        else -> {
            SleekFolderGridItem(
                video = video,
                onFolderClick = onFolderClick,
                onBookmarkToggle = onBookmarkToggle,
                isDark = isDark,
                isTvOptimized = isTvOptimized,
                modifier = modifier
            )
        }
    }
}

@Composable
fun SleekFolderGridItem(
    video: Video,
    onFolderClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hashColor = remember(video.title) {
        val hues = listOf(
            Color(0xFFFF253E), // Coral Red
            Color(0xFF00C853), // Emerald Green
            Color(0xFF00B0FF), // Neon Blue
            Color(0xFFAA00FF), // Deep Violet
            Color(0xFFFFD600)  // Gold Amber
        )
        val index = kotlin.math.abs(video.title.hashCode()) % hues.size
        hues[index]
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sleekTvFocus(shape = RoundedCornerShape(12.dp), onEnter = onFolderClick)
            .clickable(onClick = onFolderClick)
            .liquidGlass(RoundedCornerShape(12.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isChannelType = video.duration == com.example.utils.VideoType.CHANNEL

            if (isChannelType && !video.thumbnailUrl.isNullOrBlank()) {
                val imageShape = CircleShape
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(imageShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    hashColor,
                                    hashColor.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }

            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CatalogGroupHeader(
    groupName: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = groupName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}
