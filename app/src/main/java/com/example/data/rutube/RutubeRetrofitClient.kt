// RutubeRetrofitClient.kt
package com.example.data.rutube

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object RutubeRetrofitClient {
    @Volatile
    var sessionId: String? = null

    @Volatile
    var csrfToken: String? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        android.util.Log.d("RutubeAPI", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", "https://rutube.ru/")
                .header("Origin", "https://rutube.ru")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Dest", "empty")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")

            val currentSessionId = sessionId
            val currentCsrfToken = csrfToken
            val cookiesList = mutableListOf<String>()

            if (!currentSessionId.isNullOrBlank()) {
                cookiesList.add("sessionid=$currentSessionId")
            }
            if (!currentCsrfToken.isNullOrBlank()) {
                cookiesList.add("csrftoken=$currentCsrfToken")
                builder.header("X-CSRFToken", currentCsrfToken)
            }

            if (cookiesList.isNotEmpty()) {
                builder.header("Cookie", cookiesList.joinToString("; "))
            }

            val request = builder.build()
            android.util.Log.d("RutubeAPI", "=== REQUEST ===")
            android.util.Log.d("RutubeAPI", "URL: ${request.url}")
            android.util.Log.d("RutubeAPI", "Headers: ${request.headers}")
            
            val response = chain.proceed(request)
            
            android.util.Log.d("RutubeAPI", "=== RESPONSE ===")
            android.util.Log.d("RutubeAPI", "Code: ${response.code}")
            android.util.Log.d("RutubeAPI", "Headers: ${response.headers}")
            
            if (response.code != 200) {
                val errorBody = response.peekBody(5000).string()
                android.util.Log.e("RutubeAPI", "Error body: $errorBody")
            }
            
            response
        }
        .build()

    val apiService: RutubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RutubeApiService::class.java)
    }
}