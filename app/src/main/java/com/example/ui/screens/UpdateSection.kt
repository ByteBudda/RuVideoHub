package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.manager.UpdateInfo
import com.example.manager.UpdateManager
import com.example.manager.DownloadState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.ui.theme.liquidGlass
import kotlinx.coroutines.launch

@Composable
fun UpdateSection(isDarkTheme: Boolean, isTvOptimized: Boolean) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    
    var showDialog by remember { mutableStateOf(false) }
    val downloadState by UpdateManager.downloadState.collectAsStateWithLifecycle()


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(16.dp), borderWidth = 1.dp, isDark = isDarkTheme, isTvOptimized = isTvOptimized)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isChecking) return@clickable
                    isChecking = true
                    coroutineScope.launch {
                        val info = UpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
                        isChecking = false
                        if (info != null) {
                            if (info.hasUpdate) {
                                updateInfo = info
                                showDialog = true
                            } else {
                                Toast.makeText(context, "У вас установлена последняя версия", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Не удалось проверить наличие обновлений", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Проверить обновления",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Текущая версия: ${BuildConfig.VERSION_NAME}",
                        fontSize = 10.sp,
                        color = GreyText
                    )
                }
            }
        }
    }

    
    if (downloadState is DownloadState.Downloading) {
        val progress = (downloadState as DownloadState.Downloading).progress
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Скачивание обновления") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {}
        )
    } else if (downloadState is DownloadState.Error) {
        val errorMsg = (downloadState as DownloadState.Error).message
        AlertDialog(
            onDismissRequest = { UpdateManager.downloadState.value = DownloadState.Idle },
            title = { Text("Ошибка") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { UpdateManager.downloadState.value = DownloadState.Idle }) { Text("OK") }
            }
        )
    }

    if (showDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Доступно обновление") },
            text = {
                Column {
                    Text("Новая версия: ${updateInfo!!.latestVersion}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(updateInfo!!.releaseNotes, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    UpdateManager.startDownloadAndInstall(context, updateInfo!!.downloadUrl, updateInfo!!.latestVersion)
                    Toast.makeText(context, "Загрузка началась...", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Скачать и установить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
