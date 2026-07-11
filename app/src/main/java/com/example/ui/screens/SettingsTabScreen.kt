package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.viewmodel.VideoViewModel

@Composable
fun SettingsTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp) // extra padding to avoid overlapping with bottom bar
    ) {
        // Screen Header
        Column(modifier = Modifier.padding(bottom = 20.dp, start = 8.dp)) {
            Text(
                text = "Настройки",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Конфигурация приложения, тем и качества медиапотока",
                fontSize = 12.sp,
                color = GreyText,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Section: Interface Settings
        InterfaceSettingsSection(
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            isTvOptimized = isTvOptimized
        )

        // Section: Display Settings
        DisplaySettingsSection(
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            isTvOptimized = isTvOptimized
        )

        // Section: Quality Settings
        QualitySettingsSection(
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            isTvOptimized = isTvOptimized
        )

        // Section: Start Page Settings
        StartPageSettingsSection(
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            isTvOptimized = isTvOptimized
        )

        // Section: Updates
        Text(
            text = "Обновление",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 16.dp)
        )
        UpdateSection(isDarkTheme, isTvOptimized)

        // Section: Backup & Restore
        BackupSettingsSection(
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            isTvOptimized = isTvOptimized
        )

        // Section: Information
        InfoSettingsSection(
            isDarkTheme = isDarkTheme,
            isTvOptimized = isTvOptimized
        )
    }
}
