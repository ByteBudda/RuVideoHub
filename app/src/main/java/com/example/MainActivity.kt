package com.example

import com.example.viewmodel.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.screens.SleekVideoHubApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VideoViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

  private val viewModel: VideoViewModel by viewModel()

  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private val exitRunnable = Runnable { finishAndRemoveTask() }
  private val toastRunnable = Runnable {
    android.widget.Toast.makeText(this, "Удерживайте кнопку НАЗАД 3 секунды для выхода", android.widget.Toast.LENGTH_SHORT).show()
  }

  @android.annotation.SuppressLint("GestureBackNavigation")
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
      val appTheme by viewModel.appTheme.collectAsState()
      val appEffect by viewModel.appEffect.collectAsState()
      val customThemes by viewModel.customThemes.collectAsState()
      val isTvOptimized by viewModel.isTvOptimized.collectAsState()
      
      val context = androidx.compose.ui.platform.LocalContext.current
      var stableDensity = context.resources.displayMetrics.density
      try {
          val stableDpi = android.util.DisplayMetrics::class.java.getField("DENSITY_DEVICE_STABLE").getInt(null)
          stableDensity = stableDpi / 160f
      } catch (e: Throwable) {
      }
      val customDensity = androidx.compose.ui.unit.Density(density = stableDensity, fontScale = 1.0f)
      
      androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.ui.platform.LocalDensity provides customDensity
      ) {
        MyApplicationTheme(
            appTheme = appTheme,
            appEffect = appEffect,
            darkTheme = isDarkTheme,
            customThemes = customThemes,
            isTvOptimized = isTvOptimized
        ) {
          SleekVideoHubApp(viewModel = viewModel)
        }
      }
    }

    // Handle incoming intent (deep link or shared content)
    handleIntent(intent)

    // Request runtime notification permission on Android 13+ if needed
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    // Automatically resume any interrupted/pending downloads in the background
    MainScope().launch(Dispatchers.IO) {
        com.example.manager.DownloadManager.resumeAll(this@MainActivity)
    }
  }

  private fun handleIntent(intent: android.content.Intent?) {
    if (intent == null) return
    val action = intent.action
    val type = intent.type

    if (android.content.Intent.ACTION_SEND == action && type != null) {
      if ("text/plain" == type) {
        intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let { sharedText ->
          extractUrl(sharedText)?.let { url ->
            viewModel.loadVideoByUrlOrId(url)
            return
          }
        }
      }
    }

    intent.data?.toString()?.let { url ->
      viewModel.loadVideoByUrlOrId(url)
      return
    }

    intent.getStringExtra("restore_video_id")?.let { id ->
      viewModel.loadVideoByUrlOrId(id)
      return
    }
  }

  private fun extractUrl(text: String): String? {
    val regex = """https?://[^\s]+""".toRegex()
    return regex.find(text)?.value
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
    handleIntent(intent)
  }
}

