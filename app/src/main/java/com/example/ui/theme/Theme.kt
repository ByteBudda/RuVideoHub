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

private val SlateColorScheme = darkColorScheme(
  primary = SlatePrimary,
  onPrimary = SlateOnPrimary,
  primaryContainer = SlatePrimaryContainer,
  onPrimaryContainer = SlateOnPrimaryContainer,
  background = SlateBackground,
  onBackground = SlateOnBackground,
  surface = SlateSurface,
  onSurface = SlateOnSurface,
  surfaceVariant = SlateSurfaceVariant,
  onSurfaceVariant = SlateOnSurfaceVariant,
  outline = SlateOutline,
  secondaryContainer = SlateSecondaryBackground,
  tertiaryContainer = SlateProBadgeBg,
  onTertiaryContainer = SlateProBadgeText
)

private val SepiaColorScheme = lightColorScheme(
  primary = SepiaPrimary,
  onPrimary = SepiaOnPrimary,
  primaryContainer = SepiaPrimaryContainer,
  onPrimaryContainer = SepiaOnPrimaryContainer,
  background = SepiaBackground,
  onBackground = SepiaOnBackground,
  surface = SepiaSurface,
  onSurface = SepiaOnSurface,
  surfaceVariant = SepiaSurfaceVariant,
  onSurfaceVariant = SepiaOnSurfaceVariant,
  outline = SepiaOutline,
  secondaryContainer = SepiaSecondaryBackground,
  tertiaryContainer = SepiaProBadgeBg,
  onTertiaryContainer = SepiaProBadgeText
)

private val CyberpunkColorScheme = darkColorScheme(
  primary = CyberpunkPrimary,
  onPrimary = CyberpunkOnPrimary,
  primaryContainer = CyberpunkPrimaryContainer,
  onPrimaryContainer = CyberpunkOnPrimaryContainer,
  background = CyberpunkBackground,
  onBackground = CyberpunkOnBackground,
  surface = CyberpunkSurface,
  onSurface = CyberpunkOnSurface,
  surfaceVariant = CyberpunkSurfaceVariant,
  onSurfaceVariant = CyberpunkOnSurfaceVariant,
  outline = CyberpunkOutline,
  secondaryContainer = CyberpunkSecondaryBackground,
  tertiaryContainer = CyberpunkProBadgeBg,
  onTertiaryContainer = CyberpunkProBadgeText
)

private val AmoledColorScheme = darkColorScheme(
  primary = AmoledPrimary,
  onPrimary = AmoledOnPrimary,
  primaryContainer = AmoledPrimaryContainer,
  onPrimaryContainer = AmoledOnPrimaryContainer,
  background = AmoledBackground,
  onBackground = AmoledOnBackground,
  surface = AmoledSurface,
  onSurface = AmoledOnSurface,
  surfaceVariant = AmoledSurfaceVariant,
  onSurfaceVariant = AmoledOnSurfaceVariant,
  outline = AmoledOutline,
  secondaryContainer = AmoledSecondaryBackground,
  tertiaryContainer = AmoledProBadgeBg,
  onTertiaryContainer = AmoledProBadgeText
)

@Composable
fun MyApplicationTheme(
  appTheme: String = "dark",
  appEffect: String = "default",
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  customThemes: List<CustomTheme> = emptyList(),
  content: @Composable () -> Unit,
) {
  val customTheme = customThemes.find { it.id == appTheme }
  val colorScheme = if (customTheme != null) {
      customTheme.toColorScheme()
  } else {
      when (appTheme) {
          "light" -> LightColorScheme
          "slate" -> SlateColorScheme
          "sepia" -> SepiaColorScheme
          "cyberpunk" -> CyberpunkColorScheme
          "amoled" -> AmoledColorScheme
          "dark" -> DarkColorScheme
          else -> if (darkTheme) DarkColorScheme else LightColorScheme
      }
  }

  val themeEffect = if (appEffect != "default") appEffect else (customTheme?.effect ?: when (appTheme) {
      "dark", "light" -> "glassmorphism"
      else -> "none"
  })

  CompositionLocalProvider(
      LocalAppTheme provides appTheme,
      LocalThemeEffect provides themeEffect
  ) {
      MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
