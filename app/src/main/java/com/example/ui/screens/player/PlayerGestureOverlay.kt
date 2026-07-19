package com.example.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

// Уникальное имя функции, чтобы избежать конфликтов с другими файлами (Overload resolution ambiguity)
fun Context.getActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivityOrNull()
    else -> null
}

// Форматирование миллисекунд в красивую строку (например 01:23)
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

/**
 * Stateful Gesture catcher overlay supporting tap, double taps,
 * vertical gestures for volume and brightness, and horizontal swipes along the bottom 25% for seeking.
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
    
    var hudVolume by remember { mutableStateOf<Float?>(null) }
    var hudBrightness by remember { mutableStateOf<Float?>(null) }
    
    var hudSeekPosition by remember { mutableStateOf<Long?>(null) }
    var hudSeekDuration by remember { mutableStateOf<Long?>(null) }
    var isSeekingForward by remember { mutableStateOf(true) }
    
    var lastInteractionTime by remember { mutableStateOf(0L) }
    
    // Automatic dismissal of visual gestures HUDs (исчезают через 1.5 сек)
    LaunchedEffect(lastInteractionTime) {
        if (lastInteractionTime > 0L) {
            delay(1500)
            hudVolume = null
            hudBrightness = null
            hudSeekPosition = null
            hudSeekDuration = null
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            // Tap gestures
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
            // Swipe gestures (Seek, Brightness, Volume)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (down.pressed) {
                            val downPos = down.position
                            // bottom 25% of the screen triggers horizontal scroll seeking
                            val isSeekDrag = downPos.y > size.height * 0.75f
                            
                            var dragStarted = false
                            var accumulatedDragX = 0f
                            var accumulatedDragY = 0f
                            var dragType = 0 // 0: undecided, 1: seek, 2: brightness, 3: volume
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
                                    
                                    // Используем классический when, все ветки строго возвращают Unit
                                    when (dragType) {
                                        1 -> { // SEEK
                                            if (videoDuration > 0) {
                                                val maxSeekSpan = if (videoDuration < 300000L) videoDuration else 300000L
                                                val deltaMs = ((accumulatedDragX / size.width) * maxSeekSpan).toLong()
                                                val targetPos = (startPlaybackPos + deltaMs).coerceIn(0, videoDuration)
                                                isSeekingForward = deltaMs >= 0
                                                hudSeekPosition = targetPos
                                                hudSeekDuration = videoDuration
                                            }
                                        }
                                        2 -> { // BRIGHTNESS
                                            val activityWindow = context.getActivityOrNull()?.window
                                            if (activityWindow != null) {
                                                val attrs = activityWindow.attributes
                                                val currentBrightness = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                                val diff = -(posChange.y / size.height) * 1.5f
                                                val newBrightness = (currentBrightness + diff).coerceIn(0f, 1f)
                                                attrs.screenBrightness = newBrightness
                                                activityWindow.attributes = attrs
                                                hudBrightness = newBrightness
                                                hudVolume = null
                                            }
                                        }
                                        3 -> { // VOLUME
                                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                            val diff = -(posChange.y / size.height) * maxVol * 1.5f
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
    ) {
        // --- НОВЫЕ КРАСИВЫЕ ОВЕРЛЕИ (появляются только при свайпах) ---

        // 1. Яркость (Слева)
        AnimatedVisibility(
            visible = hudBrightness != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            hudBrightness?.let { bri ->
                IndicatorBar(
                    icon = Icons.Default.LightMode,
                    value = bri,
                    color = Color.White
                )
            }
        }

        // 2. Громкость (Справа)
        AnimatedVisibility(
            visible = hudVolume != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            hudVolume?.let { vol ->
                IndicatorBar(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    value = vol,
                    color = Color(0xFF00E5FF) // Голубоватый цвет для громкости
                )
            }
        }

        // 3. Перемотка (Снизу - аккуратный прогрессбар с тайм-метками)
        AnimatedVisibility(
            visible = hudSeekPosition != null && hudSeekDuration != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
        ) {
            if (hudSeekPosition != null && hudSeekDuration != null) {
                val progress = if (hudSeekDuration!! > 0) {
                    (hudSeekPosition!!.toFloat() / hudSeekDuration!!.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Тайм-метки над ползунком
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(hudSeekPosition!!),
                            color = Color(0xFF00E5FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatDuration(hudSeekDuration!!),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Сам прогресс-бар
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color(0xFF00E5FF))
                        )
                    }
                }
            }
        }
    }
}

// Вспомогательный UI-компонент для ползунков громкости и яркости
@Composable
fun IndicatorBar(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(44.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.4f)) // Полупрозрачный фон
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Заполнение ползунка
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value)
                .background(color.copy(alpha = 0.8f))
        )
        // Иконка сверху
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .padding(10.dp)
                .align(Alignment.TopCenter)
                .size(24.dp)
        )
    }
}
