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
    val effect = LocalThemeEffect.current

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                val width = size.width
                val height = size.height
                if (width <= 0 || height <= 0 || isTvOptimized) return@drawBehind

                when (effect) {
                    "glassmorphism" -> {
                        val alpha1 = if (isDark) 0.25f else 0.15f
                        val alpha2 = if (isDark) 0.2f else 0.12f
                        val alpha3 = if (isDark) 0.2f else 0.12f

                        // Blob 1: Top Right
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primary.copy(alpha = alpha1), Color.Transparent),
                                center = Offset(width * 0.85f, height * 0.2f),
                                radius = width * 0.65f
                            ),
                            radius = width * 0.65f,
                            center = Offset(width * 0.85f, height * 0.2f)
                        )
                        // Blob 2: Middle Left
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(secondary.copy(alpha = alpha2), Color.Transparent),
                                center = Offset(width * 0.1f, height * 0.55f),
                                radius = width * 0.55f
                            ),
                            radius = width * 0.55f,
                            center = Offset(width * 0.1f, height * 0.55f)
                        )
                        // Blob 3: Bottom Right
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(tertiary.copy(alpha = alpha3), Color.Transparent),
                                center = Offset(width * 0.75f, height * 0.85f),
                                radius = width * 0.6f
                            ),
                            radius = width * 0.6f,
                            center = Offset(width * 0.75f, height * 0.85f)
                        )
                    }
                    "aurora" -> {
                        val alpha = if (isDark) 0.3f else 0.15f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    primary.copy(alpha = alpha),
                                    secondary.copy(alpha = alpha),
                                    tertiary.copy(alpha = alpha),
                                    primary.copy(alpha = alpha)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(width, height)
                            )
                        )
                    }
                    "cyber_grid" -> {
                        val gridColor = primary.copy(alpha = 0.1f)
                        val gridSize = 40.dp.toPx()
                        for (x in 0..width.toInt() step gridSize.toInt()) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x.toFloat(), 0f),
                                end = Offset(x.toFloat(), height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        for (y in 0..height.toInt() step gridSize.toInt()) {
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y.toFloat()),
                                end = Offset(width, y.toFloat()),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                    "matrix" -> {
                        val matrixColor = primary.copy(alpha = 0.15f)
                        val step = 20.dp.toPx()
                        for (x in 0..width.toInt() step step.toInt()) {
                            for (y in 0..height.toInt() step step.toInt()) {
                                if (Math.random() > 0.7) {
                                    drawCircle(
                                        color = matrixColor,
                                        radius = 2.dp.toPx(),
                                        center = Offset(x.toFloat(), y.toFloat())
                                    )
                                }
                            }
                        }
                    }
                    "neon" -> {
                        // Neon dark background with slight vignette
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                center = Offset(width / 2, height / 2),
                                radius = width.coerceAtLeast(height)
                            )
                        )
                    }
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
    isDark: Boolean, // Kept for API compatibility
    isTvOptimized: Boolean = false
): Modifier = composed {
    val effect = LocalThemeEffect.current
    
    when (effect) {
        "glassmorphism", "aurora" -> {
            val surfaceColor = MaterialTheme.colorScheme.surface
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            
            val bgModifier = if (isTvOptimized) {
                Modifier.background(surfaceColor)
            } else {
                Modifier.background(surfaceColor.copy(alpha = 0.3f))
            }
            
            this
                .clip(shape)
                .then(bgModifier)
                .border(borderWidth, onSurfaceColor.copy(alpha = 0.15f), shape)
        }
        "neon", "cyber_grid", "matrix" -> {
            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceColor = MaterialTheme.colorScheme.surface
            this
                .clip(shape)
                .background(surfaceColor.copy(alpha = 0.8f))
                .border(borderWidth, primaryColor.copy(alpha = 0.8f), shape)
                .drawBehind {
                    val paint = androidx.compose.ui.graphics.Paint().apply {
                        this.color = primaryColor.copy(alpha = 0.5f)
                        val frameworkPaint = this.asFrameworkPaint()
                        frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                            12.dp.toPx(),
                            android.graphics.BlurMaskFilter.Blur.OUTER
                        )
                    }
                    drawContext.canvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        16.dp.toPx(), 16.dp.toPx(), paint
                    )
                }
        }
        else -> {
            this
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(borderWidth, MaterialTheme.colorScheme.outlineVariant, shape)
        }
    }
}

fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 8.dp
): Modifier = this.drawBehind {
    val paint = androidx.compose.ui.graphics.Paint().apply {
        this.color = color
        val frameworkPaint = this.asFrameworkPaint()
        frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
            radius.toPx(),
            android.graphics.BlurMaskFilter.Blur.NORMAL
        )
    }
    drawContext.canvas.drawRoundRect(
        0f, 0f, size.width, size.height,
        16.dp.toPx(), 16.dp.toPx(), paint
    )
}
