package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color disabled to retain the exact Sleek theme style branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme


  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
