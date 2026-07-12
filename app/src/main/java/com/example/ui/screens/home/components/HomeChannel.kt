package com.example.ui.screens.home.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.liquidGlass
import com.example.ui.screens.sleekTvFocus
import com.example.ui.screens.home.utils.ChannelAvatarPlaceholder

@Composable
fun CircularChannelItem(
    video: Video,
    onClick: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatarUrl = if (!video.thumbnailUrl.isNullOrBlank()) {
        video.thumbnailUrl
    } else if (!video.authorAvatarUrl.isNullOrBlank()) {
        video.authorAvatarUrl
    } else {
        ""
    }

    Column(
        modifier = modifier
            .width(84.dp)
            .sleekTvFocus(shape = RoundedCornerShape(12.dp), scaleAmount = 1.18f, onEnter = onClick)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF0F0F0))
                .border(2.dp, Brush.linearGradient(listOf(Primary, Color(0xFF9C27B0))), CircleShape)
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                ChannelAvatarPlaceholder(title = video.title)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = video.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun SleekChannelRowItem(
    video: Video,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatarUrl = if (!video.thumbnailUrl.isNullOrBlank()) {
        video.thumbnailUrl
    } else if (!video.authorAvatarUrl.isNullOrBlank()) {
        video.authorAvatarUrl
    } else {
        ""
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sleekTvFocus(shape = RoundedCornerShape(16.dp), scaleAmount = 1.12f, onEnter = onClick)
            .clickable(onClick = onClick)
            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDark, isTvOptimized = isTvOptimized)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    ChannelAvatarPlaceholder(title = video.title)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.views.ifBlank { "Авторский канал" },
                    fontSize = 11.sp,
                    color = GreyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onBookmarkToggle,
                modifier = Modifier
                    .size(40.dp)
                    .sleekTvFocus(CircleShape, scaleAmount = 1.18f)
            ) {
                Icon(
                    imageVector = if (video.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "В избранное",
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChannelHeader(
    channel: Video?,
    onFavoriteToggle: (Video) -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    if (channel == null) return

    val context = LocalContext.current
    
    val avatarUrl = if (!channel.authorAvatarUrl.isNullOrBlank()) {
        channel.authorAvatarUrl
    } else if (!channel.thumbnailUrl.isNullOrBlank()) {
        channel.thumbnailUrl
    } else {
        ""
    }
    
    val coverUrl = channel.thumbnailUrl

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .liquidGlass(
                RoundedCornerShape(20.dp),
                borderWidth = 1.2.dp,
                isDark = isDark,
                isTvOptimized = isTvOptimized
            )
    ) {
        if (!coverUrl.isNullOrBlank() && coverUrl != avatarUrl) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Обложка канала",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!coverUrl.isNullOrBlank() && coverUrl != avatarUrl) {
                Spacer(modifier = Modifier.height(40.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Channel Avatar
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Аватар канала",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    ChannelAvatarPlaceholder(title = channel.title)
                }
            }

            // Channel Title and details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channel.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (channel.views.isNotBlank()) {
                    Text(
                        text = channel.views,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (channel.timeAgo.isNotBlank() && channel.timeAgo != channel.views) {
                    Text(
                        text = channel.timeAgo,
                        fontSize = 11.sp,
                        color = GreyText
                    )
                }
            }
        }

        // Description if available
        if (channel.description.isNotBlank()) {
            Text(
                text = channel.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                lineHeight = 16.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
}
