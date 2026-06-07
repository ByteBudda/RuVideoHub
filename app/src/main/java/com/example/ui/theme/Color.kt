package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Colors
val LightPrimary = Color(0xFF6750A4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE8DEF8)
val LightOnPrimaryContainer = Color(0xFF21005D)

val LightBackground = Color(0xFFFEF7FF)
val LightOnBackground = Color(0xFF1D1B20)

val LightSurface = Color(0xFFFEF7FF)
val LightOnSurface = Color(0xFF1D1B20)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightOnSurfaceVariant = Color(0xFF49454F)

val LightOutline = Color(0xFFCAC4D0)
val LightSecondaryBackground = Color(0xFFF3EDF7)

val LightProBadgeBg = Color(0xFFD1E1FF)
val LightProBadgeText = Color(0xFF001D35)

// Dark Colors
val DarkPrimary = Color(0xFFD0BCFF)
val DarkOnPrimary = Color(0xFF381E72)
val DarkPrimaryContainer = Color(0xFF4F378B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)

val DarkBackground = Color(0xFF121212)
val DarkOnBackground = Color(0xFFE6E1E5)

val DarkSurface = Color(0xFF1C1B1F)
val DarkOnSurface = Color(0xFFE6E1E5)
val DarkSurfaceVariant = Color(0xFF2B2930)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)

val DarkOutline = Color(0xFF938F99)
val DarkSecondaryBackground = Color(0xFF211F26)

val DarkProBadgeBg = Color(0xFF00305F)
val DarkProBadgeText = Color(0xFFD1E1FF)

// Dynamic Theme Colors
val Primary: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

val OnPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onPrimary

val PrimaryContainer: Color
    @Composable
    get() = MaterialTheme.colorScheme.primaryContainer

val OnPrimaryContainer: Color
    @Composable
    get() = MaterialTheme.colorScheme.onPrimaryContainer

val Background: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

val OnBackground: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val Surface: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

val OnSurface: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurface

val SurfaceVariant: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

val OnSurfaceVariant: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val Outline: Color
    @Composable
    get() = MaterialTheme.colorScheme.outline

val SecondaryBackground: Color
    @Composable
    get() = MaterialTheme.colorScheme.secondaryContainer

val ProBadgeBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.tertiaryContainer

val ProBadgeText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onTertiaryContainer

// Secondary elements
val GreyText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val DarkOverlay = Color(0xB2000000)
val SemiTransparentWhite = Color(0x33FFFFFF)


