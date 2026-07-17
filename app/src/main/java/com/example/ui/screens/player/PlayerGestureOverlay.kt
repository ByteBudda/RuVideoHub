package com.example.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Вертикальная полупрозрачная полоска для громкости (справа)
 * или яркости (слева)
 */
@Composable
fun VerticalIndicatorBar(
    value: Float,
    type: String,
    modifier: Modifier = Modifier
) {
    val isVolume = type == "volume"
    val icon = if (isVolume) {
        if (value == 0f) Icons.AutoMirrored.Filled.VolumeOff
        else Icons.AutoMirrored.Filled.VolumeUp
    } else {
        Icons.Default.LightMode
    }
    
    val gradientColors = if (isVolume) {
        listOf(Color(0xFF4FC3F7), Color(0xFF00E5FF))
    } else {
        listOf(Color(0xFFFFD54F), Color(0xFFFFAB00))
    }
    
    Column(
        modifier = modifier
            .width(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = gradientColors,
                            startY = 1f,
                            endY = 0f
                        )
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${(value * 100).toInt()}%",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Горизонтальная полоска перемотки снизу
 */
@Composable
fun SeekProgressBar(
    positionMs: Long,
    durationMs: Long,
    isSeekingForward: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSeekingForward) Icons.Default.FastForward else Icons.Default.FastRewind,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = formatDuration(positionMs),
            color = Color(0xFF00E5FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(50.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF29B6F6), Color(0xFF00E5FF))
                        )
                    )
            )
        }
        
        Text(
            text = formatDuration(durationMs),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Основной обработчик жестов
 */
@Composable
fun PlayerGestureOverlay(
    modifier: Modifier = Modifier,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    currentPositionProvider: () -> Long,
    durationProvider: () -> Long,
    onSeekCompleted: (Long) -> Unit,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val viewConfiguration = LocalViewConfiguration.current
    
    var hudVolume by remember { mutableStateOf<Float?>(null) }
    var hudBrightness by remember { mutableStateOf<Float?>(null) }
    
    var hudSeekPosition by remember { mutableStateOf<Long?>(null) }
    var hudSeekDuration by remember { mutableStateOf<Long?>(null) }
    var isSeekingForward by remember { mutableStateOf(true) }
    
    var lastInteractionTime by remember { mutableStateOf(0L) }
    
    LaunchedEffect(lastInteractionTime) {
        if (lastInteractionTime > 0L) {
            delay(1500)
            hudVolume = null
            hudBrightness = null
            hudSeekPosition = null
            hudSeekDuration = null
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 3) {
                                onDoubleTapLeft()
                            } else if (offset.x > size.width * 2 / 3) {
                                onDoubleTapRight()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull() ?: continue
                            if (down.pressed) {
                                val downPos = down.position
                                val isSeekDrag = downPos.y > size.height * 0.75f
                                
                                var dragStarted = false
                                var accumulatedDragX = 0f
                                var accumulatedDragY = 0f
                                var dragType = 0
                                val touchSlop = viewConfiguration.touchSlop
                                
                                var startPlaybackPos = 0L
                                var videoDuration = 0L
                                
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                                
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    
                                    val posChange = change.positionChange()
                                    accumulatedDragX += posChange.x
                                    accumulatedDragY += posChange.y
                                    
                                    if (!dragStarted) {
                                        if (abs(accumulatedDragX) > touchSlop || abs(accumulatedDragY) > touchSlop) {
                                            dragStarted = true
                                            if (isSeekDrag) {
                                                dragType = 1
                                                startPlaybackPos = currentPositionProvider()
                                                videoDuration = durationProvider()
                                                lastInteractionTime = System.currentTimeMillis()
                                            } else {
                                                if (abs(accumulatedDragY) > abs(accumulatedDragX)) {
                                                    dragType = if (downPos.x < size.width / 2f) 2 else 3
                                                    lastInteractionTime = System.currentTimeMillis()
                                                } else {
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (dragStarted) {
                                        change.consume()
                                        lastInteractionTime = System.currentTimeMillis()
                                        
                                        when (dragType) {
                                            1 -> {
                                                if (videoDuration > 0) {
                                                    val maxSeekSpan = if (videoDuration < 300000L) videoDuration else 300000L
                                                    val deltaMs = ((accumulatedDragX / size.width) * maxSeekSpan).toLong()
                                                    val targetPos = (startPlaybackPos + deltaMs).coerceIn(0, videoDuration)
                                                    isSeekingForward = deltaMs >= 0
                                                    hudSeekPosition = targetPos
                                                    hudSeekDuration = videoDuration
                                                }
                                            }
                                            2 -> {
                                                val activity = context.findActivity()
                                                activity?.window?.let { window ->
                                                    val attrs = window.attributes
                                                    val currentBrightness = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
                                                    val diff = -(posChange.y / size.height) * 1.2f
                                                    val newBrightness = (currentBrightness + diff).coerceIn(0f, 1f)
                                                    attrs.screenBrightness = newBrightness
                                                    window.attributes = attrs
                                                    hudBrightness = newBrightness
                                                    hudVolume = null
                                                }
                                            }
                                            3 -> {
                                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                                val diff = -(posChange.y / size.height) * maxVol * 1.2f
                                                val newVol = (currentVol + diff).coerceIn(0f, maxVol)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                                                hudVolume = newVol / maxVol
                                                hudBrightness = null
                                            }
                                        }
                                    }
                                }
                                
                                if (dragStarted && dragType == 1) {
                                    hudSeekPosition?.let { finalPos ->
                                        onSeekCompleted(finalPos)
                                    }
                                }
                            }
                        }
                    }
                }
        )
        
        AnimatedVisibility(
            visible = hudBrightness != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
        ) {
            hudBrightness?.let { bri ->
                VerticalIndicatorBar(
                    value = bri,
                    type = "brightness"
                )
            }
        }
        
        AnimatedVisibility(
            visible = hudVolume != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
        ) {
            hudVolume?.let { vol ->
                VerticalIndicatorBar(
                    value = vol,
                    type = "volume"
                )
            }
        }
        
        AnimatedVisibility(
            visible = hudSeekPosition != null && hudSeekDuration != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        ) {
            if (hudSeekPosition != null && hudSeekDuration != null) {
                SeekProgressBar(
                    positionMs = hudSeekPosition!!,
                    durationMs = hudSeekDuration!!,
                    isSeekingForward = isSeekingForward
                )
            }
        }
    }
}

private fun formatDuration(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
