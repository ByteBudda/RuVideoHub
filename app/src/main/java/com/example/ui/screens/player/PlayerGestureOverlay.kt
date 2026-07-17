package com.example.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

// Format duration helper (e.g. 01:23)
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
 * A beautiful, wide semi-transparent slider-indicator HUD for Volume/Brightness.
 */
@Composable
fun VolumeBrightnessHud(
    type: String, // "volume" or "brightness"
    value: Float, // 0f..1f
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(320.dp) // Wide and gorgeous semi-transparent bar
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = if (type == "volume") {
                if (value == 0f) Icons.Default.VolumeOff
                else if (value < 0.5f) Icons.Default.VolumeDown
                else Icons.Default.VolumeUp
            } else {
                Icons.Default.LightMode
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        
        // Custom colorful modern track bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF42A5F5), // Electric Blue
                                Color(0xFF00E5FF)  // Bright Cyan
                            )
                        )
                    )
            )
        }
        
        Text(
            text = "${(value * 100).toInt()}%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * A gorgeous Seek overlay HUD showing when the user is dragging along the bottom.
 */
@Composable
fun SeekProgressHud(
    positionMs: Long,
    durationMs: Long,
    isSeekingForward: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    
    Column(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isSeekingForward) Icons.Default.FastForward else Icons.Default.FastRewind,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(36.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(positionMs),
                color = Color(0xFF00E5FF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDuration(durationMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Beautiful bottom-seek progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                            colors = listOf(
                                Color(0xFF29B6F6),
                                Color(0xFF00E5FF)
                            )
                        )
                    )
            )
        }
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
    
    // Automatic dismissal of visual gestures HUDs
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
                                    
                                    when (dragType) {
                                        1 -> { // SEEK
                                            if (videoDuration > 0) {
                                                // Dragging full screen width can seek up to 5 minutes or total duration, whichever is smaller
                                                val maxSeekSpan = if (videoDuration < 300000L) videoDuration else 300000L
                                                val deltaMs = ((accumulatedDragX / size.width) * maxSeekSpan).toLong()
                                                val targetPos = (startPlaybackPos + deltaMs).coerceIn(0, videoDuration)
                                                isSeekingForward = deltaMs >= 0
                                                hudSeekPosition = targetPos
                                                hudSeekDuration = videoDuration
                                            }
                                        }
                                        2 -> { // BRIGHTNESS
                                            context.findActivity()?.window?.let { window ->
                                                val attrs = window.attributes
                                                val currentBrightness = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                                val diff = -(posChange.y / size.height) * 1.5f
                                                val newBrightness = (currentBrightness + diff).coerceIn(0f, 1f)
                                                attrs.screenBrightness = newBrightness
                                                window.attributes = attrs
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
        // Render HUD Overlays when active
        
        // 1. Volume Overlay (Top Center)
        AnimatedVisibility(
            visible = hudVolume != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            hudVolume?.let { vol ->
                VolumeBrightnessHud(type = "volume", value = vol)
            }
        }
        
        // 2. Brightness Overlay (Top Center)
        AnimatedVisibility(
            visible = hudBrightness != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            hudBrightness?.let { bri ->
                VolumeBrightnessHud(type = "brightness", value = bri)
            }
        }
        
        // 3. Seek Progress Overlay (Center)
        AnimatedVisibility(
            visible = hudSeekPosition != null && hudSeekDuration != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (hudSeekPosition != null && hudSeekDuration != null) {
                SeekProgressHud(
                    positionMs = hudSeekPosition!!,
                    durationMs = hudSeekDuration!!,
                    isSeekingForward = isSeekingForward
                )
            }
        }
    }
}
