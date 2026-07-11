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

@Composable
fun MyApplicationTheme(
  appTheme: String = "dark",
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when (appTheme) {
      "light" -> LightColorScheme
      "slate" -> SlateColorScheme
      "sepia" -> SepiaColorScheme
      "dark" -> DarkColorScheme
      else -> if (darkTheme) DarkColorScheme else LightColorScheme
  }
  CompositionLocalProvider(LocalAppTheme provides appTheme) {
      MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
