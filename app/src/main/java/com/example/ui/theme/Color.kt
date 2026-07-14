package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Aero Vista Dark Colors
val AeroVistaDarkPrimary = Color(0xFF55B4FF)
val AeroVistaDarkOnPrimary = Color(0xFF000000)
val AeroVistaDarkPrimaryContainer = Color(0x3355B4FF)
val AeroVistaDarkOnPrimaryContainer = Color(0xFF55B4FF)
val AeroVistaDarkBackground = Color(0xFF15171A)
val AeroVistaDarkOnBackground = Color(0xFFE3E3E3)
val AeroVistaDarkSurface = Color(0x40252A30)
val AeroVistaDarkOnSurface = Color(0xFFE3E3E3)
val AeroVistaDarkSurfaceVariant = Color(0xD9252A30)
val AeroVistaDarkOnSurfaceVariant = Color(0xFFE3E3E3)
val AeroVistaDarkOutline = Color(0x5955B4FF)
val AeroVistaDarkSecondaryBackground = Color(0xFF2B2B2B)
val AeroVistaDarkProBadgeBg = Color(0x2655B4FF)
val AeroVistaDarkProBadgeText = Color(0xFF55B4FF)

// Aero Vista Classic Colors
val AeroVistaClassicPrimary = Color(0xFF0055FF)
val AeroVistaClassicOnPrimary = Color(0xFFFFFFFF)
val AeroVistaClassicPrimaryContainer = Color(0xFF80BFFF)
val AeroVistaClassicOnPrimaryContainer = Color(0xFF002266)
val AeroVistaClassicBackground = Color(0xFFE6F0FA)
val AeroVistaClassicOnBackground = Color(0xFF001133)
val AeroVistaClassicSurface = Color(0x99FFFFFF)
val AeroVistaClassicOnSurface = Color(0xFF001133)
val AeroVistaClassicSurfaceVariant = Color(0xFFCCE0FF)
val AeroVistaClassicOnSurfaceVariant = Color(0xFF003380)
val AeroVistaClassicOutline = Color(0xFF66A3FF)
val AeroVistaClassicSecondaryBackground = Color(0xFF00C3FF)
val AeroVistaClassicProBadgeBg = Color(0xFF0088FF)
val AeroVistaClassicProBadgeText = Color(0xFFFFFFFF)

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

// Sketch Dark Colors
val SketchDarkPrimary = Color(0xFFD7CCC8)
val SketchDarkOnPrimary = Color(0xFF3E2723)
val SketchDarkPrimaryContainer = Color(0xFF5D4037)
val SketchDarkOnPrimaryContainer = Color(0xFFEFEBE9)
val SketchDarkBackground = Color(0xFF121212)
val SketchDarkOnBackground = Color(0xFFE0E0E0)
val SketchDarkSurface = Color(0xFF1E1E1E)
val SketchDarkOnSurface = Color(0xFFE0E0E0)
val SketchDarkSurfaceVariant = Color(0xFF2D2D2D)
val SketchDarkOnSurfaceVariant = Color(0xFFD7CCC8)
val SketchDarkOutline = Color(0xFFFFFFFF)
val SketchDarkSecondaryBackground = Color(0xFF4E342E)
val SketchDarkProBadgeBg = Color(0xFF795548)
val SketchDarkProBadgeText = Color(0xFFFFFFFF)

// Sketch Light Colors
val SketchLightPrimary = Color(0xFF5D4037)
val SketchLightOnPrimary = Color(0xFFFFFFFF)
val SketchLightPrimaryContainer = Color(0xFFEFEBE9)
val SketchLightOnPrimaryContainer = Color(0xFF3E2723)
val SketchLightBackground = Color(0xFFF5F0EB)
val SketchLightOnBackground = Color(0xFF1A1A1A)
val SketchLightSurface = Color(0xFFFFFFFF)
val SketchLightOnSurface = Color(0xFF1A1A1A)
val SketchLightSurfaceVariant = Color(0xFFF0ECE6)
val SketchLightOnSurfaceVariant = Color(0xFF3E2723)
val SketchLightOutline = Color(0xFF1A1A1A)
val SketchLightSecondaryBackground = Color(0xFF8D6E63)
val SketchLightProBadgeBg = Color(0xFFBCAAA4)
val SketchLightProBadgeText = Color(0xFF000000)

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


