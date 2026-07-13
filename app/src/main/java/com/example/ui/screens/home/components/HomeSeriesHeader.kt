package com.example.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Video
import com.example.ui.theme.liquidGlass

@Composable
fun SeriesHeader(
    series: Video?,
    isDark: Boolean,
    isTvOptimized: Boolean,
    focusRequester: FocusRequester? = null
) {
    if (series == null) return

    val isPlaylist = series.duration == com.example.utils.VideoType.PLAYLIST || series.duration == "ПЛЕЙЛИСТ"
    val coverUrl = series.thumbnailUrl
    
    // For playlists, if authorAvatarUrl is null, we do NOT want to use coverUrl as the poster,
    // because playlists typically don't have a vertical poster, just the top banner.
    val posterUrl = if (isPlaylist) {
        series.authorAvatarUrl
    } else {
        if (!series.authorAvatarUrl.isNullOrBlank()) series.authorAvatarUrl else coverUrl
    }
    
    // We show the large cover if it's explicitly a playlist, or if cover != poster
    val showCoverBanner = !coverUrl.isNullOrBlank() && (coverUrl != posterUrl || isPlaylist)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .liquidGlass(
                RoundedCornerShape(20.dp),
                borderWidth = 1.2.dp,
                isDark = isDark,
                isTvOptimized = isTvOptimized
            )
    ) {
        if (showCoverBanner) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Обложка сериала",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
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
            if (showCoverBanner) {
                Spacer(modifier = Modifier.height(40.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Poster
                // If it's a playlist and there's no posterUrl (authorAvatar), just omit it
                // instead of showing a grey box.
                if (!posterUrl.isNullOrBlank() || !isPlaylist) {
                    val posterRatio = 2f / 3f
                    val posterWidth = 100.dp
                    
                    if (!posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = series.title,
                            modifier = Modifier
                                .width(posterWidth)
                                .aspectRatio(posterRatio)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(posterWidth)
                                .aspectRatio(posterRatio)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }

                // Title and details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = series.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    if (series.views.isNotBlank()) {
                        Text(
                            text = series.views, // e.g. "Год выпуска" or "X сезонов" or "N видео"
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Description with scroll
            if (series.description.isNotBlank()) {
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(scrollState)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = series.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
