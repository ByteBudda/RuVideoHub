package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.SleekVideoHubApp
import com.example.viewmodel.VideoViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.koin.core.context.GlobalContext

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = GlobalContext.get().get<VideoViewModel>()
    
    composeTestRule.setContent { 
      MyApplicationTheme { 
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        SleekVideoHubApp(viewModel = viewModel) 
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
