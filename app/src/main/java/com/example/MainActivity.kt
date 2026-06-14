package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.screens.SleekVideoHubApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VideoViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: VideoViewModel by viewModels {
    VideoViewModel.Factory(application)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(
        android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    )
    enableEdgeToEdge()
    setContent {
      val isDarkTheme by viewModel.isDarkTheme.collectAsState()
      MyApplicationTheme(darkTheme = isDarkTheme) {
        SleekVideoHubApp(viewModel = viewModel)
      }
    }

    // Handle deep link intent on fresh startup
    intent?.data?.toString()?.let { url ->
        viewModel.loadVideoByUrlOrId(url)
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.data?.toString()?.let { url ->
        viewModel.loadVideoByUrlOrId(url)
    }
  }
}

