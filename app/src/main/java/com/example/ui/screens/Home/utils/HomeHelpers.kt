package com.example.ui.screens.home.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Video
import com.example.ui.theme.GreyText
import com.example.ui.screens.sleekTvFocus

@Composable
fun ChannelAvatarPlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    val initials = remember(title) {
        val words = title.trim().split(Regex("\\s+"))
        if (words.size >= 2) {
            (words[0].take(1) + words[1].take(1)).uppercase()
        } else if (title.isNotBlank()) {
            title.take(2).uppercase()
        } else {
            "CH"
        }
    }
    val hashColor = remember(title) {
        val hues = listOf(
            Color(0xFFFF253E), // Coral Red
            Color(0xFF00C853), // Emerald Green
            Color(0xFF00B0FF), // Neon Blue
            Color(0xFFAA00FF), // Deep Violet
            Color(0xFFFFD600)  // Gold Amber
        )
        val index = kotlin.math.abs(title.hashCode()) % hues.size
        hues[index]
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        hashColor,
                        hashColor.copy(alpha = 0.5f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun SleekSearchChannelItem(
    channel: Video,
    onClick: () -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatarUrl = if (!channel.authorAvatarUrl.isNullOrBlank()) {
        channel.authorAvatarUrl
    } else if (!channel.thumbnailUrl.isNullOrBlank()) {
        channel.thumbnailUrl
    } else {
        ""
    }

    Column(
        modifier = modifier
            .width(96.dp)
            .sleekTvFocus(shape = CircleShape, onEnter = onClick)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circle Avatar with Liquid Glass Border Effect
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(
                    width = 2.dp,
                    color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = channel.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                ChannelAvatarPlaceholder(title = channel.title)
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Title
        Text(
            text = channel.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
        
        // Subtitle (Subscriber or video count, etc.)
        val originalText = channel.views.ifBlank { "Канал" }
        val shortSubsText = originalText
            .replace("подписчиков", "подп.")
            .replace("подписчика", "подп.")
            .replace("подписчик", "подп.")
        Text(
            text = shortSubsText,
            color = GreyText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
    }
}
