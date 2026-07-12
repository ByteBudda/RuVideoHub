package com.example.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.GreyText
import com.example.ui.theme.liquidGlass
import com.example.ui.screens.sleekTvFocus

@Composable
fun SleekSeriesCard(
    video: Video,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val customTheme = com.example.ui.theme.LocalCustomTheme.current
    val cardCornerRadius = customTheme?.cardCornerRadius ?: 16
    val cardShape = RoundedCornerShape(cardCornerRadius.dp)

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .sleekTvFocus(shape = cardShape, onEnter = onClick)
            .liquidGlass(cardShape, borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail with Series Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(
                        topStart = cardCornerRadius.dp,
                        topEnd = cardCornerRadius.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    ))
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // Info details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = video.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
