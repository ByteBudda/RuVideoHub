package com.example.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.Primary

@Composable
fun VideoThumbnail(
    id: String,
    duration: String,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    hasPlayOverlay: Boolean = false,
    isPlaying: Boolean = false,
    isWatched: Boolean = false,
    playbackProgress: Float = 0f,
    onPlayClick: (() -> Unit)? = null,
    viewsAndTimeAgo: String? = null,
    shape: Shape? = null,
    progressTimestamp: String? = null
) {
    val customTheme = com.example.ui.theme.LocalCustomTheme.current
    val cardCornerRadius = customTheme?.cardCornerRadius ?: 16
    val finalShape = shape ?: RoundedCornerShape(cardCornerRadius.dp)

    val isFolder = duration == com.example.utils.VideoType.FOLDER
    
    val gradientColors = remember(id, isFolder) {
        when {
            isFolder -> listOf(Color(0xFFFFB300), Color(0xFFE65100))
            id == "api_review" -> listOf(Color(0xFF6750A4), Color(0xFF21005D))
            id == "top_10" -> listOf(Color(0xFF4B3978), Color(0xFF1B033A))
            id == "history_rutube" -> listOf(Color(0xFF8B5CF6), Color(0xFF3B0764))
            id == "android_2026" -> listOf(Color(0xFF0284C7), Color(0xFF0369A1))
            id == "sleek_compose" -> listOf(Color(0xFFEC4899), Color(0xFFBE185D))
            id == "recommender_secrets" -> listOf(Color(0xFF10B981), Color(0xFF047857))
            else -> listOf(Color(0xFF333333), Color(0xFF111111))
        }
    }
    
    val brush = remember(gradientColors) {
        Brush.linearGradient(colors = gradientColors)
    }

    Box(
        modifier = modifier
            .clip(finalShape)
            .background(brush)
    ) {
        if (isFolder) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Папка раздела",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        } else if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Draw grid lines
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Tech grid aesthetics
                val lines = 5
                for (i in 1..lines) {
                    val r = i.toFloat() / (lines + 1)
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(width * r, 0f),
                        end = Offset(width * r, height),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, height * r),
                        end = Offset(width, height * r),
                        strokeWidth = 1.5f
                    )
                }
            }

            // Illustrative icons overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp)
            ) {
                val elementIcon = when (id) {
                    "api_review" -> Icons.Default.Code
                    "top_10" -> Icons.Default.ElectricBolt
                    "history_rutube" -> Icons.Default.Timeline
                    "android_2026" -> Icons.Default.Android
                    "sleek_compose" -> Icons.Default.AutoAwesome
                    "recommender_secrets" -> Icons.Default.Psychology
                    else -> Icons.Default.Movie
                }
                Icon(
                    imageVector = elementIcon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // Dark dim gradient layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                    )
                )
        )

        // Core visual player overlays
        if (hasPlayOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { onPlayClick?.invoke() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.40f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Кнопка воспроизведения",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Duration capsule
        if (duration.isNotBlank() && duration != "СЕРИАЛ" && duration != "ПРОМО" && duration != "КАТАЛОГ" && progressTimestamp.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = duration,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }

        if (isWatched) {
            // Subtle translucent dark overlay over the entire thumbnail
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
            // Beautiful checkmark icon in the top right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Просмотрено",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(14.dp)
                )
            }
        } else if (playbackProgress > 0f) {
            // Sleek progress bar overlay at the very bottom of the thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomStart)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(playbackProgress)
                        .background(Primary)
                )
            }

            // Beautiful "Продолжить" badge/timestamp in the top right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                if (!progressTimestamp.isNullOrBlank()) {
                    Text(
                        text = progressTimestamp,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Продолжить",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Продолжить",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // Views and Time Ago capsule removed
    }
}
