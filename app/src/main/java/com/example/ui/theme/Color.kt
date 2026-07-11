package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cyberpunk Colors
val CyberpunkPrimary = Color(0xFF00FFCC)
val CyberpunkOnPrimary = Color(0xFF000000)
val CyberpunkPrimaryContainer = Color(0xFF004433)
val CyberpunkOnPrimaryContainer = Color(0xFF00FFCC)
val CyberpunkBackground = Color(0xFF0D0A18)
val CyberpunkOnBackground = Color(0xFFE2E2E2)
val CyberpunkSurface = Color(0xFF140D24)
val CyberpunkOnSurface = Color(0xFFE2E2E2)
val CyberpunkSurfaceVariant = Color(0xFF2A1B44)
val CyberpunkOnSurfaceVariant = Color(0xFFB0A2C9)
val CyberpunkOutline = Color(0xFFFF0055)
val CyberpunkSecondaryBackground = Color(0xFF1B1130)
val CyberpunkProBadgeBg = Color(0xFFFF0055)
val CyberpunkProBadgeText = Color(0xFFFFFFFF)

// AMOLED Colors
val AmoledPrimary = Color(0xFF82B1FF)
val AmoledOnPrimary = Color(0xFF000000)
val AmoledPrimaryContainer = Color(0xFF002244)
val AmoledOnPrimaryContainer = Color(0xFF82B1FF)
val AmoledBackground = Color(0xFF000000)
val AmoledOnBackground = Color(0xFFE0E0E0)
val AmoledSurface = Color(0xFF000000)
val AmoledOnSurface = Color(0xFFE0E0E0)
val AmoledSurfaceVariant = Color(0xFF111111)
val AmoledOnSurfaceVariant = Color(0xFFAAAAAA)
val AmoledOutline = Color(0xFF333333)
val AmoledSecondaryBackground = Color(0xFF0A0A0A)
val AmoledProBadgeBg = Color(0xFF333333)
val AmoledProBadgeText = Color(0xFFFFFFFF)

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

// Slate Colors (Neutral Cool)
val SlatePrimary = Color(0xFF82B1FF)
val SlateOnPrimary = Color(0xFF002255)
val SlatePrimaryContainer = Color(0xFF004499)
val SlateOnPrimaryContainer = Color(0xFFD4E3FF)
val SlateBackground = Color(0xFF1E2329)
val SlateOnBackground = Color(0xFFE2E8F0)
val SlateSurface = Color(0xFF22272E)
val SlateOnSurface = Color(0xFFE2E8F0)
val SlateSurfaceVariant = Color(0xFF373E47)
val SlateOnSurfaceVariant = Color(0xFF94A3B8)
val SlateOutline = Color(0xFF64748B)
val SlateSecondaryBackground = Color(0xFF282E36)
val SlateProBadgeBg = Color(0xFF1E3A8A)
val SlateProBadgeText = Color(0xFFDBEAFE)

// Sepia Colors (Neutral Warm)
val SepiaPrimary = Color(0xFF8C6D46)
val SepiaOnPrimary = Color(0xFFFFFFFF)
val SepiaPrimaryContainer = Color(0xFFE6D2B3)
val SepiaOnPrimaryContainer = Color(0xFF4A3414)
val SepiaBackground = Color(0xFFF4EAD5)
val SepiaOnBackground = Color(0xFF4A4036)
val SepiaSurface = Color(0xFFEFE3CB)
val SepiaOnSurface = Color(0xFF4A4036)
val SepiaSurfaceVariant = Color(0xFFD6C8B3)
val SepiaOnSurfaceVariant = Color(0xFF7A6A56)
val SepiaOutline = Color(0xFFA89884)
val SepiaSecondaryBackground = Color(0xFFE8DAC0)
val SepiaProBadgeBg = Color(0xFFC7B191)
val SepiaProBadgeText = Color(0xFF3A2C18)

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


