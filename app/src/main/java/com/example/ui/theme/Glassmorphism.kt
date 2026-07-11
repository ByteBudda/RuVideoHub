package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A beautiful, ambient background that draws soft, colorful liquid-like glowing blobs 
 * behind your UI. This makes frosted glass layers pop with rich, vibrant colors.
 */
@Composable
fun AmbientGlassBackground(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    isTvOptimized: Boolean = false,
    content: @Composable () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val enableGlassmorphism = appTheme == "dark" || appTheme == "light"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                if (!enableGlassmorphism) return@drawBehind

                val width = size.width
                val height = size.height
                if (width > 0 && height > 0 && !isTvOptimized) {
                    // Blob 1: Top Right - Amethyst Purple
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = if (isDark) {
                                listOf(Color(0x3A7F52FF), Color.Transparent)
                            } else {
                                listOf(Color(0x227F52FF), Color.Transparent)
                            },
                            center = Offset(width * 0.85f, height * 0.2f),
                            radius = width * 0.65f
                        ),
                        radius = width * 0.65f,
                        center = Offset(width * 0.85f, height * 0.2f)
                    )
                    // Blob 2: Middle Left - Soft Cyan/Teal
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = if (isDark) {
                                listOf(Color(0x2600F5D4), Color.Transparent)
                            } else {
                                listOf(Color(0x1800F5D4), Color.Transparent)
                            },
                            center = Offset(width * 0.1f, height * 0.55f),
                            radius = width * 0.55f
                        ),
                        radius = width * 0.55f,
                        center = Offset(width * 0.1f, height * 0.55f)
                    )
                    // Blob 3: Bottom Right - Vibrant Rose
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = if (isDark) {
                                listOf(Color(0x26FF007F), Color.Transparent)
                            } else {
                                listOf(Color(0x15FF007F), Color.Transparent)
                            },
                            center = Offset(width * 0.75f, height * 0.85f),
                            radius = width * 0.6f
                        ),
                        radius = width * 0.6f,
                        center = Offset(width * 0.75f, height * 0.85f)
                    )
                }
            }
    ) {
        content()
    }
}

/**
 * Applies a liquid glass / glassmorphism card style to a Composable.
 * Features a semi-transparent background, subtle gloss highlight borders, and clipping.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    borderWidth: Dp = 1.dp,
    isDark: Boolean, // Kept for API compatibility, but we use MaterialTheme mostly
    isTvOptimized: Boolean = false
): Modifier = composed {
    val appTheme = LocalAppTheme.current
    val enableGlassmorphism = appTheme == "dark" || appTheme == "light"

    if (!enableGlassmorphism) {
        return@composed this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(borderWidth, MaterialTheme.colorScheme.outlineVariant, shape)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    val bgModifier = if (isTvOptimized) {
        Modifier.background(surfaceColor)
    } else {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    surfaceColor.copy(alpha = 0.85f),
                    surfaceColor.copy(alpha = 0.4f)
                )
            )
        )
    }
    
    val borderModifier = if (isTvOptimized) {
        Modifier.border(
            width = borderWidth,
            color = onSurfaceColor.copy(alpha = 0.15f),
            shape = shape
        )
    } else {
        Modifier.border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    onSurfaceColor.copy(alpha = 0.3f),
                    onSurfaceColor.copy(alpha = 0.1f),
                    onSurfaceColor.copy(alpha = 0.05f),
                    Color(0x24000000)
                )
            ),
            shape = shape
        )
    }

    this
        .clip(shape)
        .then(bgModifier)
        .then(borderModifier)
}
