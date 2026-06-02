package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sleek Video Hub", appName)
  }

  @Test
  fun `test live rutube api`() {
    println("--- START RUTUBE LIVE API TEST ---")
    try {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://rutube.ru/api/video/?format=json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            println("Response Code: ${response.code}")
            val body = response.body?.string() ?: ""
            println("Response Body Length: ${body.length}")
            if (body.length > 1000) {
                println("Response Body (Truncated): ${body.take(1000)}")
            } else {
                println("Response Body: $body")
            }
        }
    } catch (e: Exception) {
        println("Rutube Test Error: ${e.message}")
        e.printStackTrace()
    }
  }
}
