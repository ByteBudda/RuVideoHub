package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

data class CustomTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val secondaryContainer: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val effect: String = "none",
    val backgroundGradientStart: Color? = null,
    val backgroundGradientEnd: Color? = null,
    val cardOpacity: Float = 0.3f,
    val glowColor: Color? = null,
    val glowRadius: Int = 0,
    val glowEnabled: Boolean = false,
    val cardBorderWidth: Float = 1f,
    val cardBorderColor: Color? = null,
    val cardShadowColor: Color? = null,
    val cardShadowElevation: Float = 0f,
    val cardCornerRadius: Int = 16
) {
    fun toColorScheme(): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                secondaryContainer = secondaryContainer,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                secondaryContainer = secondaryContainer,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer
            )
        }
    }

    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("isDark", isDark)
        obj.put("primary", colorToHex(primary))
        obj.put("onPrimary", colorToHex(onPrimary))
        obj.put("primaryContainer", colorToHex(primaryContainer))
        obj.put("onPrimaryContainer", colorToHex(onPrimaryContainer))
        obj.put("background", colorToHex(background))
        obj.put("onBackground", colorToHex(onBackground))
        obj.put("surface", colorToHex(surface))
        obj.put("onSurface", colorToHex(onSurface))
        obj.put("surfaceVariant", colorToHex(surfaceVariant))
        obj.put("onSurfaceVariant", colorToHex(onSurfaceVariant))
        obj.put("outline", colorToHex(outline))
        obj.put("secondaryContainer", colorToHex(secondaryContainer))
        obj.put("tertiaryContainer", colorToHex(tertiaryContainer))
        obj.put("onTertiaryContainer", colorToHex(onTertiaryContainer))
        obj.put("effect", effect)
        
        obj.put("backgroundGradientStart", backgroundGradientStart?.let { colorToHex(it) } ?: "")
        obj.put("backgroundGradientEnd", backgroundGradientEnd?.let { colorToHex(it) } ?: "")
        obj.put("cardOpacity", cardOpacity.toDouble())
        obj.put("glowColor", glowColor?.let { colorToHex(it) } ?: "")
        obj.put("glowRadius", glowRadius)
        obj.put("glowEnabled", glowEnabled)
        obj.put("cardBorderWidth", cardBorderWidth.toDouble())
        obj.put("cardBorderColor", cardBorderColor?.let { colorToHex(it) } ?: "")
        obj.put("cardShadowColor", cardShadowColor?.let { colorToHex(it) } ?: "")
        obj.put("cardShadowElevation", cardShadowElevation.toDouble())
        obj.put("cardCornerRadius", cardCornerRadius)
        
        return obj.toString()
    }

    private fun colorToHex(color: Color): String {
        val a = (color.alpha * 255).toInt()
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return String.format("#%02X%02X%02X%02X", a, r, g, b)
    }

    companion object {
        fun fromJson(json: String): CustomTheme {
            val obj = JSONObject(json)
            
            val bgGradStartStr = obj.optString("backgroundGradientStart", "")
            val bgGradEndStr = obj.optString("backgroundGradientEnd", "")
            val glowColorStr = obj.optString("glowColor", "")
            val cardBorderColorStr = obj.optString("cardBorderColor", "")
            val cardShadowColorStr = obj.optString("cardShadowColor", "")

            val backgroundGradientStart = if (bgGradStartStr.isNotEmpty()) parseColor(bgGradStartStr) else null
            val backgroundGradientEnd = if (bgGradEndStr.isNotEmpty()) parseColor(bgGradEndStr) else null
            val cardOpacity = obj.optDouble("cardOpacity", 0.3).toFloat()
            val glowColor = if (glowColorStr.isNotEmpty()) parseColor(glowColorStr) else null
            val glowRadius = obj.optInt("glowRadius", 0)
            val glowEnabled = obj.optBoolean("glowEnabled", false)
            
            val cardBorderWidth = obj.optDouble("cardBorderWidth", 1.0).toFloat()
            val cardBorderColor = if (cardBorderColorStr.isNotEmpty()) parseColor(cardBorderColorStr) else null
            val cardShadowColor = if (cardShadowColorStr.isNotEmpty()) parseColor(cardShadowColorStr) else null
            val cardShadowElevation = obj.optDouble("cardShadowElevation", 0.0).toFloat()
            val cardCornerRadius = obj.optInt("cardCornerRadius", 16)

            return CustomTheme(
                id = obj.getString("id"),
                name = obj.getString("name"),
                isDark = obj.optBoolean("isDark", true),
                primary = parseColor(obj.getString("primary")),
                onPrimary = parseColor(obj.getString("onPrimary")),
                primaryContainer = parseColor(obj.getString("primaryContainer")),
                onPrimaryContainer = parseColor(obj.getString("onPrimaryContainer")),
                background = parseColor(obj.getString("background")),
                onBackground = parseColor(obj.getString("onBackground")),
                surface = parseColor(obj.getString("surface")),
                onSurface = parseColor(obj.getString("onSurface")),
                surfaceVariant = parseColor(obj.getString("surfaceVariant")),
                onSurfaceVariant = parseColor(obj.getString("onSurfaceVariant")),
                outline = parseColor(obj.getString("outline")),
                secondaryContainer = parseColor(obj.getString("secondaryContainer")),
                tertiaryContainer = parseColor(obj.getString("tertiaryContainer")),
                onTertiaryContainer = parseColor(obj.getString("onTertiaryContainer")),
                effect = obj.optString("effect", "none"),
                backgroundGradientStart = backgroundGradientStart,
                backgroundGradientEnd = backgroundGradientEnd,
                cardOpacity = cardOpacity,
                glowColor = glowColor,
                glowRadius = glowRadius,
                glowEnabled = glowEnabled,
                cardBorderWidth = cardBorderWidth,
                cardBorderColor = cardBorderColor,
                cardShadowColor = cardShadowColor,
                cardShadowElevation = cardShadowElevation,
                cardCornerRadius = cardCornerRadius
            )
        }

        private fun parseColor(colorString: String): Color {
            var colorStr = colorString
            if (!colorStr.startsWith("#")) {
                colorStr = "#$colorStr"
            }
            if (colorStr.length == 7) {
                colorStr = colorStr.replace("#", "#FF")
            }
            return try {
                Color(android.graphics.Color.parseColor(colorStr))
            } catch (e: Exception) {
                Color.Black // Fallback if parsing fails
            }
        }
    }
}
