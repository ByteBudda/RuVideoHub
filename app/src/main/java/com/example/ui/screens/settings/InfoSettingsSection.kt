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
fun InfoSettingsSection(
    isDarkTheme: Boolean,
    isTvOptimized: Boolean
) {
    var showAgreementDialog by remember { mutableStateOf(false) }

    SettingsGroup(title = "Информация", isDarkTheme = isDarkTheme, isTvOptimized = isTvOptimized) {
        SettingsRow(
            icon = Icons.Default.Description,
            title = "Пользовательское соглашение",
            description = "Просмотреть условия использования приложения",
            onClick = { showAgreementDialog = true },
            control = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        if (showAgreementDialog) {
            FullAgreementDialog(onDismiss = { showAgreementDialog = false })
        }
    }
}
