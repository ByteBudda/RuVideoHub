package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalAppTheme = compositionLocalOf { "dark" }
val LocalThemeEffect = compositionLocalOf { "none" }
val LocalCustomTheme = compositionLocalOf<CustomTheme?> { null }

private val DarkColorScheme = darkColorScheme(
  primary = DarkPrimary,
  onPrimary = DarkOnPrimary,
  primaryContainer = DarkPrimaryContainer,
  onPrimaryContainer = DarkOnPrimaryContainer,
  background = DarkBackground,
  onBackground = DarkOnBackground,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  outline = DarkOutline,
  secondaryContainer = DarkSecondaryBackground,
  tertiaryContainer = DarkProBadgeBg,
  onTertiaryContainer = DarkProBadgeText
)

private val LightColorScheme = lightColorScheme(
  primary = LightPrimary,
  onPrimary = LightOnPrimary,
  primaryContainer = LightPrimaryContainer,
  onPrimaryContainer = LightOnPrimaryContainer,
  background = LightBackground,
  onBackground = LightOnBackground,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant,
  outline = LightOutline,
  secondaryContainer = LightSecondaryBackground,
  tertiaryContainer = LightProBadgeBg,
  onTertiaryContainer = LightProBadgeText
)

private val SketchDarkColorScheme = darkColorScheme(
  primary = SketchDarkPrimary,
  onPrimary = SketchDarkOnPrimary,
  primaryContainer = SketchDarkPrimaryContainer,
  onPrimaryContainer = SketchDarkOnPrimaryContainer,
  background = SketchDarkBackground,
  onBackground = SketchDarkOnBackground,
  surface = SketchDarkSurface,
  onSurface = SketchDarkOnSurface,
  surfaceVariant = SketchDarkSurfaceVariant,
  onSurfaceVariant = SketchDarkOnSurfaceVariant,
  outline = SketchDarkOutline,
  secondaryContainer = SketchDarkSecondaryBackground,
  tertiaryContainer = SketchDarkProBadgeBg,
  onTertiaryContainer = SketchDarkProBadgeText
)

private val SketchLightColorScheme = lightColorScheme(
  primary = SketchLightPrimary,
  onPrimary = SketchLightOnPrimary,
  primaryContainer = SketchLightPrimaryContainer,
  onPrimaryContainer = SketchLightOnPrimaryContainer,
  background = SketchLightBackground,
  onBackground = SketchLightOnBackground,
  surface = SketchLightSurface,
  onSurface = SketchLightOnSurface,
  surfaceVariant = SketchLightSurfaceVariant,
  onSurfaceVariant = SketchLightOnSurfaceVariant,
  outline = SketchLightOutline,
  secondaryContainer = SketchLightSecondaryBackground,
  tertiaryContainer = SketchLightProBadgeBg,
  onTertiaryContainer = SketchLightProBadgeText
)

private val AeroVistaDarkColorScheme = darkColorScheme(
  primary = AeroVistaDarkPrimary,
  onPrimary = AeroVistaDarkOnPrimary,
  primaryContainer = AeroVistaDarkPrimaryContainer,
  onPrimaryContainer = AeroVistaDarkOnPrimaryContainer,
  background = AeroVistaDarkBackground,
  onBackground = AeroVistaDarkOnBackground,
  surface = AeroVistaDarkSurface,
  onSurface = AeroVistaDarkOnSurface,
  surfaceVariant = AeroVistaDarkSurfaceVariant,
  onSurfaceVariant = AeroVistaDarkOnSurfaceVariant,
  outline = AeroVistaDarkOutline,
  secondaryContainer = AeroVistaDarkSecondaryBackground,
  tertiaryContainer = AeroVistaDarkProBadgeBg,
  onTertiaryContainer = AeroVistaDarkProBadgeText
)

private val AeroVistaClassicColorScheme = lightColorScheme(
  primary = AeroVistaClassicPrimary,
  onPrimary = AeroVistaClassicOnPrimary,
  primaryContainer = AeroVistaClassicPrimaryContainer,
  onPrimaryContainer = AeroVistaClassicOnPrimaryContainer,
  background = AeroVistaClassicBackground,
  onBackground = AeroVistaClassicOnBackground,
  surface = AeroVistaClassicSurface,
  onSurface = AeroVistaClassicOnSurface,
  surfaceVariant = AeroVistaClassicSurfaceVariant,
  onSurfaceVariant = AeroVistaClassicOnSurfaceVariant,
  outline = AeroVistaClassicOutline,
  secondaryContainer = AeroVistaClassicSecondaryBackground,
  tertiaryContainer = AeroVistaClassicProBadgeBg,
  onTertiaryContainer = AeroVistaClassicProBadgeText
)

@Composable
fun MyApplicationTheme(
  appTheme: String = "dark",
  appEffect: String = "default",
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  customThemes: List<CustomTheme> = emptyList(),
  isTvOptimized: Boolean = false,
  content: @Composable () -> Unit,
) {
  val customTheme = customThemes.find { it.id == appTheme }
  val colorScheme = if (customTheme != null) {
      customTheme.toColorScheme()
  } else {
      when (appTheme) {
          "light" -> LightColorScheme
          "slate" -> SketchDarkColorScheme
          "sepia" -> SketchLightColorScheme
          "cyberpunk" -> AeroVistaDarkColorScheme
          "amoled" -> AeroVistaClassicColorScheme
          "dark" -> DarkColorScheme
          else -> if (darkTheme) DarkColorScheme else LightColorScheme
      }
  }

  val themeEffect = if (appEffect != "default") appEffect else (customTheme?.effect ?: when (appTheme) {
      "dark", "light", "cyberpunk" -> "glassmorphism"
      "amoled" -> "aurora"
      "slate", "sepia" -> "neon"
      else -> "none"
  })

  CompositionLocalProvider(
      LocalAppTheme provides appTheme,
      LocalThemeEffect provides themeEffect,
      LocalCustomTheme provides customTheme
  ) {
      MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
