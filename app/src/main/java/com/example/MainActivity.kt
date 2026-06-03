package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
      MyApplicationTheme {
        SleekVideoHubApp(viewModel = viewModel)
      }
    }
  }
}

