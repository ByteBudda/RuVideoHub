package com.example.data.rutube

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RutubeRetrofitClient {
    @Volatile
    var sessionId: String? = null

    @Volatile
    var csrfToken: String? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://rutube.ru/")

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

            chain.proceed(builder.build())
        }
        .build()

    val apiService: RutubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RutubeApiService::class.java)
    }
}
