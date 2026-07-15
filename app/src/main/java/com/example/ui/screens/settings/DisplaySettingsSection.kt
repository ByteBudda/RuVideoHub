package com.example.ui.screens.settings

import com.example.viewmodel.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.launch
import com.example.ui.screens.*

@Composable
fun DisplaySettingsSection(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    val isLargeCardsMode by viewModel.isLargeCardsMode.collectAsStateWithLifecycle()
    val isHistoryLargeCardsMode by viewModel.isHistoryLargeCardsMode.collectAsStateWithLifecycle()
    val isDownloadsLargeCardsMode by viewModel.isDownloadsLargeCardsMode.collectAsStateWithLifecycle()
    val mobileGridColumns by viewModel.mobileGridColumns.collectAsStateWithLifecycle()
    val tvGridColumns by viewModel.tvGridColumns.collectAsStateWithLifecycle()
    val tvVideoGridColumns by viewModel.tvVideoGridColumns.collectAsStateWithLifecycle()
    val focusStyle by viewModel.focusStyle.collectAsStateWithLifecycle()

    SettingsGroup(title = "Отображение", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        if (!isTvOptimized) {
            // Large Cards Switch (only for Mobile)
            SettingsRow(
                icon = Icons.Default.ViewList,
                title = "Крупные карточки в каталоге",
                description = "Использовать большой размер для видео в списках",
                onClick = { viewModel.toggleLargeCardsMode() },
                control = {
                    Switch(
                        checked = isLargeCardsMode,
                        onCheckedChange = { viewModel.toggleLargeCardsMode() },
                        modifier = Modifier.testTag("setting_large_cards_switch")
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            SettingsRow(
                icon = Icons.Default.History,
                title = "Крупные карточки в истории",
                description = "Использовать большой размер для недавних видео",
                onClick = { viewModel.toggleHistoryLargeCardsMode() },
                control = {
                    Switch(
                        checked = isHistoryLargeCardsMode,
                        onCheckedChange = { viewModel.toggleHistoryLargeCardsMode() },
                        modifier = Modifier.testTag("setting_history_large_cards_switch")
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            SettingsRow(
                icon = Icons.Default.Download,
                title = "Крупные карточки в загрузках",
                description = "Использовать большой размер для загруженных видео",
                onClick = { viewModel.toggleDownloadsLargeCardsMode() },
                control = {
                    Switch(
                        checked = isDownloadsLargeCardsMode,
                        onCheckedChange = { viewModel.toggleDownloadsLargeCardsMode() },
                        modifier = Modifier.testTag("setting_downloads_large_cards_switch")
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            // Mobile Grid Columns
            var mobileGridDropdownExpanded by remember { mutableStateOf(false) }
            val mobileColsOptions = listOf(1, 2, 3)

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.GridView,
                    title = "Колонок в сетке (Мобильный)",
                    description = "Количество столбцов на мобильных устройствах",
                    onClick = { mobileGridDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "$mobileGridColumns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = mobileGridDropdownExpanded,
                    onDismissRequest = { mobileGridDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    mobileColsOptions.forEach { cols ->
                        DropdownMenuItem(
                            text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setMobileGridColumns(cols)
                                mobileGridDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        } else {
            // TV Grid Columns
            var tvGridDropdownExpanded by remember { mutableStateOf(false) }
            val tvColsOptions = listOf(3, 4, 5, 6)

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.GridOn,
                    title = "Колонок в сетке (ТВ)",
                    description = "Количество столбцов при ТВ-оптимизации",
                    onClick = { tvGridDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "$tvGridColumns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = tvGridDropdownExpanded,
                    onDismissRequest = { tvGridDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    tvColsOptions.forEach { cols ->
                        DropdownMenuItem(
                            text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setTvGridColumns(cols)
                                tvGridDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            // TV Video Grid Columns
            var tvVideoGridDropdownExpanded by remember { mutableStateOf(false) }
            val tvVideoColsOptions = listOf(3, 4, 5, 6, 7, 8)

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.OndemandVideo,
                    title = "Колонок в видео сетке (ТВ)",
                    description = "Количество столбцов для списка видео на ТВ",
                    onClick = { tvVideoGridDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "$tvVideoGridColumns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = tvVideoGridDropdownExpanded,
                    onDismissRequest = { tvVideoGridDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    tvVideoColsOptions.forEach { cols ->
                        DropdownMenuItem(
                            text = { Text("$cols", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setTvVideoGridColumns(cols)
                                tvVideoGridDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

            // TV Focus Highlight Style
            var focusStyleDropdownExpanded by remember { mutableStateOf(false) }
            val focusStyleLabel = when (focusStyle) {
                "scale" -> "Масштабирование"
                "scale_glow" -> "Масштаб + Свечение"
                "classic" -> "Простая рамка"
                "tint" -> "Подсветка фона"
                else -> "Свечение границ"
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Default.CenterFocusStrong,
                    title = "Стиль фокуса ТВ",
                    description = "Эффект подсветки при наведении пультом",
                    onClick = { focusStyleDropdownExpanded = true },
                    control = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = focusStyleLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                DropdownMenu(
                    expanded = focusStyleDropdownExpanded,
                    onDismissRequest = { focusStyleDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    listOf(
                        "glow" to "Свечение границ",
                        "scale" to "Масштабирование",
                        "scale_glow" to "Масштаб + Свечение",
                        "classic" to "Простая рамка",
                        "tint" to "Подсветка фона"
                    ).forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setFocusStyle(id)
                                focusStyleDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
