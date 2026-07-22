package com.example.ui.screens

import com.example.ui.screens.player.*

import com.example.viewmodel.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

@Composable
fun FullBackupRestoreDialog(
    viewModel: VideoViewModel,
    isDarkTheme: Boolean,
    isTvOptimized: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    
    val bookmarkedVideos by viewModel.bookmarkedSavedVideos.collectAsStateWithLifecycle()
    val recentVideos by viewModel.recentSavedVideos.collectAsStateWithLifecycle()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = viewModel.exportBackupToJson()
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    statusMessage = "Резервная копия успешно сохранена на диск!"
                } catch (e: Exception) {
                    statusMessage = "Ошибка сохранения: ${e.localizedMessage}"
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val jsonText = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                    if (jsonText.isBlank()) {
                        statusMessage = "Выбранный файл пуст или не удалось его прочесть."
                    } else {
                        viewModel.importBackupFromJson(jsonText)
                            .onSuccess { msg ->
                                statusMessage = "Успешное восстановление: $msg"
                            }
                            .onFailure { err ->
                                statusMessage = "Ошибка восстановления: ${err.localizedMessage}"
                            }
                    }
                } catch (e: Exception) {
                    statusMessage = "Ошибка чтения файла: ${e.localizedMessage}"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Резервное копирование",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        text = {
            val backupScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(backupScrollState)
                    .mouseDragScrollable(backupScrollState, isVertical = true),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Резервная копия сохраняет ваши настройки, историю просмотров и закладки в файл .json на диске. Вы можете импортировать этот файл на любом устройстве.",
                    fontSize = 12.sp,
                    color = GreyText
                )

                if (statusMessage.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // EXPORT SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Экспорт данных",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "• Настройки приложения\n• Закладки (${bookmarkedVideos.size} шт.)\n• История просмотров (${recentVideos.size} шт.)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            try {
                                createDocumentLauncher.launch("sleek_video_hub_backup.json")
                            } catch (e: Exception) {
                                statusMessage = "Не удалось запустить выбор сохранения: ${e.localizedMessage}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sleekTvFocus(RoundedCornerShape(8.dp), scaleAmount = 1.02f)
                    ) {
                        Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Экспортировать в файл .json", fontSize = 12.sp)
                    }
                }

                // IMPORT SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Импорт данных",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Выберите ранее экспортированный файл резервной копии .json для восстановления всех данных.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            try {
                                openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                            } catch (e: Exception) {
                                statusMessage = "Не удалось открыть выбор файла: ${e.localizedMessage}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sleekTvFocus(RoundedCornerShape(8.dp), scaleAmount = 1.02f)
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Импортировать из файла .json", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.sleekTvFocus(scaleAmount = 1.02f)
            ) {
                Text("Закрыть", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
