package com.example.ui.theme

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// Helper function to convert Color to Hex string including alpha channel
fun colorToHexStr(color: Color): String {
    val a = (color.alpha * 255).toInt()
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeExporterDialog(
    customThemes: List<CustomTheme>,
    onDismiss: () -> Unit,
    onSelectTheme: (CustomTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Экспорт темы (.rvht)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Выберите созданную вами тему для экспорта в файл .rvht:",
                    fontSize = 12.sp,
                    color = GreyText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (customThemes.isEmpty()) {
                    Text(
                        text = "У вас нет кастомных тем. Создайте сначала тему в Конструкторе тем.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    customThemes.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { onSelectTheme(theme) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Color preview circle
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(theme.primary, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (theme.isDark) "Тёмная" else "Светлая",
                                    fontSize = 11.sp,
                                    color = GreyText
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCreatorDialog(
    initialTheme: CustomTheme? = null,
    onDismiss: () -> Unit,
    onSave: (CustomTheme) -> Unit
) {
    var themeName by remember { mutableStateOf(initialTheme?.name ?: "Моя Тема") }
    var isDark by remember { mutableStateOf(initialTheme?.isDark ?: true) }
    
    var primaryHex by remember { mutableStateOf(initialTheme?.primary?.let { colorToHexStr(it) } ?: "#FFBB86FC") }
    var backgroundHex by remember { mutableStateOf(initialTheme?.background?.let { colorToHexStr(it) } ?: "#FF121212") }
    var surfaceHex by remember { mutableStateOf(initialTheme?.surface?.let { colorToHexStr(it) } ?: "#FF1E1E1E") }
    
    var gradientEnabled by remember { mutableStateOf(initialTheme?.let { it.backgroundGradientStart != null && it.backgroundGradientEnd != null } ?: false) }
    var gradientEndHex by remember { mutableStateOf(initialTheme?.backgroundGradientEnd?.let { colorToHexStr(it) } ?: "#FF2D1F3D") }
    
    var cardOpacity by remember { mutableStateOf(initialTheme?.cardOpacity ?: 0.3f) }
    var cardCornerRadius by remember { mutableStateOf(initialTheme?.cardCornerRadius?.toFloat() ?: 16f) }
    
    var borderCustomEnabled by remember { mutableStateOf(initialTheme?.cardBorderColor != null) }
    var borderHex by remember { mutableStateOf(initialTheme?.cardBorderColor?.let { colorToHexStr(it) } ?: "#33FFFFFF") }
    var borderWidth by remember { mutableStateOf(initialTheme?.cardBorderWidth ?: 1f) }
    
    var shadowEnabled by remember { mutableStateOf(initialTheme?.let { it.cardShadowElevation > 0f } ?: false) }
    var shadowHex by remember { mutableStateOf(initialTheme?.cardShadowColor?.let { colorToHexStr(it) } ?: "#40000000") }
    var shadowElevation by remember { mutableStateOf(initialTheme?.cardShadowElevation ?: 4f) }
    
    var glowEnabled by remember { mutableStateOf(initialTheme?.glowEnabled ?: false) }
    var glowColorHex by remember { mutableStateOf(initialTheme?.glowColor?.let { colorToHexStr(it) } ?: "#FFBB86FC") }
    var glowRadius by remember { mutableStateOf(initialTheme?.glowRadius?.toFloat() ?: 8f) }
    
    var selectedEffect by remember { mutableStateOf(initialTheme?.effect ?: "none") }
    
    val primaryPresets = listOf("#FFBB86FC", "#FF03DAC5", "#FFFF4081", "#FFFFAB40", "#FF2979FF", "#FF00E676", "#FFFFD600", "#FFE040FB")
    val backgroundPresets = listOf("#FF121212", "#FF1A1A24", "#FF2D1F3D", "#FFF5F5F7", "#FFF4EBD0")
    val surfacePresets = listOf("#FF1E1E1E", "#FF242432", "#FF3D2C54", "#FFFFFFFF", "#FFF0E4C3")
    val glowPresets = listOf("#FFBB86FC", "#FF03DAC5", "#FFFF4081", "#FF2979FF")
    val borderPresets = listOf("#33FFFFFF", "#FFBB86FC", "#FF03DAC5", "#FFFF4081", "#33000000")
    val shadowPresets = listOf("#40000000", "#66BB86FC", "#6603DAC5", "#66FF4081")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    text = "Конструктор тем",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Live theme preview box
                ThemeLivePreview(
                    isDark = isDark,
                    primaryHex = primaryHex,
                    backgroundHex = backgroundHex,
                    surfaceHex = surfaceHex,
                    gradientEnabled = gradientEnabled,
                    gradientEndHex = gradientEndHex,
                    cardOpacity = cardOpacity,
                    cardCornerRadius = cardCornerRadius,
                    borderCustomEnabled = borderCustomEnabled,
                    borderHex = borderHex,
                    borderWidth = borderWidth,
                    shadowEnabled = shadowEnabled,
                    shadowHex = shadowHex,
                    shadowElevation = shadowElevation,
                    glowEnabled = glowEnabled,
                    glowColorHex = glowColorHex,
                    glowRadius = glowRadius
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // Settings List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Name input
                    OutlinedTextField(
                        value = themeName,
                        onValueChange = { themeName = it },
                        label = { Text("Название темы") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    // 2. Is Dark switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Тёмная тема", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Оптимизировать контраст для темного режима", fontSize = 11.sp, color = GreyText)
                        }
                        Switch(checked = isDark, onCheckedChange = { isDark = it })
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    
                    // 3. Primary Color Color Picker
                    ColorSection(
                        title = "Основной цвет (Primary)",
                        subtitle = "Цвет кнопок, активных элементов и акцентов",
                        hexValue = primaryHex,
                        onHexChange = { primaryHex = it },
                        presets = primaryPresets
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 4. Background gradient settings
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Градиентный фон", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Использовать плавный переход между цветами", fontSize = 11.sp, color = GreyText)
                            }
                            Switch(checked = gradientEnabled, onCheckedChange = { gradientEnabled = it })
                        }
                        
                        ColorSection(
                            title = if (gradientEnabled) "Цвет фона (Начало градиента)" else "Цвет фона",
                            subtitle = "Базовый цвет подложки приложения",
                            hexValue = backgroundHex,
                            onHexChange = { backgroundHex = it },
                            presets = backgroundPresets
                        )
                        
                        if (gradientEnabled) {
                            ColorSection(
                                title = "Цвет фона (Конец градиента)",
                                subtitle = "Вторая точка градиентного фона",
                                hexValue = gradientEndHex,
                                onHexChange = { gradientEndHex = it },
                                presets = backgroundPresets
                            )
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 5. Surface Color
                    ColorSection(
                        title = "Цвет поверхностей (Surface)",
                        subtitle = "Фон карточек и всплывающих панелей",
                        hexValue = surfaceHex,
                        onHexChange = { surfaceHex = it },
                        presets = surfacePresets
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 6. Card Opacity Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Прозрачность карточек", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(String.format("%.0f%%", cardOpacity * 100), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Уровень прозрачности стеклянных (glassmorphism) поверхностей", fontSize = 11.sp, color = GreyText)
                        Slider(
                            value = cardOpacity,
                            onValueChange = { cardOpacity = it },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 6b. Card Corner Radius Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Скругление углов", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${cardCornerRadius.toInt()} dp", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Установите скругление углов для всех информационных карточек", fontSize = 11.sp, color = GreyText)
                        Slider(
                            value = cardCornerRadius,
                            onValueChange = { cardCornerRadius = it },
                            valueRange = 0f..32f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 6c. Custom Border / Edge Settings
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Настраиваемая кайма / обводка", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Изменить цвет и толщину границ карточек", fontSize = 11.sp, color = GreyText)
                            }
                            Switch(checked = borderCustomEnabled, onCheckedChange = { borderCustomEnabled = it })
                        }
                        
                        if (borderCustomEnabled) {
                            ColorSection(
                                title = "Цвет каймы",
                                subtitle = "Оттенок тонкого разделительного канта",
                                hexValue = borderHex,
                                onHexChange = { borderHex = it },
                                presets = borderPresets
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Толщина каймы", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(String.format("%.1f dp", borderWidth), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = borderWidth,
                                    onValueChange = { borderWidth = it },
                                    valueRange = 0.5f..5.0f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 6d. Custom Shadow Settings
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Настраиваемая тень", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Добавить мягкую реалистичную тень под элементами", fontSize = 11.sp, color = GreyText)
                            }
                            Switch(checked = shadowEnabled, onCheckedChange = { shadowEnabled = it })
                        }
                        
                        if (shadowEnabled) {
                            ColorSection(
                                title = "Цвет тени",
                                subtitle = "Тональный оттенок проецируемой тени",
                                hexValue = shadowHex,
                                onHexChange = { shadowHex = it },
                                presets = shadowPresets
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Мягкость тени (Высота)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text("${shadowElevation.toInt()} dp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = shadowElevation,
                                    onValueChange = { shadowElevation = it },
                                    valueRange = 2f..24f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 7. Glow Settings
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Неоновое свечение (Glow)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Добавить мягкое свечение вокруг карточек", fontSize = 11.sp, color = GreyText)
                            }
                            Switch(checked = glowEnabled, onCheckedChange = { glowEnabled = it })
                        }
                        
                        if (glowEnabled) {
                            ColorSection(
                                title = "Цвет свечения",
                                subtitle = "Оттенок внешнего неонового ореола",
                                hexValue = glowColorHex,
                                onHexChange = { glowColorHex = it },
                                presets = glowPresets
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Радиус свечения", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text("${glowRadius.toInt()} dp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = glowRadius,
                                    onValueChange = { glowRadius = it },
                                    valueRange = 2f..24f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 8. Special background effect
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Специальный фоновый эффект", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Анимированные или декоративные слои на заднем плане", fontSize = 11.sp, color = GreyText)
                        
                        val effects = listOf(
                            "none" to "Без эффекта",
                            "glassmorphism" to "Стеклянные сферы (Liquid Glass)",
                            "aurora" to "Полярное сияние (Aurora)",
                            "cyber_grid" to "Кибер-сетка (Neon Grid)",
                            "matrix" to "Матричный дождь (Digital Rain)",
                            "neon" to "Неоновый виньетка (Vignette)"
                        )
                        
                        var effectDropdownExpanded by remember { mutableStateOf(false) }
                        val currentEffectLabel = effects.find { it.first == selectedEffect }?.second ?: "Без эффекта"
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { effectDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(currentEffectLabel, fontSize = 13.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            
                            DropdownMenu(
                                expanded = effectDropdownExpanded,
                                onDismissRequest = { effectDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface)
                            ) {
                                effects.forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            selectedEffect = id
                                            effectDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            val parsedPrimary = safeParseColor(primaryHex, Color(0xFFD0BCFF))
                            val parsedBackground = safeParseColor(backgroundHex, Color(0xFF121212))
                            val parsedSurface = safeParseColor(surfaceHex, Color(0xFF1E1E1E))
                            val parsedGradientStart = if (gradientEnabled) parsedBackground else null
                            val parsedGradientEnd = if (gradientEnabled) safeParseColor(gradientEndHex, Color(0xFF2D1F3D)) else null
                            val parsedGlowColor = if (glowEnabled) safeParseColor(glowColorHex, parsedPrimary) else null
                            val parsedBorderColor = if (borderCustomEnabled) safeParseColor(borderHex, Color.Transparent) else null
                            val parsedShadowColor = if (shadowEnabled) safeParseColor(shadowHex, Color.Transparent) else null
                            
                            // Let's compute M3 colors contrast-safely
                            val onPrimaryVal = if (parsedPrimary.isDarkColor()) Color.White else Color.Black
                            val onBackgroundVal = if (parsedBackground.isDarkColor()) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
                            val onSurfaceVal = if (parsedSurface.isDarkColor()) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
                            val primaryContainerVal = if (isDark) parsedPrimary.copy(alpha = 0.2f) else parsedPrimary.copy(alpha = 0.12f)
                            val surfaceVariantVal = if (isDark) parsedSurface.copy(alpha = 0.85f) else parsedSurface.copy(alpha = 0.9f)
                            
                            val customTheme = CustomTheme(
                                id = initialTheme?.id ?: "custom_${System.currentTimeMillis()}",
                                name = themeName.trim().ifEmpty { "Моя Тема" },
                                isDark = isDark,
                                primary = parsedPrimary,
                                onPrimary = onPrimaryVal,
                                primaryContainer = primaryContainerVal,
                                onPrimaryContainer = parsedPrimary,
                                background = parsedBackground,
                                onBackground = onBackgroundVal,
                                surface = parsedSurface,
                                onSurface = onSurfaceVal,
                                surfaceVariant = surfaceVariantVal,
                                onSurfaceVariant = onSurfaceVal,
                                outline = parsedPrimary.copy(alpha = 0.35f),
                                secondaryContainer = if (isDark) Color(0xFF2B2B2B) else Color(0xFFECECEC),
                                tertiaryContainer = parsedPrimary.copy(alpha = 0.15f),
                                onTertiaryContainer = parsedPrimary,
                                effect = selectedEffect,
                                backgroundGradientStart = parsedGradientStart,
                                backgroundGradientEnd = parsedGradientEnd,
                                cardOpacity = cardOpacity,
                                glowColor = parsedGlowColor,
                                glowRadius = if (glowEnabled) glowRadius.toInt() else 0,
                                glowEnabled = glowEnabled,
                                cardBorderWidth = if (borderCustomEnabled) borderWidth else 1f,
                                cardBorderColor = parsedBorderColor,
                                cardShadowColor = parsedShadowColor,
                                cardShadowElevation = if (shadowEnabled) shadowElevation else 0f,
                                cardCornerRadius = cardCornerRadius.toInt()
                            )
                            onSave(customTheme)
                        }
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

// Helper functions for HSV conversions
fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv
    )
    return Triple(hsv[0], hsv[1], hsv[2])
}

fun hsvToHex(hue: Float, saturation: Float, value: Float): String {
    val hsv = floatArrayOf(hue, saturation, value)
    val colorInt = android.graphics.Color.HSVToColor(hsv)
    return String.format("#%08X", colorInt)
}

@Composable
fun CircularColorPicker(
    hexValue: String,
    onHexChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedColor = safeParseColor(hexValue, Color.Red)
    val (hue, sat, _) = remember(hexValue) {
        colorToHsv(parsedColor)
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
    ) {
        val width = constraints.maxWidth.toFloat()
        val radius = width / 2f
        val center = Offset(radius, radius)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(radius) {
                    fun updateColorFromOffset(offset: Offset) {
                        val dx = offset.x - radius
                        val dy = offset.y - radius
                        val d = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (d > 0) {
                            var angle = kotlin.math.atan2(dy, dx)
                            var angleDegrees = Math.toDegrees(angle.toDouble()).toFloat()
                            if (angleDegrees < 0) {
                                angleDegrees += 360f
                            }
                            val s = (d / radius).coerceIn(0f, 1f)
                            val newHex = hsvToHex(angleDegrees, s, 1f)
                            onHexChange(newHex)
                        } else {
                            onHexChange(hsvToHex(0f, 0f, 1f))
                        }
                    }

                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            updateColorFromOffset(change.position)
                        }
                    )
                }
                .pointerInput(radius) {
                    detectTapGestures(
                        onTap = { offset ->
                            val dx = offset.x - radius
                            val dy = offset.y - radius
                            val d = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (d <= radius) {
                                var angle = kotlin.math.atan2(dy, dx)
                                var angleDegrees = Math.toDegrees(angle.toDouble()).toFloat()
                                if (angleDegrees < 0) {
                                    angleDegrees += 360f
                                }
                                val s = (d / radius).coerceIn(0f, 1f)
                                val newHex = hsvToHex(angleDegrees, s, 1f)
                                onHexChange(newHex)
                            }
                        }
                    )
                }
        ) {
            // Draw Hue Spectrum (Sweep gradient)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    ),
                    center = center
                ),
                radius = radius
            )

            // Draw Saturation (Radial gradient from white to transparent)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )

            // Selector handle position
            val angleRad = Math.toRadians(hue.toDouble())
            val currentR = sat * radius
            val handleX = radius + currentR * kotlin.math.cos(angleRad).toFloat()
            val handleY = radius + currentR * kotlin.math.sin(angleRad).toFloat()

            // Draw indicator
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = 12.dp.toPx(),
                center = Offset(handleX, handleY)
            )
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = Offset(handleX, handleY)
            )
            drawCircle(
                color = parsedColor,
                radius = 7.dp.toPx(),
                center = Offset(handleX, handleY)
            )
        }
    }
}

// Color section helper component for dialog
@Composable
fun ColorSection(
    title: String,
    subtitle: String,
    hexValue: String,
    onHexChange: (String) -> Unit,
    presets: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(subtitle, fontSize = 11.sp, color = GreyText)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Color Indicator Block
            val previewColor = safeParseColor(hexValue, Color.Transparent)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(previewColor, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            )
            
            // Manual Hex TextField
            OutlinedTextField(
                value = hexValue,
                onValueChange = onHexChange,
                placeholder = { Text("#FF...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        // Interactive Circular Color Picker Wheel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularColorPicker(
                hexValue = hexValue,
                onHexChange = onHexChange,
                modifier = Modifier.size(160.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        
        // Horizontal preset list
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { presetHex ->
                val pColor = safeParseColor(presetHex, Color.Gray)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(pColor, CircleShape)
                        .border(
                            width = if (hexValue.lowercase() == presetHex.lowercase()) 2.dp else 1.dp,
                            color = if (hexValue.lowercase() == presetHex.lowercase()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .clickable { onHexChange(presetHex) }
                )
            }
        }
    }
}

// Extra helper extension to estimate color luminance
fun Color.isDarkColor(): Boolean {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return luminance < 0.45f
}

// Extra helper functions
fun safeParseColor(hex: String, fallback: Color): Color {
    var hexClean = hex.trim()
    if (!hexClean.startsWith("#")) {
        hexClean = "#$hexClean"
    }
    if (hexClean.length == 7) {
        hexClean = hexClean.replace("#", "#FF")
    }
    return try {
        Color(android.graphics.Color.parseColor(hexClean))
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun ThemeLivePreview(
    isDark: Boolean,
    primaryHex: String,
    backgroundHex: String,
    surfaceHex: String,
    gradientEnabled: Boolean,
    gradientEndHex: String,
    cardOpacity: Float,
    cardCornerRadius: Float,
    borderCustomEnabled: Boolean,
    borderHex: String,
    borderWidth: Float,
    shadowEnabled: Boolean,
    shadowHex: String,
    shadowElevation: Float,
    glowEnabled: Boolean,
    glowColorHex: String,
    glowRadius: Float
) {
    val parsedPrimary = safeParseColor(primaryHex, Color(0xFFD0BCFF))
    val parsedBackground = safeParseColor(backgroundHex, Color(0xFF121212))
    val parsedSurface = safeParseColor(surfaceHex, Color(0xFF1E1E1E))
    val parsedGradientEnd = safeParseColor(gradientEndHex, Color(0xFF2D1F3D))
    val parsedGlowColor = safeParseColor(glowColorHex, parsedPrimary)
    val parsedBorderColor = safeParseColor(borderHex, Color(0xFF33FFFFFF))
    val parsedShadowColor = safeParseColor(shadowHex, Color(0x40000000))

    val onPrimaryColor = if (parsedPrimary.isDarkColor()) Color.White else Color.Black
    val textColor = if (isDark) Color.White else Color.Black
    val subTextColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF666666)

    // The background simulation box
    val bgModifier = if (gradientEnabled) {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(parsedBackground, parsedGradientEnd),
                start = Offset(0f, 0f),
                end = Offset(0f, 450f)
            )
        )
    } else {
        Modifier.background(parsedBackground)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .then(bgModifier)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
            // Decorative background blobs behind the card to make glassmorphism opacity visible
            Canvas(modifier = Modifier.matchParentSize()) {
                // Blur/opacity visualization circles
                drawCircle(
                    color = parsedPrimary.copy(alpha = 0.35f),
                    radius = size.width * 0.25f,
                    center = Offset(size.width * 0.22f, size.height * 0.45f)
                )
                drawCircle(
                    color = Color(0xFFFF4081).copy(alpha = 0.25f),
                    radius = size.width * 0.2f,
                    center = Offset(size.width * 0.78f, size.height * 0.65f)
                )
            }

            // Simulated Glass Card
            var cardModifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()

            // 1. Custom Shadow (applied first before background / clips)
            if (shadowEnabled && shadowElevation > 0f) {
                cardModifier = cardModifier.customShadow(
                    color = parsedShadowColor,
                    elevation = shadowElevation.dp,
                    borderRadius = cardCornerRadius.dp
                )
            }

            // 2. Neon Glow
            if (glowEnabled && glowRadius > 0f) {
                cardModifier = cardModifier.neonGlow(
                    color = parsedGlowColor,
                    radius = glowRadius.dp,
                    cornerRadius = cardCornerRadius.dp
                )
            }

            // 3. Clip shape
            cardModifier = cardModifier.clip(RoundedCornerShape(cardCornerRadius.dp))

            // 4. Background (surface with opacity)
            cardModifier = cardModifier.background(parsedSurface.copy(alpha = cardOpacity))

            // 5. Border/Stroke
            val finalBorderWidth = if (borderCustomEnabled) borderWidth.dp else 1.dp
            val finalBorderColor = if (borderCustomEnabled) parsedBorderColor else textColor.copy(alpha = 0.15f)
            cardModifier = cardModifier.border(finalBorderWidth, finalBorderColor, RoundedCornerShape(cardCornerRadius.dp))

            // Card content
            Column(
                modifier = cardModifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = parsedPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Стеклянный Плеер",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(parsedPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PREVIEW",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = parsedPrimary
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "The Midnight Special",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1
                    )
                    Text(
                        text = "MKBHD • 10M views • 2 days ago",
                        fontSize = 9.sp,
                        color = subTextColor,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(parsedPrimary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = onPrimaryColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "Воспроизвести",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = onPrimaryColor
                            )
                        }
                    }
                }
            }
        }
}
