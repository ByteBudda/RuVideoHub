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

  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private val exitRunnable = Runnable { finishAndRemoveTask() }
  private val toastRunnable = Runnable {
    android.widget.Toast.makeText(this, "Удерживайте кнопку НАЗАД 3 секунды для выхода", android.widget.Toast.LENGTH_SHORT).show()
  }

  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
    if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
      if (event?.repeatCount == 0) {
        handler.postDelayed(toastRunnable, 1000)
        handler.postDelayed(exitRunnable, 3000)
      }
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
    if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
      handler.removeCallbacks(toastRunnable)
      handler.removeCallbacks(exitRunnable)
      if (!isFinishing && !isDestroyed) {
        onBackPressedDispatcher.onBackPressed()
      }
      return true
    }
    return super.onKeyUp(keyCode, event)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(
        android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    )
    enableEdgeToEdge(
        statusBarStyle = androidx.activity.SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
        ),
        navigationBarStyle = androidx.activity.SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
        )
    )
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
    intent?.getStringExtra("restore_video_id")?.let { id ->
        viewModel.loadVideoByUrlOrId(id)
    }
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (viewModel.currentSelectedVideo.value != null && !viewModel.isTvOptimized.value) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        try {
          val params = android.app.PictureInPictureParams.Builder().build()
          enterPictureInPictureMode(params)
        } catch (e: Exception) {
          android.util.Log.e("MainActivity", "Error entering PiP mode in onUserLeaveHint", e)
        }
      }
    }
  }

  override fun onPictureInPictureModeChanged(
      isInPictureInPictureMode: Boolean,
      newConfig: android.content.res.Configuration
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    viewModel.setInPipMode(isInPictureInPictureMode)
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.data?.toString()?.let { url ->
        viewModel.loadVideoByUrlOrId(url)
    }
    intent.getStringExtra("restore_video_id")?.let { id ->
        viewModel.loadVideoByUrlOrId(id)
    }
  }
}

