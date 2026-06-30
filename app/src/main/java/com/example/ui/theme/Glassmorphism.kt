package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F0E13) else Color(0xFFF6F4FA))
            .drawBehind {
                val width = size.width
                val height = size.height
                if (width > 0 && height > 0) {
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
    isDark: Boolean
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    Color(0x3D1A1625), // Ultra-premium dark translucent
                    Color(0x1A09070F)
                )
            } else {
                listOf(
                    Color(0xA6FFFFFF), // Pure clear glassy white
                    Color(0x66F1EDF8)
                )
            }
        )
    )
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    Color(0x3DFFFFFF), // Refractive highlight on top edge
                    Color(0x10FFFFFF),
                    Color(0x08FFFFFF),
                    Color(0x24000000)  // Shadow on bottom edge
                )
            } else {
                listOf(
                    Color(0x99FFFFFF),
                    Color(0x30FFFFFF),
                    Color(0x15FFFFFF),
                    Color(0x267F52FF)  // Hint of violet reflection
                )
            }
        ),
        shape = shape
    )
